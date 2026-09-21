package com.drivingcoach.lap

import android.util.Log
import com.drivingcoach.data.telemetry.StartLineData
import com.drivingcoach.data.telemetry.TelemetryHeader
import com.drivingcoach.data.telemetry.TelemetrySample
import com.drivingcoach.util.GeoUtils
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.hypot
import kotlin.math.roundToLong

private const val TAG = "LocalLapDetector"

/**
 * Detects laps locally from a JSONL telemetry file using start/finish line crossing.
 * 
 * This enables offline lap time display immediately after a session ends,
 * without requiring network connectivity to the backend.
 */
@Singleton
class LocalLapDetector @Inject constructor() {

    companion object {
        /** Minimum lap time in milliseconds (20 seconds) to filter false triggers */
        const val MIN_LAP_TIME_MS = 20_000L
        
        /** Minimum distance from the start line before a crossing counts (SRS LD-06) */
        const val MIN_DISTANCE_FROM_START_M = 50.0  // 50m works for kart tracks (300-500m)
        
        /** Minimum number of samples required for valid detection */
        const val MIN_SAMPLES = 50

        /**
         * How far to either side of the start point a pass still counts, in metres.
         *
         * This replaces the length of the user's captured line as the width of the
         * detection target. Incident 13 showed the captured line cannot serve that
         * purpose: it was 7.15 m long, while GPS error in the same session averaged
         * 4.8 m.
         *
         * The value is deliberately generous. On the incident 13 session the result
         * is identical for every value from 10 m to 25 m, because the driver passed
         * within 0.8-2.3 m of the start point on all five laps. A detector whose
         * answer does not depend on this number is one that is measuring the track
         * rather than the threshold.
         */
        const val DETECTION_HALF_WIDTH_M = 15.0

        /**
         * How far the direction of travel may differ from the first crossing of the
         * session before a crossing is rejected, in degrees.
         *
         * Every pass through a start/finish is made in substantially the same
         * direction: across the five laps of the incident 13 session the heading
         * varied by only 9 degrees. 60 is therefore generous towards real driving
         * while still rejecting a pass made in the opposite direction, or one made
         * by a different part of the circuit that happens to run near the start
         * point.
         *
         * Not set to 90: where the start/finish sits on a corner, the car arriving
         * and the car leaving differ by exactly 90 degrees, which would leave the
         * guard deciding a real case on floating-point rounding.
         */
        const val MAX_HEADING_DIFFERENCE_DEG = 60.0

        /** Segments shorter than this carry no reliable direction (car stationary). */
        private const val MIN_SEGMENT_LENGTH_M = 0.5

        /**
         * How far the start point may be moved onto the driven path before the
         * correction is refused, in metres.
         *
         * Incident 14 established that this device's position is biased while the
         * phone is held still and correct once the car is moving: over the two
         * seconds in which the kart accelerated away, the reported position moved
         * 11 m sideways onto the racing line and stayed there. The start line,
         * captured minutes earlier while the tester stood at the track edge, was
         * left 16 m to the side of a track the car never drove within 15 m of.
         *
         * Correcting that is worth doing. Relocating a line that was genuinely
         * captured somewhere else is not, and the two are indistinguishable beyond
         * a certain distance, so the correction is bounded rather than trusted.
         * Incident 14 needed 16.1 m of the 20 m allowed: the bound is deliberately
         * close to the one real measurement rather than comfortably clear of it,
         * because a session past the bound fails loudly and explains itself, which
         * is a better outcome than a start line silently moved somewhere plausible.
         */
        const val MAX_ANCHOR_PROJECTION_M = 20.0

        /**
         * Speed below which a sample's position is not trusted to place the anchor.
         *
         * The whole basis of the correction is that GPS is reliable under motion
         * and not at rest, so the path it is projected onto must be made only of
         * moving samples. Incident 14's three racing laps lay within 4.5 m of each
         * other; its stationary phase wandered 11 m while reporting 7.7 m accuracy.
         */
        const val MIN_ANCHOR_SPEED_MS = 4.0

        /**
         * Speed below which a pass of the start point is not a lap boundary.
         *
         * Incident 15: the driver started recording, put the phone in a pocket and
         * queued for a kart for sixteen minutes. The queue stood beside the start
         * straight, so the very first segment of the session - a walking pace 2.9
         * m/s drift across the start point - was accepted as a crossing. Being the
         * first accepted crossing it also became the session's reference heading,
         * at 272 degrees. Every one of the twelve racing crossings that followed
         * arrived between 331 and 343 degrees, so ten of them were rejected as
         * heading mismatches and the two that squeaked past the 60 degree guard
         * produced laps of 15m54s and 6m44s.
         *
         * A lap boundary is crossed under racing speed by definition. Sharing the
         * value with [MIN_ANCHOR_SPEED_MS] is deliberate rather than coincidental:
         * both encode the same judgement, that below 4 m/s this app is looking at
         * a pedestrian rather than a vehicle. On the incident 15 session the two
         * populations are separated by a factor of four - the queue never exceeded
         * 3 m/s, the racing crossings never fell below 5.9 m/s - so the threshold
         * is not a tuned value.
         *
         * The cost is a lap boundary genuinely crossed below 14 km/h, under a
         * caution or behind traffic, which merges two laps into one. That is a
         * worse-but-rarer failure than the one it prevents, and unlike the queue
         * crossing it is recorded as [RejectionReason.TOO_SLOW] rather than
         * happening in silence.
         */
        const val MIN_CROSSING_SPEED_MS = 4.0

        /**
         * How much quicker than a track's known best lap a crossing may still be
         * accepted, as a fraction of that lap.
         *
         * A catalogue lap time is what the circuit has been seen to produce, not a
         * floor, so a driver quicker than everyone before them must not have their
         * lap thrown away. 0.8 admits a lap 20% under the known range - at Baltar,
         * 56 s against a 70 s range and a 72.5 s measured best - while still being
         * far stronger than the generic 20 s guard.
         */
        private const val LAP_TIME_PRIOR_GRACE = 0.8

        /**
         * Slowest average speed, in m/s, at which a whole lap is still credible.
         *
         * This replaces a test against the circuit's declared slowest lap, for two
         * reasons found while reviewing what that test does to a slow driver.
         *
         * The first is evidential. A lap envelope is a figure somebody typed in;
         * the lap length is surveyed. Deriving the guard from the measured quantity
         * rather than the estimated one means a badly guessed envelope can no
         * longer erase a session.
         *
         * The second is the failure it caused. Against Baltar's original 90 s upper
         * bound the old rule discarded anything over 135 s, so a timid driver
         * lapping in 150 s would have had every lap thrown away and been shown
         * nothing at all - the guard meant to protect them deleting their session
         * in silence. At 1020 m this floor admits laps out to 204 s.
         *
         * 5 m/s is the same judgement as [MIN_CROSSING_SPEED_MS], averaged over a
         * lap instead of sampled at a point: below 18 km/h sustained for an entire
         * lap, this is not a vehicle on a circuit. Incident 15's two false laps
         * average 1.07 and 2.52 m/s, so they remain comfortably caught.
         */
        const val MIN_PLAUSIBLE_LAP_SPEED_MS = 5.0

        /**
         * How close a discarded pass must come to the start point to be worth
         * reporting, in metres.
         *
         * Every lap crosses the perpendicular plane somewhere out on the far side
         * of the circuit. Those are geometry, not near misses, and recording them
         * would bury the ones that matter.
         */
        private const val REJECTION_REPORTING_RADIUS_M = 60.0

        /** Lag range searched when asking whether the driven path repeats, in seconds. */
        private const val MIN_REPEAT_LAG_S = 25
        private const val MAX_REPEAT_LAG_S = 180

        /**
         * How much better than a typical lag the best lag must be before the path
         * is called repeating.
         *
         * Measured on the two real sessions: a genuine circuit scores 0.15 and
         * 0.21, while the same samples shuffled into a path that repeats nothing
         * score 0.87 and 0.88. Any threshold in the wide gap between those would
         * do; 0.5 sits in the middle of it.
         */
        private const val REPEAT_RATIO_THRESHOLD = 0.5
    }

