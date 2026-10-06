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
 * L2 (ASPICE SWE.5) coverage for Test Circuit S.Mamede, the third circuit in the catalogue.
 *
 * The L1 suite replays this fixture on the JVM. This drives it through the production path -
 * session row, repository lookup, service, detector and diagnostics sidecar - and asserts the
 * priors that arrive are S.Mamede's own rather than another circuit's.
 *
 * ## Why `headingReference` is the assertion that matters here
 *
 * On this circuit a missing or reversed heading does not lose laps: nothing else passes near
 * the start line, so the detector falls back to reading the direction off the session and
 * still finds all three. A lap count therefore cannot tell whether the catalogue heading
 * arrived. The sidecar's `headingReference` and `referenceHeadingDeg` can.
 *
 * Detection runs against the **catalogued** line, never the one the fixture's `session.json`
 * carries (the driver's own captured line, 5.4 m away on the same straight).
 */
@LargeTest
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SMamedePriorsEndToEndTest {

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
     * See [CaboDoMundoPriorsEndToEndTest] for the full account.
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

    @Test
    fun theLookupReturnsSMamedeAndTheEarlierCircuitsRemain() {
        val sMamede = runBlocking { trackRepository.getTrack(ID) }
        assertNotNull("S.Mamede should be installed on the device", sMamede)
        assertEquals("Test Circuit S.Mamede", sMamede!!.name)
        assertEquals(284.7, sMamede.travelHeadingDeg!!, 0.01)
        assertEquals(802, sMamede.lengthM)

        val bundled = runBlocking { trackRepository.observeTracks().first() }
            .filter { it.isBundled }
            .map { it.id }
        assertTrue(
            "the list the driver picks from should offer all three circuits, got $bundled",
            bundled.containsAll(listOf("baltar", "cabo_do_mundo", ID))
        )
    }

    @Test
    fun theCataloguedCircuitInformsDetectionAndTheThreeLapsAreStored() {
        val sMamede = runBlocking { trackRepository.getTrack(ID)!! }

        val sessionId = runBlocking {
            sessionRepository.createSession(
                SessionEntity(
                    userId = "default_user",
                    trackName = "S.Mamede Replay",
                    startedAt = System.currentTimeMillis(),
                    rawFilePath = telemetry.absolutePath,
                    trackId = sMamede.id,
                    startLineLat1 = sMamede.startLine.lat1,
                    startLineLng1 = sMamede.startLine.lng1,
                    startLineLat2 = sMamede.startLine.lat2,
                    startLineLng2 = sMamede.startLine.lng2
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

        assertEquals("the three flying laps should be stored. Detected: $seconds", 3, laps.size)
        assertTrue(
            "every lap should match what the same fixture measures on the JVM, 76.3-79.9 s. " +
                "Detected: $seconds",
            seconds.all { it > 75.0 && it < 81.0 }
        )

        val diagnostics = JSONObject(
            lapDiagnosticsWriter.sidecarFor(telemetry).readText()
        ).getJSONObject("diagnostics")

        assertEquals(
            "the reference direction must come from the catalogue. FIRST_CROSSING here would " +
                "still yield three laps, which is exactly why it has to be asserted",
            "TRACK_CATALOGUE", diagnostics.getString("headingReference")
        )
        assertEquals(
            "and it must be S.Mamede's heading, not 137.8 (Baltar) or 59.8 (Cabo do Mundo)",
            284.7, diagnostics.getDouble("referenceHeadingDeg"), 0.01
        )
        assertEquals(
            "the catalogued fastest lap of 50 s should tighten the crossing gap to 40 s (LD-21)",
            40_000L, diagnostics.getLong("minLapTimeMs")
        )
        assertEquals(3, diagnostics.getInt("lapCount"))
    }

    private fun copyFixtureOutOfAssets(application: Application): File {
        val destination = File(application.cacheDir, "s-mamede-replay.jsonl")
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .context.assets.open("s_mamede/telemetry.jsonl").use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
        assertTrue("the shared L1 fixture should have been packaged", destination.length() > 0)
        return destination
    }

    private companion object {
        const val ID = "test_circuit_s_mamede"
    }
}
