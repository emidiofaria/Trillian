package com.drivingcoach.ui.recording

import android.Manifest
import android.app.Application
import com.drivingcoach.data.db.dao.CoachingInsightDao
import com.drivingcoach.data.db.dao.LapDao
import com.drivingcoach.data.db.entity.SessionEntity
import com.drivingcoach.data.repository.SessionRepository
import com.drivingcoach.data.track.SaveTrackResult
import com.drivingcoach.data.track.TrackRepository
import com.drivingcoach.lap.LapDiagnosticsWriter
import com.drivingcoach.lap.LocalLapDetector
import com.drivingcoach.service.TelemetryForegroundService
import com.drivingcoach.testing.awaitUntil
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.GrantPermissionRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject
import kotlin.math.abs

/**
 * L2 (ASPICE SWE.5) coverage for the first half of the chain that carries a chosen
 * circuit to the lap detector: what gets written to the session row when recording
 * begins.
 *
 * The whole track library rests on one nullable column. `SessionEntity.trackId` is
 * written here and read back after recording stops, and the priors - the heading
 * that guards the first crossing (LD-20), the fastest lap that tightens the
 * crossing gap (LD-21), the surveyed length behind the plausibility rule (LD-22) -
 * are reachable only through it.
 *
 * Every way this can fail produces `TrackPriors.NONE`, and `NONE` is a valid,
 * successful outcome that still detects laps. A broken handoff therefore does not
 * throw, does not log an error and shows the driver nothing unusual: the library
 * just stops informing detection, and the session looks fine. That silence is the
 * reason this test exists.
 *
 * The second half - resolving those priors again after recording and handing them
 * to the detector - is covered by [TrackPriorsEndToEndTest]. The seam between the
 * two is the session row, and both sides assert against it.
 */
@LargeTest
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class TrackPriorsHandoffTest {

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

    @Before
    fun setUp() {
        hiltRule.inject()
        viewModel = RecordingViewModel(
            ApplicationProvider.getApplicationContext<Application>(),
            sessionRepository,
            lapDao,
            coachingInsightDao,
            localLapDetector,
            lapDiagnosticsWriter,
            trackRepository
        )
    }

    @After
    fun tearDown() {
        // Starting the recording service is the view model's last act. Nothing here
        // cares what it captures, but leaving it running would carry a GPS
        // subscription into the next test in this class.
        TelemetryForegroundService.stopRecording(
            ApplicationProvider.getApplicationContext<Application>()
        )
    }

    @Test
    fun theChosenCircuitIsRecordedOnTheSession() {
        val session = startSessionAndRead(trackId = "baltar")

        assertEquals(
            "without this column the catalogue can never be found again, and detection " +
                "falls back to knowing nothing about the circuit without saying so",
            "baltar", session.trackId
        )
        assertEquals("Thursday Club Night", session.trackName)
    }

    /**
     * Navigation arguments carry coordinates as 32-bit floats. Near latitude 41
     * degrees one of those is worth about 0.55 m - nothing beside a line captured on
     * a phone accurate to six metres, and pure loss on a line that was surveyed.
     *
     * The view model therefore ignores the coordinates it was handed once it knows
     * which circuit they describe, and reads the exact ones back from the catalogue
     * instead (TL-07). This passes a line that really has been through a float, so
     * the test would fail if the substitution were removed.
     */
    @Test
    fun theSurveyedLineReplacesTheQuantisedNavigationArgument() {
        val quantised = StartLineCoords(
            lat1 = 41.187838f.toDouble(),
            lng1 = (-8.395666f).toDouble(),
            lat2 = 41.187770f.toDouble(),
            lng2 = (-8.395761f).toDouble()
        )
        assertTrue(
            "this fixture only means something if the float round trip really did move " +
                "the line; otherwise the test would pass without the substitution",
            abs(quantised.lat1 - 41.187838) > 1e-9
        )

        val session = startSessionAndRead(trackId = "baltar", startLine = quantised)
        val surveyed = runBlocking { trackRepository.getTrack("baltar")!!.startLine }

        assertEquals(
            "the stored line should be the surveyed one, exactly",
            surveyed.lat1, session.startLineLat1!!, 1e-12
        )
        assertEquals(surveyed.lng1, session.startLineLng1!!, 1e-12)
        assertEquals(surveyed.lat2, session.startLineLat2!!, 1e-12)
        assertEquals(surveyed.lng2, session.startLineLng2!!, 1e-12)
    }

    /**
     * The uncatalogued path - every session the app recorded before the library
     * existed, which must still behave identically.
     */
    @Test
    fun aSessionWithNoCircuitKeepsTheLineItWasGivenAndStaysUncatalogued() {
        val session = startSessionAndRead(trackId = null, startLine = capturedLine())

        assertNull("no circuit was chosen, so there is nothing to record", session.trackId)
        assertEquals(
            "and the line the driver captured must be kept as given",
            41.186900, session.startLineLat1!!, 1e-12
        )
    }

    /**
     * An id naming no circuit must be treated as no circuit rather than as an error.
     * A stale identifier - a saved circuit deleted between the list and the start
     * button - should cost the priors, not the session.
     */
    @Test
    fun anUnknownCircuitIdDoesNotTakeTheSessionDownWithIt() {
        val session = startSessionAndRead(
            trackId = "user-deleted-yesterday",
            startLine = capturedLine()
        )

        assertNull("an id that resolves to nothing must be stored as nothing", session.trackId)
        assertEquals(
            "and the captured line must survive, so the session still detects laps",
            41.186900, session.startLineLat1!!, 1e-12
        )
    }

    /**
     * The library is ordered by where the driver actually drives, which only works
     * if starting a session is what updates that order.
     */
    @Test
    fun startingASessionOnASavedCircuitMarksItAsRecentlyUsed() {
        val id = runBlocking {
            val saved = trackRepository.saveCapturedTrack(
                name = "Paddock Loop",
                lat1 = 41.187838, lng1 = -8.395666,
                lat2 = 41.187770, lng2 = -8.395761
            )
            (saved as SaveTrackResult.Success).track.id
        }

        startSessionAndRead(trackId = id)

        awaitUntil("the circuit just driven to reach the top of the library") {
            runBlocking { trackRepository.observeTracks().first().first().id } == id
        }
    }

    private fun capturedLine() = StartLineCoords(
        lat1 = 41.186900, lng1 = -8.395500,
        lat2 = 41.186830, lng2 = -8.395600
    )

    /**
     * Starts a session through the production entry point and returns the row it
     * wrote. The view model creates the session inside its own scope, so the row is
     * awaited rather than assumed.
     */
    private fun startSessionAndRead(
        trackId: String?,
        startLine: StartLineCoords? = null
    ): SessionEntity {
        viewModel.createSessionAndStartRecording(
            trackName = "Thursday Club Night",
            startLine = startLine,
            trackId = trackId
        )

        awaitUntil("the session id to reach the view model") {
            viewModel.uiState.value.sessionId != -1L
        }

        val session = runBlocking {
            sessionRepository.getSessionByIdSync(viewModel.uiState.value.sessionId)
        }
        assertNotNull("the session the view model reports should be readable back", session)
        return session!!
    }
}
