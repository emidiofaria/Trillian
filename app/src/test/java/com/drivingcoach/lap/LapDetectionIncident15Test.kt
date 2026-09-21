package com.drivingcoach.lap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The session from incident 15, replayed.
 *
 * Fixture `baltar2` is a real kart session at Kartódromo de Baltar: the driver
 * started recording, put the phone in a pocket, queued sixteen minutes for a
 * kart, then drove twelve laps of 72-85 s. The app reported **two** laps, of
 * 15m54s and 6m44s.
 *
 * The queue stood beside the start straight, so the first segment of the session
 * - a 2.9 m/s walking drift across the start point - was accepted as a crossing.
 * Being the first accepted crossing it also set the session's reference heading,
 * at 272 degrees. The twelve racing crossings that followed arrived between 331
 * and 343 degrees: ten were rejected as heading mismatches, and the two that
 * squeaked past the 60 degree guard, at 54.3 and 59.6 degrees, produced the two
 * nonsense laps the driver was shown.
 *
 * Two independent defences are asserted here, because the app must survive this
 * on a circuit it has never heard of as well as on one it knows:
 *
 *  - [theTwelveLapsAreDetectedFromTheLineTheDriverCaptured] - the speed gate
 *    alone, with no catalogue entry, recovers all twelve laps.
 *  - [theTwelveLapsAreDetectedFromTheCataloguedStartLine] - the catalogue's
 *    surveyed line and travel heading recover them too, independently.
 */
class LapDetectionIncident15Test {

    @get:Rule
    val tempFolder = TemporaryFolder()

    /**
     * The start/finish as surveyed on foot, 164.6 m from the point the app
     * captured. Both describe Baltar; only this one is on the start straight.
     */
    private val surveyedStartLine = LocalLapDetector.StartLine(
        lat1 = 41.187838, lng1 = -8.395666,
        lat2 = 41.187770, lng2 = -8.395761
    )

    /** Baltar as the catalogue describes it: driven C to D at 137.8 degrees, 40-120 s, 1020 m. */
    private val baltarPriors = LocalLapDetector.TrackPriors(
        travelHeadingDeg = 137.8,
        fastestLapMs = 40_000L,
        slowestLapMs = 120_000L,
        lengthM = 1020
    )

    private fun fixture() = LapReplayHarness.load("baltar2", tempFolder.newFolder())

    @Test
    fun theFixtureIsTheSessionFromIncident15() {
        val fixture = fixture()

        assertEquals("Baltar 2", fixture.trackName)
        assertEquals("2.97", fixture.appVersionName)
        assertEquals("ZTE ZTE Blade A53+", fixture.deviceModel)
        assertEquals(
            "the app recorded two laps for a twelve lap session - that is the incident",
            2, fixture.lapsRecordedByApp
        )
    }

    @Test
    fun theTwelveLapsAreDetectedFromTheLineTheDriverCaptured() {
        val result = LapReplayHarness.detect(fixture())

        assertTrue(
            "expected the driver's twelve laps, got ${LapReplayHarness.describe(result)}",
            result is LocalLapDetector.DetectionResult.Success
        )
        val laps = LapReplayHarness.lapSeconds(result)
        assertEquals("twelve laps were driven", 12, laps.size)
        assertTrue(
            "every lap should be a kart lap, not a queue: $laps",
            laps.all { it in 60.0..95.0 }
        )
    }

    @Test
    fun theTwelveLapsAreDetectedFromTheCataloguedStartLine() {
        val result = LapReplayHarness.detect(
            fixture(), priors = baltarPriors, startLine = surveyedStartLine
        )

        val laps = LapReplayHarness.lapSeconds(result)
        assertEquals(
            "expected twelve laps, got ${LapReplayHarness.describe(result)}", 12, laps.size
        )
        assertEquals("fastest lap of the session", 72.5, laps.min(), 0.5)
        assertEquals("slowest lap of the session", 84.3, laps.max(), 0.5)
    }

    /**
     * The specific mechanism, named. Without the speed gate the opening pass is
     * accepted and the session's reference heading is set by a pedestrian.
     */
    @Test
    fun theWalkingPassAcrossTheStartPointIsRejectedAsTooSlow() {
        val fixture = fixture()
        val outcome = LocalLapDetector().detectLapsWithDiagnostics(
            fixture.telemetryFile, fixture.startLine
        )
        val diagnostics = assertNotNull(outcome.diagnostics).let { outcome.diagnostics!! }

        val tooSlow = diagnostics.rejectedCrossings
            .filter { it.reason == LocalLapDetector.RejectionReason.TOO_SLOW }
        assertTrue(
            "the queue pass across the start point should be recorded, not dropped in silence",
            tooSlow.isNotEmpty()
        )

        val firstCrossing = diagnostics.acceptedCrossings.firstOrNull()
        assertNotNull("the session should still open with a real crossing", firstCrossing)
        assertTrue(
            "the first accepted crossing must be a driving one, was ${firstCrossing!!.headingDeg}",
            firstCrossing.headingDeg in 300.0..360.0
        )
    }

    /**
     * The circuit's own heading guards the opening crossing, which a heading read
     * from that crossing cannot do.
     */
    @Test
    fun theCatalogueHeadingIsUsedAsTheReference() {
        val fixture = fixture()
        val outcome = LocalLapDetector().detectLapsWithDiagnostics(
            fixture.telemetryFile, surveyedStartLine, baltarPriors
        )
        val diagnostics = outcome.diagnostics!!

        assertEquals(
            LocalLapDetector.HeadingReference.TRACK_CATALOGUE, diagnostics.headingReference
        )
        assertEquals(137.8, diagnostics.referenceHeadingDeg!!, 0.001)
    }

