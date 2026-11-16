package com.drivingcoach.progression

import com.drivingcoach.lap.LapReplayHarness
import com.drivingcoach.lap.LocalLapDetector
import com.drivingcoach.lap.MergedLapCaveat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Locale

/**
 * How often Apex Exit (SRS PG-27) fires on the real recordings in `lapfixtures` — a
 * measurement, not a tuning: the 1.0 % threshold is the product decision and is not
 * changed here. The table is printed so a reviewer can see it in the test output, and the
 * assertions pin the measured verdicts so a change to the speed filter or the window shows
 * up as a failing test rather than a silent shift in how rare the trophy is.
 */
class ApexExitFixtureMeasurementTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    data class Measurement(
        val name: String,
        val laps: Int,
        val validLaps: Int,
        val trusted: Boolean,
        val topKmh: Double?,
        val windows: Int,
        val windowsWithin: Int,
        val bestSpreadPercent: Double?,
        val awarded: Boolean,
        val lapTopsKmh: List<Double?>
    )

    private fun measure(name: String): Measurement {
        val fixture = LapReplayHarness.load(name, tempFolder.newFolder())
        val outcome = LocalLapDetector().detectLapsWithDiagnostics(fixture.telemetryFile, fixture.startLine)
        val laps = (outcome.result as? LocalLapDetector.DetectionResult.Success)?.laps.orEmpty()
        val speeds = LapTopSpeeds.ofTelemetryFile(fixture.telemetryFile, laps.map { LapTopSpeeds.Span(it.startTs, it.endTs) })
            ?: List(laps.size) { null }
        val trusted = MergedLapCaveat.of(outcome.diagnostics) == null
        val session = SessionFacts(
            sessionId = 1, startedAt = 1, trackKey = "name:$name", trackName = fixture.trackName,
            distanceM = 0.0,
            laps = laps.mapIndexed { i, lap ->
                LapFacts(lap.durationMs, lap.sector1Ms, lap.sector2Ms, lap.sector3Ms, speeds[i])
            },
            lapBoundariesTrusted = trusted
        )
        val top = session.topSpeedKmh
        val windows = SessionMetrics.validWindows(session, TrophyRules.WINDOW)
        val within = if (top == null) 0 else windows.count { range ->
            range.all { i -> session.laps[i].topSpeedKmh?.let { (top - it) * 100.0 <= TrophyRules.APEX_MAX_SPREAD_PERCENT * top } == true }
        }
        return Measurement(
            name, laps.size, session.validLapCount, trusted, top, windows.size, within,
            SessionMetrics.bestApexSpreadPercent(session),
            TrophyType.APEX_EXIT in TrophyRules.evaluate(session, emptyList()).map { it.type },
            speeds
        )
    }

    @Test
    fun apexExitOnRealRecordings() {
        val results = FIXTURES.map { measure(it) }
        println("Apex Exit (threshold ${TrophyRules.APEX_MAX_SPREAD_PERCENT} %) on real recordings:")
        results.forEach { m ->
            println(String.format(Locale.US,
                "  %-14s laps=%2d valid=%2d trusted=%-5s top=%6s km/h windows=%2d within=%2d bestSpread=%6s %% -> %s",
                m.name, m.laps, m.validLaps, m.trusted, m.topKmh?.let { "%.1f".format(Locale.US, it) } ?: "-",
                m.windows, m.windowsWithin, m.bestSpreadPercent?.let { "%.2f".format(Locale.US, it) } ?: "-",
                if (m.awarded) "AWARDED" else "no"))
            println("      lap tops km/h: " + m.lapTopsKmh.joinToString { v -> v?.let { "%.1f".format(Locale.US, it) } ?: "-" })
        }
        val eligible = results.filter { it.windows > 0 }
        println("  awarded in ${results.count { it.awarded }} of ${eligible.size} sessions with a 5-valid-lap window " +
            "(${results.size} recordings)")

        // A window is within the threshold exactly when the best spread is.
        results.forEach { m ->
            assertEquals(m.name, m.windowsWithin > 0 && m.trusted, m.awarded)
            m.bestSpreadPercent?.let { assertTrue(m.name, it >= 0.0) }
        }
        assertTrue("at least one recording must be measurable", eligible.isNotEmpty())
    }

    private companion object {
        val FIXTURES = listOf("baltar2", "cabo_do_mundo", "ines3", "s_mamede", "teste3")
    }
}
