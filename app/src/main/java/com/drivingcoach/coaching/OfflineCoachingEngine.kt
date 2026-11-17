package com.drivingcoach.coaching

import com.drivingcoach.data.db.entity.LapEntity
import org.json.JSONObject
import java.io.File
import java.util.Locale
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Generates offline coaching insights from lap data.
 * No network required - runs entirely on device.
 */
object OfflineCoachingEngine {

    // Consistency thresholds
    private const val EXCELLENT_CONSISTENCY_THRESHOLD = 95.0
    private const val SOLID_CONSISTENCY_THRESHOLD = 85.0
    
    // Sector delta threshold (ms) - below this, all sectors are considered strong
    private const val SECTOR_STRONG_THRESHOLD_MS = 100

    // Outlier lap: fewest laps before one can be called unusual, and how far off the
    // median it must be. Below 4 laps there is no "typical" lap to be unusual against.
    private const val MIN_LAPS_FOR_OUTLIER = 4
    private const val OUTLIER_THRESHOLD_FRACTION = 0.15

    // Pace trend: fewest laps before halves can be compared (3 per half), and the
    // smallest difference between halves worth reporting as a trend rather than scatter.
    private const val MIN_LAPS_FOR_TREND = 6
    private const val TREND_THRESHOLD_FRACTION = 0.02
    
    // Dream Lap card title and confidence labels (OC-13, OC-35)
    const val DREAM_LAP_TITLE = "Dream Lap"
    const val LABEL_RELIABLE = "Reliable"
    const val LABEL_ESTIMATE = "Estimate"

    // Speed limits for filtering GPS noise (m/s)
    private const val MAX_PLAUSIBLE_SPEED_MS = 97.2  // 350 km/h - covers fastest track cars
    private const val MIN_MEANINGFUL_SPEED_MS = 5.0  // ~18 km/h - ignore very slow speeds

    /**
     * A single coaching insight with headline and detail.
     */
    data class OfflineInsight(
        val headline: String,
        val detail: String
    )
    
    /**
     * Top speed data extracted from telemetry.
     */
    data class TopSpeedData(
        val speedKmh: Int,
        /** Null when the top speed was reached outside every timed lap. */
        val lapNumber: Int?
    )

    /**
     * Generates coaching insights from lap data.
     *
     * @param laps List of laps from the session (minimum 2 required)
     * @param telemetryFilePath Optional path to JSONL telemetry file for top speed extraction
     * @param mergedLapCaveat Warning from `MergedLapCaveat` when detection suspects two
     *   laps were reported as one, or null. Passed through to the dream lap, which can
     *   then never be labelled Reliable (OC-14, OC-35).
     * @param suspectCrossingTimesMs `MergedLapCaveat.suspectCrossingTimes`: when each
     *   swallowed start-line pass happened, so the dream lap can leave out the lap that
     *   absorbed it rather than distrust every lap.
     * @param mergedSignalKnown false when the merged-lap signal could not be recovered
     *   (a session recorded before detection diagnostics existed). A null caveat then
     *   means "unknown" rather than "no doubt", so the dream lap is built only from
     *   laps near the median lap time, where a merged lap or half-lap cannot hide (OC-33).
     *   If none of those carries sector times, every lap is used and the result is
     *   labelled an Estimate.
     * @return The insights the data supports, which may be fewer than the maximum and
     *   may be empty. Nothing here pads the list to a fixed length: an insight that
     *   exists only to fill a slot teaches the driver to skim past all of them.
     *
     * Insights generated, where the data supports each:
     * 1. Best Lap Highlight (always positive)
     * 2. Top Speed (always positive) - requires telemetry file
     * 3. Consistency Score (positive or constructive based on score)
     * 4. Dream Lap - always present; Reliable or Estimate, or why there is none
     * 5. Sector Diagnostic - requires sectors
     * 6. Outlier Lap - requires a lap far enough off the pace to be worth naming
     * 7. Pace Trend - requires at least 6 laps
     */
    fun generateInsights(
        laps: List<LapEntity>,
        telemetryFilePath: String? = null,
        mergedLapCaveat: String? = null,
        mergedSignalKnown: Boolean = true,
        suspectCrossingTimesMs: List<Long> = emptyList()
    ): List<OfflineInsight> {
        if (laps.size < 2) return emptyList()
        
        val bestLap = laps.minByOrNull { it.durationMs } ?: return emptyList()
        val sectorsAvailable = areSectorsAvailable(laps)
        
        val insights = mutableListOf<OfflineInsight>()
        
        // 1. Best Lap (with or without sector detail)
        insights.add(generateBestLapInsight(laps, bestLap, sectorsAvailable))
        
        // 2. Top Speed (if telemetry file available)
        telemetryFilePath?.let { path ->
            findTopSpeed(path, laps)?.let { topSpeed ->
                insights.add(generateTopSpeedInsight(topSpeed))
            }
        }
        
        // 3. Consistency
        insights.add(generateConsistencyInsight(laps))
        
        // 4. Dream Lap - always present: the labelled calculation, or why there is none
        //    (OC-13, OC-35)
        insights.add(
            generateDreamLapInsight(
                evaluateDreamLap(laps, mergedLapCaveat, mergedSignalKnown, suspectCrossingTimesMs)
            )
        )
        
        // 5. Sector Diagnostic - omitted when there are no sectors, rather than
        //    replaced by an advertisement for a feature that does not exist
        if (sectorsAvailable) {
            generateSectorDiagnosticInsight(laps)?.let { insights.add(it) }
        }
        
        // 6. Outlier Lap
        generateOutlierLapInsight(laps)?.let { insights.add(it) }
        
        // 7. Pace Trend
        generatePaceTrendInsight(laps)?.let { insights.add(it) }
        
        return insights
    }
    