    /**
     * Represents a start/finish line defined by two GPS coordinates.
     *
     * Only the midpoint of these two points is used to locate the start/finish.
     * Their orientation is deliberately ignored - see [detectCrossings].
     */
    data class StartLine(
        val lat1: Double,
        val lng1: Double,
        val lat2: Double,
        val lng2: Double
    ) {
        /**
         * A line is usable if neither endpoint is the null island (0, 0).
         *
         * The previous form of this check used `||`, which passed as long as any
         * single coordinate was non-zero: a line with one endpoint off the coast of
         * Africa was accepted as valid. Note that testing each coordinate against
         * zero individually would be wrong in the other direction, since longitude
         * 0 is Greenwich and a perfectly legitimate place to drive.
         */
        fun isValid(): Boolean {
            val firstIsNullIsland = lat1 == 0.0 && lng1 == 0.0
            val secondIsNullIsland = lat2 == 0.0 && lng2 == 0.0
            return !firstIsNullIsland && !secondIsNullIsland
        }
        
        /** Returns the midpoint of the start line */
        fun midpoint(): Pair<Double, Double> = Pair((lat1 + lat2) / 2, (lng1 + lng2) / 2)
    }

    /**
     * What a known circuit tells the detector before it looks at the telemetry.
     *
     * Supplied by the track catalogue when the driver picked a circuit rather than
     * capturing a line. Every field is optional and [NONE] reproduces the previous
     * behaviour exactly, because the detector must remain a single code path: a
     * session on a known track and a session on an unknown one differ in how well
     * informed the detector is, not in which algorithm runs.
     *
     * @param travelHeadingDeg direction the circuit is driven through the
     *   start/finish. This replaces the first accepted crossing as the reference
     *   for [MAX_HEADING_DIFFERENCE_DEG], which is what incident 15 poisoned. At
     *   Baltar it is load-bearing rather than merely helpful: the nearest other
     *   part of that circuit passes 24.7 m from the start/finish, against a 15 m
     *   detection half width and a device reporting +/-6 m, so the heading guard
     *   is the margin.
     * @param fastestLapMs quickest lap the circuit is known to produce.
     * @param slowestLapMs slowest lap the circuit is known to produce. Advisory
     *   only: it is shown to the driver as the expected lap window, and is
     *   deliberately *not* used to discard laps - see [lapsArePlausible].
     * @param lengthM surveyed lap length. This, not [slowestLapMs], is what
     *   decides whether a detected lap could have been driven.
     */
    data class TrackPriors(
        val travelHeadingDeg: Double? = null,
        val fastestLapMs: Long? = null,
        val slowestLapMs: Long? = null,
        val lengthM: Int? = null
    ) {
        companion object {
            /** No prior knowledge: the detector behaves as it does for any captured line. */
            val NONE = TrackPriors()
        }

        /**
         * Minimum time between crossings, tightened by the circuit's known best lap
         * where one is available.
         */
        fun minLapTimeMs(): Long {
            val fromTrack = fastestLapMs?.let { (it * LAP_TIME_PRIOR_GRACE).toLong() } ?: 0L
            return maxOf(MIN_LAP_TIME_MS, fromTrack)
        }

        /**
         * Whether [laps] could have been driven on this circuit.
         *
         * Measured against the surveyed [lengthM] rather than the declared lap
         * envelope: a lap is incredible when its *average speed* falls below
         * [MIN_PLAUSIBLE_LAP_SPEED_MS], which means the boundaries either side of
         * it are far enough apart that a whole lap went missing between them.
         *
         * Two deliberate choices here, both of which protect a slow driver:
         *
         * The envelope is not the yardstick. `slowestLapMs` is a figure somebody
         * typed into the catalogue, and an envelope guessed too tight would erase
         * real sessions. The lap length was surveyed, so it is the better evidence.
         *
         * The set is discarded only when *every* lap fails. A single long lap among
         * normal ones is a real lap - a spin, an off, a slow kart ahead - and
         * throwing it away would be editing the driver's session to make it tidy.
         * What incident 15 produced was different in kind: every "lap" in the set
         * was nonsense, because none of them were laps. That is the shape this
         * looks for.
         *
         * Always true when the circuit's length is unknown, since then there is
         * nothing to measure against.
         */
        fun lapsArePlausible(laps: List<DetectedLap>): Boolean {
            val length = lengthM?.toDouble()?.takeIf { it > 0 } ?: return true
            if (laps.isEmpty()) return true
            return laps.any { lap ->
                lap.durationMs > 0 &&
                    length / (lap.durationMs / 1000.0) >= MIN_PLAUSIBLE_LAP_SPEED_MS
            }
        }
    }

