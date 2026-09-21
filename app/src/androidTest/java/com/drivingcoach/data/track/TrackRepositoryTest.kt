package com.drivingcoach.data.track

import com.drivingcoach.data.db.dao.TrackDao
import com.drivingcoach.util.GeoUtils
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

/**
 * L2 (ASPICE SWE.5) coverage for the store behind the track library.
 *
 * [TrackRepository] and [TrackDao] had no test at any level. They cannot honestly
 * have one below this level either: the thing most worth checking is what SQLite
 * does to a surveyed circuit on the way through, and an in-memory fake would
 * answer that question by not being SQLite.
 *
 * The repository merges two stores that behave differently - a read-only
 * catalogue baked into the APK and the circuits the driver saved - and the seam
 * between them is where the interesting failures live. A bundled circuit that
 * became deletable would take a circuit out of the app until the next release,
 * with no way for the driver to get it back.
 */
@HiltAndroidTest
class TrackRepositoryTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var repository: TrackRepository

    @Inject
    lateinit var trackDao: TrackDao

    @Inject
    lateinit var bundled: BundledTrackCatalog

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun aCapturedCircuitSurvivesTheRoundTripThroughTheDatabase() = runBlocking {
        val result = repository.saveCapturedTrack(
            name = "  Paddock Loop  ",
            lat1 = 41.187838, lng1 = -8.395666,
            lat2 = 41.187770, lng2 = -8.395761
        )

        assertTrue("an 11 m line is well clear of the minimum", result is SaveTrackResult.Success)
        val saved = (result as SaveTrackResult.Success).track

        assertEquals("the name should be stored trimmed", "Paddock Loop", saved.name)

        val reloaded = repository.getTrack(saved.id)
        assertNotNull("a saved circuit must be findable by the id a session stores", reloaded)
        assertEquals(41.187838, reloaded!!.startLine.lat1, 1e-9)
        assertEquals(-8.395666, reloaded.startLine.lng1, 1e-9)
        assertEquals(41.187770, reloaded.startLine.lat2, 1e-9)
        assertEquals(-8.395761, reloaded.startLine.lng2, 1e-9)
        assertEquals(
            "a circuit the driver captured must not come back claiming to be surveyed",
            GeometrySource.CAPTURED_IN_APP, reloaded.startLine.source
        )
        assertTrue("only circuits shipped in the APK are bundled", !reloaded.isBundled)
    }

    /**
     * The test this class exists for.
     *
     * Baltar's centreline is 140 walked waypoints, and two features read it back:
     * the corridor filter that keeps queue time out of the statistics (AS-18) and
     * the lap length that decides whether a detected lap could have been driven
     * (LD-22). Both degrade quietly if the ring loses precision, loses its ordering
     * or loses a point on the way through SQLite - the app would still work, and
     * would simply be slightly wrong about that circuit forever after.
     *
     * A centimetre is two orders of magnitude below the device's own accuracy, so
     * this measures the storage rather than the survey.
     */
    @Test
    fun aSurveyedCentrelineSurvivesTheDatabaseWithoutLosingItsShape() = runBlocking {
        val original = bundled.byId("baltar")?.centreline
        assertNotNull("Baltar should ship with a centreline to copy", original)
        val points = original!!.points

        val stored = Track(
            id = "user-with-centreline",
            name = "Copy Of Baltar",
            startLine = TrackStartLine(
                lat1 = 41.187838, lng1 = -8.395666,
                lat2 = 41.187770, lng2 = -8.395761,
                source = GeometrySource.CAPTURED_IN_APP
            ),
            lengthM = 1020,
            centreline = original
        )
        trackDao.upsert(TrackMapper.toEntity(stored))

        val reloaded = repository.getTrack("user-with-centreline")?.centreline
        assertNotNull("the centreline should come back at all", reloaded)

        assertEquals("every waypoint should survive", points.size, reloaded!!.points.size)

        var worst = 0.0
        points.forEachIndexed { index, point ->
            val back = reloaded.points[index]
            val drift = GeoUtils.haversineDistance(
                point.latitude, point.longitude, back.latitude, back.longitude
            )
            if (drift > worst) worst = drift
        }
        assertTrue(
            "the ring moved by ${"%.3f".format(worst)} m in storage, and it is read in the " +
                "stored order. AS-18 and LD-22 both consume this, and neither would report " +
                "an error if it were wrong",
            worst < 0.01
        )

        assertEquals(
            "provenance is recorded per dataset and must not be lost in storage (TL-05)",
            GeometrySource.SURVEYED_ON_FOOT, reloaded.source
        )
        assertEquals("Emidio Costa", reloaded.surveyedBy)
    }

    /**
     * Ordering is a product decision rather than an accident: the circuit driven
     * last weekend is the one wanted tonight, and the bundled catalogue sits behind
     * everything the driver has actually used.
     */
    @Test
    fun savedCircuitsAreOfferedAheadOfBundledOnes() = runBlocking {
        val before = repository.observeTracks().first()
        assertTrue("the bundled catalogue should already be offered", before.isNotEmpty())
        assertTrue("with nothing saved, every entry is bundled", before.all { it.isBundled })

        repository.saveCapturedTrack(
            name = "Evening Circuit",
            lat1 = 41.187838, lng1 = -8.395666,
            lat2 = 41.187770, lng2 = -8.395761
        )

        val after = repository.observeTracks().first()
        assertEquals(
            "the saved circuit should be added, not replace anything",
            before.size + 1, after.size
        )
        assertEquals(
            "a circuit the driver saved belongs above the ones shipped with the app",
            "Evening Circuit", after.first().name
        )
        assertTrue("and the bundled ones follow", after.last().isBundled)
    }

    /**
     * A bundled circuit is corrected by shipping a build, not by editing a row, so
     * the repository refuses rather than quietly doing nothing.
     */
    @Test
    fun aBundledCircuitCannotBeDeletedOrRenamed() = runBlocking {
        val baltar = repository.getTrack("baltar")
        assertNotNull("Baltar should be reachable through the repository", baltar)
        assertTrue("and should identify itself as bundled", baltar!!.isBundled)

        var deleteRefused = false
        try {
            repository.delete("baltar")
        } catch (e: IllegalArgumentException) {
            deleteRefused = true
        }
        assertTrue("deleting a bundled circuit should be refused", deleteRefused)

        var renameRefused = false
        try {
            repository.rename("baltar", "Something Else")
        } catch (e: IllegalArgumentException) {
            renameRefused = true
        }
        assertTrue("renaming a bundled circuit should be refused", renameRefused)

        assertNotNull("and Baltar should still be there afterwards", repository.getTrack("baltar"))
    }

    @Test
    fun aSavedCircuitCanBeRenamedAndDeleted() = runBlocking {
        val saved = (
            repository.saveCapturedTrack(
                name = "Typo Ciruit",
                lat1 = 41.187838, lng1 = -8.395666,
                lat2 = 41.187770, lng2 = -8.395761
            ) as SaveTrackResult.Success
            ).track

        repository.rename(saved.id, "  Fixed Circuit  ")
        assertEquals(
            "a rename should trim, as saving does",
            "Fixed Circuit", repository.getTrack(saved.id)?.name
        )

        repository.delete(saved.id)
        assertNull("a deleted circuit should be gone", repository.getTrack(saved.id))
        assertNotNull(
            "and deleting a saved circuit must not disturb the bundled catalogue",
            repository.getTrack("baltar")
        )
    }

    /**
     * `markUsed` is called when a session starts, and the common case is a bundled
     * circuit with no row to update. It must tolerate that rather than throw on the
     * path that begins every catalogued session.
     */
    @Test
    fun markUsedReordersSavedCircuitsAndToleratesBundledOnes() = runBlocking {
        val first = (
            repository.saveCapturedTrack(
                name = "First", lat1 = 41.187838, lng1 = -8.395666,
                lat2 = 41.187770, lng2 = -8.395761
            ) as SaveTrackResult.Success
            ).track
        Thread.sleep(5)
        val second = (
            repository.saveCapturedTrack(
                name = "Second", lat1 = 41.187838, lng1 = -8.395666,
                lat2 = 41.187770, lng2 = -8.395761
            ) as SaveTrackResult.Success
            ).track

        assertEquals(
            "with neither driven, the most recently created leads",
            second.id, repository.observeTracks().first().first().id
        )

        repository.markUsed(first.id)
        assertEquals(
            "the circuit just driven should lead the list",
            first.id, repository.observeTracks().first().first().id
        )

        repository.markUsed("baltar")
        assertEquals(
            "marking a bundled circuit has no row to touch and must not throw or reorder",
            first.id, repository.observeTracks().first().first().id
        )
    }

    /**
     * Track Setup refuses a line whose ends are too close together to define a
     * crossing. Saving one would turn a single bad capture into a circuit that is
     * unusable on every future visit, and a name in a list gives the driver no way
     * to see that.
     */
    @Test
    fun aLineTooShortToDetectALapIsRefusedRatherThanStored() = runBlocking {
        val before = repository.observeTracks().first().size

        val result = repository.saveCapturedTrack(
            name = "Too Tight",
            lat1 = 41.187838, lng1 = -8.395666,
            lat2 = 41.187840, lng2 = -8.395667
        )

        assertTrue("a line well under 3 m should be refused", result is SaveTrackResult.Rejected)
        assertTrue(
            "the refusal should say how far apart the points were, not just that it failed",
            (result as SaveTrackResult.Rejected).reason.contains("m apart")
        )
        assertEquals(
            "and nothing should have been stored",
            before, repository.observeTracks().first().size
        )
    }

    @Test
    fun anEmptyNameIsRefusedBecauseTheDriverCouldNotFindItAgain() = runBlocking {
        val result = repository.saveCapturedTrack(
            name = "   ",
            lat1 = 41.187838, lng1 = -8.395666,
            lat2 = 41.187770, lng2 = -8.395761
        )

        assertTrue(result is SaveTrackResult.Rejected)
    }

    /**
     * Two visits to two different circuits that happen to share a name must not
     * overwrite one another. The id is what a stored session points at, so a
     * collision would silently repoint an old session at a new start line.
     */
    @Test
    fun twoCircuitsSavedUnderTheSameNameDoNotOverwriteOneAnother() = runBlocking {
        val first = (
            repository.saveCapturedTrack(
                name = "Club Day", lat1 = 41.187838, lng1 = -8.395666,
                lat2 = 41.187770, lng2 = -8.395761
            ) as SaveTrackResult.Success
            ).track
        Thread.sleep(5)
        val second = (
            repository.saveCapturedTrack(
                name = "Club Day", lat1 = 41.186900, lng1 = -8.395500,
                lat2 = 41.186830, lng2 = -8.395600
            ) as SaveTrackResult.Success
            ).track

        assertTrue("the two circuits must not share an id", first.id != second.id)
        assertNotNull("the older one must survive", repository.getTrack(first.id))
        assertEquals(
            "and must keep its own start line",
            41.187838, repository.getTrack(first.id)!!.startLine.lat1, 1e-9
        )
    }
}