    /**
     * Checks if sector data is available and meaningful.
     * Returns false if all sectors are 0 (local detection placeholder).
     */
    private fun areSectorsAvailable(laps: List<LapEntity>): Boolean {
        return laps.all { it.sector1Ms > 0 && it.sector2Ms > 0 && it.sector3Ms > 0 }
    }
    
    /**
     * Finds the top speed from telemetry file, filtering out GPS noise.
     * Returns null if file cannot be read or no valid speeds found.
     */
    fun findTopSpeed(filePath: String, laps: List<LapEntity>): TopSpeedData? {
        val file = File(filePath)
        if (!file.exists()) return null
        
        var maxSpeedMs = 0.0
        var maxSpeedTs = 0L
        
        try {
            file.useLines { lines ->
                lines.forEach { line ->
                    if (line.isBlank()) return@forEach
                    try {
                        val json = JSONObject(line)
                        val speedMs = json.optDouble("speedMs", 0.0)
                        // The recorder writes "timestampMs"; "ts" is kept for files
                        // written by older tooling.
                        val timestamp = json.optLong("timestampMs", json.optLong("ts", 0L))
                        
                        // Filter out GPS noise and implausible values
                        if (speedMs > MIN_MEANINGFUL_SPEED_MS && 
                            speedMs <= MAX_PLAUSIBLE_SPEED_MS && 
                            speedMs > maxSpeedMs) {
                            maxSpeedMs = speedMs
                            maxSpeedTs = timestamp
                        }
                    } catch (e: Exception) {
                        // Skip malformed lines
                    }
                }
            }
        } catch (e: Exception) {
            return null
        }
        
        if (maxSpeedMs <= MIN_MEANINGFUL_SPEED_MS) return null
        
        // Name a lap only when the top speed really fell inside one. Guessing "Lap 1"
        // credited every out-lap and in-lap top speed to the first lap.
        val lapNumber = laps.find { lap ->
            maxSpeedTs >= lap.startTs && maxSpeedTs <= lap.endTs
        }?.lapNumber
        
        val speedKmh = (maxSpeedMs * 3.6).toInt()
        
        return TopSpeedData(speedKmh, lapNumber)
    }
    
    /**
     * Insight: Top Speed (always positive)
     */
    private fun generateTopSpeedInsight(topSpeed: TopSpeedData): OfflineInsight {
        val detail = topSpeed.lapNumber
            ?.let { "Hit on Lap $it — you were flying! Can you beat it next session?" }
            ?: "Your fastest moment of the session — can you beat it next time?"
        return OfflineInsight(
            headline = "🚀 Top Speed: ${topSpeed.speedKmh} km/h",
            detail = detail
        )
    }

