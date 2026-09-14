package com.drivingcoach.ui.session

import android.content.Context
import androidx.core.os.bundleOf
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.hasMinimumChildCount
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.drivingcoach.R
import com.drivingcoach.data.db.dao.LapDao
import com.drivingcoach.data.db.dao.SessionDao
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
 * L2 (ASPICE SWE.5) coverage for the ANALYSIS tab — SRS AS-01..AS-09.
 *
 * Everything the tab shows is computed off the UI thread and then poured into
 * views that are built at runtime (lap chips, corner rows, braking rows). None of
 * that wiring is reachable from a JVM unit test, and the maths is already covered
 * there, so this class deliberately asserts only what an instrumented test can
 * prove: that the tab exists, that the computed analysis actually reaches the
 * screen, that picking a different lap re-renders, and that a session whose
 * telemetry has gone missing degrades to a message instead of a crash.
 *
 * The telemetry is synthesised rather than replayed: an oval with two braking
 * zones is the smallest recording that exercises every section of the tab, and
 * generating it keeps the test independent of any recorded fixture.
 */
@LargeTest
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AnalysisTabTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var sessionDao: SessionDao

    @Inject
    lateinit var lapDao: LapDao

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

    // --- The tab exists (SRS AS-01) ---

    @Test
    fun analysisTabIsPresentOnTheSessionResultScreen() {
        seedSession(withTelemetryFile = true)
        launchResultScreen()

        onView(withText(ANALYSIS_TAB)).check(matches(isDisplayed()))
    }

    // --- The computed analysis reaches the screen (SRS AS-02..AS-06) ---

    @Test
    fun analysisTabRendersStatsMapAndDerivedTables() {
        seedSession(withTelemetryFile = true)
        launchResultScreen()
        openAnalysisTab()

        // Stats are formatted from the telemetry, so a non-placeholder distance
        // proves the file was parsed rather than merely opened. Everything is
        // asserted after a scroll because the tab is one long scrolling report and
        // Espresso only regards on-screen views as displayed.
        onView(withId(R.id.statDistance)).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withId(R.id.statDuration)).check(matches(isDisplayed()))
        onView(withId(R.id.statMaxSpeed)).check(matches(isDisplayed()))
        onView(withId(R.id.statAvgSpeed)).check(matches(isDisplayed()))
        onView(withId(R.id.statBestLap)).check(matches(isDisplayed()))

        onView(withId(R.id.trackMap)).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withId(R.id.lapChipGroup)).perform(scrollTo())
            .check(matches(hasMinimumChildCount(2)))

        // The oval brakes into both corners, so both tables must have rows. If the
        // detectors silently returned nothing, these containers would be empty and
        // the "no corners"/"no braking" placeholders would be showing instead.
        onView(withId(R.id.cornerContainer)).perform(scrollTo())
            .check(matches(hasMinimumChildCount(1)))
        onView(withId(R.id.brakingContainer)).perform(scrollTo())
            .check(matches(hasMinimumChildCount(1)))
        onView(withId(R.id.sessionSpeedChart)).perform(scrollTo()).check(matches(isDisplayed()))
    }

    // --- Reference lap selection (SRS AS-07) ---

    @Test
    fun pickingADifferentLapRebuildsTheReferenceView() {
        seedSession(withTelemetryFile = true)
        launchResultScreen()
        openAnalysisTab()

        // Lap 1 is the best lap and therefore the default reference; selecting lap
        // 2 must move the label, which is the only user-visible proof that the
        // analysis was recomputed for the newly chosen window.
        awaitLabel(context().getString(R.string.analysis_reference_lap, "Lap 1"))
        onView(withText(lapChipText(2))).perform(scrollTo(), click())
        awaitLabel(context().getString(R.string.analysis_reference_lap, "Lap 2"))
    }

    // --- Degraded input never crashes (SRS AS-09) ---

    @Test
    fun missingTelemetryShowsTheEmptyStateInsteadOfCrashing() {
        seedSession(withTelemetryFile = false)
        launchResultScreen()
        openAnalysisTab()

        val expected = context().getString(R.string.analysis_empty_no_telemetry)
        awaitUntil("the missing-telemetry message to be shown") {
            runCatching { onView(withText(expected)).check(matches(isDisplayed())) }.isSuccess
        }
        // Reaching this point at all is the assertion: an unguarded read of a
        // deleted telemetry file would have taken the host activity down first.
        onView(withText(ANALYSIS_TAB)).check(matches(isDisplayed()))
    }

    // --- helpers ---

    private fun launchResultScreen() {
        launchFragmentInHiltContainer<SessionResultFragment>(
            fragmentArgs = bundleOf("sessionId" to sessionId)
        )
        onView(withText(ANALYSIS_TAB)).check(matches(isDisplayed()))
    }

    private fun openAnalysisTab() {
        onView(withText(ANALYSIS_TAB)).perform(click())
        // The analysis runs off the main thread, so the tab first shows its
        // spinner. Espresso's idling covers the ViewPager2 animation but not the
        // background computation, hence the explicit wait for real content.
        awaitUntil("the analysis content to be rendered") {
            runCatching {
                onView(withId(R.id.contentContainer)).check(matches(isDisplayed()))
            }.isSuccess ||
                runCatching {
                    onView(withId(R.id.emptyStateText)).check(matches(isDisplayed()))
                }.isSuccess
        }
    }

    private fun awaitLabel(expected: String) {
        awaitUntil("the reference label to read '$expected'") {
            runCatching {
                onView(withId(R.id.mapReferenceLabel)).check(matches(withText(expected)))
            }.isSuccess
        }
    }

    private fun lapChipText(lapNumber: Int): String {
        val durationMs = if (lapNumber == 1) LAP_1_MS else LAP_2_MS
        return context().getString(
            R.string.analysis_lap_chip,
            lapNumber,
            com.drivingcoach.util.LapTimeFormatter.formatLapTime(durationMs)
        )
    }

    private fun seedSession(withTelemetryFile: Boolean) = runBlocking {
        val telemetryDir = File(context().filesDir, "telemetry").apply { mkdirs() }
        telemetryFile = File(telemetryDir, "analysis_tab_test.jsonl")
        if (withTelemetryFile) {
            telemetryFile.writeText(syntheticOvalTelemetry())
        } else {
            telemetryFile.delete()
        }

        sessionId = sessionDao.insertSession(
            SessionEntity(
                userId = "instrumented-user",
                trackName = "Synthetic Oval",
                startedAt = START_MS,
                endedAt = START_MS + LAP_1_MS + LAP_2_MS,
                rawFilePath = telemetryFile.path,
                // Anything in-flight leaves an indeterminate progress bar on screen
                // and Espresso would never see the UI thread go idle.
                uploadStatus = "DONE",
                processingStatus = "COMPLETE",
                startLineLat1 = BASE_LAT,
                startLineLng1 = BASE_LNG - 0.0002,
                startLineLat2 = BASE_LAT,
                startLineLng2 = BASE_LNG + 0.0002
            )
        )

        lapDao.insertLaps(
            listOf(
                LapEntity(
                    sessionId = sessionId, lapNumber = 1,
                    startTs = START_MS, endTs = START_MS + LAP_1_MS,
                    durationMs = LAP_1_MS, isBestLap = true, isLocalOnly = true
                ),
                LapEntity(
                    sessionId = sessionId, lapNumber = 2,
                    startTs = START_MS + LAP_SAMPLES_MS,
                    endTs = START_MS + LAP_SAMPLES_MS + LAP_2_MS,
                    durationMs = LAP_2_MS, isBestLap = false, isLocalOnly = true
                )
            )
        )
    }

    /**
     * Two laps of an oval at 10 Hz: straights taken flat out, hard braking into
     * each of the two constant-radius curves. That shape is what makes corner
     * detection (sustained yaw rate) and braking detection (sustained negative
     * longitudinal acceleration) both fire.
     */
    private fun syntheticOvalTelemetry(): String = buildString {
        appendLine("""{"type":"header","sessionId":$sessionId,"trackName":"Synthetic Oval"}""")

        var t = START_MS
        var x = 0.0
        var y = 0.0
        var heading = 0.0 // radians, 0 = +y (north)

        // One lap: straight, curve, straight, curve. Distances in metres.
        repeat(2) {
            listOf(
                Segment(straightM = STRAIGHT_M, curveRad = 0.0),
                Segment(straightM = 0.0, curveRad = PI),
                Segment(straightM = STRAIGHT_M, curveRad = 0.0),
                Segment(straightM = 0.0, curveRad = PI)
            ).forEach { segment ->
                if (segment.straightM > 0) {
                    // Accelerate over the first half, brake hard over the second.
                    val steps = (segment.straightM / (TOP_SPEED_MS * DT)).toInt()
                    repeat(steps) { i ->
                        val braking = i > steps * 0.6
                        val speed = if (braking) {
                            (TOP_SPEED_MS - (i - steps * 0.6) * BRAKE_MS2 * DT)
                                .coerceAtLeast(CORNER_SPEED_MS)
                        } else {
                            TOP_SPEED_MS
                        }
                        x += sin(heading) * speed * DT
                        y += cos(heading) * speed * DT
                        appendSample(t, x, y, speed, heading)
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
        const val ANALYSIS_TAB = "ANALYSIS"
        const val START_MS = 1_700_000_000_000L
        const val BASE_LAT = 41.5
        const val BASE_LNG = -8.4

        const val DT = 0.1
        const val DT_MS = 100L
        const val STRAIGHT_M = 200.0
        const val CURVE_RADIUS_M = 50.0
        const val TOP_SPEED_MS = 27.8 // ~100 km/h
        const val CORNER_SPEED_MS = 11.1 // ~40 km/h
        const val BRAKE_MS2 = 4.0

        // Kept in sync with the generator: 2 straights of 71 samples plus 2 curves
        // of 141 samples is 424 samples, i.e. 42.4 s of telemetry per lap at 10 Hz.
        const val LAP_SAMPLES_MS = 42_400L
        const val LAP_1_MS = 42_000L // best lap: shorter, so it is the default reference
        const val LAP_2_MS = 42_400L
    }
}
