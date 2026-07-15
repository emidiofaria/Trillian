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
     * Generates 4 coaching insights from lap data.
     * 
     * @param laps List of laps from the session (minimum 2 required)
     * @param telemetryFilePath Optional path to JSONL telemetry file for top speed extraction
     * @return List of insights, or empty list if insufficient laps
     * 
     * Insights generated:
     * 1. Best Lap Highlight (always positive)
     * 2. Top Speed (always positive) - requires telemetry file
     * 3. Consistency Score (positive or constructive based on score)
     * 4. Sector Focus (upsell if sectors unavailable)
     */
    fun generateInsights(
        laps: List<LapEntity>,
        telemetryFilePath: String? = null
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
        
        // 4. Sector Focus (or upsell if sectors unavailable)
        insights.add(generateSectorFocusInsight(laps, bestLap, sectorsAvailable))
        
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
     * Insight 3: Sector Focus
     * Shows sector analysis when available, otherwise shows upsell message.
     */
    private fun generateSectorFocusInsight(
        laps: List<LapEntity>, 
        bestLap: LapEntity,
        sectorsAvailable: Boolean
    ): OfflineInsight {
        if (!sectorsAvailable) {
            return OfflineInsight(
                headline = "Sector Analysis Coming Soon",
                detail = "Upload when online for detailed sector breakdown and braking points."
            )
        }
        
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
