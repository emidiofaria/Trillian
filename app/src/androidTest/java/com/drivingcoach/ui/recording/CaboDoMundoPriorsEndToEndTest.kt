package com.drivingcoach.ui.recording

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.GrantPermissionRule
import com.drivingcoach.data.db.dao.CoachingInsightDao
import com.drivingcoach.data.db.dao.LapDao
import com.drivingcoach.data.db.entity.SessionEntity
import com.drivingcoach.data.repository.SessionRepository
import com.drivingcoach.data.track.TrackRepository
import com.drivingcoach.lap.LapDiagnosticsWriter
import com.drivingcoach.lap.LocalLapDetector
import com.drivingcoach.service.TelemetryForegroundService
import com.drivingcoach.testing.awaitUntil
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import javax.inject.Inject

/**
 * L2 (ASPICE SWE.5) coverage for the second circuit in the catalogue.
 *
 * [TrackPriorsEndToEndTest] proves the app closes the loop for Baltar. That was worth
 * proving once, but while the catalogue held a single entry it could not distinguish
 * "the app looks up the circuit on the session" from "the app uses the only circuit it
 * has". Adding Cabo do Mundo is the first time the lookup can be wrong, and a lookup
 * that returns the wrong circuit does not fail - it detects laps against another
 * track's heading and length and reports a plausible-looking set of times.
 *
 * So this is the same end-to-end path as Baltar's, driven with the other circuit's
 * telemetry, asserting the other circuit's numbers. Between them the two tests pin the
 * lookup: neither can pass on the other's data.
 *
 * ## Why the numbers here are worth asserting on a device
 *
 * The L1 suite already replays this fixture on the JVM. What it cannot show is that
 * the priors survive the production path - the session row, the repository lookup, the
 * service, the detector and the diagnostics sidecar. On this circuit that path carries
 * something Baltar's does not: the direction prior is the only thing preventing a
 * silent zero-lap result, because the circuit's return section passes 18.4 m from the
 * start point against a 15 m detection corridor. If `travelHeadingDeg` fails to arrive,
 * the detector falls back to the first crossing and the failure is invisible.
 *
 * ## The start line
 *
 * Detection runs against the **catalogued** line, resolved through [TrackRepository]
 * exactly as the app resolves it. The fixture's own `session.json` carries a different
 * line - the driver marked it in the pit lane, 7 m off the racing line and 55 m short
 * of the real start/finish - and it is deliberately never used here.
 */