    /**
     * A crossing of the start/finish that was accepted.
     *
     * @param timestampMs interpolated instant of the crossing, not the timestamp of
     *   the sample that followed it
     * @param lateralOffsetM how far to the side of the start point the car passed
     * @param headingDeg direction of travel through the crossing
     */
    data class Crossing(
        val timestampMs: Long,
        val lateralOffsetM: Double,
        val headingDeg: Double
    )

    /** Why a candidate crossing was not counted as a lap boundary. */
    enum class RejectionReason {
        /** Came sooner after the previous crossing than [MIN_LAP_TIME_MS]. */
        TOO_SOON,
        /** The car never got [MIN_DISTANCE_FROM_START_M] away since the last crossing. */
        TOO_CLOSE_TO_START,
        /** Direction of travel differed from the first crossing by too much. */
        HEADING_MISMATCH,
        /** Passed the start point further to the side than [DETECTION_HALF_WIDTH_M]. */
        TOO_FAR_TO_THE_SIDE,

        /** Crossed the start point below [MIN_CROSSING_SPEED_MS], so not under power. */
        TOO_SLOW
    }

    /** A candidate crossing that was discarded, recorded so failures can be diagnosed. */
    data class RejectedCrossing(
        val timestampMs: Long,
        val reason: RejectionReason,
        val detail: String,
        /**
         * How far to the side of the start point this pass went, in metres, when
         * that is what disqualified it. Kept as a number as well as prose so the
         * app can tell the driver how close they came without parsing a sentence.
         */
        val lateralOffsetM: Double? = null
    )

    /**
     * Represents a detected lap with timing information.
     */
    data class DetectedLap(
        val lapNumber: Int,
        val startTs: Long,
        val endTs: Long,
        val durationMs: Long
    )

    /**
     * Result of lap detection containing laps and any error/warning messages.
     */
    sealed class DetectionResult {
        data class Success(val laps: List<DetectedLap>) : DetectionResult()
        data class InsufficientLaps(val lapCount: Int) : DetectionResult()
        data class NoStartLine(val message: String = "No start line defined") : DetectionResult()
        data class Error(val message: String) : DetectionResult()
    }

    private val gson = Gson()

    /**
     * What the detector observed and decided during a run.
     *
     * Recorded because incident 09 - the same "no laps detected" symptom - was
     * closed at 55% confidence and its cause never established, for want of any
     * record of what the detector had decided. Incident 13 was only diagnosable
     * because the raw telemetry happened to be retained by hand. These figures
     * make the next occurrence answerable from the session itself.
     *
     * @param angleBetweenLineAndTravelDeg 0 means the captured start line lies
     *   along the direction the car travels, 90 means squarely across it. Incident
     *   13 measured 1.2 degrees. This single number identifies that failure.
     */
    data class DetectionDiagnostics(
        val sampleCount: Int,
        val durationMs: Long,
        val observedSampleRateHz: Double,
        val startLineLengthM: Double,
        val startLineBearingDeg: Double,
        val angleBetweenLineAndTravelDeg: Double?,
        val acceptedCrossings: List<Crossing>,
        val rejectedCrossings: List<RejectedCrossing>,
        val lapCount: Int,
        val anchor: AnchorSource = AnchorSource.CAPTURED,
        val anchorProjectionM: Double? = null,
        val captureWindowSpeedMs: Double? = null,
        val captureWindowScatterM: Double? = null,
        val pathRepeats: Boolean? = null,
        /**
         * Where the reference direction came from. [HeadingReference.FIRST_CROSSING]
         * on a session whose opening minutes were spent stationary is the signature
         * of incident 15.
         */
        val headingReference: HeadingReference = HeadingReference.FIRST_CROSSING,
        val referenceHeadingDeg: Double? = null,
        /** Minimum gap enforced between crossings, tightened by the circuit if known. */
        val minLapTimeMs: Long = MIN_LAP_TIME_MS
    )

    /** Which start point the reported laps were measured against. */
    enum class AnchorSource {
        /** The midpoint of the line the driver captured, used as given. */
        CAPTURED,

        /**
         * That midpoint moved onto the path the car actually drove, because the
         * captured one yielded no laps. See [MAX_ANCHOR_PROJECTION_M].
         */
        PROJECTED_ONTO_PATH
    }

    /** A start point to measure crossings against, and where it came from. */
    private data class Anchor(
        val latitude: Double,
        val longitude: Double,
        val source: AnchorSource,
        val projectionM: Double? = null
    )

    /** Where the direction a lap boundary is crossed in was taken from. */
    enum class HeadingReference {
        /** The first crossing the session produced, as it has always been. */
        FIRST_CROSSING,

