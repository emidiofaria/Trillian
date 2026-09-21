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

/**
 * Crosses the **surveyed** Baltar entry against the **recorded** incident 15 session.
 *
 * [BundledTrackCatalogTest] holds the catalogue to its own internal claims. This class
 * asks the harder question: the numbers in that entry came from a walk and from a map,
 * so does a real kart, on a real day, agree with them?
 *
 * It matters because of what now depends on those two numbers:
 *
 * | Number | Origin | What rests on it |
 * |--------|--------|------------------|
 * | `travelHeadingDeg` 137.8 | map coordinates | LD-19 direction filter, LD-20 heading reference |
 * | `lengthM` 1020 | surveyed on foot | LD-22 plausibility floor |
 *
 * Neither had any evidence behind it beyond the survey itself. A wrong heading rejects
 * laps that happened; a wrong length either discards real sessions or stops catching
 * incident 15. Both failures are silent.
 *
 * ## The start line these tests use
 *
 * Every test here measures against the **catalogued** start/finish line, never the one
 * `session.json` carries. The session was recorded against a line 164 m further round
 * the lap, drawn in the opposite direction — that line is what incident 15 happened on,
 * and replaying it is [com.drivingcoach.lap.LapDetectionIncident15Test]'s job, not this
 * class's. [theCataloguedLineIsNotTheLineTheDriverCaptured] exists so the two can never
 * be confused by a later edit.
 */
class BaltarSurveyCorroborationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val catalogueFile = File("src/main/assets/tracks/tracks.json")

    private fun baltar(): Track =
        BundledTrackCatalog.parse(catalogueFile.readText())
            .firstOrNull { it.id == "baltar" }
            ?: error("Baltar is missing from the catalogue")

    /** The catalogued line, as the app would resolve it at the moment recording starts (TL-07). */
    private fun cataloguedStartLine(): LocalLapDetector.StartLine = baltar().startLine.let {
        LocalLapDetector.StartLine(it.lat1, it.lng1, it.lat2, it.lng2)
    }

    private fun fixture() = LapReplayHarness.load("baltar2", tempFolder.newFolder())

    // --- The heading ---------------------------------------------------------------------

    /**
     * `travelHeadingDeg` was read off a map: two points up the start straight, C to D.
     * Nothing had ever checked it against a kart.
     *
     * The measurement is taken with [LocalLapDetector.TrackPriors.NONE] on purpose. Given
     * no catalogue, the detector derives its reference heading from the session itself, so
     * every accepted crossing's `headingDeg` is a *measurement of the kart* rather than an
     * echo of the number under test. Comparing the two is therefore a real cross-check and
     * not a tautology.
     *
     * 20 degrees of tolerance because a 1 Hz fix at 16 m/s places consecutive samples 16 m
     * apart, and a heading derived from two such points carries several degrees of noise on
     * its own. The observed spread is 134.7 to 147.9 degrees.
     */
    @Test
    fun theHeadingTheKartsActuallyCrossAtMatchesTheOneSurveyedFromTheMap() {
        val outcome = LocalLapDetector().detectLapsWithDiagnostics(
            fixture().telemetryFile,
            cataloguedStartLine(),
            LocalLapDetector.TrackPriors.NONE
        )
        val diagnostics = requireNotNull(outcome.diagnostics) {
            "the detector should always return diagnostics"
        }

        assertTrue(
            "the session must yield crossings of the catalogued line or there is nothing " +
                "to corroborate, got ${diagnostics.acceptedCrossings.size}",
            diagnostics.acceptedCrossings.size >= 10
        )

        val surveyed = baltar().travelHeadingDeg!!
        val deviations = diagnostics.acceptedCrossings.map {
            GeoUtils.angularDifferenceDegrees(it.headingDeg, surveyed)
        }

        assertTrue(
            "every crossing the karts made should run the way the map says the circuit is " +
                "driven; worst deviation from $surveyed deg was ${deviations.max()} deg " +
                "across ${deviations.size} crossings",
            deviations.all { it <= 20.0 }
        )
    }

    // --- The length ----------------------------------------------------------------------

    /**
     * `lengthM` came from the walk. This measures what the karts covered between
     * consecutive crossings of the catalogued line and checks the two agree.
     *
     * The band is wide, and deliberately so. Summing distances between consecutive 1 Hz
     * fixes **over-reads**: each fix carries its own error (6.4 m on average in this
     * session), so the sum adds a random walk on top of the true path. The observed median
     * is about 1094 m against a surveyed 1020 — roughly +7%, which is what that bias
     * predicts. Asserting a tight equality here would be fake precision; asserting the
     * surveyed figure is not out by a quarter is the real claim.
     */
    @Test
    fun theSurveyedLapLengthIsCorroboratedByTheDistanceTheKartsCovered() {
        val lapDistances = lapDistancesBetweenCrossings()

        assertTrue(
            "need several clean laps to take a median of, got ${lapDistances.size}",
            lapDistances.size >= 5
        )

        val median = lapDistances.sorted()[lapDistances.size / 2]
        val surveyed = baltar().lengthM!!

        assertTrue(
            "the karts covered a median of ${"%.0f".format(median)} m between crossings, " +
                "which should corroborate the surveyed $surveyed m allowing for GPS " +
                "path-length over-read",
            median in 900.0..1200.0
        )
        assertTrue(
            "GPS should over-read rather than under-read a surveyed path; if the measured " +
                "distance is shorter than the survey, the survey is probably too long",
            median >= surveyed - 50
        )
    }

    /**
     * Anti-drift, and the cheapest test here.
     *
     * `lengthM` and the centreline are two statements of the same fact, stored separately.
     * Adding or moving waypoints without updating `lengthM` would leave LD-22 measuring
     * laps against a stale number, which changes nothing visible until a driver is slow
     * enough to be affected. Nothing else in the suite compares them.
     */
    @Test
    fun theDeclaredLapLengthMatchesTheCentrelineItWasDerivedFrom() {
        val track = baltar()
        val declared = track.lengthM!!.toDouble()
        val perimeter = centrelinePerimetreM(track)

        assertEquals(
            "the declared lap length and the centreline it came from have drifted apart: " +
                "declared $declared m, centreline measures ${"%.1f".format(perimeter)} m",
            declared, perimeter, declared * 0.02
        )
    }

    /**
     * LD-22 discards a lap set only when every lap implies under
     * [LocalLapDetector.MIN_PLAUSIBLE_LAP_SPEED_MS] over the surveyed length. This checks
     * the floor sits far enough below real racing that it can never touch a genuine
     * session — the failure the rework was written to prevent.
     */
    @Test
    fun everyRealLapIsComfortablyInsideTheLd22Floor() {
        val result = LapReplayHarness.detect(
            fixture(),
            priors = baltar().priors(),
            startLine = cataloguedStartLine()
        )
        val laps = LapReplayHarness.lapSeconds(result)
        assertEquals("expected the driver's twelve laps", 12, laps.size)

        val length = baltar().lengthM!!.toDouble()
        val slowest = laps.max()
        val slowestImpliedSpeed = length / slowest

        assertTrue(
            "the slowest real lap implies ${"%.1f".format(slowestImpliedSpeed)} m/s, which " +
                "must stay well clear of the 5.0 m/s floor or LD-22 would be discarding " +
                "sessions that happened",
            slowestImpliedSpeed >= 10.0
        )
    }

    // --- The guard -----------------------------------------------------------------------

    /**
     * The catalogued line and the line the driver captured are different lines, and every
     * test in this class depends on using the first.
     *
     * They are not obviously different at a glance — both are about 11 m long and both sit
     * within a couple of metres of the racing line, because the captured one was a
     * perfectly reasonable place to stand. Swapping them in a test would not throw; it
     * would quietly measure the wrong circuit. This states the difference in numbers so
     * that a later edit which conflates them fails here, loudly, with an explanation.
     */
    @Test
    fun theCataloguedLineIsNotTheLineTheDriverCaptured() {
        val catalogued = cataloguedStartLine()
        val captured = fixture().startLine

        val separation = GeoUtils.haversineDistance(
            (catalogued.lat1 + catalogued.lat2) / 2, (catalogued.lng1 + catalogued.lng2) / 2,
            (captured.lat1 + captured.lat2) / 2, (captured.lng1 + captured.lng2) / 2
        )
        assertTrue(
            "the two lines should be most of a straight apart, measured " +
                "${"%.0f".format(separation)} m",
            separation > 100.0
        )

        val cataloguedBearing = GeoUtils.bearingDegrees(
            catalogued.lat1, catalogued.lng1, catalogued.lat2, catalogued.lng2
        )
        val capturedBearing = GeoUtils.bearingDegrees(
            captured.lat1, captured.lng1, captured.lat2, captured.lng2
        )
        assertTrue(
            "the two lines are also drawn in opposing directions, which is why a swap " +
                "changes the sign of every crossing rather than merely its position",
            GeoUtils.angularDifferenceDegrees(cataloguedBearing, capturedBearing) > 150.0
        )
    }

    // --- Measurement helpers -------------------------------------------------------------

    /**
     * Distance covered between consecutive crossings of the catalogued line, in metres.
     *
     * Crossings come from the detector rather than from geometry re-implemented here: a
     * hand-rolled segment test misses crossings near the ends of an 11 m line when samples
     * are 16 m apart, which would make this test assert its own bug. Intervals longer than
     * 120 s are dropped as multi-lap gaps.
     */
    private fun lapDistancesBetweenCrossings(): List<Double> {
        val fixtureFile = fixture().telemetryFile
        val outcome = LocalLapDetector().detectLapsWithDiagnostics(
            fixtureFile,
            cataloguedStartLine(),
            baltar().priors()
        )
        val crossings = outcome.diagnostics!!.acceptedCrossings.map { it.timestampMs }.sorted()
        val samples = readSamples(fixtureFile)

        return crossings.zipWithNext()
            .filter { (from, to) -> to - from in 30_000..120_000 }
            .map { (from, to) -> pathLengthM(samples, from, to) }
    }

    private data class Sample(val timestampMs: Long, val latitude: Double, val longitude: Double)

    private fun readSamples(file: File): List<Sample> = file.readLines().mapNotNull { line ->
        if (!line.contains("\"latitude\"")) return@mapNotNull null
        val json = org.json.JSONObject(line)
        Sample(
            json.getLong("timestampMs"),
            json.getDouble("latitude"),
            json.getDouble("longitude")
        )
    }

    private fun pathLengthM(samples: List<Sample>, fromMs: Long, toMs: Long): Double =
        samples.filter { it.timestampMs in fromMs..toMs }
            .zipWithNext()
            .sumOf { (a, b) ->
                GeoUtils.haversineDistance(a.latitude, a.longitude, b.latitude, b.longitude)
            }

    private fun centrelinePerimetreM(track: Track): Double {
        val points = track.centreline!!.points
        return points.indices.sumOf { i ->
            val a = points[i]
            val b = points[(i + 1) % points.size]
            GeoUtils.haversineDistance(a.latitude, a.longitude, b.latitude, b.longitude)
        }
    }
}
