package com.drivingcoach.data.track

import com.drivingcoach.data.telemetry.TelemetryFileReader
import com.drivingcoach.data.telemetry.TelemetrySample
import com.drivingcoach.lap.LapReplayHarness
import com.drivingcoach.lap.LocalLapDetector
import com.drivingcoach.util.GeoUtils
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot

/**
 * The map is only worth drawing if the labels on it are in the right places. These
 * tests are mostly about that: that a surveyed centreline is preferred when one
 * exists, that the sector boundaries drawn on it are the ones that were *measured*,
 * and that the whole thing refuses rather than guesses.
 */
class CoachMapTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // ==================== Choosing a shape ====================

    @Test
    fun `without a centreline the driver's own laps are drawn`() {
        val (samples, laps) = replay("baltar2")
        val drawing = CoachMap.build(samples, laps, centreline = null)!!

        assertEquals(CoachMap.Provenance.DERIVED_FROM_LAPS, drawing.provenance)
        assertTrue("a derived shape must report its scatter", drawing.spreadM > 0.0)
    }

    @Test
    fun `with a centreline the surveyed circuit is drawn instead`() {
        val (samples, laps) = replay("baltar2")
        val drawing = CoachMap.build(samples, laps, centreline = catalogue("baltar"))!!

        assertEquals(CoachMap.Provenance.SURVEYED_CENTRELINE, drawing.provenance)
        assertEquals("a surveyed line carries no lap-to-lap scatter", 0.0, drawing.spreadM, 0.0)
    }

    @Test
    fun `a single lap says so`() {
        val (samples, laps) = replay("s_mamede")
        val drawing = CoachMap.build(samples, laps.take(1), centreline = null)!!

        assertEquals(CoachMap.Provenance.SINGLE_LAP, drawing.provenance)
        assertEquals(1, drawing.lapsUsed)
    }

    // ==================== Shown must equal measured ====================

    @Test
    fun `the drawn boundaries sit where the sector times were measured`() {
        // This is the property the whole projection exists for. The boundary is defined
        // by the driving - a third of the distance the car actually covered - so the
        // place drawn on the surveyed centreline has to be that same place on the
        // ground, not a third of the centreline's own length.
        val (samples, laps) = replay("baltar2")
        val outline = SessionOutline.build(samples, laps)!!
        val drawing = CoachMap.build(samples, laps, centreline = catalogue("baltar"))!!

        val station = TrackStation.from(catalogue("baltar"))!!

        // Measured forwards from the start/finish, because that is where a lap begins
        // and therefore where index 0 of the drawing sits. Baltar's centreline numbers
        // itself from a point 400 m before the line, so comparing raw s values here
        // would be comparing two different origins.
        val originS = station.project(outline.startAt.first, outline.startAt.second).s
        val measured = station.forwardDistance(
            originS,
            station.project(outline.firstBoundaryAt.first, outline.firstBoundaryAt.second).s
        )
        val drawn = drawing.firstBoundary.toDouble() / SessionOutline.POINTS * station.lengthM

        // One index step is the drawing's resolution, so that is the tightest this can
        // possibly be. Anything much larger means the projection is wrong.
        val stepM = station.lengthM / SessionOutline.POINTS
        assertEquals(
            "the drawn sector 1 boundary is ${abs(drawn - measured)} m from the measured one",
            measured, drawn, stepM
        )
    }

    @Test
    fun `index zero is the start finish line on a surveyed circuit`() {
        // A catalogue centreline does not necessarily begin at the start/finish - Baltar's
        // begins 400 m before it - so the drawing has to be rotated. Without this, sector 1
        // would start in the middle of the circuit and the start/finish marker would sit
        // wherever the surveyor happened to begin walking.
        val (samples, laps) = replay("baltar2")
        val outline = SessionOutline.build(samples, laps)!!
        val drawing = CoachMap.build(samples, laps, centreline = catalogue("baltar"))!!

        val station = TrackStation.from(catalogue("baltar"))!!
        val originS = station.project(outline.startAt.first, outline.startAt.second).s
        assertTrue(
            "this test is pointless unless Baltar's centreline really is offset; it is at $originS",
            originS > 100.0
        )

        // Point 0 of the drawing must be the line, not the centreline's own first point.
        val drawnStart = drawing.points[0]
        val derivedStart = CoachMap.build(samples, laps, centreline = null)!!.points[0]
        val gap = hypot(
            (drawnStart.x - derivedStart.x).toDouble(),
            (drawnStart.y - derivedStart.y).toDouble()
        )
        assertTrue(
            "the surveyed drawing starts $gap away from where the driven one does; " +
                "both are supposed to start at the start/finish line",
            gap < 0.1
        )
    }

    @Test
    fun `drawn boundaries are close to centreline thirds but not identical to them`() {
        val (samples, laps) = replay("baltar2")
        val drawing = CoachMap.build(samples, laps, centreline = catalogue("baltar"))!!

        // Near the thirds, because a lap is roughly the length of the circuit...
        assertTrue(
            "sector 1 boundary at ${drawing.firstBoundary} is nowhere near a third",
            abs(drawing.firstBoundary - SessionOutline.POINTS / 3) < SessionOutline.POINTS / 12
        )
        assertTrue(
            "sector 2 boundary at ${drawing.secondBoundary} is nowhere near two thirds",
            abs(drawing.secondBoundary - SessionOutline.POINTS * 2 / 3) < SessionOutline.POINTS / 12
        )
    }

    @Test
    fun `boundaries stay in order and inside the drawing`() {
        listOf("baltar2" to "baltar", "cabo_do_mundo" to "cabo_do_mundo").forEach { (fixture, track) ->
            val (samples, laps) = replay(fixture)
            val drawing = CoachMap.build(samples, laps, centreline = catalogue(track))!!

            assertTrue("$fixture: boundaries out of order", drawing.firstBoundary < drawing.secondBoundary)
            assertTrue("$fixture: first boundary outside the drawing", drawing.firstBoundary in 1 until drawing.points.size)
            assertTrue("$fixture: second boundary outside the drawing", drawing.secondBoundary in 1 until drawing.points.size)
        }
    }

    @Test
    fun `every catalogue circuit can be drawn`() {
        listOf("baltar", "cabo_do_mundo", "test_circuit_s_mamede").forEach { id ->
            val centreline = catalogue(id)
            assertNotNull("$id has no centreline", TrackStation.from(centreline))

            val (samples, laps) = replay(fixtureFor(id))
            val drawing = CoachMap.build(samples, laps, centreline = centreline)
            assertNotNull("$id could not be drawn", drawing)
            assertEquals(SessionOutline.POINTS, drawing!!.points.size)
        }
    }

    // ==================== Refusing ====================

    @Test
    fun `a centreline from a different circuit falls back to the driven shape`() {
        // A session tagged with the wrong track. Projecting Baltar's laps onto Cabo do
        // Mundo would put the sector labels in places that mean nothing, so the driven
        // shape - which is at least the right circuit - is drawn instead. The giveaway
        // is the lateral distance: the laps sit kilometres from a line they never drove.
        val (samples, laps) = replay("baltar2")
        val drawing = CoachMap.build(samples, laps, centreline = catalogue("cabo_do_mundo"))!!

        assertTrue(
            "a mismatched centreline must not be drawn as if it were the circuit",
            drawing.provenance != CoachMap.Provenance.SURVEYED_CENTRELINE
        )
    }

    @Test
    fun `the right centreline is recognised as agreeing`() {
        // The counterpart to the test above: the rejection must be discriminating, not
        // a blanket refusal that happens to pass. Measured offsets on the shipped
        // circuits are a few metres - the width of a racing line.
        listOf("baltar2" to "baltar", "cabo_do_mundo" to "cabo_do_mundo", "s_mamede" to "test_circuit_s_mamede")
            .forEach { (fixture, track) ->
                val (samples, laps) = replay(fixture)
                val drawing = CoachMap.build(samples, laps, centreline = catalogue(track))!!
                assertEquals(
                    "$fixture should have been drawn on its own surveyed centreline",
                    CoachMap.Provenance.SURVEYED_CENTRELINE, drawing.provenance
                )
            }
    }

    @Test
    fun `laps that disagree wildly are not drawn at all`() {
        // Five laps scattered over 100 m of each other is not a circuit anyone would
        // recognise; a shape that cannot be recognised invites reading corners into noise.
        val samples = scatteredLaps(noiseM = 300.0)
        val drawing = CoachMap.build(samples, windows(5), centreline = null)

        assertNull("a shape this noisy is worse than no shape", drawing)
    }

    @Test
    fun `a thin centreline is not used`() {
        val thin = Centreline(
            source = GeometrySource.MAP_COORDINATES,
            points = listOf(
                TrackPoint(41.0, -8.0), TrackPoint(41.001, -8.0), TrackPoint(41.001, -8.001)
            )
        )
        val (samples, laps) = replay("s_mamede")
        val drawing = CoachMap.build(samples, laps, centreline = thin)!!

        assertTrue(
            "three points are not a circuit",
            drawing.provenance != CoachMap.Provenance.SURVEYED_CENTRELINE
        )
    }

    @Test
    fun `no laps means no drawing`() {
        val (samples, _) = replay("s_mamede")
        assertNull(CoachMap.build(samples, emptyList(), centreline = catalogue("test_circuit_s_mamede")))
    }

    @Test
    fun `the drawing never leaves the unit box`() {
        val (samples, laps) = replay("cabo_do_mundo")
        val drawing = CoachMap.build(samples, laps, centreline = catalogue("cabo_do_mundo"))!!

        drawing.points.forEach { p ->
            assertTrue("x=${p.x} outside the box", p.x in 0f..1f)
            assertTrue("y=${p.y} outside the box", p.y in 0f..1f)
        }
    }

    // ==================== Helpers ====================

    private fun fixtureFor(trackId: String) = when (trackId) {
        "baltar" -> "baltar2"
        "cabo_do_mundo" -> "cabo_do_mundo"
        else -> "s_mamede"
    }

    private fun replay(name: String): Pair<List<TelemetrySample>, List<SessionOutline.LapWindow>> {
        val fixture = LapReplayHarness.load(name, tempFolder.newFolder())
        val result = LapReplayHarness.detect(fixture)
        val laps = (result as? LocalLapDetector.DetectionResult.Success)?.laps
            ?: error("$name should detect laps, got ${LapReplayHarness.describe(result)}")

        val samples = runBlocking { TelemetryFileReader.readAll(fixture.telemetryFile.absolutePath) }
        return samples to laps.map { SessionOutline.LapWindow(it.startTs, it.endTs) }
    }

    /** Reads a circuit's centreline straight out of the shipped catalogue. */
    private fun catalogue(trackId: String): Centreline {
        val json = JSONObject(File("src/main/assets/tracks/tracks.json").readText())
        val tracks = json.getJSONArray("tracks")
        for (i in 0 until tracks.length()) {
            val track = tracks.getJSONObject(i)
            if (track.getString("id") != trackId) continue

            val centreline = track.getJSONObject("centreline")
            val points = centreline.getJSONArray("points")
            return Centreline(
                source = GeometrySource.valueOf(centreline.getString("source")),
                points = (0 until points.length()).map { p ->
                    val point = points.getJSONArray(p)
                    TrackPoint(point.getDouble(0), point.getDouble(1))
                }
            )
        }
        error("No catalogue track $trackId")
    }

    private fun scatteredLaps(noiseM: Double): List<TelemetrySample> {
        val out = ArrayList<TelemetrySample>()
        val perLap = 120
        for (lap in 0 until 5) {
            for (i in 0 until perLap) {
                val angle = 2 * Math.PI * i / perLap
                val jitter = Math.sin((lap * 1000 + i) * 12.9898) * 43758.5453 % 1.0
                val x = 300.0 * Math.cos(angle) + jitter * noiseM
                val y = 300.0 * Math.sin(angle) + jitter * noiseM
                val (lat, lng) = GeoUtils.fromLocalMetres(x, y, 41.0, -8.0)
                out.add(sample(lap * 120_000L + i * 1000L, lat, lng))
            }
        }
        val (lat, lng) = GeoUtils.fromLocalMetres(300.0, 0.0, 41.0, -8.0)
        out.add(sample(5 * 120_000L, lat, lng))
        return out
    }

    private fun windows(laps: Int) =
        (0 until laps).map { SessionOutline.LapWindow(it * 120_000L, (it + 1) * 120_000L) }

    private fun sample(ts: Long, lat: Double, lng: Double) = TelemetrySample(
        timestampMs = ts, latitude = lat, longitude = lng,
        speedMs = 30f, headingDeg = 0f,
        accelX = 0f, accelY = 0f, accelZ = 0f,
        gyroX = 0f, gyroY = 0f, gyroZ = 0f,
        gpsAccuracyM = 5f
    )
}
