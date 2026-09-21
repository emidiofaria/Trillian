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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import javax.inject.Inject

/**
 * L2 (ASPICE SWE.5) end-to-end coverage for the track library, and the anchor for
 * everything else asserted about it.
 *
 * `LapDetectionIncident15Test` proves on the JVM that the detector does the right
 * thing when it is handed Baltar's priors. `TrackPriorsHandoffTest` proves that
 * choosing Baltar writes the circuit onto the session. Neither proves that the app
 * closes the loop - that the circuit is found again after recording stops and the
 * priors actually reach the detector. Every failure in between is silent, because
 * a session that loses its priors still detects laps and still looks correct.
 *
 * So this drives the real telemetry from incident 15 through the real production
 * path, on a device, and asserts both halves of the outcome:
 *
 *  - the 12 laps the driver actually drove are stored, against the 2 they were shown
 *  - the diagnostics sidecar records that the catalogue is what informed detection
 *
 * The diagnostics are the better assertion of the two. They are a production
 * artifact written for whoever investigates the next incident, not a hook added for
 * testing, and they name the priors that arrived rather than the laps that came out.
 *
 * The fixture is the same file the L1 suite replays, shared through `sourceSets`
 * rather than copied, so the two levels cannot drift apart.
 */
@LargeTest
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class TrackPriorsEndToEndTest {

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
        TelemetryForegroundService.stopRecording(
            ApplicationProvider.getApplicationContext<Application>()
        )
        lapDiagnosticsWriter.sidecarFor(telemetry).delete()
        telemetry.delete()
    }

    @Test
    fun theCataloguedCircuitInformsDetectionAndTheTwelveLapsAreStored() {
        val baltar = runBlocking { trackRepository.getTrack("baltar")!! }

        // The session as it would stand the moment recording stops: the circuit
        // recorded against it, and the surveyed line copied onto it. Writing the row
        // here rather than recording one keeps this test to the second half of the
        // chain; the first half is TrackPriorsHandoffTest, and the session row is
        // the seam both of them assert against.
        val sessionId = runBlocking {
            sessionRepository.createSession(
                SessionEntity(
                    userId = "default_user",
                    trackName = "Incident 15 Replay",
                    startedAt = System.currentTimeMillis(),
                    rawFilePath = telemetry.absolutePath,
                    trackId = baltar.id,
                    startLineLat1 = baltar.startLine.lat1,
                    startLineLng1 = baltar.startLine.lng1,
                    startLineLat2 = baltar.startLine.lat2,
                    startLineLng2 = baltar.startLine.lng2
                )
            )
        }

        // The service records to a path of its own derived from the session id, so it
        // cannot disturb the fixture this session points at.
        viewModel.startRecording(sessionId)
        viewModel.stopRecording()

        awaitUntil("lap processing to finish", timeoutMs = 60_000L) {
            viewModel.uiState.value.isReadyToNavigate
        }

        val laps = runBlocking { lapDao.getLapsForSession(sessionId).first() }
        val seconds = laps.map { it.durationMs / 1000.0 }.sorted()

        assertEquals(
            "12 laps were driven. The app reported 2, of 954 s and 404 s, and that is " +
                "what incident 15 was. Detected: $seconds",
            12, laps.size
        )
        assertTrue(
            "every lap should fall inside the range the fixture was measured at on the " +
                "JVM, 72.5-84.3 s. Detected: $seconds",
            seconds.all { it > 70.0 && it < 86.0 }
        )

        // The other half of the claim, and the half that names a cause rather than a
        // symptom: detection was informed by the catalogue, not by the session.
        val diagnostics = JSONObject(
            lapDiagnosticsWriter.sidecarFor(telemetry).readText()
        ).getJSONObject("diagnostics")

        assertEquals(
            "the reference direction must come from the circuit. Taking it from the " +
                "first crossing is the signature of incident 15, and the fallback is " +
                "silent - it detects laps either way",
            "TRACK_CATALOGUE", diagnostics.getString("headingReference")
        )
        assertEquals(
            "and it must be the heading the catalogue actually declares (LD-20)",
            137.8, diagnostics.getDouble("referenceHeadingDeg"), 0.01
        )
        assertEquals(
            "the catalogued fastest lap of 40 s should have tightened the crossing gap " +
                "to 80% of itself (LD-21). 20000 here would mean the priors never arrived",
            32_000L, diagnostics.getLong("minLapTimeMs")
        )
        assertEquals(12, diagnostics.getInt("lapCount"))
    }

    /**
     * The fixture ships in the test APK's assets and has to be a real file on disk
     * before the detector can read it, because the production path takes a path from
     * the session row rather than a stream.
     */
    private fun copyFixtureOutOfAssets(application: Application): File {
        val destination = File(application.cacheDir, "baltar2-replay.jsonl")
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .context.assets.open("baltar2/telemetry.jsonl").use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
        assertTrue("the shared L1 fixture should have been packaged", destination.length() > 0)
        return destination
    }
}