    /**
     * A catalogue entry that does not describe the session must not be able to
     * suppress it. Driven the other way round, detection falls back to reading the
     * reference off the session itself.
     */
    @Test
    fun aWrongCatalogueHeadingFallsBackToTheSession() {
        val reversed = baltarPriors.copy(travelHeadingDeg = 317.8)
        val result = LapReplayHarness.detect(
            fixture(), priors = reversed, startLine = surveyedStartLine
        )

        assertEquals(
            "a reversed catalogue heading must not cost the driver their session, got " +
                LapReplayHarness.describe(result),
            12, LapReplayHarness.lapSeconds(result).size
        )
    }

    /**
     * The laps the app actually showed - 954 s and 404 s - average 1.07 and
     * 2.52 m/s over Baltar's surveyed 1020 m. Nothing on a circuit moves that
     * slowly for a whole lap. Reporting them was worse than reporting nothing,
     * because they looked like an answer.
     */
    @Test
    fun lapsImpossibleForTheCircuitAreNotReported() {
        val impossible = listOf(
            LocalLapDetector.DetectedLap(1, 0L, 954_363L, 954_363L),
            LocalLapDetector.DetectedLap(2, 954_363L, 1_358_870L, 404_507L)
        )

        assertTrue(
            "with no catalogue entry there is nothing to measure against",
            LocalLapDetector.TrackPriors.NONE.lapsArePlausible(impossible)
        )
        assertTrue(
            "these are the two laps the driver was shown",
            !baltarPriors.lapsArePlausible(impossible)
        )
    }

    /**
     * The guard must not delete a slow driver's session.
     *
     * Baltar's declared envelope tops out at 120 s. A timid weekend driver lapping
     * in 150 s is outside it and is still driving: 6.8 m/s over 1020 m. An earlier
     * version of this rule measured against the declared envelope and would have
     * thrown all four of these laps away, showing that driver nothing at all.
     */
    @Test
    fun aDriverSlowerThanTheCircuitsDeclaredEnvelopeStillGetsTheirLaps() {
        val slow = listOf(
            LocalLapDetector.DetectedLap(1, 0L, 150_000L, 150_000L),
            LocalLapDetector.DetectedLap(2, 150_000L, 302_000L, 152_000L),
            LocalLapDetector.DetectedLap(3, 302_000L, 450_000L, 148_000L),
            LocalLapDetector.DetectedLap(4, 450_000L, 608_000L, 158_000L)
        )

        assertTrue(
            "150 s at Baltar is 6.8 m/s - slow, but unmistakably driving",
            baltarPriors.lapsArePlausible(slow)
        )
        assertTrue(
            "every one of these laps is beyond the declared 120 s upper bound",
            slow.all { it.durationMs > baltarPriors.slowestLapMs!! }
        )
    }

    /**
     * One long lap among normal ones is a real lap - a spin, an off, a slow kart
     * ahead - and must survive. The set is only discarded when nothing in it
     * could have been driven, which is the shape incident 15 produced.
     */
    @Test
    fun aSingleSpinLapDoesNotDiscardTheSessionAroundIt() {
        val withSpin = listOf(
            LocalLapDetector.DetectedLap(1, 0L, 75_000L, 75_000L),
            LocalLapDetector.DetectedLap(2, 75_000L, 375_000L, 300_000L),
            LocalLapDetector.DetectedLap(3, 375_000L, 448_000L, 73_000L)
        )

        assertTrue(
            "the 300 s lap alone averages 3.4 m/s, but the session around it is real",
            baltarPriors.lapsArePlausible(withSpin)
        )
    }

    /**
     * The guard is derived from the surveyed lap length, not from the declared
     * envelope, because the length was measured and the envelope was typed in.
     * A circuit with no length carries no such guard.
     */
    @Test
    fun withoutASurveyedLengthNothingIsDiscarded() {
        val impossible = listOf(
            LocalLapDetector.DetectedLap(1, 0L, 954_363L, 954_363L)
        )

        assertTrue(
            "an envelope on its own must not be enough to erase a session",
            LocalLapDetector.TrackPriors(fastestLapMs = 40_000L, slowestLapMs = 120_000L)
                .lapsArePlausible(impossible)
        )
        assertTrue(
            "the surveyed length is what makes the judgement possible",
            !LocalLapDetector.TrackPriors(lengthM = 1020).lapsArePlausible(impossible)
        )
    }

    @Test
    fun theCircuitsOwnLapTimeTightensTheMinimumGapBetweenCrossings() {
        assertEquals(
            "20 s generic guard applies when the circuit is unknown",
            LocalLapDetector.MIN_LAP_TIME_MS,
            LocalLapDetector.TrackPriors.NONE.minLapTimeMs()
        )
        assertEquals(
            "40 s known best lap, less the 20% grace that protects a quicker driver",
            32_000L, baltarPriors.minLapTimeMs()
        )
        assertTrue(
            "the guard must stay under the quickest lap the circuit is known to produce",
            baltarPriors.minLapTimeMs() < baltarPriors.fastestLapMs!!
        )
    }
}