        /** The direction the circuit is driven, supplied by the track catalogue. */
        TRACK_CATALOGUE
    }

    /** One complete pass of a session against a given start point and reference. */
    private data class Attempt(
        val anchor: Anchor,
        val seedHeadingDeg: Double?,
        val crossings: List<Crossing>,
        val rejections: List<RejectedCrossing>,
        val laps: List<DetectedLap>
    )

    /** A detection result together with the reasoning that produced it. */
    data class DetectionOutcome(
        val result: DetectionResult,
        val diagnostics: DetectionDiagnostics?
    )

    /**
     * Detects laps from a JSONL telemetry file.
     *
     * @param jsonlFile The telemetry file to process
     * @param startLine The start/finish line coordinates
     * @param priors what a known circuit already tells us, or [TrackPriors.NONE]
     * @return DetectionResult containing detected laps or error information
     */
    @JvmOverloads
    fun detectLaps(
        jsonlFile: File,
        startLine: StartLine,
        priors: TrackPriors = TrackPriors.NONE
    ): DetectionResult = detectLapsWithDiagnostics(jsonlFile, startLine, priors).result

    /**
     * As [detectLaps], but also returns what the detector observed and why it
     * accepted or rejected each candidate crossing.
     *
     * Diagnostics are null when detection could not run at all (no start line, no
     * file, too few samples) - in those cases the result itself is the explanation.
     */
    @JvmOverloads
    fun detectLapsWithDiagnostics(
        jsonlFile: File,
        startLine: StartLine,
        priors: TrackPriors = TrackPriors.NONE
    ): DetectionOutcome {
        Log.d(TAG, "=== LAP DETECTION START ===")
        Log.d(TAG, "Start line: (${startLine.lat1}, ${startLine.lng1}) to (${startLine.lat2}, ${startLine.lng2})")
        Log.d(TAG, "Start line valid: ${startLine.isValid()}")
        
        // Validate start line
        if (!startLine.isValid()) {
            Log.w(TAG, "Invalid start line!")
            return DetectionOutcome(DetectionResult.NoStartLine(), null)
        }

        // Check file exists
        if (!jsonlFile.exists()) {
            Log.e(TAG, "File not found: ${jsonlFile.absolutePath}")
            return DetectionOutcome(
                DetectionResult.Error("Telemetry file not found: ${jsonlFile.absolutePath}"), null
            )
        }

        // Read and parse samples
        val samples = readSamples(jsonlFile)
        Log.d(TAG, "Read ${samples.size} telemetry samples")
        if (samples.size < MIN_SAMPLES) {
            return DetectionOutcome(
                DetectionResult.Error(
                    "Insufficient telemetry data (${samples.size} samples, need $MIN_SAMPLES)"
                ),
                null
            )
        }
        
        // Log GPS bounds
        val minLat = samples.minOf { it.latitude }
        val maxLat = samples.maxOf { it.latitude }
        val minLng = samples.minOf { it.longitude }
        val maxLng = samples.maxOf { it.longitude }
        Log.d(TAG, "GPS bounds: lat[$minLat to $maxLat], lng[$minLng to $maxLng]")

        // Detect crossings of the start/finish point as the driver captured it.
        val (capturedLat, capturedLng) = startLine.midpoint()
        val captured = Anchor(capturedLat, capturedLng, AnchorSource.CAPTURED)
        val minLapTimeMs = priors.minLapTimeMs()

        /** One complete pass of the session against a given start point and reference. */
        fun attempt(against: Anchor, seedHeadingDeg: Double?): Attempt {
            val found = mutableListOf<RejectedCrossing>()
            val accepted = detectCrossings(samples, against, found, seedHeadingDeg, minLapTimeMs)
            return Attempt(against, seedHeadingDeg, accepted, found, buildLaps(accepted))
        }

        /**
         * An attempt is only worth keeping if it produced laps that could have been
         * driven. Incident 15 returned two laps of 954 s and 404 s around a 1020 m
         * circuit - walking pace - and because they were laps at all every later
         * correction was skipped and the driver was shown them as fact.
         */
        fun Attempt.isSatisfactory(): Boolean =
            laps.size >= 2 && priors.lapsArePlausible(laps)

        var best = attempt(captured, priors.travelHeadingDeg)
        Log.d(
            TAG,
            "Detected ${best.crossings.size} crossings, rejected ${best.rejections.size} candidates" +
                (priors.travelHeadingDeg?.let { " using the track's %.1f degree reference".format(it) } ?: "")
        )

        // A catalogue heading that does not match the session is worse than none at
        // all, because it rejects everything. The circuit may have been driven the
        // other way round, or the entry may simply be wrong. Fall back to reading
        // the reference off the session itself rather than reporting no laps.
        if (!best.isSatisfactory() && priors.travelHeadingDeg != null) {
            val unseeded = attempt(captured, null)
            Log.d(
                TAG,
                "Track heading yielded ${best.laps.size} laps; reading the reference from the " +
                    "session itself yields ${unseeded.laps.size}"
            )
            if (unseeded.isSatisfactory()) best = unseeded
        }

        // A session with no laps against the captured line may be a session whose
        // line was captured while the phone was stationary and therefore several
        // metres off the track - incident 14. Retry against the path the car
        // actually drove, and keep that answer only if it is a better one.
        if (!best.isSatisfactory()) {
            val projected = projectOntoDrivenPath(samples, capturedLat, capturedLng)
            val distance = projected?.projectionM
            if (projected != null && distance != null && distance <= MAX_ANCHOR_PROJECTION_M) {
                val retry = attempt(projected, priors.travelHeadingDeg)
                Log.d(
                    TAG,
                    "Captured start point found ${best.laps.size} laps; %.1fm onto the driven path finds ${retry.laps.size}"
                        .format(distance)
                )
                if (retry.isSatisfactory()) best = retry
            } else {
                Log.d(
                    TAG,
                    "Start point is %s to correct onto the driven path"
                        .format(
                            distance?.let { "%.1fm off, too far".format(it) }
                                ?: "not correctable: no moving path"
                        )
                )
            }
        }

        val anchor = best.anchor
        val crossings = best.crossings
        val rejections = best.rejections

        // Where the circuit's length is known and nothing in the answer could have
        // been driven at speed, nothing has been detected. Incident 15 showed a
        // driver two laps of 15m54s and 6m44s on a 1020 m kart track; reporting them
        // as laps was a worse failure than reporting none, because it looked like an
        // answer.
        val plausible = priors.lapsArePlausible(best.laps)
        if (!plausible) {
            val slowest = best.laps.maxOf { it.durationMs }
            val impliedSpeed = (priors.lengthM ?: 0) / (slowest / 1000.0)
            Log.w(
                TAG,
                "Discarding ${best.laps.size} laps: none could have been driven. Slowest is " +
                    "${slowest}ms, which is %.2f m/s over ${priors.lengthM} m".format(impliedSpeed)
            )
        }
        val laps = if (plausible) best.laps else emptyList()
        rejections.forEach { Log.d(TAG, "  rejected at ${it.timestampMs}: ${it.reason} - ${it.detail}") }
        Log.d(TAG, "Built ${laps.size} laps from crossings using ${anchor.source}")

        val diagnostics = buildDiagnostics(
            samples, startLine, anchor, crossings, rejections, laps.size,
            headingReference = if (best.seedHeadingDeg != null) {
                HeadingReference.TRACK_CATALOGUE
            } else {
                HeadingReference.FIRST_CROSSING
            },
            referenceHeadingDeg = best.seedHeadingDeg ?: crossings.firstOrNull()?.headingDeg,
            minLapTimeMs = minLapTimeMs
        )
        Log.d(
            TAG,
            "Start line: %.2fm long, bearing %.1f, %s to the direction of travel".format(
                diagnostics.startLineLengthM,
                diagnostics.startLineBearingDeg,
                diagnostics.angleBetweenLineAndTravelDeg
                    ?.let { "%.1f degrees".format(it) } ?: "angle unknown"
            )
        )

        val result = if (laps.size >= 2) {
            DetectionResult.Success(laps)
        } else {
            DetectionResult.InsufficientLaps(laps.size)
        }
        return DetectionOutcome(result, diagnostics)
    }

