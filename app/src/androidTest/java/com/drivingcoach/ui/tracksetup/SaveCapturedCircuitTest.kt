package com.drivingcoach.ui.tracksetup

import com.drivingcoach.data.track.GeometrySource
import com.drivingcoach.data.track.SaveTrackResult
import com.drivingcoach.data.track.TrackRepository
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * L2 (ASPICE SWE.5) coverage for saving a captured line as a reusable circuit
 * (TL-09 to TL-11), driven through the Track Setup view model that the dialog on
 * that screen calls.
 *
 * This is the feature that lets the library grow past what ships in the APK, and
 * its value is entirely in the second visit: a line captured once at a circuit the
 * app has never heard of should be waiting the next time, identical, with no
 * capture step. That is a claim about persistence across a round trip rather than
 * about a single call, so it is asserted by reading the circuit back out of the
 * library the way the list screen would.
 *
 * The refusal path matters as much as the happy one. A line too short to define a
 * crossing is refused at save time rather than stored, because a bad line saved
 * once becomes a bad line reused on every future visit, and a name in a list gives
 * the driver no way to tell.
 */
@LargeTest
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SaveCapturedCircuitTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var repository: TrackRepository

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    /**
     * The whole point of the feature: capture once, and find it there next time.
     */
    @Test
    fun aCapturedCircuitIsWaitingInTheLibraryOnTheNextVisit() = runBlocking {
        val bundledOnly = repository.observeTracks().first()

        val saved = repository.saveCapturedTrack(
            name = "Braga Indoor",
            lat1 = 41.550100, lng1 = -8.420300,
            lat2 = 41.550050, lng2 = -8.420390
        )
        assertTrue("a 9 m line is usable and should be accepted", saved is SaveTrackResult.Success)
        val id = (saved as SaveTrackResult.Success).track.id

        val library = repository.observeTracks().first()
        assertEquals(
            "the saved circuit should join the library, not replace the bundled ones",
            bundledOnly.size + 1, library.size
        )

        val reloaded = library.firstOrNull { it.id == id }
        assertNotNull("the circuit should be offered on the next visit", reloaded)
        assertEquals("Braga Indoor", reloaded!!.name)
        assertEquals(
            "and the line must come back exactly as captured, or the second visit " +
                "silently records against a different start line than the first",
            41.550100, reloaded.startLine.lat1, 1e-9
        )
        assertEquals(-8.420390, reloaded.startLine.lng2, 1e-9)
    }

    /**
     * A saved circuit is reusable, which means a session can be started against it
     * by id alone - no coordinates carried, no capture step.
     */
    @Test
    fun aSavedCircuitCanBeResolvedByIdTheWayASessionStartDoes() = runBlocking {
        val id = (
            repository.saveCapturedTrack(
                name = "Braga Indoor",
                lat1 = 41.550100, lng1 = -8.420300,
                lat2 = 41.550050, lng2 = -8.420390
            ) as SaveTrackResult.Success
            ).track.id

        val resolved = repository.getTrack(id)

        assertNotNull("starting a session looks the circuit up by id and nothing else", resolved)
        assertEquals(
            "a captured circuit must not claim a provenance it does not have (TL-05)",
            GeometrySource.CAPTURED_IN_APP, resolved!!.startLine.source
        )
        assertTrue(
            "a captured circuit carries no survey, so no priors. Detection falls back " +
                "to the behaviour every session had before the catalogue existed",
            resolved.travelHeadingDeg == null && resolved.lengthM == null
        )
        assertEquals(
            "and with no surveyed length, LD-22 cannot apply - there is nothing to " +
                "measure a lap against, so no lap set can be discarded",
            true, resolved.priors().lapsArePlausible(
                listOf(com.drivingcoach.lap.LocalLapDetector.DetectedLap(1, 0L, 954_363L, 954_363L))
            )
        )
    }

    /**
     * Track Setup refuses a line whose ends are too close together to define a
     * crossing, and the save path must refuse it too rather than storing a circuit
     * that can never detect a lap.
     */
    @Test
    fun aLineTooShortToDetectALapIsRefusedWithAReasonTheDriverCanActOn() = runBlocking {
        val before = repository.observeTracks().first().size

        val result = repository.saveCapturedTrack(
            name = "Two Steps Apart",
            lat1 = 41.550100, lng1 = -8.420300,
            lat2 = 41.550102, lng2 = -8.420301
        )

        assertTrue(result is SaveTrackResult.Rejected)
        assertTrue(
            "the driver should be told how far apart the points were, so they know to " +
                "walk further rather than that something went wrong",
            (result as SaveTrackResult.Rejected).reason.contains("m apart")
        )
        assertEquals(
            "and a refused circuit must not reach the library",
            before, repository.observeTracks().first().size
        )
    }

    /**
     * Saving the same circuit twice is a plausible mistake - a driver who is not
     * sure whether the first save worked. Both must survive as distinct circuits,
     * because the id is what a stored session points at and reusing one would
     * repoint an old session at a new line.
     */
    @Test
    fun savingTheSameCircuitTwiceDoesNotOverwriteTheFirst() = runBlocking {
        val first = (
            repository.saveCapturedTrack(
                name = "Braga Indoor", lat1 = 41.550100, lng1 = -8.420300,
                lat2 = 41.550050, lng2 = -8.420390
            ) as SaveTrackResult.Success
            ).track
        Thread.sleep(5)
        val second = (
            repository.saveCapturedTrack(
                name = "Braga Indoor", lat1 = 41.550100, lng1 = -8.420300,
                lat2 = 41.550050, lng2 = -8.420390
            ) as SaveTrackResult.Success
            ).track

        assertTrue("the two must be distinguishable", first.id != second.id)
        assertNotNull("and a session pointing at the first must still resolve", repository.getTrack(first.id))
    }

    /**
     * A circuit the driver saved is theirs to correct or remove, unlike a bundled
     * one. This is the difference the library exposes through `isBundled`.
     */
    @Test
    fun aSavedCircuitCanBeCorrectedAndRemovedUnlikeABundledOne() = runBlocking {
        val id = (
            repository.saveCapturedTrack(
                name = "Braga Indor", lat1 = 41.550100, lng1 = -8.420300,
                lat2 = 41.550050, lng2 = -8.420390
            ) as SaveTrackResult.Success
            ).track.id

        assertTrue("a captured circuit is not bundled", repository.getTrack(id)!!.isBundled.not())

        repository.rename(id, "Braga Indoor")
        assertEquals("Braga Indoor", repository.getTrack(id)?.name)

        repository.delete(id)
        assertEquals(
            "a removed circuit should leave the library",
            null, repository.getTrack(id)
        )
    }
}
