package com.drivingcoach.ui.session

import android.content.Context
import androidx.core.os.bundleOf
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.drivingcoach.R
import com.drivingcoach.data.db.dao.CoachingInsightDao
import com.drivingcoach.data.db.dao.LapDao
import com.drivingcoach.data.db.dao.SessionDao
import com.drivingcoach.data.db.entity.CoachingInsightEntity
import com.drivingcoach.data.db.entity.LapEntity
import com.drivingcoach.data.db.entity.SessionEntity
import com.drivingcoach.testing.awaitUntil
import com.drivingcoach.testing.launchFragmentInHiltContainer
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * L2 (ASPICE SWE.5) coverage for the COACH tab — SRS OC-19, OC-20, OC-26, OC-27,
 * OC-31 and OC-32.
 *
 * ### Why this class exists
 *
 * OC-19 ("the derived-sector caveat is shown only when sectors exist") was until now
 * claimed by `OfflineCoachingEngineTest`, a JVM unit test that cannot see a view. The
 * engine decides what the insight *says*; the fragment decides whether the caveat is
 * *shown*. Those are different claims and only one of them was actually being tested.
 * The same gap applies to the sector map: all of its geometry is unit-tested, but
 * nothing proved the result ever reached the screen or that a session without a usable
 * shape hid the card rather than drawing a blob.
 *
 * ### What is deliberately not asserted
 *
 * Not the shape itself. Espresso can prove the map is on screen and that it describes
 * itself correctly; it cannot tell a circuit from a scribble. The geometry is covered
 * in `SessionOutlineTest` and `CoachMapTest`, against real recorded laps, which is a
 * far stronger check than anything that could be done here.
 */
