package com.drivingcoach.coaching

import com.drivingcoach.data.db.entity.LapEntity
import com.drivingcoach.data.track.BundledTrackCatalog
import com.drivingcoach.lap.LapReplayHarness
import com.drivingcoach.lap.LocalLapDetector
import com.drivingcoach.lap.MergedLapCaveat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Locale

/**
 * How the Dream Lap (SRS OC-13, OC-14, OC-35) classifies the real recordings in
 * `lapfixtures` - a measurement, not a tuning. Each session runs the whole chain the app
 * runs at save time: detection with diagnostics, derived sectors, the merged-lap signal,
 * then [OfflineCoachingEngine.evaluateDreamLap]. The table is printed for a reviewer and
 * the verdicts are pinned, so a change to detection, sector splitting or the gates shows
 * up here as a changed label rather than a silent shift in what drivers are told.
 *
 * Each recording is measured twice: as a captured line with no circuit knowledge, and
 * with the catalogue circuit it was driven on, whose declared fastest lap tightens the
 * minimum gap between crossings (LD-21) - the configuration in which a merged lap can
 * actually arise - and once more as a session opened without its diagnostics sidecar,
 * where the merged-lap signal is unknown and the OC-33 median guard applies.
 */
class DreamLapFixtureMeasurementTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private data class Measurement(
        val name: String,
        val priors: String,
        val laps: Int,
        val evaluation: DreamLap.Evaluation
    ) {
        val label: String
            get() = when (evaluation) {
                is DreamLap.Evaluation.Available -> evaluation.confidence.name
                is DreamLap.Evaluation.NotAvailable -> "NOT_AVAILABLE"
            }
        val reasons: List<String>
            get() = (evaluation as? DreamLap.Evaluation.Available)?.reasons
                ?.map { it.toString().substringAfterLast('.').substringBefore('@') }.orEmpty()
    }

    private val catalogue by lazy {
        BundledTrackCatalog.parse(File("src/main/assets/tracks/tracks.json").readText())
    }

    private fun measure(name: String, trackId: String?, signalKnown: Boolean = true): Measurement {
        val fixture = LapReplayHarness.load(name, tempFolder.newFolder())
        val priors = trackId?.let { id -> catalogue.first { it.id == id }.priors() }
            ?: LocalLapDetector.TrackPriors.NONE
        val outcome = LocalLapDetector().detectLapsWithDiagnostics(fixture.telemetryFile, fixture.startLine, priors)
        val laps = (outcome.result as? LocalLapDetector.DetectionResult.Success)?.laps.orEmpty()
        val entities = laps.mapIndexed { i, lap ->
            LapEntity(
                sessionId = 1L, lapNumber = i + 1, startTs = lap.startTs, endTs = lap.endTs,
                durationMs = lap.durationMs,
                sector1Ms = lap.sector1Ms, sector2Ms = lap.sector2Ms, sector3Ms = lap.sector3Ms
            )
        }
        val evaluation = OfflineCoachingEngine.evaluateDreamLap(
            entities,
            mergedLapCaveat = MergedLapCaveat.of(outcome.diagnostics),
            mergedSignalKnown = signalKnown && outcome.diagnostics != null,
            suspectCrossingTimesMs = MergedLapCaveat.suspectCrossingTimes(outcome.diagnostics)
        )
        val label = (trackId ?: "none") + if (signalKnown) "" else "+no-sidecar"
        return Measurement(name, label, entities.size, evaluation)
    }

    @Test
    fun dreamLapOnRealRecordings() {
        val results = FIXTURES.flatMap { (name, trackId) -> 
            listOf(measure(name, null), measure(name, trackId), measure(name, null, signalKnown = false))
        }
        println("Dream Lap confidence on real recordings:")
        results.forEach { m ->
            val r = (m.evaluation as? DreamLap.Evaluation.Available)?.result
            println(String.format(Locale.US,
                "  %-14s priors=%-22s laps=%2d -> %-13s dream=%s best=%s gain=%s reasons=%s",
                m.name, m.priors, m.laps, m.label,
                r?.let { "%.3f".format(Locale.US, it.totalMs / 1000.0) } ?: "-",
                r?.let { "%.3f".format(Locale.US, it.bestLapMs / 1000.0) } ?: "-",
                r?.let { "%.3f".format(Locale.US, it.gainMs / 1000.0) } ?: "-",
                m.reasons))
        }

        results.forEach { m ->
            val available = m.evaluation as? DreamLap.Evaluation.Available
            if (available != null) {
                val r = available.result
                assertEquals("${m.name}: total is its three parts",
                    r.sector1Ms + r.sector2Ms + r.sector3Ms, r.totalMs)
                assertTrue("${m.name}: gain cannot be negative", r.gainMs >= 0L)
                assertEquals("${m.name}: reliable means no reasons",
                    available.confidence == DreamLap.Confidence.RELIABLE, available.reasons.isEmpty())
            }
        }
        results.forEach { m ->
            assertEquals("${m.name}/${m.priors}: ${m.reasons}", "RELIABLE", m.label)
            assertEquals(
                "${m.name}/${m.priors}: the reliable number must not move",
                EXPECTED_TOTAL_MS.getValue(m.name),
                (m.evaluation as DreamLap.Evaluation.Available).result.totalMs
            )
        }
    }

    private companion object {
        /** Recording -> the catalogue circuit it was driven on (by start-line position). */
        val FIXTURES = listOf(
            "baltar2" to "baltar",
            "cabo_do_mundo" to "cabo_do_mundo",
            "ines3" to "test_circuit_s_mamede",
            "s_mamede" to "test_circuit_s_mamede",
            "teste3" to "test_circuit_s_mamede"
        )

        /**
         * Measured on 3.08: every recording is Reliable in every configuration - none
         * trips a gate, so none changes from what the strict rules showed before.
         */
        val EXPECTED_TOTAL_MS = mapOf(
            "baltar2" to 68_982L,
            "cabo_do_mundo" to 63_145L,
            "ines3" to 84_648L,
            "s_mamede" to 71_830L,
            "teste3" to 72_428L
        )
    }
}
