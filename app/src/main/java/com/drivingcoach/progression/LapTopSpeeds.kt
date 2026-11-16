package com.drivingcoach.progression

import org.json.JSONObject
import java.io.File

/**
 * Per-lap top speed from the recorder trace, for Apex Exit and Top Gun (SRS PG-27, PG-29).
 *
 * The same filter as `OfflineCoachingEngine.findTopSpeed`: Doppler speed above 5 m/s
 * (≈18 km/h, pit-lane creep) and at most 97.2 m/s (350 km/h, a GPS spike), so the Session
 * Result's top speed and the medals agree on what "fast" was.
 */
object LapTopSpeeds {

    const val MIN_MEANINGFUL_SPEED_MS = 5.0
    const val MAX_PLAUSIBLE_SPEED_MS = 97.2

    data class Sample(val timestampMs: Long, val speedMs: Double)

    /** One lap's time span, inclusive at both ends, as LapEntity stores it. */
    data class Span(val startTs: Long, val endTs: Long)

    /**
     * Highest plausible speed, km/h, among the samples inside each span; null for a lap with
     * none. Pure, so the rule inputs can be tested without a file.
     */
    fun of(samples: List<Sample>, laps: List<Span>): List<Double?> {
        val usable = samples
            .filter { it.speedMs > MIN_MEANINGFUL_SPEED_MS && it.speedMs <= MAX_PLAUSIBLE_SPEED_MS }
            .sortedBy { it.timestampMs }
        return laps.map { lap ->
            usable.asSequence()
                .filter { it.timestampMs >= lap.startTs && it.timestampMs <= lap.endTs }
                .maxOfOrNull { it.speedMs }
                ?.times(3.6)
        }
    }

    /** Reads a recorder JSONL file; null when it is missing or unreadable. */
    fun ofTelemetryFile(file: File, laps: List<Span>): List<Double?>? {
        if (!file.isFile) return null
        return try {
            val samples = ArrayList<Sample>()
            file.useLines { lines -> lines.forEach { line -> parse(line)?.let(samples::add) } }
            of(samples, laps)
        } catch (e: Exception) {
            null
        }
    }

    private fun parse(line: String): Sample? {
        if (line.isBlank()) return null
        return try {
            val json = JSONObject(line)
            if (!json.has("speedMs")) return null
            Sample(json.optLong("timestampMs", json.optLong("ts", 0L)), json.optDouble("speedMs", 0.0))
        } catch (e: Exception) {
            null
        }
    }
}
