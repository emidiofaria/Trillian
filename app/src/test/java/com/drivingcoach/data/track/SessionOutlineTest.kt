package com.drivingcoach.data.track

import com.drivingcoach.data.telemetry.TelemetryFileReader
import com.drivingcoach.data.telemetry.TelemetrySample
import com.drivingcoach.lap.LapReplayHarness
import com.drivingcoach.lap.LocalLapDetector
import com.drivingcoach.util.GeoUtils
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The outline is what makes a coaching sentence point at a place, so the thing worth
 * testing is not that it produces 240 points - it is that the shape is more faithful
 * than the traces it was built from, and that it refuses to draw when it is not.
 */
class SessionOutlineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // ==================== The outline starts at the line ====================

    @Test
    fun `index 0 is the interpolated crossing, not the first fix after it`() {
        // 120 fixes to a 300 m-radius lap puts them 15.7 m apart. Starting each lap
        // window half a sample interval in places the line 7.85 m along the circle,
        // and the first recorded fix another 7.85 m beyond that - the exact distance
        // the start/finish marker used to be wrong by.
        val perLap = 120
        val halfStep = (LAP_MS / perLap) / 2
        val samples = circleLaps(laps = 4)
        val offsetWindows = (0 until 3).map {
            SessionOutline.LapWindow(lapStart(it) + halfStep, lapStart(it + 1) + halfStep)
        }

        val outline = SessionOutline.build(samples, offsetWindows)!!

        val lineAngle = Math.PI / perLap
        val (lineLat, lineLng) = GeoUtils.fromLocalMetres(
            300.0 * cos(lineAngle), 300.0 * sin(lineAngle), 41.0, -8.0
        )
        val toLine = GeoUtils.haversineDistance(
            outline.startAt.first, outline.startAt.second, lineLat, lineLng
        )
        assertTrue(
            "index 0 sits $toLine m from where the lap window actually began",
            toLine < 1.0
        )

        val firstFixAngle = 2 * Math.PI / perLap
        val (fixLat, fixLng) = GeoUtils.fromLocalMetres(
            300.0 * cos(firstFixAngle), 300.0 * sin(firstFixAngle), 41.0, -8.0
        )
        val toFirstFix = GeoUtils.haversineDistance(
            outline.startAt.first, outline.startAt.second, fixLat, fixLng
        )
        assertTrue(
            "index 0 is still sitting on the first recorded fix ($toFirstFix m away)",
            toFirstFix > 5.0
        )
    }

    @Test
    fun `a real session's outline starts nearer the line than its first fix does`() {
        // No magic threshold: the claim is simply that anchoring moved the start of the
        // drawn shape towards the start/finish line on every circuit, which is what the
        // marker on the Coach map is drawn from.
        listOf("s_mamede", "cabo_do_mundo", "baltar2").forEach { name ->
            val fixture = LapReplayHarness.load(name, tempFolder.newFolder())
            val result = LapReplayHarness.detect(fixture)
            val laps = (result as? LocalLapDetector.DetectionResult.Success)?.laps
                ?: error("$name should detect laps")
            val samples = runBlocking {
                TelemetryFileReader.readAll(fixture.telemetryFile.absolutePath)
            }
            val outline = SessionOutline.build(
                samples, laps.map { SessionOutline.LapWindow(it.startTs, it.endTs) }
            )!!

            val midLat = (fixture.startLine.lat1 + fixture.startLine.lat2) / 2.0
            val midLng = (fixture.startLine.lng1 + fixture.startLine.lng2) / 2.0

            val anchored = GeoUtils.haversineDistance(
                midLat, midLng, outline.startAt.first, outline.startAt.second
            )
            // What index 0 used to be: the first fix recorded inside the first window.
            val firstFix = samples.first { it.timestampMs >= laps.first().startTs }
            val unanchored = GeoUtils.haversineDistance(
                midLat, midLng, firstFix.latitude, firstFix.longitude
            )

            assertTrue(
                "$name: anchored start is $anchored m from the line, the first fix is " +
                    "$unanchored m - anchoring should not have made this worse",
                anchored <= unanchored + 1.0
            )
        }
    }

    // ==================== Shape and boundaries ====================

    @Test
    fun `a circle comes back as a circle`() {
        val outline = SessionOutline.build(circleLaps(laps = 5), windows(laps = 5))!!

        assertEquals(SessionOutline.POINTS, outline.points.size)

        // Every point of a circle is the same distance from its centre. In normalised
        // space the circle fills the box, so that distance is half the box.
        outline.points.forEach { p ->
            val r = hypot((p.x - 0.5f).toDouble(), (p.y - 0.5f).toDouble())
            assertEquals("a circle should stay round", 0.5, r, 0.02)
        }
    }

    @Test
    fun `the sector boundaries fall exactly on a point`() {
        val outline = SessionOutline.build(circleLaps(laps = 5), windows(laps = 5))!!

        // This is the property that makes the drawn sectors honest: because every lap
        // is resampled onto fractions of its own distance - the same definition
        // SectorSplitter uses - the boundary is an index, not an approximation of one.
        assertEquals(80, outline.firstBoundary)
        assertEquals(160, outline.secondBoundary)
        assertEquals(0, SessionOutline.POINTS % 3)
    }

    @Test
    fun `the three drawn sectors cover the whole lap and do not overlap`() {
        val outline = SessionOutline.build(circleLaps(laps = 5), windows(laps = 5))!!

        val s1 = 0 until outline.firstBoundary
        val s2 = outline.firstBoundary until outline.secondBoundary
        val s3 = outline.secondBoundary until outline.points.size

        assertEquals("sectors must tile the lap", outline.points.size, s1.count() + s2.count() + s3.count())
        assertEquals("sectors must be equal thirds", s1.count(), s2.count())
        assertEquals("sectors must be equal thirds", s2.count(), s3.count())
    }

    // ==================== The reason the median is there ====================

    @Test
    fun `the median outline is closer to the true shape than any single lap`() {
        // Five clean laps of a circle plus scatter, which is what a phone GPS gives.
        val noisy = circleLaps(laps = 5, noiseM = 6.0)
        val outline = SessionOutline.build(noisy, windows(laps = 5))!!
        val single = SessionOutline.build(noisy, windows(laps = 1))!!

        assertTrue(
            "the merged outline (${err(outline)}) should be rounder than one lap (${err(single)}), " +
                "otherwise there is no reason to average at all",
            err(outline) < err(single)
        )
    }

    @Test
    fun `one wild lap does not bend the outline`() {
        // Four clean laps and one that wanders 120 m off line for a third of the lap:
        // a spin, the gravel, or a fix lost behind a grandstand.
        val clean = circleLaps(laps = 5)
        val wild = clean.toMutableList()
        for (i in wild.indices) {
            val s = wild[i]
            if (s.timestampMs in lapStart(4)..(lapStart(4) + LAP_MS / 3)) {
                wild[i] = s.copy(latitude = s.latitude + 120.0 / 111_320.0)
            }
        }

        val withWild = SessionOutline.build(wild, windows(laps = 5))!!
        val withoutWild = SessionOutline.build(clean, windows(laps = 5))!!

        val moved = withWild.points.indices.maxOf { i ->
            hypot(
                (withWild.points[i].x - withoutWild.points[i].x).toDouble(),
                (withWild.points[i].y - withoutWild.points[i].y).toDouble()
            )
        }
        // A mean of five would be dragged a fifth of the way - roughly 24 m, which on
        // this circle is about 0.04 normalised. The median should barely notice.
        assertTrue("one bad lap moved the outline by $moved; the median should ignore it", moved < 0.01)
    }

    @Test
    fun `spread reports how much the laps disagree`() {
        val tight = SessionOutline.build(circleLaps(laps = 5, noiseM = 1.0), windows(laps = 5))!!
        val loose = SessionOutline.build(circleLaps(laps = 5, noiseM = 20.0), windows(laps = 5))!!

        assertTrue(
            "scattered laps (${loose.spreadM}) must report more spread than tight ones (${tight.spreadM}), " +
                "or the caller cannot tell when the shape is untrustworthy",
            loose.spreadM > tight.spreadM
        )
    }

    // ==================== Refusing to draw ====================

    @Test
    fun `fewer than three laps is marked as a single lap`() {
        val outline = SessionOutline.build(circleLaps(laps = 2), windows(laps = 2))!!

        assertTrue("two laps cannot support a median and must say so", outline.isSingleLap)
        assertEquals(0.0, outline.spreadM, 0.0)
    }

    @Test
    fun `three laps is not marked as a single lap`() {
        val outline = SessionOutline.build(circleLaps(laps = 3), windows(laps = 3))!!

        assertTrue(!outline.isSingleLap)
        assertEquals(3, outline.lapsUsed)
    }

    @Test
    fun `no samples and no laps produce no outline`() {
        assertNull(SessionOutline.build(emptyList(), windows(laps = 3)))
        assertNull(SessionOutline.build(circleLaps(laps = 3), emptyList()))
    }

    @Test
    fun `a lap too small to be a circuit produces no outline`() {
        // Twenty metres of GPS scatter is not a lap of anything. The figure matters:
        // it has to be below SectorSplitter's own 50 m floor, because the guarantee
        // being tested is that outline and sectors refuse at the same point.
        val samples = (0 until 60).map { i ->
            sample(i * 1000L, 41.0 + i * 0.000003, -8.0)
        }
        val outline = SessionOutline.build(samples, listOf(SessionOutline.LapWindow(0L, 59_000L)))

        assertNull("a 20 m scatter is not a circuit", outline)
    }

    @Test
    fun `a lap that earned sector times can always be drawn`() {
        // The two thresholds are deliberately the same constant. If they drift apart,
        // the Coach tab could state three sector times and then refuse to show where
        // they are - the exact gap the outline exists to close.
        assertEquals(
            "outline and sectors must refuse at the same distance",
            com.drivingcoach.lap.SectorSplitter.MIN_LAP_DISTANCE_M,
            SessionOutline.MIN_LAP_DISTANCE_M,
            0.0
        )
    }

    @Test
    fun `a lap with too few fixes is dropped rather than padded`() {
        val good = circleLaps(laps = 3)
        // A fourth window over a stretch holding only three fixes.
        val sparse = good + listOf(
            sample(lapStart(3), 41.0, -8.0),
            sample(lapStart(3) + 20_000, 41.001, -8.0),
            sample(lapStart(3) + 40_000, 41.002, -8.0)
        )
        val outline = SessionOutline.build(sparse, windows(laps = 4))!!

        assertEquals("the sparse lap must not be counted", 3, outline.lapsUsed)
    }

    @Test
    fun `an inverted window produces no outline`() {
        val outline = SessionOutline.build(
            circleLaps(laps = 3),
            listOf(SessionOutline.LapWindow(90_000L, 10_000L))
        )
        assertNull(outline)
    }

    @Test
    fun `the outline never leaves the unit box`() {
        val outline = SessionOutline.build(circleLaps(laps = 5, noiseM = 10.0), windows(laps = 5))!!

        outline.points.forEach { p ->
            assertTrue("x=${p.x} is outside the drawable box", p.x in 0f..1f)
            assertTrue("y=${p.y} is outside the drawable box", p.y in 0f..1f)
        }
    }

    // ==================== Against real recorded sessions ====================

    @Test
    fun `a real session produces a drawable outline`() {
        val (samples, laps) = replay("s_mamede")
        val outline = SessionOutline.build(samples, laps)

        assertNotNull("S. Mamede should be drawable", outline)
        assertEquals(SessionOutline.POINTS, outline!!.points.size)
        assertTrue("should have used every detected lap", outline.lapsUsed >= 3)
        assertTrue(
            "S. Mamede laps agreed to within 0.81 m when measured; " +
                "a spread of ${outline.spreadM} m means something changed",
            outline.spreadM < 10.0
        )
    }

    @Test
    fun `a real outline closes back on itself`() {
        val (samples, laps) = replay("cabo_do_mundo")
        val outline = SessionOutline.build(samples, laps)!!

        // The last resampled point sits one step short of the start/finish, so the
        // gap between it and the first point should be about one step - not a leap
        // across the map, which is what a mis-stitched loop looks like.
        val gap = hypot(
            (outline.points.last().x - outline.points.first().x).toDouble(),
            (outline.points.last().y - outline.points.first().y).toDouble()
        )
        assertTrue("the loop does not close: last point is $gap from the first", gap < 0.1)
    }

    @Test
    fun `a real session with many laps is not drawn from one of them`() {
        val (samples, laps) = replay("baltar2")
        val outline = SessionOutline.build(samples, laps)!!

        assertTrue(
            "baltar2 detects 12 laps; the outline used only ${outline.lapsUsed}",
            outline.lapsUsed >= 10
        )
        assertTrue(!outline.isSingleLap)
    }

    // ==================== Helpers ====================

    private fun err(outline: SessionOutline.Outline): Double =
        outline.points.sumOf { p ->
            val r = hypot((p.x - 0.5f).toDouble(), (p.y - 0.5f).toDouble())
            Math.abs(r - 0.5)
        } / outline.points.size

    private fun replay(name: String): Pair<List<TelemetrySample>, List<SessionOutline.LapWindow>> {
        val fixture = LapReplayHarness.load(name, tempFolder.newFolder())
        val result = LapReplayHarness.detect(fixture)
        val laps = (result as? LocalLapDetector.DetectionResult.Success)?.laps
            ?: error("$name should detect laps, got ${LapReplayHarness.describe(result)}")

        val samples = runBlocking { TelemetryFileReader.readAll(fixture.telemetryFile.absolutePath) }
        return samples to laps.map { SessionOutline.LapWindow(it.startTs, it.endTs) }
    }

    /** Deterministic scatter, so a failure is reproducible rather than occasional. */
    private fun noise(seed: Int): Double = sin(seed * 12.9898) * 43758.5453 % 1.0

    private fun circleLaps(laps: Int, radiusM: Double = 300.0, noiseM: Double = 0.0): List<TelemetrySample> {
        val out = ArrayList<TelemetrySample>()
        val perLap = 120
        for (lap in 0 until laps) {
            for (i in 0 until perLap) {
                val angle = 2 * Math.PI * i / perLap
                val jitterX = if (noiseM == 0.0) 0.0 else noise(lap * 1000 + i) * noiseM
                val jitterY = if (noiseM == 0.0) 0.0 else noise(lap * 1000 + i + 500) * noiseM
                val x = radiusM * cos(angle) + jitterX
                val y = radiusM * sin(angle) + jitterY
                val (lat, lng) = GeoUtils.fromLocalMetres(x, y, 41.0, -8.0)
                out.add(sample(lapStart(lap) + i * (LAP_MS / perLap), lat, lng))
            }
        }
        // Close the final loop so the last lap's window has an end fix.
        val (lat, lng) = GeoUtils.fromLocalMetres(radiusM, 0.0, 41.0, -8.0)
        out.add(sample(lapStart(laps), lat, lng))
        return out
    }

    private fun windows(laps: Int): List<SessionOutline.LapWindow> =
        (0 until laps).map { SessionOutline.LapWindow(lapStart(it), lapStart(it + 1)) }

    private fun lapStart(lap: Int): Long = lap * LAP_MS

    private fun sample(ts: Long, lat: Double, lng: Double) = TelemetrySample(
        timestampMs = ts,
        latitude = lat,
        longitude = lng,
        speedMs = 30f,
        headingDeg = 0f,
        accelX = 0f, accelY = 0f, accelZ = 0f,
        gyroX = 0f, gyroY = 0f, gyroZ = 0f,
        gpsAccuracyM = 5f
    )

    private companion object {
        const val LAP_MS = 120_000L
    }
}
