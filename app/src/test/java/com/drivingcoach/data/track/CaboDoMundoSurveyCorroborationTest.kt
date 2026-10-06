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
 * Crosses the **surveyed** Cabo do Mundo entry against a **recorded** kart session.
 *
 * [CaboDoMundoCatalogueTest] holds the entry to its own internal claims, which is a
 * closed loop: the heading, the length and the ring all came from one person walking
 * the circuit on one afternoon, so they agree with each other by construction. This
 * class asks whether a kart agrees with them.
 *
 * | Number | Origin | What rests on it |
 * |--------|--------|------------------|
 * | `travelHeadingDeg` 59.8 | two surveyed points, 18 m apart | LD-19 direction filter, LD-20 heading reference |
 * | `lengthM` 826 | derived from the walked ring | LD-22 plausibility floor |
 * | `fastestLapMs` 50000 | what a quick pilot laps this circuit in, supplied by the operator | minimum gap between crossings |
 *
 * ## Why this session is evidence rather than decoration
 *
 * It was recorded on 2026-09-26, two days before the circuit was surveyed and three
 * before it was catalogued, so no number here could have been fitted to it. More
 * usefully still, the driver marked their start line **in the pit lane** - 7 m off the
 * centreline, 55 m short of the real start/finish, with the kart sitting stationary on
 * it for the first 76 s of the recording. The catalogued line and the recorded line are
 * therefore independent measurements of the same circuit, taken by different means, and
 * [theCataloguedLineIsNotTheLineTheDriverCaptured] exists so a later edit cannot quietly
 * collapse them into one.
 *
 * ## The start line these tests use
 *
 * Every test here measures against the **catalogued** line, never the one `session.json`
 * carries. The fixture is committed exactly as the device exported it, pit-lane line and
 * all, because a fixture that has been tidied up is no longer evidence.
 */
class CaboDoMundoSurveyCorroborationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val catalogueFile = File("src/main/assets/tracks/tracks.json")

    private fun track(): Track =
        BundledTrackCatalog.parse(catalogueFile.readText())
            .firstOrNull { it.id == "cabo_do_mundo" }
            ?: error("Cabo do Mundo is missing from the catalogue")

    /** The catalogued line, as the app resolves it when recording starts (TL-07). */
    private fun cataloguedStartLine(): LocalLapDetector.StartLine = track().startLine.let {
        LocalLapDetector.StartLine(it.lat1, it.lng1, it.lat2, it.lng2)
    }

    private fun fixture() = LapReplayHarness.load("cabo_do_mundo", tempFolder.newFolder())

    // --- The heading ---------------------------------------------------------------------

    /**
     * `travelHeadingDeg` came from two points a surveyor stood on. Nothing had checked
     * it against a kart.
     *
     * Measured with [LocalLapDetector.TrackPriors.NONE] on purpose: given no catalogue,
     * the detector derives its reference heading from the session itself, so every
     * accepted crossing's `headingDeg` is a measurement of the kart rather than an echo
     * of the number under test.
     *
     * 25 degrees of tolerance, and the reason is a single crossing. Nine of the ten are
     * within 3.4 degrees. The tenth is the out-lap, taken at 6.6 m/s while the kart was
     * still finding the circuit, and a heading derived from two 1 Hz fixes at that speed
     * is worth little - it measures 21.5 degrees off. Tightening this to 20 would fail on
     * the noisiest sample in the session rather than on anything about the survey.
     */
    @Test
    fun theHeadingTheKartsActuallyCrossAtMatchesTheSurveyedOne() {
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
            diagnostics.acceptedCrossings.size >= 8
        )

        val surveyed = track().travelHeadingDeg!!
        val deviations = diagnostics.acceptedCrossings.map {
            GeoUtils.angularDifferenceDegrees(it.headingDeg, surveyed)
        }

        assertTrue(
            "every crossing the karts made should run the way the survey says the circuit " +
                "is driven; worst deviation from $surveyed deg was " +
                "${"%.1f".format(deviations.max())} deg across ${deviations.size} crossings",
            deviations.all { it <= 25.0 }
        )

        val sorted = deviations.sorted()
        assertTrue(
            "and the typical crossing should be far tighter than the tolerance, median was " +
                "${"%.1f".format(sorted[sorted.size / 2])} deg",
            sorted[sorted.size / 2] <= 5.0
        )
    }

    /**
     * The direction filter is not a refinement on this circuit, it is the thing that
     * makes it detectable.
     *
     * The return section passes 18.4 m from the start point against a 15 m detection
     * corridor, so karts running the other way come close enough to be considered. With
     * the heading reversed - the single most likely data-entry error, and the exact
     * shape of incident 15 - the detector accepts two crossings and reports **no laps
     * at all**, with no error raised anywhere.
     *
     * This is a mutation test kept as a permanent assertion. It fails if someone
     * reverses the heading, and it also fails if the direction filter is ever weakened
     * to the point where reversing it stops mattering.
     */
    @Test
    fun theDirectionPriorIsLoadBearingOnThisCircuit() {
        val correct = LapReplayHarness.lapSeconds(
            LocalLapDetector().detectLaps(
                fixture().telemetryFile, cataloguedStartLine(), track().priors()
            )
        )
        assertTrue("the catalogued heading should produce a full set of laps", correct.size >= 8)

        val reversed = track().priors().copy(travelHeadingDeg = 239.8)
        val outcome = LocalLapDetector().detectLapsWithDiagnostics(
            fixture().telemetryFile, cataloguedStartLine(), reversed
        )

        assertEquals(
            "a reversed heading must not quietly produce a plausible-looking lap set - " +
                "that is how incident 15 shipped",
            0, LapReplayHarness.lapSeconds(outcome.result).size
        )
        assertTrue(
            "and the rejections should say why, so the failure is diagnosable",
            outcome.diagnostics!!.rejectedCrossings.any {
                it.reason == LocalLapDetector.RejectionReason.HEADING_MISMATCH
            }
        )
    }

    // --- The length ----------------------------------------------------------------------

    /**
     * `lengthM` was derived from the walked ring, so the walk cannot corroborate it -
     * it *is* it. This measures what the karts covered between consecutive crossings
     * instead, which is a genuinely separate observation.
     *
     * The band is wide and deliberately so. Summing distances between consecutive 1 Hz
     * fixes over-reads, because each fix carries its own error and the sum adds a random
     * walk on top of the true path. Asserting an equality here would be fake precision.
     */
    @Test
    fun theSurveyedLapLengthIsCorroboratedByTheDistanceTheKartsCovered() {
        val lapDistances = lapDistancesBetweenCrossings()

        assertTrue(
            "need several clean laps to take a median of, got ${lapDistances.size}",
            lapDistances.size >= 5
        )

        val median = lapDistances.sorted()[lapDistances.size / 2]
        val surveyed = track().lengthM!!

        assertTrue(
            "the karts covered a median of ${"%.0f".format(median)} m between crossings, " +
                "which should corroborate the surveyed $surveyed m allowing for GPS " +
                "path-length over-read",
            median in 743.0..991.0
        )
        assertTrue(
            "GPS should over-read rather than under-read a surveyed path; if the measured " +
                "distance is shorter than the survey, the survey is probably too long",
            median >= surveyed - 50
        )
    }

    /**
     * Anti-drift, and the cheapest test here. `lengthM` and the centreline are two
     * statements of the same fact, stored separately: moving waypoints without updating
     * `lengthM` leaves LD-22 measuring laps against a stale number, which changes nothing
     * visible until a driver is slow enough to be affected.
     */
    @Test
    fun theDeclaredLapLengthMatchesTheCentrelineItWasDerivedFrom() {
        val track = track()
        val declared = track.lengthM!!.toDouble()
        val perimeter = centrelinePerimetreM(track)

        assertEquals(
            "the declared lap length and the centreline it came from have drifted apart: " +
                "declared $declared m, centreline measures ${"%.1f".format(perimeter)} m",
            declared, perimeter, declared * 0.02
        )
    }

    // --- The envelope --------------------------------------------------------------------

    /**
     * The entry was first drafted with Baltar's 40 s fastest lap, on a circuit 194 m
     * shorter. 826 m in 40 s demands a 74.3 km/h average when the highest speed recorded
     * anywhere in the session is 72.5 km/h, so that figure was not reachable and went.
     *
     * What replaced it was, briefly, 60 s - the best lap in this session, rounded down.
     * That was a methodological mistake and is worth recording, because the test below
     * would have gone on passing regardless. A recorded session bounds what *has* been
     * driven, not what *can* be; it is evidence for the fast end of an envelope only if
     * a fast driver happened to be the one holding the phone. The driver here was an
     * average one, so 60 s described them rather than the circuit, and every quick pilot
     * would have been measured against a floor set by somebody slower.
     *
     * The shipped 50 s is the operator's own figure for what quick pilots lap this
     * circuit in. It is reachable - 59.5 km/h average, comfortably inside the 72.5 km/h
     * peak - and it still sits below every lap recorded here, which is what makes it an
     * envelope rather than a guess.
     *
     * The lower bound below is the weakest assertion in this class and deliberately so:
     * it ties the declared figure to one session's best lap, which is the very thing the
     * paragraph above says an envelope must not depend on. It is kept as a guard against
     * a figure typed in by accident - a stray 5000 would sail through every other check
     * here - not as corroboration of 50 s.
     */
    @Test
    fun theDeclaredFastestLapIsQuickerThanAnyLapActuallyDrivenButStillReachable() {
        val laps = LapReplayHarness.lapSeconds(
            LocalLapDetector().detectLaps(
                fixture().telemetryFile, cataloguedStartLine(), track().priors()
            )
        )
        assertTrue("need laps to measure against, got ${laps.size}", laps.size >= 5)

        val declared = track().fastestLapMs!! / 1000.0
        val quickest = laps.min()

        assertTrue(
            "the declared fastest lap of $declared s must be quicker than the quickest lap " +
                "recorded (${"%.1f".format(quickest)} s) or it is not an envelope",
            declared < quickest
        )
        assertTrue(
            "but not so much quicker that it stops constraining anything - $declared s " +
                "against a recorded best of ${"%.1f".format(quickest)} s",
            declared > quickest * 0.75
        )

        val impliedKmh = track().lengthM!! / declared * 3.6
        assertTrue(
            "a $declared s lap over ${track().lengthM} m implies " +
                "${"%.0f".format(impliedKmh)} km/h, which must stay under what a kart does",
            impliedKmh <= 126.0
        )
    }

    /**
     * The measurement behind dropping `fastestLapMs` from 60 s to 50 s.
     *
     * LD-21 derives the minimum gap between crossings from this figure, so the change
     * moved the floor from 48 s to 40 s. A lower floor cannot discard anything - it can
     * only *admit* a candidate crossing that the higher one was refusing - which makes
     * the risk one-directional and specific: somewhere on this circuit the return
     * section passes 18.4 m from the start point, inside the 15 m corridor's error
     * budget once GPS scatter is allowed for, and it is driven late in the lap. Were it
     * to produce a candidate between 40 s and 48 s after the previous crossing, the 48 s
     * floor was the thing holding it back and this change would hand the driver a
     * phantom lap.
     *
     * The direction prior (LD-20) is the other defence and is proven load-bearing here
     * by [theDirectionPriorIsLoadBearingOnThisCircuit]. This test asks whether it is
     * carrying the weight on its own, by replaying one session against both floors and
     * requiring the same laps out of both. Comparing the two is better than asserting a
     * lap count, because a count can stay the same while the boundaries move.
     */
    @Test
    fun looseningTheFloorToFortySecondsAdmitsNothingTheOldOneWasHolding() {
        val fixture = fixture()
        val detector = LocalLapDetector()
        val shipped = track().priors()

        assertEquals(
            "this test compares the shipped floor against the 60 s one it replaced; " +
                "if the catalogue moves again, the comparison has to move with it",
            50_000L, shipped.fastestLapMs
        )

        val atFortySecondFloor = LapReplayHarness.lapSeconds(
            detector.detectLaps(fixture.telemetryFile, cataloguedStartLine(), shipped)
        )
        val atFortyEightSecondFloor = LapReplayHarness.lapSeconds(
            detector.detectLaps(
                fixture.telemetryFile,
                cataloguedStartLine(),
                shipped.copy(fastestLapMs = 60_000L)
            )
        )

        // Without this, two empty lists would satisfy the comparison below and the test
        // would pass while measuring nothing at all.
        assertTrue(
            "the session has to detect laps for this comparison to mean anything, got " +
                "${atFortyEightSecondFloor.size} at the old floor",
            atFortyEightSecondFloor.size >= 5
        )

        assertEquals(
            "lowering the minimum gap from 48 s to 40 s changed what this session " +
                "detects. At the 48 s floor: $atFortyEightSecondFloor. At 40 s: " +
                "$atFortySecondFloor. An extra boundary here is the return section " +
                "being counted as a lap, and it means 50 s cannot be declared without " +
                "a stronger guard than the direction prior alone",
            atFortyEightSecondFloor, atFortySecondFloor
        )
    }

    /**
     * LD-22 discards a lap set only when every lap implies under
     * [LocalLapDetector.MIN_PLAUSIBLE_LAP_SPEED_MS] over the surveyed length. This checks
     * the floor sits far enough below real racing that it can never touch a genuine
     * session.
     */
    @Test
    fun everyRealLapIsComfortablyInsideTheLd22Floor() {
        val laps = LapReplayHarness.lapSeconds(
            LocalLapDetector().detectLaps(
                fixture().telemetryFile, cataloguedStartLine(), track().priors()
            )
        )
        assertTrue("need laps to measure against, got ${laps.size}", laps.size >= 5)

        val length = track().lengthM!!.toDouble()
        val slowestImpliedSpeed = length / laps.max()

        assertTrue(
            "the slowest real lap implies ${"%.1f".format(slowestImpliedSpeed)} m/s, which " +
                "must stay well clear of the ${LocalLapDetector.MIN_PLAUSIBLE_LAP_SPEED_MS} m/s " +
                "floor or LD-22 would be discarding sessions that happened",
            slowestImpliedSpeed >= 10.0
        )
    }

    // --- The strongest cross-check -------------------------------------------------------

    /**
     * The catalogued line and the driver's line are 56.8 m apart around the lap and were
     * produced by entirely different means - one walked with a surveyor's intent, one
     * tapped out in a pit lane. Timing the same session at two unrelated points on the
     * circuit must yield the same lap times, because a lap is a lap wherever you start
     * counting it.
     *
     * They agree to within a third of a second. That is the single most persuasive piece
     * of evidence this circuit has: the surveyed geometry reproduces, independently,
     * what the driver's own session measured on the day.
     */
    @Test
    fun theCataloguedLineReproducesTheLapTimesTheDriverMeasuredIndependently() {
        val detected = LapReplayHarness.lapSeconds(
            LocalLapDetector().detectLaps(
                fixture().telemetryFile, cataloguedStartLine(), track().priors()
            )
        )
        val recorded = lapSecondsRecordedByTheApp()

        assertTrue("the fixture should carry the driver's own laps", recorded.size >= detected.size)
        assertTrue("expected the flying laps to be detected, got ${detected.size}", detected.size >= 8)

        // the app also timed an out-lap and an in-lap that the current detector rejects,
        // so match each detected lap to the closest recorded one rather than by index
        val worst = detected.maxOf { lap -> recorded.minOf { kotlin.math.abs(it - lap) } }

        assertTrue(
            "lap times measured at the catalogued start/finish should reproduce the ones the " +
                "driver recorded 57 m away round the lap; worst disagreement was " +
                "${"%.2f".format(worst)} s across ${detected.size} laps",
            worst <= 1.0
        )
    }

    /**
     * The catalogued line and the line the driver captured are different lines, and
     * every test in this class depends on using the first.
     *
     * A swap would not throw. It would quietly measure the circuit from a point in the
     * pit lane and still return a plausible set of laps - as the probe for this work
     * confirmed, the driver's line yields eight laps of its own. This states the
     * difference in numbers so that conflating them fails here, loudly, with a reason.
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
            separation > 40.0
        )

        // the captured line sits in the pit lane, off the surveyed centreline; the
        // catalogued one is on it. That is the difference that matters, and it is not
        // visible from the coordinates alone.
        val centreline = track().centreline!!.points
        fun distanceToCentreline(lat: Double, lng: Double) = centreline.minOf {
            GeoUtils.haversineDistance(lat, lng, it.latitude, it.longitude)
        }

        val cataloguedOffset = distanceToCentreline(
            (catalogued.lat1 + catalogued.lat2) / 2, (catalogued.lng1 + catalogued.lng2) / 2
        )
        val capturedOffset = distanceToCentreline(
            (captured.lat1 + captured.lat2) / 2, (captured.lng1 + captured.lng2) / 2
        )

        assertTrue(
            "the catalogued line should sit on the circuit, measured " +
                "${"%.1f".format(cataloguedOffset)} m from the centreline",
            cataloguedOffset < 2.0
        )
        assertTrue(
            "the driver's line should sit off it, in the pit lane, measured " +
                "${"%.1f".format(capturedOffset)} m from the centreline",
            capturedOffset > 5.0
        )
    }

    // --- Measurement helpers -------------------------------------------------------------

    /**
     * Distance covered between consecutive crossings of the catalogued line, in metres.
     *
     * Crossings come from the detector rather than from geometry re-implemented here. A
     * hand-rolled segment test gets this wrong: during the work that added this circuit,
     * one counted the karts as crossing 2.8-6.5 m to the side of a 7.01 m line and
     * concluded seven laps in ten were being missed. They were not. The detector never
     * uses the line's width - [LocalLapDetector.DETECTION_HALF_WIDTH_M] replaces it - so
     * the conclusion was an artefact of the re-implementation. Asking the detector is the
     * only way to measure what the detector does.
     */
    private fun lapDistancesBetweenCrossings(): List<Double> {
        val fixtureFile = fixture().telemetryFile
        val outcome = LocalLapDetector().detectLapsWithDiagnostics(
            fixtureFile,
            cataloguedStartLine(),
            track().priors()
        )
        val crossings = outcome.diagnostics!!.acceptedCrossings.map { it.timestampMs }.sorted()
        val samples = readSamples(fixtureFile)

        return crossings.zipWithNext()
            .filter { (from, to) -> to - from in 30_000..120_000 }
            .map { (from, to) -> pathLengthM(samples, from, to) }
    }

    /** The lap times the app itself recorded on the day, from the unmodified export. */
    private fun lapSecondsRecordedByTheApp(): List<Double> {
        val json = requireNotNull(
            javaClass.getResourceAsStream("/lapfixtures/cabo_do_mundo/session.json")
        ) { "fixture metadata missing" }.bufferedReader().use { it.readText() }

        val laps = org.json.JSONObject(json).getJSONArray("laps")
        return (0 until laps.length()).map {
            laps.getJSONObject(it).getLong("durationMs") / 1000.0
        }
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
