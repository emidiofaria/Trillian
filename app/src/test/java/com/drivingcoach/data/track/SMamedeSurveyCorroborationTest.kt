package com.drivingcoach.data.track

import com.drivingcoach.lap.LapReplayHarness
import com.drivingcoach.lap.LocalLapDetector
import com.drivingcoach.util.GeoUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot

/**
 * Crosses the **surveyed** Test Circuit S.Mamede entry against a **recorded** kart session.
 *
 * | Number | Origin | Corroborated by |
 * |--------|--------|-----------------|
 * | `travelHeadingDeg` 284.7 | two points supplied by the operator | the karts' own crossing headings |
 * | `lengthM` 802 | GPS distance of this session | the walked ring (see [SMamedeCatalogueTest]) - **not** this session |
 * | `fastestLapMs` 50000 | operator's figure for a quick pilot | reachable at this session's top speed |
 *
 * ## Why this session is evidence
 *
 * Recorded on 2026-09-14, three weeks before the start line and ring were walked on
 * 2026-10-05, so neither could have been fitted to it. Only three flying laps - an average
 * driver, 76-80 s - so the thresholds below are scaled to three, not to the eight or ten the
 * other circuits have.
 *
 * Every test measures against the **catalogued** line, never the one `session.json` carries,
 * and the fixture is committed exactly as the phone exported it.
 */
class SMamedeSurveyCorroborationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val catalogueFile = File("src/main/assets/tracks/tracks.json")

    private fun track(): Track =
        BundledTrackCatalog.parse(catalogueFile.readText())
            .firstOrNull { it.id == "test_circuit_s_mamede" }
            ?: error("Test Circuit S.Mamede is missing from the catalogue")

    private fun cataloguedStartLine(): LocalLapDetector.StartLine = track().startLine.let {
        LocalLapDetector.StartLine(it.lat1, it.lng1, it.lat2, it.lng2)
    }

    private fun fixture() = LapReplayHarness.load("s_mamede", tempFolder.newFolder())

    // --- The heading ---------------------------------------------------------------------

    /**
     * Measured with [LocalLapDetector.TrackPriors.NONE] so the reference heading comes from
     * the session itself and each crossing's heading is a measurement of the kart rather
     * than an echo of the number under test. Measured: four crossings, 2.8-4.5 deg off.
     */
    @Test
    fun theHeadingTheKartsActuallyCrossAtMatchesTheSurveyedOne() {
        val outcome = LocalLapDetector().detectLapsWithDiagnostics(
            fixture().telemetryFile,
            cataloguedStartLine(),
            LocalLapDetector.TrackPriors.NONE
        )
        val diagnostics = requireNotNull(outcome.diagnostics)

        assertTrue(
            "the session must yield crossings of the catalogued line, got " +
                "${diagnostics.acceptedCrossings.size}",
            diagnostics.acceptedCrossings.size >= 4
        )

        val surveyed = track().travelHeadingDeg!!
        val deviations = diagnostics.acceptedCrossings.map {
            GeoUtils.angularDifferenceDegrees(it.headingDeg, surveyed)
        }
        assertTrue(
            "every crossing should run the way the catalogue says; worst deviation from " +
                "$surveyed deg was ${"%.1f".format(deviations.max())} deg",
            deviations.all { it <= 25.0 }
        )
        val sorted = deviations.sorted()
        assertTrue(
            "the typical crossing should be far tighter than the tolerance, median was " +
                "${"%.1f".format(sorted[sorted.size / 2])} deg",
            sorted[sorted.size / 2] <= 5.0
        )
    }

    /**
     * The catalogue heading has to be the reference the detector actually uses.
     *
     * On this circuit a reversed heading does **not** lose the laps, unlike Cabo do Mundo:
     * nothing else passes near the start line, so with the catalogue heading rejecting
     * every crossing the detector falls back to reading the direction off the session
     * (`LocalLapDetector`, "A catalogue heading that does not match the session is worse
     * than none") and recovers all three. A lap count therefore cannot detect a reversed
     * entry here. [LocalLapDetector.DetectionDiagnostics.headingReference] can.
     */
    @Test
    fun theCatalogueHeadingIsTheReferenceAndAReversedOneIsDetectable() {
        val correct = LocalLapDetector().detectLapsWithDiagnostics(
            fixture().telemetryFile, cataloguedStartLine(), track().priors()
        )
        assertEquals(
            "with the catalogued heading the detector should use it as its reference",
            LocalLapDetector.HeadingReference.TRACK_CATALOGUE,
            correct.diagnostics!!.headingReference
        )
        assertEquals(3, LapReplayHarness.lapSeconds(correct.result).size)

        val reversed = LocalLapDetector().detectLapsWithDiagnostics(
            fixture().telemetryFile,
            cataloguedStartLine(),
            track().priors().copy(travelHeadingDeg = 104.7)
        )
        assertEquals(
            "a reversed heading should be abandoned for the session's own - if this ever " +
                "reads TRACK_CATALOGUE, a backwards entry is being trusted",
            LocalLapDetector.HeadingReference.FIRST_CROSSING,
            reversed.diagnostics!!.headingReference
        )
    }

    // --- The length ----------------------------------------------------------------------

    /**
     * `lengthM` came from this session, so this is not corroboration of 802 m - it is a
     * check that the karts and the *walked ring* (821.6 m) describe the same circuit.
     *
     * Measured: 795-810 m per lap, 0.97x the ring. That is an **under**-read, the opposite
     * of Baltar and Cabo do Mundo. 1 Hz fixes on a short twisty circuit chord the corners,
     * and the kart drives a racing line shorter than a mid-track walk; together those
     * outweigh the random-walk over-read. So this asserts a band, not a direction.
     */
    @Test
    fun theDistanceTheKartsCoveredAgreesWithTheWalkedRing() {
        val lapDistances = lapDistancesBetweenCrossings()
        assertTrue("need the three flying laps, got ${lapDistances.size}", lapDistances.size >= 3)

        val median = lapDistances.sorted()[lapDistances.size / 2]
        val ring = centrelinePerimetreM(track())
        val ratio = median / ring

        assertTrue(
            "the karts covered a median ${"%.0f".format(median)} m between crossings against a " +
                "walked ring of ${"%.0f".format(ring)} m, ratio ${"%.3f".format(ratio)}",
            ratio in 0.9..1.2
        )
    }

    // --- The envelope --------------------------------------------------------------------

    /**
     * 50 s is the operator's figure for a quick pilot, and the driver in this session was an
     * average one at 76-80 s, so it cannot be tied to this session's best lap the way Cabo
     * do Mundo's is (that check would fail at 0.66x and would be wrong to). What can be
     * checked is that it is reachable: 802 m in 50 s averages 16.0 m/s, below the 19.1 m/s
     * this session's kart actually reached.
     */
    @Test
    fun theDeclaredFastestLapIsQuickerThanAnyLapDrivenButReachable() {
        val laps = LapReplayHarness.lapSeconds(
            LocalLapDetector().detectLaps(
                fixture().telemetryFile, cataloguedStartLine(), track().priors()
            )
        )
        assertTrue("need laps to measure against, got ${laps.size}", laps.size >= 3)

        val declared = track().fastestLapMs!! / 1000.0
        assertTrue(
            "the declared fastest lap of $declared s must be quicker than the quickest " +
                "recorded (${"%.1f".format(laps.min())} s)",
            declared < laps.min()
        )
        // guards a figure typed in the wrong unit, not a corroboration of 50 s
        assertTrue("$declared s is implausibly far below every real lap", declared > laps.min() * 0.5)

        val averageMs = track().lengthM!! / declared
        val peakMs = fixture().telemetryFile.readLines()
            .filter { it.contains("\"speedMs\"") }
            .maxOf { org.json.JSONObject(it).getDouble("speedMs") }
        assertTrue(
            "a $declared s lap averages ${"%.1f".format(averageMs)} m/s, which must be below the " +
                "${"%.1f".format(peakMs)} m/s peak a kart reached here",
            averageMs < peakMs
        )
    }

    /**
     * The 40 s floor the catalogue imposes (LD-21) changes nothing a session-derived 20 s
     * floor would find - so the priors tighten the detector without editing this session.
     */
    @Test
    fun theCataloguePriorsDetectExactlyTheLapsTheSessionAloneDoes() {
        val fixture = fixture()
        val withPriors = LapReplayHarness.lapSeconds(
            LocalLapDetector().detectLaps(fixture.telemetryFile, cataloguedStartLine(), track().priors())
        )
        val withoutPriors = LapReplayHarness.lapSeconds(
            LocalLapDetector().detectLaps(
                fixture.telemetryFile, cataloguedStartLine(), LocalLapDetector.TrackPriors.NONE
            )
        )
        assertTrue("need laps for the comparison to mean anything", withoutPriors.size >= 3)
        assertEquals(withoutPriors, withPriors)
    }

    @Test
    fun everyRealLapIsComfortablyInsideTheLd22Floor() {
        val laps = LapReplayHarness.lapSeconds(
            LocalLapDetector().detectLaps(
                fixture().telemetryFile, cataloguedStartLine(), track().priors()
            )
        )
        assertTrue("need laps to measure against, got ${laps.size}", laps.size >= 3)

        val slowestImpliedSpeed = track().lengthM!! / laps.max()
        assertTrue(
            "the slowest real lap implies ${"%.1f".format(slowestImpliedSpeed)} m/s, which must " +
                "stay well clear of the ${LocalLapDetector.MIN_PLAUSIBLE_LAP_SPEED_MS} m/s floor",
            slowestImpliedSpeed >= 8.0
        )
    }

    // --- The cross-check -----------------------------------------------------------------

    /**
     * The phone timed this session against its own captured line; the catalogue times it
     * against a line walked three weeks later. Both must give the same laps. Measured:
     * within 0.1 s on all three.
     */
    @Test
    fun theCataloguedLineReproducesTheLapTimesTheDriverMeasuredIndependently() {
        val detected = LapReplayHarness.lapSeconds(
            LocalLapDetector().detectLaps(
                fixture().telemetryFile, cataloguedStartLine(), track().priors()
            )
        )
        val recorded = lapSecondsRecordedByTheApp()

        assertTrue("expected the three flying laps, got ${detected.size}", detected.size >= 3)
        val worst = detected.maxOf { lap -> recorded.minOf { abs(it - lap) } }
        assertTrue(
            "lap times at the catalogued line should reproduce the driver's own; worst " +
                "disagreement ${"%.2f".format(worst)} s",
            worst <= 0.5
        )
    }

    /**
     * The two lines are only 5.4 m apart - both across the same straight - so unlike Cabo do
     * Mundo they are not distinguishable by position on the circuit. What makes them two
     * measurements is that they were taken by different means, weeks apart. This pins the
     * separation so a later edit cannot quietly replace the walked line with the captured one.
     */
    @Test
    fun theCataloguedLineIsNotTheLineTheDriverCaptured() {
        val catalogued = cataloguedStartLine()
        val captured = fixture().startLine

        val separation = GeoUtils.haversineDistance(
            (catalogued.lat1 + catalogued.lat2) / 2, (catalogued.lng1 + catalogued.lng2) / 2,
            (captured.lat1 + captured.lat2) / 2, (captured.lng1 + captured.lng2) / 2
        )
        assertEquals("the walked and captured lines are distinct", 5.4, separation, 0.5)

        val centreline = track().centreline!!.points
        val offset = distanceToRingM(
            centreline,
            (catalogued.lat1 + catalogued.lat2) / 2, (catalogued.lng1 + catalogued.lng2) / 2
        )
        assertTrue(
            "the catalogued line should sit on the walked ring, measured ${"%.1f".format(offset)} m",
            offset < 2.0
        )
    }

    // --- Measurement helpers -------------------------------------------------------------

    /** Crossings come from the detector itself; see the Cabo do Mundo equivalent for why. */
    private fun lapDistancesBetweenCrossings(): List<Double> {
        val fixtureFile = fixture().telemetryFile
        val outcome = LocalLapDetector().detectLapsWithDiagnostics(
            fixtureFile, cataloguedStartLine(), track().priors()
        )
        val crossings = outcome.diagnostics!!.acceptedCrossings.map { it.timestampMs }.sorted()
        val samples = readSamples(fixtureFile)
        return crossings.zipWithNext()
            .filter { (from, to) -> to - from in 40_000..160_000 }
            .map { (from, to) -> pathLengthM(samples, from, to) }
    }

    private fun lapSecondsRecordedByTheApp(): List<Double> {
        val json = requireNotNull(
            javaClass.getResourceAsStream("/lapfixtures/s_mamede/session.json")
        ) { "fixture metadata missing" }.bufferedReader().use { it.readText() }
        val laps = org.json.JSONObject(json).getJSONArray("laps")
        return (0 until laps.length()).map { laps.getJSONObject(it).getLong("durationMs") / 1000.0 }
    }

    private data class Sample(val timestampMs: Long, val latitude: Double, val longitude: Double)

    private fun readSamples(file: File): List<Sample> = file.readLines().mapNotNull { line ->
        if (!line.contains("\"latitude\"")) return@mapNotNull null
        val json = org.json.JSONObject(line)
        Sample(json.getLong("timestampMs"), json.getDouble("latitude"), json.getDouble("longitude"))
    }

    private fun pathLengthM(samples: List<Sample>, fromMs: Long, toMs: Long): Double =
        samples.filter { it.timestampMs in fromMs..toMs }
            .zipWithNext()
            .sumOf { (a, b) -> GeoUtils.haversineDistance(a.latitude, a.longitude, b.latitude, b.longitude) }

    private fun centrelinePerimetreM(track: Track): Double {
        val points = track.centreline!!.points
        return points.indices.sumOf { i ->
            val a = points[i]
            val b = points[(i + 1) % points.size]
            GeoUtils.haversineDistance(a.latitude, a.longitude, b.latitude, b.longitude)
        }
    }

    /**
     * Distance to the nearest *segment* of the ring. Waypoints here are ~11 m apart, so
     * distance to the nearest waypoint would read up to ~5 m for a point lying on the ring.
     */
    private fun distanceToRingM(ring: List<TrackPoint>, lat: Double, lng: Double): Double {
        val mPerDegLat = 111_320.0
        val mPerDegLng = 111_320.0 * cos(Math.toRadians(lat))
        fun local(p: TrackPoint) = (p.longitude - lng) * mPerDegLng to (p.latitude - lat) * mPerDegLat
        return ring.indices.minOf { i ->
            val (ax, ay) = local(ring[i])
            val (bx, by) = local(ring[(i + 1) % ring.size])
            val dx = bx - ax
            val dy = by - ay
            val lengthSq = dx * dx + dy * dy
            val t = if (lengthSq == 0.0) 0.0 else ((-ax) * dx + (-ay) * dy) / lengthSq
            val c = t.coerceIn(0.0, 1.0)
            hypot(ax + c * dx, ay + c * dy)
        }
    }
}
