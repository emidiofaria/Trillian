package com.drivingcoach.lap

import com.drivingcoach.data.telemetry.TelemetrySample

/**
 * Recovers where the car was at a given instant, by interpolating between the two
 * recorded fixes either side of it.
 *
 * ### Why this exists
 *
 * Lap detection decides that a lap began at an instant *between* two fixes: the
 * moment the car's path crossed the start/finish plane (NF-16). That instant is
 * persisted. The matching *position* is computed at the same moment, from the same
 * interpolation fraction, and then discarded.
 *
 * Everything downstream that measures distance therefore used to start its ruler at
 * the first fix *after* the line, while its clock started at the line. At the
 * reference device's 1 Hz that is fifteen to twenty metres of road that the clock
 * counted and the ruler did not, which pushed every sector boundary - and the
 * start/finish marker on the map - along the circuit by a drifting amount.
 *
 * ### Why interpolating here is sound rather than a second guess
 *
 * This is not a reimplementation of the detector's plane geometry, which would be a
 * second opinion liable to disagree with the first. The detector derives the crossing
 * *timestamp* and the crossing *position* from one shared fraction of one segment.
 * Given the timestamp, that fraction is recoverable by simple proportion, and the
 * position follows exactly. The only loss is the detector's rounding of the instant
 * to a whole millisecond, which at racing speed is about two centimetres.
 *
 * The consequence worth stating plainly: if lap detection ever stops deriving the two
 * from a common fraction, this inversion stops being exact and silently becomes an
 * estimate. [LocalLapDetector] carries a note to that effect at the crossing site.
 */
object LapAnchor {

    /** A point on the ground, in degrees. */
    data class Position(val latitude: Double, val longitude: Double)

    /**
     * Where the car was at [instantMs].
     *
     * @param samples the session's fixes, sorted ascending by timestamp
     * @return the interpolated position, or null when [instantMs] falls outside the
     *   recorded range - there is nothing to interpolate between, and extrapolating
     *   past the end of the data would be inventing a position rather than recovering
     *   one.
     */
    fun positionAt(samples: List<TelemetrySample>, instantMs: Long): Position? {
        if (samples.isEmpty()) return null
        if (instantMs < samples.first().timestampMs) return null
        if (instantMs > samples.last().timestampMs) return null

        for (i in samples.indices) {
            val s = samples[i]
            if (s.timestampMs == instantMs) return Position(s.latitude, s.longitude)
            if (s.timestampMs < instantMs) continue

            // First sample past the instant: the one before it brackets the other side.
            val prev = samples[i - 1]
            val spanMs = s.timestampMs - prev.timestampMs
            if (spanMs <= 0L) return Position(prev.latitude, prev.longitude)

            val fraction = (instantMs - prev.timestampMs).toDouble() / spanMs
            return Position(
                latitude = prev.latitude + (s.latitude - prev.latitude) * fraction,
                longitude = prev.longitude + (s.longitude - prev.longitude) * fraction
            )
        }
        return null
    }
}
