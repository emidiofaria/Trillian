package com.drivingcoach.coaching

import com.drivingcoach.data.db.entity.LapEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit tests for OfflineCoachingEngine.
 * Tests follow TDD approach - written before implementation.
 */
class OfflineCoachingEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // ==================== Edge Cases ====================

    @Test
    fun `generateInsights returns empty list when no laps`() {
        val laps = emptyList<LapEntity>()
        val insights = OfflineCoachingEngine.generateInsights(laps)
        assertTrue("Expected empty list", insights.isEmpty())
    }

    @Test
    fun `generateInsights returns empty list when only 1 lap`() {
        val laps = listOf(createLap(1, 45000))
        val insights = OfflineCoachingEngine.generateInsights(laps)
        assertTrue("Expected empty list for single lap", insights.isEmpty())
    }

    @Test
    fun `generateInsights returns 3 insights without telemetry file`() {
        val laps = listOf(
            createLap(1, 45000),
            createLap(2, 46000),
            createLap(3, 44500)
        )
        val insights = OfflineCoachingEngine.generateInsights(laps)
        assertEquals("Expected 3 insights without telemetry", 3, insights.size)
    }
    
    @Test
    fun `generateInsights returns 4 insights with telemetry file`() {
        val file = createTelemetryFile(listOf(
            TelemetrySample(1000, 25.0),  // ~90 kmh
            TelemetrySample(2000, 50.0),  // ~180 kmh
            TelemetrySample(3000, 30.0)
        ))
        val laps = listOf(
            createLap(1, 45000, startTs = 0, endTs = 45000),
            createLap(2, 46000, startTs = 45000, endTs = 91000)
        )
        val insights = OfflineCoachingEngine.generateInsights(laps, file.absolutePath)
        assertEquals("Expected 4 insights with telemetry", 4, insights.size)
    }

    // ==================== Best Lap Insight ====================

    @Test
    fun `best lap insight identifies correct lap number`() {
        val laps = listOf(
            createLap(1, 48000),
            createLap(2, 45000),  // Best lap
            createLap(3, 47000)
        )
        val insights = OfflineCoachingEngine.generateInsights(laps)
        val bestLapInsight = insights[0]
        assertTrue("Headline should contain 'Lap 2'", bestLapInsight.headline.contains("Lap 2"))
    }

    @Test
    fun `best lap insight contains Fastest keyword`() {
        val laps = listOf(
            createLap(1, 48000),
            createLap(2, 45000),
            createLap(3, 47000)
        )
        val insights = OfflineCoachingEngine.generateInsights(laps)
        val bestLapInsight = insights[0]
        assertTrue("Headline should contain 'Fastest'", bestLapInsight.headline.contains("Fastest"))
    }

    @Test
    fun `best lap insight identifies strongest sector`() {
        val laps = listOf(
            createLap(1, 48000, s1 = 15000, s2 = 18000, s3 = 15000),
            createLap(2, 45000, s1 = 14800, s2 = 15500, s3 = 14700),  // S2 biggest gain (2500ms)
            createLap(3, 47000, s1 = 15200, s2 = 17500, s3 = 14300)
        )
        val insights = OfflineCoachingEngine.generateInsights(laps)
        assertTrue("Detail should mention Sector 2", insights[0].detail.contains("Sector 2"))
    }

    @Test
    fun `best lap insight shows time gained in detail`() {
        val laps = listOf(
            createLap(1, 48000, s1 = 16000, s2 = 16000, s3 = 16000),
            createLap(2, 45000, s1 = 15000, s2 = 15000, s3 = 15000),
            createLap(3, 48000, s1 = 16000, s2 = 16000, s3 = 16000)
        )
        val insights = OfflineCoachingEngine.generateInsights(laps)
        assertTrue("Detail should contain time in ms", insights[0].detail.contains("ms"))
    }

    // ==================== Consistency Insight ====================

    @Test
    fun `consistency insight shows Excellent for high consistency`() {
        // Laps within 150ms of each other = very high consistency
        val laps = listOf(
            createLap(1, 45000),
            createLap(2, 45100),
            createLap(3, 45050),
            createLap(4, 45080)
        )
        val insights = OfflineCoachingEngine.generateInsights(laps)
        val consistencyInsight = insights[1]
        assertTrue("Headline should contain 'Excellent'", consistencyInsight.headline.contains("Excellent"))
    }

    @Test
    fun `consistency insight shows Solid for moderate consistency`() {
        // Laps vary by ~4-5 seconds = moderate consistency (85-95%)
        // StdDev ~4500ms for mean ~45000ms = ~90% consistency
        val laps = listOf(
            createLap(1, 42000),
            createLap(2, 50000),
            createLap(3, 44000),
            createLap(4, 48000)
        )
        val insights = OfflineCoachingEngine.generateInsights(laps)
        val consistencyInsight = insights[1]
        assertTrue("Headline should contain 'Solid', got: ${consistencyInsight.headline}", 
            consistencyInsight.headline.contains("Solid"))
    }

    @Test
    fun `consistency insight shows constructive for poor consistency`() {
        // Laps vary by 2+ seconds = poor consistency
        val laps = listOf(
            createLap(1, 45000),
            createLap(2, 48000),
            createLap(3, 44000),
            createLap(4, 49000)
        )
        val insights = OfflineCoachingEngine.generateInsights(laps)
        val consistencyInsight = insights[1]
        assertTrue("Headline should mention consistency work", 
            consistencyInsight.headline.contains("Consistency") || consistencyInsight.headline.contains("Work"))
    }

    @Test
    fun `consistency insight includes variation in detail`() {
        val laps = listOf(
            createLap(1, 45000),
            createLap(2, 46000),
            createLap(3, 44500)
        )
        val insights = OfflineCoachingEngine.generateInsights(laps)
        val consistencyInsight = insights[1]
        assertTrue("Detail should contain a number", consistencyInsight.detail.any { it.isDigit() })
    }

    // ==================== Sector Focus Insight ====================

    @Test
    fun `sector focus identifies weakest sector`() {
        val laps = listOf(
            createLap(1, 48000, s1 = 15000, s2 = 19000, s3 = 14000),  // S2 worst
            createLap(2, 45000, s1 = 14500, s2 = 16500, s3 = 14000),
            createLap(3, 47000, s1 = 14800, s2 = 18200, s3 = 14000)
        )
        val insights = OfflineCoachingEngine.generateInsights(laps)
        val sectorInsight = insights[2]
        assertTrue("Headline should contain 'Sector 2'", sectorInsight.headline.contains("Sector 2"))
    }

    @Test
    fun `sector focus shows All Sectors Strong when balanced`() {
        val laps = listOf(
            createLap(1, 45000, s1 = 15000, s2 = 15000, s3 = 15000),
            createLap(2, 45090, s1 = 15030, s2 = 15030, s3 = 15030),
            createLap(3, 45060, s1 = 15020, s2 = 15020, s3 = 15020)
        )
        val insights = OfflineCoachingEngine.generateInsights(laps)
        val sectorInsight = insights[2]
        assertTrue("Headline should mention all sectors", sectorInsight.headline.contains("All Sectors"))
    }

    @Test
    fun `sector focus shows time lost in detail`() {
        val laps = listOf(
            createLap(1, 48000, s1 = 15000, s2 = 18000, s3 = 15000),
            createLap(2, 45000, s1 = 14500, s2 = 16000, s3 = 14500),
            createLap(3, 47000, s1 = 15200, s2 = 17500, s3 = 14300)
        )
        val insights = OfflineCoachingEngine.generateInsights(laps)
        val sectorInsight = insights[2]
        assertTrue("Detail should contain time in ms", sectorInsight.detail.contains("ms"))
    }

    @Test
    fun `sector focus correctly identifies S1 as weakest`() {
        val laps = listOf(
            createLap(1, 48000, s1 = 18000, s2 = 15000, s3 = 15000),  // S1 worst
            createLap(2, 45000, s1 = 15500, s2 = 14750, s3 = 14750),
            createLap(3, 47000, s1 = 17500, s2 = 14750, s3 = 14750)
        )
        val insights = OfflineCoachingEngine.generateInsights(laps)
        val sectorInsight = insights[2]
        assertTrue("Headline should contain 'Sector 1'", sectorInsight.headline.contains("Sector 1"))
    }

    @Test
    fun `sector focus correctly identifies S3 as weakest`() {
        val laps = listOf(
            createLap(1, 48000, s1 = 15000, s2 = 15000, s3 = 18000),  // S3 worst
            createLap(2, 45000, s1 = 14750, s2 = 14750, s3 = 15500),
            createLap(3, 47000, s1 = 14750, s2 = 14750, s3 = 17500)
        )
        val insights = OfflineCoachingEngine.generateInsights(laps)
        val sectorInsight = insights[2]
        assertTrue("Headline should contain 'Sector 3'", sectorInsight.headline.contains("Sector 3"))
    }

    // ==================== Tone Validation (70/30 Rule) ====================

    @Test
    fun `at least 2 of 3 insights have positive tone - good session`() {
        val laps = listOf(
            createLap(1, 45000),
            createLap(2, 45100),
            createLap(3, 45050)
        )
        val insights = OfflineCoachingEngine.generateInsights(laps)
        
        val positiveCount = countPositiveInsights(insights)
        assertTrue("Expected at least 2 positive insights, got $positiveCount", positiveCount >= 2)
    }

    @Test
    fun `at least 1 insight is positive even in poor session`() {
        // Even with inconsistent laps, best lap insight should be positive
        val laps = listOf(
            createLap(1, 45000, s1 = 15000, s2 = 15000, s3 = 15000),
            createLap(2, 50000, s1 = 17000, s2 = 17000, s3 = 16000),
            createLap(3, 48000, s1 = 16000, s2 = 16000, s3 = 16000)
        )
        val insights = OfflineCoachingEngine.generateInsights(laps)
        
        val positiveCount = countPositiveInsights(insights)
        assertTrue("Expected at least 1 positive insight (best lap)", positiveCount >= 1)
    }

    // ==================== Insight Order ====================

    @Test
    fun `insights are returned in correct order`() {
        val laps = listOf(
            createLap(1, 45000),
            createLap(2, 46000),
            createLap(3, 44500)
        )
        val insights = OfflineCoachingEngine.generateInsights(laps)
        
        assertTrue("First insight should be about fastest lap", insights[0].headline.contains("Fastest"))
        assertTrue("Second insight should be about consistency", 
            insights[1].headline.contains("Consistency") || 
            insights[1].headline.contains("Excellent") || 
            insights[1].headline.contains("Solid"))
        assertTrue("Third insight should be about sectors",
            insights[2].headline.contains("Sector") || 
            insights[2].headline.contains("All Sectors") ||
            insights[2].headline.contains("Coming Soon"))
    }
    
    // ==================== Zero Sectors Tests (Bug Fix) ====================
    
    @Test
    fun `zero sectors - best lap shows lap-level insight without sector mention`() {
        val laps = listOf(
            createLapZeroSectors(1, 48000),
            createLapZeroSectors(2, 45000),  // Best lap
            createLapZeroSectors(3, 47000)
        )
        val insights = OfflineCoachingEngine.generateInsights(laps)
        val bestLapInsight = insights[0]
        
        assertTrue("Headline should mention Lap 2", bestLapInsight.headline.contains("Lap 2"))
        assertTrue("Detail should NOT mention Sector", !bestLapInsight.detail.contains("Sector"))
        assertTrue("Detail should mention ahead of average", bestLapInsight.detail.contains("ahead"))
    }
    
    @Test
    fun `zero sectors - sector focus shows Coming Soon upsell`() {
        val laps = listOf(
            createLapZeroSectors(1, 45000),
            createLapZeroSectors(2, 46000),
            createLapZeroSectors(3, 44500)
        )
        val insights = OfflineCoachingEngine.generateInsights(laps)
        val sectorInsight = insights.last()
        
        assertTrue("Headline should say Coming Soon: ${sectorInsight.headline}", 
            sectorInsight.headline.contains("Coming Soon"))
        assertTrue("Detail should mention upload", sectorInsight.detail.contains("Upload") || 
            sectorInsight.detail.contains("online"))
    }
    
    @Test
    fun `zero sectors - no 0ms quicker text appears`() {
        val laps = listOf(
            createLapZeroSectors(1, 45000),
            createLapZeroSectors(2, 46000),
            createLapZeroSectors(3, 44500)
        )
        val insights = OfflineCoachingEngine.generateInsights(laps)
        
        insights.forEach { insight ->
            assertTrue("Should not contain '0ms quicker': ${insight.detail}", 
                !insight.detail.contains("0ms quicker"))
            assertTrue("Should not contain 'within 0ms': ${insight.detail}", 
                !insight.detail.contains("within 0ms"))
        }
    }
    
    // ==================== Consistency Wording Tests (Bug Fix) ====================
    
    @Test
    fun `consistency insight uses within not vary by`() {
        val laps = listOf(
            createLap(1, 45000),
            createLap(2, 45100),
            createLap(3, 45050)
        )
        val insights = OfflineCoachingEngine.generateInsights(laps)
        val consistencyInsight = insights[1]
        
        assertTrue("Detail should contain 'within': ${consistencyInsight.detail}", 
            consistencyInsight.detail.contains("within"))
        assertTrue("Detail should NOT contain 'vary by': ${consistencyInsight.detail}", 
            !consistencyInsight.detail.contains("vary by"))
    }
    
    // ==================== Top Speed Tests (New Feature) ====================
    
    @Test
    fun `findTopSpeed can read temp file`() {
        val file = createTelemetryFile(listOf(
            TelemetrySample(1000, 50.0)   // 180 kmh
        ))
        assertTrue("File should exist", file.exists())
        val content = file.readText()
        assertTrue("File should have content", content.contains("speedMs"))
        
        val laps = listOf(
            createLap(1, 45000, startTs = 0, endTs = 45000)
        )
        val result = OfflineCoachingEngine.findTopSpeed(file.absolutePath, laps)
        assertNotNull("findTopSpeed should return data: file=${file.absolutePath}, content=$content", result)
    }
    
    @Test
    fun `top speed insight shows correct speed in kmh`() {
        val file = createTelemetryFile(listOf(
            TelemetrySample(1000, 25.0),   // 90 kmh
            TelemetrySample(2000, 69.5),   // 250.2 kmh - top speed (rounds to 250)
            TelemetrySample(3000, 30.0)    // 108 kmh
        ))
        val laps = listOf(
            createLap(1, 45000, startTs = 0, endTs = 45000),
            createLap(2, 46000, startTs = 45000, endTs = 91000)
        )
        val insights = OfflineCoachingEngine.generateInsights(laps, file.absolutePath)
        
        val topSpeedInsight = insights.find { it.headline.contains("Top Speed") }
        assertNotNull("Should have top speed insight", topSpeedInsight)
        assertTrue("Should show 250 kmh: ${topSpeedInsight!!.headline}", 
            topSpeedInsight.headline.contains("250"))
    }
    
    @Test
    fun `top speed insight identifies correct lap`() {
        val file = createTelemetryFile(listOf(
            TelemetrySample(1000, 25.0),    // Lap 1
            TelemetrySample(50000, 69.4),   // Lap 2 - top speed here
            TelemetrySample(60000, 30.0)
        ))
        val laps = listOf(
            createLap(1, 45000, startTs = 0, endTs = 45000),
            createLap(2, 46000, startTs = 45000, endTs = 91000)
        )
        val insights = OfflineCoachingEngine.generateInsights(laps, file.absolutePath)
        
        val topSpeedInsight = insights.find { it.headline.contains("Top Speed") }
        assertNotNull("Should have top speed insight", topSpeedInsight)
        assertTrue("Should mention Lap 2: ${topSpeedInsight!!.detail}", 
            topSpeedInsight.detail.contains("Lap 2"))
    }
    
    @Test
    fun `top speed filters out GPS noise above 350 kmh`() {
        val file = createTelemetryFile(listOf(
            TelemetrySample(1000, 25.0),    // 90 kmh - valid
            TelemetrySample(2000, 150.0),   // 540 kmh - GPS noise, should be ignored
            TelemetrySample(3000, 55.5)     // 200 kmh - should be the top speed
        ))
        val laps = listOf(
            createLap(1, 45000, startTs = 0, endTs = 45000),
            createLap(2, 46000, startTs = 45000, endTs = 91000)
        )
        val insights = OfflineCoachingEngine.generateInsights(laps, file.absolutePath)
        
        val topSpeedInsight = insights.find { it.headline.contains("Top Speed") }
        assertNotNull("Should have top speed insight", topSpeedInsight)
        assertTrue("Should show ~200 kmh, not 540: ${topSpeedInsight!!.headline}", 
            topSpeedInsight.headline.contains("200") || topSpeedInsight.headline.contains("199"))
    }
    
    @Test
    fun `findTopSpeed returns null for missing file`() {
        val laps = listOf(createLap(1, 45000), createLap(2, 46000))
        val result = OfflineCoachingEngine.findTopSpeed("/nonexistent/file.jsonl", laps)
        assertNull("Should return null for missing file", result)
    }
    
    @Test
    fun `top speed has rocket emoji in headline`() {
        val file = createTelemetryFile(listOf(
            TelemetrySample(1000, 50.0)  // 180 kmh
        ))
        val laps = listOf(
            createLap(1, 45000, startTs = 0, endTs = 45000),
            createLap(2, 46000, startTs = 45000, endTs = 91000)
        )
        val insights = OfflineCoachingEngine.generateInsights(laps, file.absolutePath)
        
        val topSpeedInsight = insights.find { it.headline.contains("Top Speed") }
        assertNotNull("Should have top speed insight", topSpeedInsight)
        assertTrue("Headline should have rocket emoji: ${topSpeedInsight!!.headline}", 
            topSpeedInsight.headline.contains("🚀"))
    }

    // ==================== Helper Functions ====================

    private fun countPositiveInsights(insights: List<OfflineCoachingEngine.OfflineInsight>): Int {
        val positiveKeywords = listOf(
            "Excellent", "Solid", "Strong", "Fastest", "Great", "Good", "Well", "Top Speed"
        )
        return insights.count { insight ->
            positiveKeywords.any { keyword -> 
                insight.headline.contains(keyword, ignoreCase = true) 
            }
        }
    }

    private fun createLap(
        number: Int,
        durationMs: Long,
        s1: Long = durationMs / 3,
        s2: Long = durationMs / 3,
        s3: Long = durationMs - s1 - s2,
        startTs: Long = 0,
        endTs: Long = durationMs
    ) = LapEntity(
        id = number.toLong(),
        sessionId = 1,
        lapNumber = number,
        startTs = startTs,
        endTs = endTs,
        durationMs = durationMs,
        sector1Ms = s1,
        sector2Ms = s2,
        sector3Ms = s3,
        isBestLap = false,
        isLocalOnly = true
    )
    
    private fun createLapZeroSectors(
        number: Int,
        durationMs: Long,
        startTs: Long = 0,
        endTs: Long = durationMs
    ) = LapEntity(
        id = number.toLong(),
        sessionId = 1,
        lapNumber = number,
        startTs = startTs,
        endTs = endTs,
        durationMs = durationMs,
        sector1Ms = 0L,
        sector2Ms = 0L,
        sector3Ms = 0L,
        isBestLap = false,
        isLocalOnly = true
    )
    
    data class TelemetrySample(val ts: Long, val speedMs: Double)
    
    private var testFileCounter = 0
    
    private fun createTelemetryFile(samples: List<TelemetrySample>): File {
        testFileCounter++
        val file = tempFolder.newFile("telemetry_test_$testFileCounter.jsonl")
        file.writeText(samples.joinToString("\n") { sample ->
            """{"ts":${sample.ts},"speedMs":${sample.speedMs},"lat":47.22,"lng":14.76}"""
        })
        return file
    }
}