    private fun buildDiagnostics(
        samples: List<TelemetrySample>,
        startLine: StartLine,
        anchor: Anchor,
        crossings: List<Crossing>,
        rejections: List<RejectedCrossing>,
        lapCount: Int,
        headingReference: HeadingReference = HeadingReference.FIRST_CROSSING,
        referenceHeadingDeg: Double? = null,
        minLapTimeMs: Long = MIN_LAP_TIME_MS
    ): DetectionDiagnostics {
        val durationMs = samples.last().timestampMs - samples.first().timestampMs
        val rateHz = if (durationMs > 0) (samples.size - 1) * 1000.0 / durationMs else 0.0

        val lengthM = GeoUtils.haversineDistance(
            startLine.lat1, startLine.lng1, startLine.lat2, startLine.lng2
        )
        val bearingDeg = GeoUtils.bearingDegrees(
            startLine.lat1, startLine.lng1, startLine.lat2, startLine.lng2
        )

        // When nothing was accepted there is no crossing to take a heading from, and
        // that is precisely the case this figure exists to explain. Fall back to the
        // heading of the sample nearest the start point: the car passed the start at
        // some point even in a session where no crossing was recognised.
        val travelHeading = crossings.firstOrNull()?.headingDeg
            ?: headingAtClosestApproach(samples, startLine)

        // Folded onto 0..90: a line pointing back down the track is just as parallel
        // to the racing direction as one pointing up it.
        val angleToTravel = travelHeading?.let {
            val difference = GeoUtils.angularDifferenceDegrees(bearingDeg, it)
            if (difference > 90.0) 180.0 - difference else difference
        }

        // How the session opened, because a start line is captured moments before
        // it. Incident 14's opening was a walk to the kart and then a wait in it,
        // all of it reported 11-18 m to one side of the track the car went on to
        // drive. Recording the conditions makes that visible instead of inferred.
        val opening = samples.filter { it.timestampMs - samples.first().timestampMs <= 30_000L }
        val openingSpeed = opening.takeIf { it.isNotEmpty() }?.map { it.speedMs }?.average()
        val stationary = opening.filter { it.speedMs < MIN_ANCHOR_SPEED_MS }
        val openingScatter = if (stationary.size >= 3) {
            val meanLat = stationary.map { it.latitude }.average()
            val meanLng = stationary.map { it.longitude }.average()
            stationary.maxOf {
                GeoUtils.haversineDistance(it.latitude, it.longitude, meanLat, meanLng)
            }
        } else {
            null
        }

        return DetectionDiagnostics(
            sampleCount = samples.size,
            durationMs = durationMs,
            observedSampleRateHz = rateHz,
            startLineLengthM = lengthM,
            startLineBearingDeg = bearingDeg,
            angleBetweenLineAndTravelDeg = angleToTravel,
            acceptedCrossings = crossings,
            rejectedCrossings = rejections,
            lapCount = lapCount,
            anchor = anchor.source,
            anchorProjectionM = anchor.projectionM,
            captureWindowSpeedMs = openingSpeed,
            captureWindowScatterM = openingScatter,
            // Only asked when it can change what the driver is told.
            pathRepeats = if (lapCount < 2) pathRepeats(samples) else null,
            headingReference = headingReference,
            referenceHeadingDeg = referenceHeadingDeg,
            minLapTimeMs = minLapTimeMs
        )
    }