@LargeTest
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CoachTabTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var sessionDao: SessionDao

    @Inject
    lateinit var lapDao: LapDao

    @Inject
    lateinit var insightDao: CoachingInsightDao

    private lateinit var telemetryFile: File
    private var sessionId: Long = 0L

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @After
    fun tearDown() {
        if (::telemetryFile.isInitialized) telemetryFile.delete()
    }

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    // --- The map reaches the screen (SRS OC-20, OC-26) ---

    @Test
    fun coachTabShowsTheSectorMapWhenLapsAreUsable() {
        seedSession(lapCount = 3, withSectorTimes = true, withTelemetryFile = true)
        launchCoachTab()

        onView(withId(R.id.sectorMapCard)).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withId(R.id.sectorMap)).check(matches(isDisplayed()))
    }

    /**
     * This session has no catalogue track, so its shape can only have come from the
     * driver's own laps. The note is what stops them reading that shape as the edge of
     * the road when it is really the line they drove.
     */
    @Test
    fun aMapDerivedFromLapsSaysSoBeneathItself() {
        seedSession(lapCount = 3, withSectorTimes = true, withTelemetryFile = true)
        launchCoachTab()

        onView(withId(R.id.sectorMapNote)).perform(scrollTo())
            .check(matches(withText(R.string.coach_map_note_derived)))
    }

    // --- Degraded input hides the map but never the insights (SRS OC-27) ---

    /**
     * The insights were generated at save time and stored as text. They do not depend
     * on telemetry still being readable, so losing the file must cost the driver the
     * picture and nothing else.
     */
    @Test
    fun missingTelemetryHidesTheMapAndKeepsTheInsights() {
        seedSession(lapCount = 3, withSectorTimes = true, withTelemetryFile = false)
        launchCoachTab()

        onView(withText(HEADLINE)).perform(scrollTo()).check(matches(isDisplayed()))
        awaitUntil("the sector map card to be hidden") {
            runCatching {
                onView(withId(R.id.sectorMapCard)).check(matches(not(isDisplayed())))
            }.isSuccess
        }
    }

    // --- The sector caveat is tied to data, not wording (SRS OC-19) ---

    @Test
    fun sectorCaveatIsShownWhenLapsCarrySectorTimes() {
        seedSession(lapCount = 3, withSectorTimes = true, withTelemetryFile = true)
        launchCoachTab()

        onView(withId(R.id.sectorCaveat)).perform(scrollTo()).check(matches(isDisplayed()))
    }

    /**
     * Laps stored without sector times have nothing for the caveat to qualify, and
     * showing it anyway would explain a concept the driver is not being shown.
     *
     * The telemetry is deliberately absent. With a readable file this state no longer
     * survives being opened: OC-31 recomputes the sectors and, finding stored zeroes
     * where the road can be divided, fills them in - which is exactly what gives a
     * session recorded before sectors existed its sectors back. A fixture combining
     * splittable telemetry with empty sector times therefore describes a session that
     * cannot persist, and testing against it would be testing a transient.
     */
    @Test
    fun sectorCaveatIsHiddenWhenLapsCarryNoSectorTimes() {
        seedSession(lapCount = 3, withSectorTimes = false, withTelemetryFile = false)
        launchCoachTab()

        onView(withText(HEADLINE)).perform(scrollTo()).check(matches(isDisplayed()))
        awaitUntil("the sector caveat to be hidden") {
            runCatching {
                onView(withId(R.id.sectorCaveat)).check(matches(not(isDisplayed())))
            }.isSuccess
        }
    }

    // --- Sectors measured the old way are repaired on open (SRS OC-31, OC-32) ---

    /**
     * The repair runs on a read path, so this is the only level at which it can be
     * shown to actually happen: a real Room database, the real view model, and the
     * fragment that triggers it. The assertion is made against the database rather
     * than the screen because the correction's whole point is that it reaches *every*
     * tab, not just the one the driver happened to open.
     */
    @Test
    fun staleSectorTimesAreCorrectedWhenTheSessionIsOpened() {
        seedSession(lapCount = 3, withSectorTimes = true, withTelemetryFile = true, staleSectors = true)
        launchCoachTab()

        awaitUntil("the stored sector times to be recomputed") {
            val lap = storedLaps().firstOrNull { it.lapNumber == 1 } ?: return@awaitUntil false
            lap.sector1Ms != LAP_MS / 2
        }

        val lap = storedLaps().first { it.lapNumber == 1 }
        assertEquals(
            "corrected sectors must still account for the whole lap and nothing more",
            lap.durationMs,
            lap.sector1Ms + lap.sector2Ms + lap.sector3Ms
        )
        assertTrue(
            "sector 1 of ${lap.sector1Ms} ms is not a plausible third of a ${lap.durationMs} ms lap",
            lap.sector1Ms.toDouble() / lap.durationMs in 0.15..0.55
        )
    }

    /**
     * No diagnostics sidecar was written beside this telemetry, so the merged-lap
     * signal of OC-14 cannot be recovered. The numbers are corrected; the wording the
     * driver already had must survive untouched, because regenerating it without that
     * signal could resurrect a dream lap detection had deliberately withheld.
     */
    @Test
    fun correctingSectorsWithoutDiagnosticsLeavesTheInsightTextAlone() {
        seedSession(lapCount = 3, withSectorTimes = true, withTelemetryFile = true, staleSectors = true)
        launchCoachTab()

        awaitUntil("the stored sector times to be recomputed") {
            val lap = storedLaps().firstOrNull { it.lapNumber == 1 } ?: return@awaitUntil false
            lap.sector1Ms != LAP_MS / 2
        }

        onView(withText(HEADLINE)).perform(scrollTo()).check(matches(isDisplayed()))
        assertEquals(
            "the seeded insight should neither have been removed nor duplicated",
            1,
            storedInsights().count { it.headline == HEADLINE }
        )
    }

    /**
     * Without telemetry there is nothing to recompute from. Blanking or guessing the
     * stored sectors would lose a measurement to no one's benefit.
     */
    @Test
    fun missingTelemetryLeavesTheStoredSectorsUntouched() {
        seedSession(lapCount = 3, withSectorTimes = true, withTelemetryFile = false, staleSectors = true)
        launchCoachTab()

        awaitUntil("the sector map card to be hidden") {
            runCatching {
                onView(withId(R.id.sectorMapCard)).check(matches(not(isDisplayed())))
            }.isSuccess
        }

        storedLaps().forEach { lap ->
            assertEquals(
                "lap ${lap.lapNumber} was rewritten despite there being nothing to measure",
                LAP_MS / 2,
                lap.sector1Ms
            )
        }
    }

    // --- helpers ---

    private fun storedLaps(): List<LapEntity> =
        runBlocking { lapDao.getLapsForSession(sessionId).first() }

    private fun storedInsights(): List<CoachingInsightEntity> =
        runBlocking { insightDao.getInsightsForSession(sessionId).first() }

    private fun not(matcher: org.hamcrest.Matcher<android.view.View>) =
        org.hamcrest.CoreMatchers.not(matcher)

    private fun launchCoachTab() {
        launchFragmentInHiltContainer<SessionResultFragment>(
            fragmentArgs = bundleOf("sessionId" to sessionId)
        )
        onView(withText(COACH_TAB)).check(matches(isDisplayed()))
        onView(withText(COACH_TAB)).perform(click())
        // The insights come from the database and the map from a file read, so the tab
        // is briefly empty. The wait scrolls rather than merely looking, because with
        // the map pinned above them the insights now start entirely below the fold:
        // `isDisplayed()` would never become true on a tall enough map, which is the
        // ordinary case rather than a failure.
        awaitUntil("the coaching insights to be rendered") {
            runCatching {
                onView(withText(HEADLINE)).perform(scrollTo())
            }.isSuccess
        }
    }

    private fun seedSession(
        lapCount: Int,
        withSectorTimes: Boolean,
        withTelemetryFile: Boolean,
        staleSectors: Boolean = false
    ) = runBlocking {
        val telemetryDir = File(context().filesDir, "telemetry").apply { mkdirs() }
        telemetryFile = File(telemetryDir, "coach_tab_test.jsonl")
        if (withTelemetryFile) {
            telemetryFile.writeText(syntheticOvalTelemetry(lapCount))
        } else {
            telemetryFile.delete()
        }

        sessionId = sessionDao.insertSession(
            SessionEntity(
                userId = "instrumented-user",
                trackName = "Synthetic Oval",
                startedAt = START_MS,
                endedAt = START_MS + lapCount * LAP_MS,
                rawFilePath = telemetryFile.path,
                uploadStatus = "DONE",
                processingStatus = "COMPLETE"
            )
        )

        lapDao.insertLaps(
            (1..lapCount).map { n ->
                val start = START_MS + (n - 1) * LAP_MS
                LapEntity(
                    sessionId = sessionId,
                    lapNumber = n,
                    startTs = start,
                    endTs = start + LAP_MS,
                    durationMs = LAP_MS,
                    // `staleSectors` stands in for a session measured before the
                    // ruler was anchored at the start/finish line: the halves are far
                    // enough from a third that no rounding could explain them.
                    sector1Ms = when {
                        !withSectorTimes -> 0L
                        staleSectors -> LAP_MS / 2
                        else -> LAP_MS / 3
                    },
                    sector2Ms = when {
                        !withSectorTimes -> 0L
                        staleSectors -> LAP_MS / 4
                        else -> LAP_MS / 3
                    },
                    sector3Ms = when {
                        !withSectorTimes -> 0L
                        staleSectors -> LAP_MS - LAP_MS / 2 - LAP_MS / 4
                        else -> LAP_MS - 2 * (LAP_MS / 3)
                    },
                    isBestLap = n == 1,
                    isLocalOnly = true
                )
            }
        )

        insightDao.insertInsight(
            CoachingInsightEntity(
                sessionId = sessionId,
                headline = HEADLINE,
                detail = "Instrumented-test insight.",
                generatedAt = START_MS,
                isLocalOnly = true
            )
        )
    }

    /**
     * [lapCount] laps of the same oval used by `AnalysisTabTest`, driven identically
     * each time. Identical laps make the derived outline unambiguous, which keeps this
     * test about the wiring rather than about the geometry.
     */
    private fun syntheticOvalTelemetry(lapCount: Int): String = buildString {
        appendLine("""{"type":"header","sessionId":$sessionId,"trackName":"Synthetic Oval"}""")

        var t = START_MS
        var x = 0.0
        var y = 0.0
        var heading = 0.0

        repeat(lapCount) {
            listOf(
                Segment(straightM = STRAIGHT_M, curveRad = 0.0),
                Segment(straightM = 0.0, curveRad = PI),
                Segment(straightM = STRAIGHT_M, curveRad = 0.0),
                Segment(straightM = 0.0, curveRad = PI)
            ).forEach { segment ->
                if (segment.straightM > 0) {
                    val steps = (segment.straightM / (TOP_SPEED_MS * DT)).toInt()
                    repeat(steps) {
                        x += sin(heading) * TOP_SPEED_MS * DT
                        y += cos(heading) * TOP_SPEED_MS * DT
                        appendSample(t, x, y, TOP_SPEED_MS, heading)
                        t += DT_MS
                    }
                } else {
                    val arcLength = CURVE_RADIUS_M * segment.curveRad
                    val steps = (arcLength / (CORNER_SPEED_MS * DT)).toInt()
                    val dHeading = segment.curveRad / steps
                    repeat(steps) {
                        heading += dHeading
                        x += sin(heading) * CORNER_SPEED_MS * DT
                        y += cos(heading) * CORNER_SPEED_MS * DT
                        appendSample(t, x, y, CORNER_SPEED_MS, heading)
                        t += DT_MS
                    }
                }
            }
        }

        // Close the final lap. The seeded lap windows run to START_MS + laps * LAP_MS,
        // and a real session's windows always do fall between two recorded fixes,
        // because lap detection derived them from this very file. Without this sample
        // the fixture describes a session that stops mid-lap, and the last lap is
        // correctly refused a measured start and end.
        appendSample(maxOf(t, START_MS + lapCount * LAP_MS), x, y, TOP_SPEED_MS, heading)
    }

    private fun StringBuilder.appendSample(
        t: Long,
        x: Double,
        y: Double,
        speedMs: Double,
        headingRad: Double
    ) {
        val lat = BASE_LAT + y / 111_320.0
        val lng = BASE_LNG + x / (111_320.0 * cos(Math.toRadians(BASE_LAT)))
        val headingDeg = ((Math.toDegrees(headingRad) % 360) + 360) % 360
        appendLine(
            """{"timestampMs":$t,"latitude":$lat,"longitude":$lng,""" +
                """"speedMs":$speedMs,"headingDeg":$headingDeg,""" +
                """"accelX":0.0,"accelY":0.0,"accelZ":9.81,""" +
                """"gyroX":0.0,"gyroY":0.0,"gyroZ":0.0,"gpsAccuracyM":3.0}"""
        )
    }

    private data class Segment(val straightM: Double, val curveRad: Double)

    private companion object {
        const val COACH_TAB = "COACH"
        const val HEADLINE = "Instrumented coaching headline"
        const val START_MS = 1_700_000_000_000L
        const val BASE_LAT = 41.5
        const val BASE_LNG = -8.4

        const val DT = 0.1
        const val DT_MS = 100L
        const val STRAIGHT_M = 200.0
        const val CURVE_RADIUS_M = 50.0
        const val TOP_SPEED_MS = 27.8
        const val CORNER_SPEED_MS = 11.1

        /** 2 straights of 71 samples plus 2 curves of 141 samples at 10 Hz. */
        const val LAP_MS = 42_400L
    }
}