    /**
     * Insight 1: Best Lap Highlight (always positive)
     * Shows sector detail only when sectors are available.
     */
    private fun generateBestLapInsight(
        laps: List<LapEntity>, 
        bestLap: LapEntity,
        sectorsAvailable: Boolean
    ): OfflineInsight {
        val avgDuration = laps.map { it.durationMs }.average()
        val gainVsAverage = (avgDuration - bestLap.durationMs).toLong()
        val headline = "Lap ${bestLap.lapNumber} Was Your Fastest"
        val lapLevel = OfflineInsight(
            headline = headline,
            detail = "You were ${formatTime(gainVsAverage.toDouble())} ahead of your average. Great rhythm on this lap!"
        )
        if (!sectorsAvailable) return lapLevel

        // Sector gain = the session's average time in that sector minus the best lap's
        // time in it; the sector where the best lap gained most is the one named.
        val (bestSectorGain, bestSectorNum) = listOf<(LapEntity) -> Long>(
            { it.sector1Ms }, { it.sector2Ms }, { it.sector3Ms }
        ).mapIndexed { index, sectorOf ->
            laps.map(sectorOf).average() - sectorOf(bestLap) to index + 1
        }.maxBy { it.first }

        // Sectors that sum to their laps make this positive; anything else is bad data,
        // and "quicker by a negative amount" must never reach the driver.
        if (bestSectorGain <= 0.0) return lapLevel

        return OfflineInsight(
            headline = headline,
            detail = "You nailed Sector $bestSectorNum: ${formatTime(bestSectorGain)} quicker than your average. " +
                     "Try to replicate that next session."
        )
    }

    /**
     * Insight 2: Consistency Score
     * Positive for consistent sessions, constructive for inconsistent ones.
     */
    private fun generateConsistencyInsight(laps: List<LapEntity>): OfflineInsight {
        val times = laps.map { it.durationMs.toDouble() }
        val mean = times.average()
        val variance = times.map { (it - mean).pow(2) }.average()
        val stdDev = sqrt(variance)
        val consistencyPct = ((1 - stdDev / mean) * 100).coerceIn(0.0, 100.0)
        
        return when {
            consistencyPct >= EXCELLENT_CONSISTENCY_THRESHOLD -> {
                OfflineInsight(
                    headline = "Excellent Consistency!",
                    detail = "Laps within ${formatTime(stdDev)} of each other — that's race-ready precision!"
                )
            }
            consistencyPct >= SOLID_CONSISTENCY_THRESHOLD -> {
                OfflineInsight(
                    headline = "Solid Consistency",
                    detail = "Laps within ${formatTime(stdDev)} of each other. You're building good habits."
                )
            }
            else -> {
                OfflineInsight(
                    headline = "Work on Consistency",
                    detail = "Laps within ${formatTime(stdDev)} of each other. Focus on repeating the same lines before chasing speed."
                )
            }
        }
    }

    /**
     * The dream lap for this session, with the OC-33 median guard applied when the
     * merged-lap signal is unknown.
     *
     * The guard keeps a merged lap or half-lap out of the stitching by using only laps
     * near the median. If that leaves nothing with sector times, the guard has nothing
     * to protect, so every lap is used instead and the result is labelled an estimate
     * rather than withheld.
     */
    internal fun evaluateDreamLap(
        laps: List<LapEntity>,
        mergedLapCaveat: String?,
        mergedSignalKnown: Boolean,
        suspectCrossingTimesMs: List<Long>
    ): DreamLap.Evaluation {
        if (mergedSignalKnown) return DreamLap.evaluate(laps, mergedLapCaveat, suspectCrossingTimesMs)

        val guarded = DreamLap.evaluate(CleanLaps.of(laps), mergedLapCaveat, suspectCrossingTimesMs)
        if (guarded is DreamLap.Evaluation.Available) return guarded

        return when (val all = DreamLap.evaluate(laps, mergedLapCaveat, suspectCrossingTimesMs)) {
            is DreamLap.Evaluation.Available -> all.withReason(DreamLap.EstimateReason.OffPaceLapsUsed)
            is DreamLap.Evaluation.NotAvailable -> all
        }
    }