    /**
     * Direction the car was travelling as it came closest to the start point.
     *
     * Used to report the line-versus-travel angle for sessions where no crossing was
     * accepted at all. Returns null only if no segment in the session was long
     * enough to yield a meaningful bearing.
     */
    /**
     * The captured start point moved onto the path the car actually drove.
     *
     * Only segments travelled at [MIN_ANCHOR_SPEED_MS] or more are considered. That
     * restriction is the whole point rather than an optimisation: incident 14
     * showed this device reporting a position 11-18 m to one side while the phone
     * was walked and then held still, and reporting it correctly the moment the
     * car was moving. Projecting onto the stationary part of the session would
     * reproduce the error instead of correcting it.
     *
     * Returns null when no segment was driven fast enough to define a path.
     */
    private fun projectOntoDrivenPath(
        samples: List<TelemetrySample>,
        fromLat: Double,
        fromLng: Double
    ): Anchor? {
        var bestDistance = Double.MAX_VALUE
        var bestX = 0.0
        var bestY = 0.0
        var found = false

        for (i in 1 until samples.size) {
            val previous = samples[i - 1]
            val current = samples[i]
            if (previous.speedMs < MIN_ANCHOR_SPEED_MS || current.speedMs < MIN_ANCHOR_SPEED_MS) {
                continue
            }

            val (px, py) = GeoUtils.toLocalMetres(
                previous.latitude, previous.longitude, fromLat, fromLng
            )
            val (qx, qy) = GeoUtils.toLocalMetres(
                current.latitude, current.longitude, fromLat, fromLng
            )
            val dx = qx - px
            val dy = qy - py
            val squaredLength = dx * dx + dy * dy
            if (squaredLength < MIN_SEGMENT_LENGTH_M * MIN_SEGMENT_LENGTH_M) continue

            // Foot of the perpendicular from the start point, clamped to the segment.
            val along = (-(px * dx + py * dy) / squaredLength).coerceIn(0.0, 1.0)
            val footX = px + dx * along
            val footY = py + dy * along
            val distance = hypot(footX, footY)
            if (distance < bestDistance) {
                bestDistance = distance
                bestX = footX
                bestY = footY
                found = true
            }
        }
        if (!found) return null

        val (latitude, longitude) = GeoUtils.fromLocalMetres(bestX, bestY, fromLat, fromLng)
        return Anchor(latitude, longitude, AnchorSource.PROJECTED_ONTO_PATH, bestDistance)
    }

    /**
     * Whether the car drove the same path more than once.
     *
     * Reports only *that* the path repeats, never how often or how long a lap took.
     * The obvious extension - report the lag itself as the lap time - is wrong: on
     * the incident 13 session the strongest lag is 157 s, exactly twice its real
     * 79 s lap, because a path that repeats every lap also repeats every two laps.
     * Resolving that ambiguity is a detector in its own right. Until it exists,
     * this answers the one question a driver with no laps actually has - "are my
     * laps in there at all?" - and says nothing it cannot support.
     *
     * Returns null when the session is too short to judge.
     */
    private fun pathRepeats(samples: List<TelemetrySample>): Boolean? {
        val moving = samples.filter { it.speedMs >= MIN_ANCHOR_SPEED_MS }
        if (moving.isEmpty()) return null
        val origin = moving.first()
        val bySecond = LinkedHashMap<Int, Pair<Double, Double>>()
        for (sample in moving) {
            val second = ((sample.timestampMs - origin.timestampMs) / 1000L).toInt()
            bySecond.getOrPut(second) {
                GeoUtils.toLocalMetres(
                    sample.latitude, sample.longitude, origin.latitude, origin.longitude
                )
            }
        }

        val separations = mutableListOf<Double>()
        for (lag in MIN_REPEAT_LAG_S..MAX_REPEAT_LAG_S) {
            var total = 0.0
            var pairs = 0
            for ((second, point) in bySecond) {
                val later = bySecond[second + lag] ?: continue
                total += hypot(point.first - later.first, point.second - later.second)
                pairs++
            }
            if (pairs >= 30) separations.add(total / pairs)
        }
        if (separations.size < 10) return null

        val sorted = separations.sorted()
        val median = sorted[sorted.size / 2]
        if (median <= 0.0) return null
        return sorted.first() / median <= REPEAT_RATIO_THRESHOLD
    }

    private fun headingAtClosestApproach(
        samples: List<TelemetrySample>,
        startLine: StartLine
    ): Double? {
        val (startLat, startLng) = startLine.midpoint()
        var bestDistance = Double.MAX_VALUE
        var bestHeading: Double? = null

        for (i in 0 until samples.size - 1) {
            val from = samples[i]
            val to = samples[i + 1]
            if (GeoUtils.haversineDistance(
                    from.latitude, from.longitude, to.latitude, to.longitude
                ) < MIN_SEGMENT_LENGTH_M
            ) continue

            val distance = GeoUtils.haversineDistance(
                startLat, startLng, from.latitude, from.longitude
            )
            if (distance < bestDistance) {
                bestDistance = distance
                bestHeading = GeoUtils.bearingDegrees(
                    from.latitude, from.longitude, to.latitude, to.longitude
                )
            }
        }
        return bestHeading
    }

    /**
     * Data class to hold both header and samples from a telemetry file.
     */
    private data class TelemetryFileData(
        val header: TelemetryHeader? = null,
        val samples: List<TelemetrySample> = emptyList()
    )

