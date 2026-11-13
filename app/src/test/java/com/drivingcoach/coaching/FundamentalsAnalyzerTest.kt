package com.drivingcoach.coaching

import com.drivingcoach.coaching.FundamentalsAnalyzer.Tip
import com.drivingcoach.coaching.FundamentalsAnalyzer.Verdict
import com.drivingcoach.data.db.entity.LapEntity
import com.drivingcoach.data.telemetry.TelemetryFileReader
import com.drivingcoach.data.telemetry.TelemetrySample
import com.drivingcoach.lap.LapReplayHarness
import com.drivingcoach.lap.LocalLapDetector
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The fundamentals verdicts on real recorded sessions. Thresholds were chosen against
 * these fixtures, so a change to them should show up here first.
 */
class FundamentalsAnalyzerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `tips are always all four, in display order`() {
        val result = FundamentalsAnalyzer.analyze(emptyList(), emptyList())
        assertEquals(Tip.values().toList(), result.map { it.tip })
        assertEquals(FundamentalsAnalyzer.DEFAULT, result)
    }

    @Test
    fun `a consistent driver gets the braking-references tick`() {
        val cabo = analyze("cabo_do_mundo")
        assertEquals(Verdict.GOOD, cabo.verdictOf(Tip.BRAKING_REFERENCES))
    }

    @Test
    fun `a wandering sector is named`() {
        val sMamede = analyze("s_mamede").first { it.tip == Tip.BRAKING_REFERENCES }
        assertEquals(Verdict.NEEDS_WORK, sMamede.verdict)
        assertEquals(2, sMamede.focusSector)

        val teste3 = analyze("teste3").first { it.tip == Tip.BRAKING_REFERENCES }
        assertEquals(Verdict.NEEDS_WORK, teste3.verdict)
        assertEquals(1, teste3.focusSector)
    }

    @Test
    fun `final corner verdicts follow the line-speed gap`() {
        // cabo: typical lap crosses the line ~14 km/h slower than the best one.
        assertEquals(Verdict.NEEDS_WORK, analyze("cabo_do_mundo").verdictOf(Tip.FINAL_CORNER))
        // baltar2: every clean lap within a few km/h of the best.
        assertEquals(Verdict.GOOD, analyze("baltar2").verdictOf(Tip.FINAL_CORNER))
    }

    @Test
    fun `fewer than three clean laps are not assessed`() {
        val (laps, samples) = replay("cabo_do_mundo")
        val result = FundamentalsAnalyzer.analyze(laps.take(2), samples)
        assertEquals(FundamentalsAnalyzer.DEFAULT, result)
    }

    @Test
    fun `tips that 1 Hz GPS cannot see are never assessed`() {
        listOf("cabo_do_mundo", "s_mamede", "baltar2", "ines3", "teste3").forEach { name ->
            val result = analyze(name)
            assertEquals(name, Verdict.NOT_ASSESSED, result.verdictOf(Tip.SLOW_IN_FAST_OUT))
            assertEquals(name, Verdict.NOT_ASSESSED, result.verdictOf(Tip.SMOOTH_STEERING))
        }
    }

    private fun List<FundamentalsAnalyzer.Fundamental>.verdictOf(tip: Tip) =
        first { it.tip == tip }.verdict

    private fun analyze(name: String): List<FundamentalsAnalyzer.Fundamental> {
        val (laps, samples) = replay(name)
        return FundamentalsAnalyzer.analyze(laps, samples)
    }

    private fun replay(name: String): Pair<List<LapEntity>, List<TelemetrySample>> {
        val fixture = LapReplayHarness.load(name, tempFolder.newFolder())
        val outcome = LocalLapDetector().detectLapsWithDiagnostics(
            fixture.telemetryFile, fixture.startLine, LocalLapDetector.TrackPriors.NONE
        )
        val laps = (outcome.result as LocalLapDetector.DetectionResult.Success).laps
            .mapIndexed { i, lap ->
                LapEntity(
                    id = i + 1L, sessionId = 1, lapNumber = lap.lapNumber,
                    startTs = lap.startTs, endTs = lap.endTs, durationMs = lap.durationMs,
                    sector1Ms = lap.sector1Ms, sector2Ms = lap.sector2Ms, sector3Ms = lap.sector3Ms
                )
            }
        val samples = runBlocking { TelemetryFileReader.readAll(fixture.telemetryFile.absolutePath) }
        return laps to samples
    }
}