@LargeTest
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CaboDoMundoPriorsEndToEndTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    @Inject
    lateinit var sessionRepository: SessionRepository

    @Inject
    lateinit var trackRepository: TrackRepository

    @Inject
    lateinit var lapDao: LapDao

    @Inject
    lateinit var coachingInsightDao: CoachingInsightDao

    @Inject
    lateinit var localLapDetector: LocalLapDetector

    @Inject
    lateinit var lapDiagnosticsWriter: LapDiagnosticsWriter

    private lateinit var viewModel: RecordingViewModel
    private lateinit var telemetry: File

    /**
     * Whether a test in this class actually started the service.
     *
     * `TelemetryForegroundService.stopRecording` sends its command with `startService`, which
     * **creates** the service if it is not already running. Calling it unconditionally in
     * teardown therefore starts a service in order to stop it, and by that point the Hilt test
     * component has gone - so `onCreate` fails its injection and takes the whole test process
     * with it. That does not show up as a failed test; it shows up as the instrumentation run
     * ending early, with every class after this one silently unreported.
     *
     * [TrackPriorsEndToEndTest] never met this because it holds a single test that always
     * records. The moment a class has one test that does not, the teardown has to ask.
     */
    private var recordingStarted = false

    @Before
    fun setUp() {
        hiltRule.inject()
        val application = ApplicationProvider.getApplicationContext<Application>()
        viewModel = RecordingViewModel(
            application,
            sessionRepository,
            lapDao,
            coachingInsightDao,
            localLapDetector,
            lapDiagnosticsWriter,
            trackRepository
        )
        telemetry = copyFixtureOutOfAssets(application)
    }

    @After
    fun tearDown() {
        if (recordingStarted) {
            TelemetryForegroundService.stopRecording(
                ApplicationProvider.getApplicationContext<Application>()
            )
        }
        lapDiagnosticsWriter.sidecarFor(telemetry).delete()
        telemetry.delete()
    }

    /**
     * The catalogue now holds more than one circuit, so the app has to fetch the right
     * one. A wrong answer here is not an exception - it is Baltar's 137.8 degrees and
     * 1020 m applied to a different track.
     */
    @Test
    fun theCatalogueHoldsBothCircuitsAndTheLookupReturnsTheRequestedOne() {
        val caboDoMundo = runBlocking { trackRepository.getTrack("cabo_do_mundo") }
        assertNotNull("Cabo do Mundo should be installed on the device", caboDoMundo)
        assertEquals("Cabo do Mundo", caboDoMundo!!.name)
        assertEquals(59.8, caboDoMundo.travelHeadingDeg!!, 0.01)

        val baltar = runBlocking { trackRepository.getTrack("baltar") }
        assertNotNull("adding a circuit must not displace the one already shipped", baltar)
        assertEquals(137.8, baltar!!.travelHeadingDeg!!, 0.01)

        val bundled = runBlocking { trackRepository.observeTracks().first() }.filter { it.isBundled }
        assertEquals(
            "both circuits should be offered to the driver, got ${bundled.map { it.id }}",
            2, bundled.size
        )
    }

    @Test
    fun theCataloguedCircuitInformsDetectionAndTheEightLapsAreStored() {
        val caboDoMundo = runBlocking { trackRepository.getTrack("cabo_do_mundo")!! }

        val sessionId = runBlocking {
            sessionRepository.createSession(
                SessionEntity(
                    userId = "default_user",
                    trackName = "Cabo do Mundo Replay",
                    startedAt = System.currentTimeMillis(),
                    rawFilePath = telemetry.absolutePath,
                    trackId = caboDoMundo.id,
                    // the surveyed line, not the pit-lane one the fixture carries
                    startLineLat1 = caboDoMundo.startLine.lat1,
                    startLineLng1 = caboDoMundo.startLine.lng1,
                    startLineLat2 = caboDoMundo.startLine.lat2,
                    startLineLng2 = caboDoMundo.startLine.lng2
                )
            )
        }

        viewModel.startRecording(sessionId)
        recordingStarted = true
        viewModel.stopRecording()

        awaitUntil("lap processing to finish", timeoutMs = 60_000L) {
            viewModel.uiState.value.isReadyToNavigate
        }

        val laps = runBlocking { lapDao.getLapsForSession(sessionId).first() }
        val seconds = laps.map { it.durationMs / 1000.0 }.sorted()

        // The session holds ten timed laps. The out-lap runs to 167.1 s, past the LD-22
        // floor of 165.2 s for an 826 m circuit, and the in-lap ends in the pit lane -
        // both are correctly discarded. The eight flying laps are the session.
        assertEquals(
            "the eight flying laps should be stored. Detected: $seconds",
            8, laps.size
        )
        assertTrue(
            "every lap should match what the same fixture measures on the JVM, 63.4-70.8 s. " +
                "Detected: $seconds",
            seconds.all { it > 62.0 && it < 72.0 }
        )

        val diagnostics = JSONObject(
            lapDiagnosticsWriter.sidecarFor(telemetry).readText()
        ).getJSONObject("diagnostics")

        assertEquals(
            "the reference direction must come from the circuit. On this track the fallback " +
                "to the first crossing is not merely imprecise - the return section sits " +
                "inside the detection corridor, so the direction filter is what keeps the " +
                "lap count honest",
            "TRACK_CATALOGUE", diagnostics.getString("headingReference")
        )
        assertEquals(
            "and it must be Cabo do Mundo's surveyed heading, not the other circuit's 137.8 (LD-20)",
            59.8, diagnostics.getDouble("referenceHeadingDeg"), 0.01
        )
        assertEquals(
            "the catalogued fastest lap of 60 s should have tightened the crossing gap to 80% " +
                "of itself (LD-21). 20000 would mean the priors never arrived; 32000 would " +
                "mean Baltar's arrived instead",
            48_000L, diagnostics.getLong("minLapTimeMs")
        )
        assertEquals(8, diagnostics.getInt("lapCount"))
    }

    /**
     * The fixture ships in the test APK's assets, shared with the L1 suite through
     * `sourceSets` so both levels read the same bytes, and has to be a real file on
     * disk before the detector can read it.
     */
    private fun copyFixtureOutOfAssets(application: Application): File {
        val destination = File(application.cacheDir, "cabo-do-mundo-replay.jsonl")
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .context.assets.open("cabo_do_mundo/telemetry.jsonl").use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
        assertTrue("the shared L1 fixture should have been packaged", destination.length() > 0)
        return destination
    }
}