    /**
     * Insight: Dream Lap (OC-12 to OC-15, OC-35).
     *
     * Always titled "Dream Lap", so the driver can find it, and always labelled
     * "(Reliable)" or "(Estimate)" when there is a number. The detail shows the
     * arithmetic - each best sector and the lap it came from, their sum, and the best
     * lap actually driven - so the number can be checked rather than trusted, and an
     * estimate ends with the reason(s) in a few words. Only a session with no sector
     * times at all has no number, and the card says so.
     */
    private fun generateDreamLapInsight(evaluation: DreamLap.Evaluation): OfflineInsight {
        val available = when (evaluation) {
            is DreamLap.Evaluation.Available -> evaluation
            is DreamLap.Evaluation.NotAvailable -> return OfflineInsight(
                headline = DREAM_LAP_TITLE,
                detail = when (evaluation.reason) {
                    DreamLap.Withheld.NoSectorTimes -> "Not available: no lap had sector times."
                }
            )
        }
        val dream = available.result
        val estimate = available.confidence == DreamLap.Confidence.ESTIMATE

        val sum = "Best S1 ${formatLapTime(dream.sector1Ms)} (Lap ${dream.sector1LapNumber}) + " +
            "best S2 ${formatLapTime(dream.sector2Ms)} (Lap ${dream.sector2LapNumber}) + " +
            "best S3 ${formatLapTime(dream.sector3Ms)} (Lap ${dream.sector3LapNumber}) " +
            "= ${formatLapTime(dream.totalMs)}. " +
            "Your best lap: ${formatLapTime(dream.bestLapMs)} (Lap ${dream.bestLapNumber}). "

        val verdict = when {
            dream.isCompleteLap ->
                "All three best sectors came from that lap, so there was nothing left on the " +
                    "table. Now repeat it."
            estimate ->
                "Put your best sectors together and you'd be about " +
                    "${formatTime(dream.gainMs.toDouble())} faster."
            else ->
                "Put your best sectors together and you'd be ${formatTime(dream.gainMs.toDouble())} " +
                    "faster. That's time you've already proven you can find."
        }

        val why = if (estimate) {
            "\nWhy it's an estimate: " + available.reasons.joinToString("; ") { describe(it) } + "."
        } else {
            ""
        }

        val label = if (estimate) LABEL_ESTIMATE else LABEL_RELIABLE
        return OfflineInsight(
            headline = "$DREAM_LAP_TITLE: ${formatLapTime(dream.totalMs)} ($label)",
            detail = sum + verdict + why
        )
    }

    /** One short, plain reason per [DreamLap.EstimateReason] (OC-35). */
    private fun describe(reason: DreamLap.EstimateReason): String = when (reason) {
        is DreamLap.EstimateReason.SuspectLapsLeftOut -> {
            val n = reason.lapNumbers
            if (n.size == 1) {
                "lap ${n.single()} left out — it may be two laps timed as one"
            } else {
                "laps ${n.dropLast(1).joinToString(", ")} and ${n.last()} left out — " +
                    "each may be two laps timed as one"
            }
        }
        DreamLap.EstimateReason.MergedLapsSuspected ->
            "lap detection suspects some laps were timed as one"
        is DreamLap.EstimateReason.FewLaps ->
            if (reason.laps == 1) "based on 1 lap" else "based on ${reason.laps} laps"
        DreamLap.EstimateReason.UnusualSectorShares -> "sector times look unusual"
        DreamLap.EstimateReason.LargeGain ->
            "much faster than any real lap — sector timing may be off"
        DreamLap.EstimateReason.SectorsInconsistent ->
            "sector times don't add up to the lap times"
        DreamLap.EstimateReason.OffPaceLapsUsed ->
            "includes laps far from your typical lap time"
    }

    /**
     * Insight: Sector Diagnostic (OC-16).
     *
     * For each sector, the average time lost against **that sector's own best** across
     * the session, and the sector where that loss is largest. Measured on clean laps
     * only, so an out-lap or in-lap cannot decide which sector the driver is told to
     * work on.
     *
     * The reference used to be the best *lap's* sectors. That made this the Best Lap
     * insight's arithmetic restated - average minus best-lap sector, maximised - so it
     * always named the same sector with the same number, praising and criticising it in
     * consecutive cards.
     */
    private fun generateSectorDiagnosticInsight(laps: List<LapEntity>): OfflineInsight? {
        val clean = CleanLaps.of(laps)
        if (clean.size < 2) return null

        val sectorTimes = listOf<(LapEntity) -> Long>(
            { it.sector1Ms }, { it.sector2Ms }, { it.sector3Ms }
        )
        val losses = sectorTimes.mapIndexed { index, sectorOf ->
            val best = clean.minOf(sectorOf)
            clean.map { sectorOf(it) - best }.average() to index + 1
        }

        val (worstLoss, worstSectorNum) = losses.maxByOrNull { it.first } ?: return null

        return if (worstLoss < SECTOR_STRONG_THRESHOLD_MS) {
            OfflineInsight(
                headline = "All Sectors Strong",
                detail = "No single sector is holding you back. Solid all-round pace!"
            )
        } else {
            OfflineInsight(
                headline = "Focus on Sector $worstSectorNum",
                detail = "You lose ${formatTime(worstLoss)} here on average against your own best " +
                         "Sector $worstSectorNum. Small gains here will drop your lap time."
            )
        }
    }

