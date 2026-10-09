package com.drivingcoach.coaching

import com.drivingcoach.data.db.entity.LapEntity
import org.json.JSONObject
import java.io.File
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
        val lapNumber: Int
    )

    /**
     * Generates coaching insights from lap data.
     *
     * @param laps List of laps from the session (minimum 2 required)
     * @param telemetryFilePath Optional path to JSONL telemetry file for top speed extraction
     * @param mergedLapCaveat Warning from `MergedLapCaveat` when detection suspects two
     *   laps were reported as one, or null. Passed through to the dream lap, which must
     *   stay silent when the lap boundaries themselves are in doubt.
     * @return The insights the data supports, which may be fewer than the maximum and
     *   may be empty. Nothing here pads the list to a fixed length: an insight that
     *   exists only to fill a slot teaches the driver to skim past all of them.
     *
     * Insights generated, where the data supports each:
     * 1. Best Lap Highlight (always positive)
     * 2. Top Speed (always positive) - requires telemetry file
     * 3. Consistency Score (positive or constructive based on score)
     * 4. Dream Lap - requires trustworthy sectors across at least 3 laps
     * 5. Sector Diagnostic - requires sectors
     * 6. Outlier Lap - requires a lap far enough off the pace to be worth naming
     * 7. Pace Trend - requires at least 6 laps
     */
    fun generateInsights(
        laps: List<LapEntity>,
        telemetryFilePath: String? = null,
        mergedLapCaveat: String? = null
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
        
        // 4. Dream Lap - omitted entirely when the sectors cannot carry it
        DreamLap.of(laps, mergedLapCaveat)?.let { insights.add(generateDreamLapInsight(it)) }
        
        // 5. Sector Diagnostic - omitted when there are no sectors, rather than
        //    replaced by an advertisement for a feature that does not exist
        if (sectorsAvailable) {
            insights.add(generateSectorDiagnosticInsight(laps, bestLap))
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
                        val timestamp = json.optLong("ts", 0L)
                        
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
        
        // Find which lap the top speed occurred in
        val lapNumber = laps.find { lap ->
            maxSpeedTs >= lap.startTs && maxSpeedTs <= lap.endTs
        }?.lapNumber ?: laps.firstOrNull()?.lapNumber ?: 1
        
        val speedKmh = (maxSpeedMs * 3.6).toInt()
        
        return TopSpeedData(speedKmh, lapNumber)
    }
    
    /**
     * Insight: Top Speed (always positive)
     */
    private fun generateTopSpeedInsight(topSpeed: TopSpeedData): OfflineInsight {
        return OfflineInsight(
            headline = "🚀 Top Speed: ${topSpeed.speedKmh} km/h",
            detail = "Hit on Lap ${topSpeed.lapNumber} — you were flying! Can you beat it next session?"
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
        
        return if (sectorsAvailable) {
            // Calculate average sector times
            val avgS1 = laps.map { it.sector1Ms }.average()
            val avgS2 = laps.map { it.sector2Ms }.average()
            val avgS3 = laps.map { it.sector3Ms }.average()
            
            // Find which sector had the biggest gain vs average
            val gainS1 = avgS1 - bestLap.sector1Ms
            val gainS2 = avgS2 - bestLap.sector2Ms
            val gainS3 = avgS3 - bestLap.sector3Ms
            
            val (bestSectorGain, bestSectorNum) = listOf(
                gainS1 to 1,
                gainS2 to 2,
                gainS3 to 3
            ).maxByOrNull { it.first } ?: (gainS1 to 1)
            
            OfflineInsight(
                headline = "Lap ${bestLap.lapNumber} Was Your Fastest",
                detail = "You nailed Sector $bestSectorNum — ${bestSectorGain.toLong()}ms quicker than average. " +
                         "Try to replicate that next session."
            )
        } else {
            // No sector data - show lap-level insight
            OfflineInsight(
                headline = "Lap ${bestLap.lapNumber} Was Your Fastest",
                detail = "You were ${formatTime(gainVsAverage.toDouble())} ahead of your average. Great rhythm on this lap!"
            )
        }
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
     * Insight: Dream Lap.
     *
     * Deliberately worded as a lap the driver has already driven in pieces, not as a
     * prediction. The sectors are real and were each recorded on a named lap; what is
     * theoretical is only putting them together.
     */
    private fun generateDreamLapInsight(dream: DreamLap.Result): OfflineInsight {
        if (dream.isCompleteLap) {
            return OfflineInsight(
                headline = "Lap ${dream.sector1LapNumber} Was Your Complete Lap",
                detail = "Your best sector 1, 2 and 3 all came from the same lap. " +
                         "There was nothing left on the table — now repeat it."
            )
        }

        return OfflineInsight(
            headline = "Dream Lap: ${formatLapTime(dream.totalMs)}",
            detail = "Your best sectors came from laps ${dream.sector1LapNumber}, " +
                     "${dream.sector2LapNumber} and ${dream.sector3LapNumber}. Put them together and " +
                     "you'd be ${formatTime(dream.gainMs.toDouble())} under your best lap — that's time " +
                     "you've already proven you can find."
        )
    }

    /**
     * Insight: Sector Diagnostic.
     *
     * Reports where time is being lost against the driver's own best, in the sector
     * where the loss is largest. Only ever shown when sectors exist.
     */
    private fun generateSectorDiagnosticInsight(
        laps: List<LapEntity>, 
        bestLap: LapEntity
    ): OfflineInsight {
        // Calculate average delta vs best lap for each sector
        val avgDeltaS1 = laps.map { it.sector1Ms - bestLap.sector1Ms }.average()
        val avgDeltaS2 = laps.map { it.sector2Ms - bestLap.sector2Ms }.average()
        val avgDeltaS3 = laps.map { it.sector3Ms - bestLap.sector3Ms }.average()
        
        val deltas = listOf(
            avgDeltaS1 to 1,
            avgDeltaS2 to 2,
            avgDeltaS3 to 3
        )
        
        val (worstDelta, worstSectorNum) = deltas.maxByOrNull { it.first } ?: (avgDeltaS1 to 1)
        
        return if (worstDelta < SECTOR_STRONG_THRESHOLD_MS) {
            OfflineInsight(
                headline = "All Sectors Strong",
                detail = "No single sector is holding you back. Solid all-round pace!"
            )
        } else {
            OfflineInsight(
                headline = "Focus on Sector $worstSectorNum",
                detail = "You lose ${worstDelta.toLong()}ms here on average. Small gains here will drop your lap time."
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
            String.format("%d:%06.3f", minutes, seconds)
        } else {
            String.format("%.3fs", seconds)
        }
    }

    /**
     * Formats time for display - uses ms for small values, s for larger ones.
     */
    private fun formatTime(ms: Double): String {
        return if (ms < 1000) {
            "${ms.toLong()}ms"
        } else {
            String.format("%.1fs", ms / 1000)
        }
    }
}
