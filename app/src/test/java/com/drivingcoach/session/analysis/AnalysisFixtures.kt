package com.drivingcoach.session.analysis

import com.drivingcoach.data.telemetry.TelemetrySample
import com.drivingcoach.lap.LapReplayHarness
import com.drivingcoach.lap.LocalLapDetector
import com.drivingcoach.ui.session.tabs.analysis.LapOption
import com.drivingcoach.ui.session.tabs.analysis.SessionAnalysisProcessor
import org.json.JSONObject
import java.io.File

/**
 * Loads a recorded telemetry fixture for the analysis tests.
 *
 * The fixture is the same recorded session the lap-detection tests replay
 * (`teste3`, incident 13), read straight out of test resources. Using a real
 * session rather than synthetic data means the corner and braking thresholds are
 * judged against a track that was actually driven.
 */
internal object AnalysisFixtures {

    /** Lap boundaries measured from `teste3` during the incident 13 RCA. */
    val TESTE3_LAP_SECONDS = listOf(79.83, 77.08, 77.26, 83.44)

    internal data class Fixture(
        val samples: List<SessionAnalysisProcessor.Point>,
        val laps: List<LapOption>,
        val lapWindows: Map<Long, LongRange>,
        val startLine: Pair<Double, Double>
    )

    /** Raw telemetry lines, header included, exactly as exported from the device. */
    internal fun rawSamples(name: String = "teste3"): List<TelemetrySample> {
        val text = requireNotNull(javaClass.getResourceAsStream("/lapfixtures/$name/telemetry.jsonl")) {
            "Fixture telemetry not found: /lapfixtures/$name/telemetry.jsonl"
        }.bufferedReader().use { it.readText() }

        return text.lineSequence()
            .filter { it.isNotBlank() }
            .map { JSONObject(it) }
            .map { json ->
                TelemetrySample(
                    timestampMs = json.optLong("timestampMs", 0L),
                    latitude = json.optDouble("latitude", 0.0),
                    longitude = json.optDouble("longitude", 0.0),
                    speedMs = json.optDouble("speedMs", 0.0).toFloat(),
                    headingDeg = json.optDouble("headingDeg", 0.0).toFloat(),
                    accelX = json.optDouble("accelX", 0.0).toFloat(),
                    accelY = json.optDouble("accelY", 0.0).toFloat(),
                    accelZ = json.optDouble("accelZ", 0.0).toFloat(),
                    gyroX = json.optDouble("gyroX", 0.0).toFloat(),
                    gyroY = json.optDouble("gyroY", 0.0).toFloat(),
                    gyroZ = json.optDouble("gyroZ", 0.0).toFloat(),
                    gpsAccuracyM = json.optDouble("gpsAccuracyM", 0.0).toFloat()
                )
            }
            .toList()
    }

    /**
     * The fixture prepared for analysis, with lap windows taken from the real
     * detector rather than estimated.
     *
     * [com.drivingcoach.lap.LocalLapDetector] is what decides lap boundaries in
     * the app, so the analysis is exercised against exactly the windows it will
     * be given in production. Deriving them any other way here would be testing
     * a session that never existed.
     */
    internal fun teste3(tempDir: File): Fixture {
        val samples = SessionAnalysisProcessor.prepare(rawSamples())

        val replay = LapReplayHarness.load("teste3", tempDir)
        val detected = LapReplayHarness.detect(replay)
        check(detected is LocalLapDetector.DetectionResult.Success) {
            "Fixture teste3 should yield laps, got ${LapReplayHarness.describe(detected)}"
        }

        val laps = mutableListOf<LapOption>()
        val windows = mutableMapOf<Long, LongRange>()
        val fastest = detected.laps.minOf { it.durationMs }
        detected.laps.forEachIndexed { index, lap ->
            val id = (index + 1).toLong()
            windows[id] = lap.startTs..lap.endTs
            laps.add(
                LapOption(
                    lapId = id,
                    lapNumber = index + 1,
                    durationMs = lap.durationMs,
                    isBestLap = lap.durationMs == fastest
                )
            )
        }

        return Fixture(
            samples = samples,
            laps = laps,
            lapWindows = windows,
            startLine = (replay.startLine.lat1 + replay.startLine.lat2) / 2 to
                    (replay.startLine.lng1 + replay.startLine.lng2) / 2
        )
    }

    /**
     * Resamples a session to [targetHz] by linear interpolation.
     *
     * The recorded fixtures predate the move to 10 Hz capture, so the only way to
     * check that the analysis is insensitive to sample rate is to reconstruct the
     * same drive at a higher rate. Interpolation adds no new information, which is
     * the point: an algorithm that reports different corners for the same
     * trajectory sampled more often is measuring its own sample interval.
     */
    internal fun resample(
        samples: List<SessionAnalysisProcessor.Point>,
        targetHz: Double
    ): List<SessionAnalysisProcessor.Point> {
        if (samples.size < 2) return samples
        val stepMs = (1000.0 / targetHz).toLong()
        val out = mutableListOf<SessionAnalysisProcessor.Point>()

        var t = samples.first().timestampMs
        val end = samples.last().timestampMs
        var i = 0
        while (t <= end) {
            while (i < samples.size - 2 && samples[i + 1].timestampMs < t) i++
            val a = samples[i]
            val b = samples[i + 1]
            val span = (b.timestampMs - a.timestampMs).toDouble()
            val f = if (span > 0.0) ((t - a.timestampMs) / span).coerceIn(0.0, 1.0) else 0.0
            out.add(
                SessionAnalysisProcessor.Point(
                    timestampMs = t,
                    latitude = a.latitude + (b.latitude - a.latitude) * f,
                    longitude = a.longitude + (b.longitude - a.longitude) * f,
                    speedKmh = a.speedKmh + (b.speedKmh - a.speedKmh) * f
                )
            )
            t += stepMs
        }
        return out
    }
}