    /**
     * Reads telemetry file, parsing both header and samples.
     * Header line (if present) has type="header" field.
     */
    private fun readTelemetryFile(file: File): TelemetryFileData {
        val samples = mutableListOf<TelemetrySample>()
        var header: TelemetryHeader? = null
        
        try {
            BufferedReader(FileReader(file)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    line?.let { jsonLine ->
                        if (jsonLine.isNotBlank()) {
                            try {
                                // Check if this is a header line
                                val jsonObj = gson.fromJson(jsonLine, JsonObject::class.java)
                                if (jsonObj.has("type") && jsonObj.get("type").asString == "header") {
                                    header = gson.fromJson(jsonLine, TelemetryHeader::class.java)
                                    Log.d(TAG, "Parsed header: startLine=${header?.startLine}")
                                } else {
                                    // Regular telemetry sample
                                    val sample = gson.fromJson(jsonLine, TelemetrySample::class.java)
                                    samples.add(sample)
                                }
                            } catch (e: Exception) {
                                // Skip malformed lines
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading telemetry file", e)
        }
        
        return TelemetryFileData(header, samples.sortedBy { it.timestampMs })
    }

    /**
     * Reads telemetry samples from a JSONL file (legacy method for compatibility).
     */
    private fun readSamples(file: File): List<TelemetrySample> {
        return readTelemetryFile(file).samples
    }

    /**
     * Reads start line from file header if available.
     */
    fun readStartLineFromFile(file: File): StartLine? {
        val data = readTelemetryFile(file)
        val startLineData = data.header?.startLine ?: return null
        return StartLine(
            lat1 = startLineData.lat1,
            lng1 = startLineData.lng1,
            lat2 = startLineData.lat2,
            lng2 = startLineData.lng2
        )
    }

    /**
     * Detects the instants at which the car passed the start/finish point.
     *
     * ## Why this does not use the user's line
     *
     * The obvious implementation - test each pair of GPS samples for intersection
     * with the line the user captured - was the shipped implementation, and it
     * failed completely in incident 13. The user's two points were 7.15 m apart
     * while GPS accuracy in that session averaged 4.8 m, so the *bearing* of the
     * line they defined was dominated by measurement noise. It came out at 283.7
     * degrees; the car drove through it at 280-289 degrees. The car travelled
     * *along* the line rather than across it, and no intersection was ever
     * possible. The driver's five laps were reported as none. Making the line
     * longer does not help: none of those passes crosses even the infinite
     * extension of it.
     *
     * The rule this replaces it with: never derive a direction from two
     * measurements that are closer together than the measurement error.
     *
     * ## What it does instead
     *
     * The user's two points are used only to say *where* the start/finish is -
     * their midpoint. The direction is taken from the car, which is something GPS
     * measures well. For each pair of consecutive samples, the detector asks
     * whether the car passed the plane through the start point perpendicular to
     * its own direction of travel, and if so, how far to the side of that point it
     * passed. Within [DETECTION_HALF_WIDTH_M], that is a crossing.
     *
     * The instant of the crossing is interpolated between the two samples. At the
     * 1 Hz that many phones deliver, the car covers 15-20 m between samples, so
     * attributing the crossing to whichever sample came next costs up to a full
     * second on the lap time - well over 1% on a kart lap, enough to hide a real
     * improvement from the driver.
     *
     * ## Guards
     *
     * - a crossing sooner than [MIN_LAP_TIME_MS] after the last one is rejected;
     * - a crossing is rejected unless the car got [MIN_DISTANCE_FROM_START_M] away
     *   from the start point since the last one;
     * - a crossing whose direction of travel differs from the first crossing of the
     *   session by more than [MAX_HEADING_DIFFERENCE_DEG] is rejected (SRS LD-14).
     *
     * Distance travelled is measured since the last *accepted* crossing, and is
     * deliberately not reset when a candidate is rejected. The incident 13 RCA
     * flagged this as suspicious (finding F2) on the grounds that a rejected
     * crossing leaks distance into the next candidate. Testing it showed the
     * opposite: where the start/finish sits on a corner, one pass produces two
     * candidates a second apart - the car arriving, then the car leaving. The
     * first is rejected on heading. Resetting the distance there makes the
     * legitimate second candidate look as though the car had travelled ten metres,
     * and the lap is lost. A rejected candidate is not a lap boundary, so the
     * window it measures must not restart.
     */
    private fun detectCrossings(
        samples: List<TelemetrySample>,
        anchor: Anchor,
        rejections: MutableList<RejectedCrossing> = mutableListOf(),
        seedHeadingDeg: Double? = null,
        minLapTimeMs: Long = MIN_LAP_TIME_MS
    ): List<Crossing> {
        if (samples.size < 2) return emptyList()

        val crossings = mutableListOf<Crossing>()
        val anchorLat = anchor.latitude
        val anchorLng = anchor.longitude

        var lastCrossingTs: Long? = null
        // A catalogue heading is a reference before the session starts, so unlike a
        // reference read off the first crossing it also guards that first crossing.
        // That is what keeps a pedestrian from opening the session.
        var referenceHeadingDeg: Double? = seedHeadingDeg
        var maxDistanceFromStart = 0.0

        for (i in 1 until samples.size) {
            val prev = samples[i - 1]
            val curr = samples[i]

            maxDistanceFromStart = maxOf(
                maxDistanceFromStart,
                GeoUtils.haversineDistance(curr.latitude, curr.longitude, anchorLat, anchorLng)
            )

            // Both samples in metres relative to the start point, which sits at the origin.
            val (px, py) = GeoUtils.toLocalMetres(prev.latitude, prev.longitude, anchorLat, anchorLng)
            val (qx, qy) = GeoUtils.toLocalMetres(curr.latitude, curr.longitude, anchorLat, anchorLng)

            val dx = qx - px
            val dy = qy - py
            val segmentLength = hypot(dx, dy)
            if (segmentLength < MIN_SEGMENT_LENGTH_M) continue  // stationary: no direction

            val ux = dx / segmentLength
            val uy = dy / segmentLength

            // Distance of each sample from the plane through the start point that is
            // perpendicular to this direction of travel. A sign change means the car
            // passed it somewhere within this segment.
            val before = px * ux + py * uy
            val after = qx * ux + qy * uy
            if (!(before <= 0.0 && after >= 0.0) && !(before >= 0.0 && after <= 0.0)) continue
            if (before == after) continue

            val fraction = (0.0 - before) / (after - before)
            val crossingX = px + dx * fraction
            val crossingY = py + dy * fraction
            val lateralOffset = hypot(crossingX, crossingY)

            val crossingTs = prev.timestampMs +
                ((curr.timestampMs - prev.timestampMs) * fraction).roundToLong()
            val headingDeg = GeoUtils.bearingDegrees(
                prev.latitude, prev.longitude, curr.latitude, curr.longitude
            )

            if (lateralOffset > DETECTION_HALF_WIDTH_M) {
                // Every lap passes this plane somewhere out on the far side of the
                // circuit. Those are geometry rather than near misses, so only
                // passes close enough to have been meant for the start/finish are
                // recorded - but they are recorded. Incident 14 was four passes of
                // the start straight discarded here in silence, leaving diagnostics
                // reporting no crossings and no rejections for a session in which
                // the driver completed three laps.
                if (lateralOffset <= REJECTION_REPORTING_RADIUS_M) {
                    rejections.add(
                        RejectedCrossing(
                            crossingTs, RejectionReason.TOO_FAR_TO_THE_SIDE,
                            "passed %.1fm to the side of the start point, limit is %.1fm"
                                .format(lateralOffset, DETECTION_HALF_WIDTH_M),
                            lateralOffsetM = lateralOffset
                        )
                    )
                }
                continue
            }

            // A lap boundary is crossed under power. Anything slower than a jog
            // across the start point is the driver walking to the kart, pushing it,
            // or standing in a queue beside the start straight with the phone in a
            // pocket - incident 15, where that pass was accepted, became the
            // session's heading reference, and cost the driver ten of twelve laps.
            val crossingSpeedMs = maxOf(prev.speedMs, curr.speedMs).toDouble()
            if (crossingSpeedMs < MIN_CROSSING_SPEED_MS) {
                rejections.add(
                    RejectedCrossing(
                        crossingTs, RejectionReason.TOO_SLOW,
                        "crossed at %.1fm/s, minimum is %.1fm/s"
                            .format(crossingSpeedMs, MIN_CROSSING_SPEED_MS),
                        lateralOffsetM = lateralOffset
                    )
                )
                continue
            }

            if (lastCrossingTs == null) {
                // A reference supplied by the catalogue exists before the session
                // does, so it can guard the opening crossing - which a reference
                // read from that same crossing obviously cannot. Incident 15 turned
                // on exactly this: the first thing the session saw was a pedestrian.
                val seeded = referenceHeadingDeg
                if (seeded != null) {
                    val difference = GeoUtils.angularDifferenceDegrees(headingDeg, seeded)
                    if (difference > MAX_HEADING_DIFFERENCE_DEG) {
                        rejections.add(
                            RejectedCrossing(
                                crossingTs, RejectionReason.HEADING_MISMATCH,
                                "travelling %.0f degrees, %.0f from the circuit's %.0f"
                                    .format(headingDeg, difference, seeded)
                            )
                        )
                        continue
                    }
                }
                crossings.add(Crossing(crossingTs, lateralOffset, headingDeg))
                lastCrossingTs = crossingTs
                if (referenceHeadingDeg == null) referenceHeadingDeg = headingDeg
                maxDistanceFromStart = 0.0
                continue
            }

            val timeSinceLastCrossing = crossingTs - lastCrossingTs

            if (timeSinceLastCrossing < minLapTimeMs) {
                rejections.add(
                    RejectedCrossing(
                        crossingTs, RejectionReason.TOO_SOON,
                        "${timeSinceLastCrossing}ms since the previous crossing, minimum is $minLapTimeMs"
                    )
                )
                continue
            }

            if (maxDistanceFromStart < MIN_DISTANCE_FROM_START_M) {
                rejections.add(
                    RejectedCrossing(
                        crossingTs, RejectionReason.TOO_CLOSE_TO_START,
                        "got %.1fm from the start point, minimum is %.1fm"
                            .format(maxDistanceFromStart, MIN_DISTANCE_FROM_START_M)
                    )
                )
                continue
            }

            val headingDifference = referenceHeadingDeg?.let {
                GeoUtils.angularDifferenceDegrees(headingDeg, it)
            } ?: 0.0

            if (headingDifference > MAX_HEADING_DIFFERENCE_DEG) {
                rejections.add(
                    RejectedCrossing(
                        crossingTs, RejectionReason.HEADING_MISMATCH,
                        "travelling %.0f degrees, %.0f from the first crossing's %.0f"
                            .format(headingDeg, headingDifference, referenceHeadingDeg ?: 0.0)
                    )
                )
                continue
            }

            crossings.add(Crossing(crossingTs, lateralOffset, headingDeg))
            lastCrossingTs = crossingTs
            maxDistanceFromStart = 0.0
        }

        return crossings
    }

    /**
     * Builds lap objects from crossing timestamps.
     * 
     * A lap is defined as the time between two consecutive crossings.
     * The last incomplete lap (if session ended before re-crossing) is discarded.
     */
    private fun buildLaps(crossings: List<Crossing>): List<DetectedLap> {
        if (crossings.size < 2) return emptyList()

        val laps = mutableListOf<DetectedLap>()
        
        for (i in 1 until crossings.size) {
            val startTs = crossings[i - 1].timestampMs
            val endTs = crossings[i].timestampMs
            val durationMs = endTs - startTs

            laps.add(
                DetectedLap(
                    lapNumber = i,
                    startTs = startTs,
                    endTs = endTs,
                    durationMs = durationMs
                )
            )
        }

        return laps
    }

    /**
     * Finds the best (fastest) lap from a list of detected laps.
     */
    fun findBestLap(laps: List<DetectedLap>): DetectedLap? {
        return laps.minByOrNull { it.durationMs }
    }
}
