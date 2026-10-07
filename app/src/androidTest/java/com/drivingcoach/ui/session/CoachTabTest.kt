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
import kotlinx.coroutines.runBlocking
import org.junit.After
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
 * L2 (ASPICE SWE.5) coverage for the COACH tab — SRS OC-19, LD-27, LD-28, LD-29.
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

    // --- The map reaches the screen (SRS LD-27, LD-28) ---

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

    // --- Degraded input hides the map but never the insights (SRS LD-29) ---

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
     * A single lap earns no sector comparison, so the laps are stored without sector
     * times and the caveat has nothing to qualify. Showing it anyway would explain a
     * concept the driver is not being shown.
     */
    @Test
    fun sectorCaveatIsHiddenWhenLapsCarryNoSectorTimes() {
        seedSession(lapCount = 3, withSectorTimes = false, withTelemetryFile = true)
        launchCoachTab()

        onView(withText(HEADLINE)).perform(scrollTo()).check(matches(isDisplayed()))
        awaitUntil("the sector caveat to be hidden") {
            runCatching {
                onView(withId(R.id.sectorCaveat)).check(matches(not(isDisplayed())))
            }.isSuccess
        }
    }

    // --- helpers ---

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
        withTelemetryFile: Boolean
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
                    sector1Ms = if (withSectorTimes) LAP_MS / 3 else 0L,
                    sector2Ms = if (withSectorTimes) LAP_MS / 3 else 0L,
                    sector3Ms = if (withSectorTimes) LAP_MS - 2 * (LAP_MS / 3) else 0L,
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