    /**
     * Insight: Outlier Lap.
     *
     * Names the one lap furthest off the driver's median pace, so that a single
     * spin, a lift for traffic or an off can be set aside instead of quietly
     * dragging down the session's averages.
     *
     * Uses the **median** as the reference, not the mean: the outlier itself pulls the
     * mean towards it, which is how a bad lap hides from a test that uses the mean.
     *
     * Deliberately silent when nothing stands out. "No outliers" is not news, and
     * reporting it every session trains the driver to stop reading.
     */
    private fun generateOutlierLapInsight(laps: List<LapEntity>): OfflineInsight? {
        if (laps.size < MIN_LAPS_FOR_OUTLIER) return null

        val sorted = laps.map { it.durationMs }.sorted()
        val median = if (sorted.size % 2 == 0) {
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
        } else {
            sorted[sorted.size / 2].toDouble()
        }
        if (median <= 0.0) return null

        val slowest = laps.maxByOrNull { it.durationMs } ?: return null
        val excess = slowest.durationMs - median
        if (excess <= 0.0) return null
        if (excess / median < OUTLIER_THRESHOLD_FRACTION) return null

        return OfflineInsight(
            headline = "Lap ${slowest.lapNumber} Was The Odd One Out",
            detail = "It was ${formatTime(excess)} slower than your typical lap. If something " +
                     "happened out there — traffic, a missed apex, an off — set it aside and judge " +
                     "the session on the rest."
        )
    }

    /**
     * Insight: Pace Trend.
     *
     * Compares the first and second half of the session to say whether the driver
     * built pace, held it, or faded.
     *
     * Needs enough laps that each half is more than a couple of laps, otherwise one
     * bad lap decides the verdict. Silent when the two halves are close, because a
     * difference smaller than normal lap-to-lap scatter is not a trend.
     */
    private fun generatePaceTrendInsight(laps: List<LapEntity>): OfflineInsight? {
        if (laps.size < MIN_LAPS_FOR_TREND) return null

        val ordered = laps.sortedBy { it.lapNumber }
        val half = ordered.size / 2
        val firstHalf = ordered.take(half).map { it.durationMs.toDouble() }.average()
        val secondHalf = ordered.takeLast(half).map { it.durationMs.toDouble() }.average()
        if (firstHalf <= 0.0) return null

        val delta = firstHalf - secondHalf
        if (kotlin.math.abs(delta) / firstHalf < TREND_THRESHOLD_FRACTION) return null

        return if (delta > 0) {
            OfflineInsight(
                headline = "You Built Pace Through The Session",
                detail = "Your second half averaged ${formatTime(delta)} quicker than your first. " +
                         "You were still learning the circuit — there's likely more to come."
            )
        } else {
            OfflineInsight(
                headline = "Your Pace Faded Late On",
                detail = "Your second half averaged ${formatTime(-delta)} slower than your first. " +
                         "Tyres, fuel load, or concentration — worth knowing which before next time."
            )
        }
    }

    /**
     * Formats a lap time as m:ss.SSS, which is how lap times are read.
     */
    private fun formatLapTime(ms: Long): String {
        val minutes = ms / 60_000
        val seconds = (ms % 60_000) / 1000.0
        return if (minutes > 0) {
            String.format(Locale.US, "%d:%06.3f", minutes, seconds)
        } else {
            String.format(Locale.US, "%.3fs", seconds)
        }
    }

    /**
     * Formats a time gap in seconds with millisecond precision, e.g. "7.526s".
     * One format everywhere, so cards can be compared at a glance.
     */
    private fun formatTime(ms: Double): String =
        String.format(Locale.US, "%.3fs", ms / 1000)
}
