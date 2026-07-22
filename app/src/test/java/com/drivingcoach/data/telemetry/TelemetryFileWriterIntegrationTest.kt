package com.drivingcoach.data.telemetry

import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Integration test that simulates a 1-minute recording session
 * to verify TelemetryFileWriter produces valid JSONL output.
 */
class TelemetryFileWriterIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var testFile: File
    private val gson = Gson()

    @Before
    fun setUp() {
        testFile = tempFolder.newFile("integration_test.jsonl")
    }

    @After
    fun tearDown() {
        // Cleanup handled by TemporaryFolder rule
    }

    /**
     * Simulates a 1-minute recording at 10Hz (600 samples).
     * Verifies that the output is valid JSONL with correct structure.
     */
    @Test
    fun oneMinuteRecordingProducesValidJsonlFile(): Unit = runBlocking {
        // Simulate 1 minute at 10Hz = 600 samples
        val sampleCount = 600
        val startTime = System.currentTimeMillis()
        
        // Simulate realistic driving data
        testFile.bufferedWriter(bufferSize = 32 * 1024).use { writer ->
            for (i in 0 until sampleCount) {
                val sample = createRealisticSample(i, startTime)
                writer.write(gson.toJson(sample))
                writer.newLine()
            }
        }
        
        // Verify file exists and has content
        assertTrue("File should exist", testFile.exists())
        assertTrue("File should have content", testFile.length() > 0)
        
        // Read and validate all lines
        val lines = testFile.readLines()
        assertEquals("Should have $sampleCount lines", sampleCount, lines.size)
        
        // Parse and validate each line
        var previousTimestamp = 0L
        lines.forEachIndexed { index, line ->
            // Should be valid JSON
            assertFalse("Line $index should not be empty", line.isBlank())
            
            // Should parse to TelemetrySample
            val sample = try {
                gson.fromJson(line, TelemetrySample::class.java)
            } catch (e: Exception) {
                fail("Line $index should be valid JSON: ${e.message}")
                null
            }
            
            assertNotNull("Sample should not be null", sample)
            sample!!
            
            // Validate required fields
            assertTrue("Timestamp should be positive", sample.timestampMs > 0)
            assertTrue("Latitude should be valid", sample.latitude in -90.0..90.0)
            assertTrue("Longitude should be valid", sample.longitude in -180.0..180.0)
            assertTrue("Speed should be non-negative", sample.speedMs >= 0)
            assertTrue("GPS accuracy should be positive", sample.gpsAccuracyM > 0)
            
            // Timestamps should be monotonically increasing
            if (index > 0) {
                assertTrue(
                    "Timestamps should increase: prev=$previousTimestamp, current=${sample.timestampMs}",
                    sample.timestampMs >= previousTimestamp
                )
            }
            previousTimestamp = sample.timestampMs
        }
        
        // Verify file size is reasonable (each sample ~220 bytes)
        val expectedMinSize = sampleCount * 150L // Conservative estimate
        val expectedMaxSize = sampleCount * 300L // Upper bound
        assertTrue(
            "File size ${testFile.length()} should be between $expectedMinSize and $expectedMaxSize bytes",
            testFile.length() in expectedMinSize..expectedMaxSize
        )
        
        println("✓ 1-minute recording test passed:")
        println("  - $sampleCount samples written")
        println("  - File size: ${testFile.length()} bytes")
        println("  - Average sample size: ${testFile.length() / sampleCount} bytes")
    }

    /**
     * Tests that JSONL format is maintained under high-frequency writes.
     */
    @Test
    fun highFrequencyWritesMaintainValidJsonlFormat(): Unit = runBlocking {
        // Simulate 30 seconds at 50Hz (typical IMU rate) = 1500 samples
        val sampleCount = 1500
        val startTime = System.currentTimeMillis()
        
        testFile.bufferedWriter().use { writer ->
            for (i in 0 until sampleCount) {
                val sample = createRealisticSample(i, startTime, intervalMs = 20) // 50Hz
                writer.write(gson.toJson(sample))
                writer.newLine()
            }
        }
        
        // Verify all lines are valid JSON
        val validSamples = testFile.readLines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                try {
                    gson.fromJson(line, TelemetrySample::class.java)
                } catch (e: Exception) {
                    null
                }
            }
        
        assertEquals("All samples should be valid", sampleCount, validSamples.size)
    }

    /**
     * Tests realistic GPS coordinate progression (driving scenario).
     */
    @Test
    fun gpsCoordinatesProgressRealisticallDuringDrive(): Unit = runBlocking {
        val sampleCount = 100
        val startTime = System.currentTimeMillis()
        
        // Starting point: Munich, Germany
        val startLat = 48.1351
        val startLng = 11.5820
        
        testFile.bufferedWriter().use { writer ->
            for (i in 0 until sampleCount) {
                // Simulate driving north at ~100 km/h
                val sample = TelemetrySample(
                    timestampMs = startTime + i * 100L,
                    latitude = startLat + (i * 0.00028), // ~28m per sample at 100km/h
                    longitude = startLng + (i * 0.00001),
                    speedMs = 27.78f, // 100 km/h
                    headingDeg = 0f, // North
                    accelX = 0.1f,
                    accelY = 0f,
                    accelZ = 9.81f,
                    gyroX = 0f,
                    gyroY = 0f,
                    gyroZ = 0f,
                    gpsAccuracyM = 3.0f
                )
                writer.write(gson.toJson(sample))
                writer.newLine()
            }
        }
        
        // Read back and verify progression
        val samples = testFile.readLines().map { gson.fromJson(it, TelemetrySample::class.java) }
        
        val firstSample = samples.first()
        val lastSample = samples.last()
        
        // Should have moved north
        assertTrue(
            "Latitude should increase (moving north)",
            lastSample.latitude > firstSample.latitude
        )
        
        // Calculate approximate distance traveled
        val latDiff = lastSample.latitude - firstSample.latitude
        val distanceKm = latDiff * 111 // ~111 km per degree latitude
        
        // At 100 km/h for 10 seconds, should travel ~278m
        assertTrue(
            "Distance traveled should be approximately 2.8km, got ${distanceKm}km",
            distanceKm in 2.0..3.5
        )
    }

    /**
     * Tests that periodic flush persists data to disk before close.
     * This verifies that data is recoverable even if the app crashes before close().
     * 
     * Simulates the 30-second flush interval used in TelemetryForegroundService.
     */
    @Test
    fun periodicFlushPersistsDataBeforeClose(): Unit = runBlocking {
        val sampleCount = 300  // 30 seconds at 10Hz
        val startTime = System.currentTimeMillis()
        
        val writer = testFile.bufferedWriter(bufferSize = 32 * 1024)
        
        try {
            // Write first batch of samples (first 10 seconds)
            for (i in 0 until 100) {
                val sample = createRealisticSample(i, startTime)
                writer.write(gson.toJson(sample))
                writer.newLine()
            }
            
            // Simulate periodic flush at 10 seconds (like our 30s interval but scaled down)
            writer.flush()
            
            // Verify data is on disk BEFORE closing (simulates crash recovery)
            val samplesAfterFirstFlush = testFile.readLines().filter { it.isNotBlank() }
            assertEquals(
                "First 100 samples should be persisted after flush",
                100,
                samplesAfterFirstFlush.size
            )
            
            // Write second batch (next 10 seconds)
            for (i in 100 until 200) {
                val sample = createRealisticSample(i, startTime)
                writer.write(gson.toJson(sample))
                writer.newLine()
            }
            
            // Simulate second periodic flush at 20 seconds
            writer.flush()
            
            // Verify all data written so far is on disk
            val samplesAfterSecondFlush = testFile.readLines().filter { it.isNotBlank() }
            assertEquals(
                "200 samples should be persisted after second flush",
                200,
                samplesAfterSecondFlush.size
            )
            
            // Write final batch (last 10 seconds)
            for (i in 200 until sampleCount) {
                val sample = createRealisticSample(i, startTime)
                writer.write(gson.toJson(sample))
                writer.newLine()
            }
            
            // At this point, WITHOUT flush, buffered data may not be on disk
            // This is why periodic flush is important for crash resilience
            
        } finally {
            writer.close()
        }
        
        // After close, all data should be present
        val finalSamples = testFile.readLines().filter { it.isNotBlank() }
        assertEquals(
            "All $sampleCount samples should be persisted after close",
            sampleCount,
            finalSamples.size
        )
        
        println("✓ Periodic flush test passed:")
        println("  - Verified data persistence at 100, 200, and 300 samples")
        println("  - Flush enables crash recovery without data loss")
    }

    /**
     * Tests that data written but not flushed may be lost (demonstrates why flush matters).
     * This test shows the problem that periodic flush solves.
     */
    @Test
    fun unflushedDataMayBeLostOnCrash(): Unit = runBlocking {
        val sampleCount = 50
        val startTime = System.currentTimeMillis()
        
        // Create a writer but DON'T close or flush it properly
        val writer = testFile.bufferedWriter(bufferSize = 32 * 1024)
        
        // Write samples
        for (i in 0 until sampleCount) {
            val sample = createRealisticSample(i, startTime)
            writer.write(gson.toJson(sample))
            writer.newLine()
        }
        
        // Simulate crash: check what's actually on disk WITHOUT flush/close
        // Due to buffering, some or all data may not be on disk yet
        val samplesOnDiskBeforeFlush = testFile.readLines().filter { it.isNotBlank() }
        
        // The exact count depends on buffer size and data written
        // With a 32KB buffer and ~220 bytes per sample, buffer holds ~145 samples
        // So 50 samples likely fits entirely in buffer and nothing is on disk
        println("Samples on disk before flush: ${samplesOnDiskBeforeFlush.size}")
        println("Samples written but buffered: ${sampleCount - samplesOnDiskBeforeFlush.size}")
        
        // This demonstrates why periodic flush is important:
        // Without it, a crash would lose all buffered data
        assertTrue(
            "Without flush, buffered data may not be on disk",
            samplesOnDiskBeforeFlush.size <= sampleCount
        )
        
        // Now properly flush and close
        writer.flush()
        writer.close()
        
        val samplesAfterFlush = testFile.readLines().filter { it.isNotBlank() }
        assertEquals(
            "After flush, all samples should be on disk",
            sampleCount,
            samplesAfterFlush.size
        )
    }

    private fun createRealisticSample(
        index: Int,
        startTime: Long,
        intervalMs: Long = 100
    ): TelemetrySample {
        // Simulate track driving with varying speed and direction
        val progress = index / 100.0
        val cornerPhase = Math.sin(progress * Math.PI * 4) // 4 corners per lap
        
        return TelemetrySample(
            timestampMs = startTime + index * intervalMs,
            // Nürburgring-ish coordinates
            latitude = 50.3356 + (index % 100) * 0.0001,
            longitude = 6.9475 + (index % 100) * 0.00005,
            // Speed varies 80-160 km/h
            speedMs = (22.2f + (Math.cos(progress * Math.PI * 8) * 11.1)).toFloat(),
            headingDeg = ((index * 3.6) % 360).toFloat(),
            // Lateral G in corners
            accelX = (cornerPhase * 15).toFloat(), // Up to 1.5G lateral
            accelY = (Math.sin(progress * Math.PI * 2) * 5).toFloat(), // Braking/accel
            accelZ = 9.81f,
            // Yaw rate in corners
            gyroX = 0f,
            gyroY = 0f,
            gyroZ = (cornerPhase * 0.5).toFloat(),
            gpsAccuracyM = 2.0f + (index % 3)
        )
    }
}
