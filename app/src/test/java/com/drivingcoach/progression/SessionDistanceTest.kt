package com.drivingcoach.progression

import com.drivingcoach.progression.SessionDistance.Fix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SessionDistanceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    /** About 11.1 m per 0.0001° of latitude. */
    private fun fix(t: Long, latSteps: Int, speed: Double? = 20.0, accuracy: Double? = 5.0) =
        Fix(t * 1000, 41.0 + latSteps * 0.0001, -8.0, speed, accuracy)

    @Test
    fun sumsMovingSegments() {
        val fixes = (0..10).map { fix(it.toLong(), it) }
        assertEquals(111.2, SessionDistance.ofFixes(fixes), 0.5)
    }

    @Test
    fun ignoresStationaryJitter() {
        // Parked: the position wanders a few metres but the receiver reports ~0 km/h.
        val fixes = (0..60).map { fix(it.toLong(), it % 2, speed = 0.3) }
        assertEquals(0.0, SessionDistance.ofFixes(fixes), 0.0)
    }

    @Test
    fun ignoresStationaryJitterWithoutReportedSpeed() {
        val fixes = (0..60).map { Fix(it * 1000L, 41.0 + (it % 2) * 0.00001, -8.0, null, 5.0) }
        assertEquals(0.0, SessionDistance.ofFixes(fixes), 0.0)
    }

    @Test
    fun ignoresInaccurateFixes() {
        val fixes = listOf(fix(0, 0), fix(1, 50, accuracy = 80.0), fix(2, 2))
        assertEquals(22.2, SessionDistance.ofFixes(fixes), 0.5)
    }

    @Test
    fun implausibleJumpAddsNothingAndReanchors() {
        // A one-second jump of ~1.1 km (4000 km/h), then normal driving from there.
        val fixes = listOf(fix(0, 0), fix(1, 1), fix(2, 101), fix(3, 102), fix(4, 103))
        assertEquals(11.1 + 22.2, SessionDistance.ofFixes(fixes), 0.5)
    }

    @Test
    fun longGapAddsNothing() {
        val fixes = listOf(fix(0, 0), fix(1, 1), fix(120, 50), fix(121, 51))
        assertEquals(22.2, SessionDistance.ofFixes(fixes), 0.5)
    }

    @Test
    fun missingFileIsNull() {
        assertNull(SessionDistance.ofTelemetryFile(File(tempFolder.root, "nope.jsonl")))
    }

    @Test
    fun readsTelemetryJsonlSkippingHeader() {
        val file = tempFolder.newFile("t.jsonl")
        file.writeText(buildString {
            appendLine("""{"type":"header","version":1}""")
            for (i in 0..10) {
                appendLine("""{"timestampMs":${i * 1000},"latitude":${41.0 + i * 0.0001},"longitude":-8.0,"speedMs":20.0,"gpsAccuracyM":4.0}""")
            }
        })
        assertEquals(111.2, SessionDistance.ofTelemetryFile(file)!!, 0.5)
    }

    @Test
    fun realBaltarSessionMeasuresPlausibly() {
        val stream = requireNotNull(javaClass.getResourceAsStream("/lapfixtures/baltar2/telemetry.jsonl"))
        val file = tempFolder.newFile("baltar2.jsonl")
        stream.use { input -> file.outputStream().use { input.copyTo(it) } }
        val metres = SessionDistance.ofTelemetryFile(file)!!
        assertTrue("got $metres m", metres in 13_000.0..14_500.0)
    }

    @Test
    fun fallbackUsesTrackLength() {
        assertEquals(6_000.0, SessionDistance.fallback(5, 1_200), 0.0)
        assertEquals(0.0, SessionDistance.fallback(5, null), 0.0)
        assertEquals(0.0, SessionDistance.fallback(5, 0), 0.0)
    }
}
