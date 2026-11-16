package com.drivingcoach.progression

import com.drivingcoach.util.GeoUtils
import org.json.JSONObject
import java.io.File

/**
 * Distance driven in one session, measured from its GPS trace (SRS PG-03).
 *
 * The trace is summed fix to fix, but a phone's GPS wanders even when the car is parked,
 * and a re-lock after a tunnel or a cold start can throw one fix hundreds of metres away.
 * Summing those as driving would let a driver earn kilometres in the paddock, so:
 *
 * - a fix reporting horizontal accuracy worse than [MAX_ACCURACY_M] is ignored outright;
 * - a segment whose reported speed (or, without one, implied speed) is under
 *   [MIN_MOVING_SPEED_MS] is stationary jitter and adds nothing;
 * - a segment implying more than [MAX_PLAUSIBLE_SPEED_MS], or spanning more than
 *   [MAX_GAP_MS] of missing data, adds nothing and the trace re-anchors on the new fix,
 *   so one bad fix costs at most two segments rather than poisoning the rest.
 */
object SessionDistance {

    const val MAX_ACCURACY_M = 20.0
    const val MIN_MOVING_SPEED_MS = 5.0 / 3.6
    const val MAX_PLAUSIBLE_SPEED_MS = 250.0 / 3.6
    const val MAX_GAP_MS = 30_000L

    data class Fix(
        val timestampMs: Long,
        val lat: Double,
        val lng: Double,
        /** Doppler speed reported by the receiver, m/s; null when not recorded. */
        val speedMs: Double? = null,
        /** Horizontal accuracy, metres; null or non-positive means unknown. */
        val accuracyM: Double? = null
    )

    fun ofFixes(fixes: List<Fix>): Double {
        var total = 0.0
        var anchor: Fix? = null
        for (fix in fixes.sortedBy { it.timestampMs }) {
            val accuracy = fix.accuracyM
            if (accuracy != null && accuracy > MAX_ACCURACY_M) continue
            if (fix.lat == 0.0 && fix.lng == 0.0) continue

            val previous = anchor
            if (previous == null) {
                anchor = fix
                continue
            }
            val dtMs = fix.timestampMs - previous.timestampMs
            if (dtMs <= 0L) continue

            val metres = GeoUtils.haversineDistance(previous.lat, previous.lng, fix.lat, fix.lng)
            val implied = metres / (dtMs / 1000.0)
            anchor = fix
            if (dtMs > MAX_GAP_MS || implied > MAX_PLAUSIBLE_SPEED_MS) continue

            val speed = fix.speedMs?.takeIf { it >= 0.0 } ?: implied
            if (speed < MIN_MOVING_SPEED_MS) continue

            total += metres
        }
        return total
    }

    /**
     * Reads a recorder JSONL file (SRS §14.2). Returns null when the file is missing or
     * unreadable, so the caller can tell "no trace" from "a trace of a parked car".
     */
    fun ofTelemetryFile(file: File): Double? {
        if (!file.isFile) return null
        return try {
            val fixes = ArrayList<Fix>()
            file.useLines { lines ->
                lines.forEach { line -> parseFix(line)?.let(fixes::add) }
            }
            ofFixes(fixes)
        } catch (e: Exception) {
            null
        }
    }

    internal fun parseFix(line: String): Fix? {
        if (line.isBlank()) return null
        return try {
            val json = JSONObject(line)
            if (!json.has("latitude") || !json.has("longitude")) return null
            Fix(
                timestampMs = json.optLong("timestampMs", json.optLong("ts", 0L)),
                lat = json.getDouble("latitude"),
                lng = json.getDouble("longitude"),
                speedMs = if (json.has("speedMs")) json.optDouble("speedMs") else null,
                accuracyM = if (json.has("gpsAccuracyM")) {
                    json.optDouble("gpsAccuracyM").takeIf { it > 0.0 }
                } else {
                    null
                }
            )
        } catch (e: Exception) {
            null
        }
    }

    /** Used when there is no trace: laps times the circuit's declared length, if known. */
    fun fallback(lapCount: Int, trackLengthM: Int?): Double =
        if (trackLengthM != null && trackLengthM > 0) lapCount * trackLengthM.toDouble() else 0.0
}
