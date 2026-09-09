package com.drivingcoach.ui.session.tabs.analysis

/**
 * Aggregate statistics for a whole recorded session.
 *
 * [avgSpeedKmh] is the mean over every retained sample, stationary time included,
 * which is what the reference trackday report shows. It is deliberately not a
 * "moving average" - the UI labels it plainly so it cannot be misread.
 */
data class SessionStats(
    val distanceM: Double,
    val durationMs: Long,
    val maxSpeedKmh: Float,
    val avgSpeedKmh: Float,
    val bestLapMs: Long?,
    val sampleCount: Int,
    val sampleRateHz: Float
)

/** Direction of travel through a corner. */
enum class TurnDirection { LEFT, RIGHT }

/**
 * A corner detected from the rate of change of GPS bearing.
 *
 * [name] is assigned in order of passing along the reference lap (T1, T2, ...).
 * It does not necessarily match the circuit's official corner numbering.
 */
data class Corner(
    val name: String,
    val startMs: Long,
    val endMs: Long,
    val apexMs: Long,
    val apexSpeedKmh: Float,
    val turnDeg: Float,
    val direction: TurnDirection
)

/**
 * A braking zone detected from GPS deceleration (delta speed / delta time).
 *
 * The phone accelerometer is deliberately not used: its axes depend on how the
 * device happens to be mounted, so they show no reliable correlation with real
 * longitudinal acceleration. GPS speed is direct and mount-independent.
 */
data class BrakingZone(
    val startMs: Long,
    val endMs: Long,
    val entrySpeedKmh: Float,
    val exitSpeedKmh: Float,
    val speedDropKmh: Float,
    val peakDecelMs2: Float,
    val peakG: Float,
    val entersCorner: String?
) {
    val durationMs: Long get() = endMs - startMs
}

/**
 * One point of the drawable track outline.
 *
 * [x] and [y] are normalised to 0..1 with the aspect ratio of the real track
 * preserved, so a renderer only has to scale by the smaller view dimension.
 * y is already flipped for screen coordinates (0 = north edge).
 */
data class TrackPoint(
    val x: Float,
    val y: Float,
    val speedKmh: Float,
    val timestampMs: Long,
    val isBraking: Boolean
)

/** A labelled marker positioned on the track outline. */
data class TrackMarker(
    val label: String,
    val x: Float,
    val y: Float
)

/**
 * The complete drawable track: the outline plus everything overlaid on it.
 *
 * [minSpeedKmh] and [maxSpeedKmh] bound the colour gradient.
 */
data class TrackPath(
    val points: List<TrackPoint>,
    val cornerMarkers: List<TrackMarker>,
    val startFinish: TrackMarker?,
    val minSpeedKmh: Float,
    val maxSpeedKmh: Float
) {
    val isEmpty: Boolean get() = points.size < 2
}

/** One point of the session-wide speed trace. */
data class SpeedTimePoint(
    val elapsedS: Float,
    val speedKmh: Float
)

/** A lap offered in the reference-lap selector strip. */
data class LapOption(
    val lapId: Long,
    val lapNumber: Int,
    val durationMs: Long,
    val isBestLap: Boolean
)

/**
 * Everything the ANALYSIS tab renders for one session.
 *
 * [stats], [path] and [speedByTime] describe the whole session. [corners] and
 * [brakingZones] are relative to [referenceLapId], the lap the user selected
 * (best lap by default). When no lap could be detected the reference falls back
 * to the whole session and [referenceIsWholeSession] is true.
 */
data class SessionAnalysis(
    val stats: SessionStats,
    val path: TrackPath,
    val corners: List<Corner>,
    val brakingZones: List<BrakingZone>,
    val speedByTime: List<SpeedTimePoint>,
    val lapOptions: List<LapOption>,
    val referenceLapId: Long?,
    val referenceLapLabel: String?,
    val referenceIsWholeSession: Boolean
) {
    val hasData: Boolean get() = stats.sampleCount >= MIN_SAMPLES_FOR_ANALYSIS

    companion object {
        /** Below this many usable samples a session cannot be analysed at all. */
        const val MIN_SAMPLES_FOR_ANALYSIS = 2

        /** Result for a session with no usable telemetry. */
        val EMPTY = SessionAnalysis(
            stats = SessionStats(0.0, 0L, 0f, 0f, null, 0, 0f),
            path = TrackPath(emptyList(), emptyList(), null, 0f, 0f),
            corners = emptyList(),
            brakingZones = emptyList(),
            speedByTime = emptyList(),
            lapOptions = emptyList(),
            referenceLapId = null,
            referenceLapLabel = null,
            referenceIsWholeSession = true
        )
    }
}
