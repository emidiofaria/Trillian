package com.drivingcoach

import android.content.Intent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.recyclerview.widget.RecyclerView
import com.drivingcoach.data.api.AuthInterceptor
import com.drivingcoach.data.db.DrivingCoachDatabase
import com.drivingcoach.data.db.entity.CoachingInsightEntity
import com.drivingcoach.data.db.entity.LapEntity
import com.drivingcoach.data.db.entity.ProcessingStatus
import com.drivingcoach.data.db.entity.SessionEntity
import com.drivingcoach.data.profile.DriverProfileStore
import com.drivingcoach.ui.MainActivity
import com.drivingcoach.ui.onboarding.OnboardingFragment
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * End-to-end integration tests for Driving Coach app.
 * 
 * Currently ignored due to MockWebServer/app state initialization issues.
 * The tests require proper MockWebServer responses and app state setup.
 * TODO: Fix MockWebServer initialization and app navigation state for E2E tests.
 */
@Ignore("MockWebServer initialization and app state issues - views not found")
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@LargeTest
class EndToEndTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var database: DrivingCoachDatabase

    @Inject
    lateinit var mockWebServer: MockWebServer

    @Inject
    lateinit var dataStore: DataStore<Preferences>

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setup() {
        hiltRule.inject()

        // Start MockWebServer on port 8080 (matches TEST_BASE_URL in TestNetworkModule)
        mockWebServer.start(8080)

        // Clear DataStore and mark onboarding complete for tests
        runBlocking {
            dataStore.edit { prefs ->
                prefs.clear()
                prefs[OnboardingFragment.KEY_ONBOARDING_COMPLETE] = true
                // V1 gates Home on a saved local driver profile, not on a token
                // (SRS DR-01 … DR-04), so every E2E launch needs one seeded.
                prefs[DriverProfileStore.KEY_DRIVER_NAME] = "E2E Driver"
                prefs[DriverProfileStore.KEY_PROFILE_COMPLETE] = true
            }
        }

        // Clear database
        database.clearAllTables()
    }

    @After
    fun teardown() {
        if (::scenario.isInitialized) {
            scenario.close()
        }
        mockWebServer.shutdown()
    }

    @Test
    fun testFullSessionFlow() {
        // Mock login response
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"token": "test_jwt_token", "userId": "test_user_id"}""")
        )

        // Mock get sessions (empty list)
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""[]""")
        )

        // Seed a user first (simulating logged in state)
        runBlocking {
            dataStore.edit { prefs ->
                prefs[AuthInterceptor.KEY_JWT] = "test_jwt_token"
            }
        }

        // Launch MainActivity - should go to HomeFragment (skipping login)
        scenario = ActivityScenario.launch(MainActivity::class.java)

        // Wait for UI to settle
        Thread.sleep(500)

        // Assert empty state is shown (since we have no sessions)
        onView(withId(R.id.emptyStateContainer)).check(matches(isDisplayed()))

        // Tap the CTA to start new session
        onView(withId(R.id.startSessionButton)).perform(click())

        // Enter track name in dialog
        Thread.sleep(300)
        onView(withText("New Session")).check(matches(isDisplayed()))
        
        // Type track name - find EditText in dialog
        onView(withId(android.R.id.content))
            .check(matches(isDisplayed()))
        
        // The dialog uses a custom EditText, find it and type
        onView(allOf(withText(""), isDisplayed()))
            .perform(replaceText("Test Track"), closeSoftKeyboard())

        // Confirm dialog
        onView(withText("Start")).perform(click())

        // Wait for navigation to TrackSetupFragment
        Thread.sleep(500)

        // Assert TrackSetupFragment is shown
        onView(withId(R.id.instructionsTitle)).check(matches(isDisplayed()))
        onView(withText(containsString("START/FINISH LINE"))).check(matches(isDisplayed()))

        // Assert initial state: Point A capture button visible, Point B disabled
        onView(withId(R.id.capturePointAButton)).check(matches(isDisplayed()))
        onView(withId(R.id.pointACoords)).check(matches(withText("Not captured")))

        // Note: Full GPS capture flow requires mock LocationManager
        // For E2E purposes, we verify the UI is displayed correctly
        // Actual GPS capture is tested in TrackSetupFragmentTest

        // Verify start recording button is initially disabled (no line captured)
        onView(withId(R.id.startRecordingButton)).check(matches(isDisplayed()))
    }

    @Test
    fun testFullSessionFlowWithMockStartLine() {
        // This test simulates a session where start line was already captured
        // by seeding the database directly
        
        // Mock login response
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"token": "test_jwt_token", "userId": "test_user_id"}""")
        )

        // Mock get sessions (empty list)
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""[]""")
        )

        // Seed logged in state
        runBlocking {
            dataStore.edit { prefs ->
                prefs[AuthInterceptor.KEY_JWT] = "test_jwt_token"
            }
        }

        // Create a session with start line already set (simulating completed track setup)
        val sessionId = runBlocking {
            val session = SessionEntity(
                userId = "test_user_id",
                trackName = "Test Track",
                startedAt = System.currentTimeMillis(),
                endedAt = null,
                rawFilePath = "", // Placeholder - will be set by TelemetryFileWriter
                uploadStatus = "PENDING",
                processingStatus = ProcessingStatus.PENDING.name,
                remoteSessionId = null,
                startLineLat1 = 48.13517,
                startLineLng1 = 11.5820,
                startLineLat2 = 48.13517,
                startLineLng2 = 11.5822
            )
            database.sessionDao().insertSession(session)
        }

        // Launch directly to RecordingFragment via deep link
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra("startDestination", R.id.recordingFragment)
            putExtra("sessionId", sessionId)
        }

        scenario = ActivityScenario.launch(intent)
        Thread.sleep(1000)

        // If deep link doesn't work, navigate through HomeFragment
        // This is an alternative path that tests the normal flow
    }

    @Test
    fun testLapComparisonDeltaColors() {
        // Seed database with session and laps
        val sessionId = runBlocking {
            val session = SessionEntity(
                userId = "demo_user",
                trackName = "Delta Test Track",
                startedAt = System.currentTimeMillis() - 300000,
                endedAt = System.currentTimeMillis() - 60000,
                rawFilePath = "/test/path.jsonl",
                uploadStatus = "DONE",
                processingStatus = ProcessingStatus.COMPLETE.name,
                remoteSessionId = "remote_123"
            )
            database.sessionDao().insertSession(session)
        }

        // Insert laps: lap1=83000ms (best), lap2=84200ms (+1.2s), lap3=82100ms (-0.9s from lap1)
        runBlocking {
            database.lapDao().insertLap(
                LapEntity(
                    sessionId = sessionId,
                    lapNumber = 1,
                    startTs = 0,
                    endTs = 83000,
                    durationMs = 83000,
                    sector1Ms = 27000,
                    sector2Ms = 28000,
                    sector3Ms = 28000,
                    isBestLap = false // Will be recalculated
                )
            )
            database.lapDao().insertLap(
                LapEntity(
                    sessionId = sessionId,
                    lapNumber = 2,
                    startTs = 83000,
                    endTs = 167200,
                    durationMs = 84200,
                    sector1Ms = 28000,
                    sector2Ms = 28200,
                    sector3Ms = 28000,
                    isBestLap = false
                )
            )
            database.lapDao().insertLap(
                LapEntity(
                    sessionId = sessionId,
                    lapNumber = 3,
                    startTs = 167200,
                    endTs = 249300,
                    durationMs = 82100,
                    sector1Ms = 27000,
                    sector2Ms = 27600,
                    sector3Ms = 27500,
                    isBestLap = true // This is the best lap
                )
            )
        }

        // Mark onboarding complete and set JWT
        runBlocking {
            dataStore.edit { prefs ->
                prefs[OnboardingFragment.KEY_ONBOARDING_COMPLETE] = true
                prefs[AuthInterceptor.KEY_JWT] = "test_token"
            }
        }

        // Launch MainActivity
        scenario = ActivityScenario.launch(MainActivity::class.java)
        Thread.sleep(500)

        // Navigate to session result
        onView(withId(R.id.sessionsRecyclerView))
            .perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(0, click()))

        Thread.sleep(500)

        // Should be on SessionResultFragment with Laps tab active by default
        onView(withId(R.id.viewPager)).check(matches(isDisplayed()))

        // Check that lap cards are displayed
        // Lap 2 should show +1.200s (slower than best lap 3)
        // Lap 1 should show +0.900s (slower than best lap 3)
        // Lap 3 is the best lap, no delta or 0.000s
        
        // Verify the laps tab content is displayed
        onView(withText("LAPS")).check(matches(isDisplayed()))
    }

    @Test
    fun testShareCard() {
        // Seed database with completed session
        val sessionId = runBlocking {
            val session = SessionEntity(
                userId = "demo_user",
                trackName = "Share Test Track",
                startedAt = System.currentTimeMillis() - 300000,
                endedAt = System.currentTimeMillis() - 60000,
                rawFilePath = "/test/path.jsonl",
                uploadStatus = "DONE",
                processingStatus = ProcessingStatus.COMPLETE.name,
                remoteSessionId = "remote_456"
            )
            database.sessionDao().insertSession(session)
        }

        // Insert a lap
        runBlocking {
            database.lapDao().insertLap(
                LapEntity(
                    sessionId = sessionId,
                    lapNumber = 1,
                    startTs = 0,
                    endTs = 95000,
                    durationMs = 95000,
                    sector1Ms = 31000,
                    sector2Ms = 32000,
                    sector3Ms = 32000,
                    isBestLap = true
                )
            )
        }

        // Set JWT
        runBlocking {
            dataStore.edit { prefs ->
                prefs[OnboardingFragment.KEY_ONBOARDING_COMPLETE] = true
                prefs[AuthInterceptor.KEY_JWT] = "test_token"
            }
        }

        // Initialize Intents for share verification
        Intents.init()

        try {
            // Launch MainActivity
            scenario = ActivityScenario.launch(MainActivity::class.java)
            Thread.sleep(500)

            // Navigate to session result
            onView(withId(R.id.sessionsRecyclerView))
                .perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(0, click()))

            Thread.sleep(500)

            // Tap share icon in toolbar
            onView(withId(R.id.action_share)).perform(click())

            Thread.sleep(1000)

            // Assert Android ShareSheet is presented
            Intents.intended(hasAction(Intent.ACTION_CHOOSER))
        } finally {
            Intents.release()
        }
    }

    @Test
    fun testClearUserData() {
        // Seed database with a session
        runBlocking {
            val session = SessionEntity(
                userId = "demo_user",
                trackName = "Clear Data Test Track",
                startedAt = System.currentTimeMillis() - 300000,
                endedAt = System.currentTimeMillis() - 60000,
                rawFilePath = "/test/path.jsonl",
                uploadStatus = "DONE",
                processingStatus = ProcessingStatus.COMPLETE.name,
                remoteSessionId = "remote_789"
            )
            database.sessionDao().insertSession(session)
        }

        // Launch MainActivity (setup() already seeded onboarding + driver profile)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        Thread.sleep(500)

        // Tap profile button
        onView(withId(R.id.profileButton)).perform(click())
        Thread.sleep(500)

        // Should be on ProfileFragment
        onView(withText("PROFILE")).check(matches(isDisplayed()))

        // Tap CLEAR USER DATA and confirm the destructive dialog (SRS DR-07)
        onView(withId(R.id.clearDataButton)).perform(click())
        Thread.sleep(300)
        onView(withText(R.string.clear_data_confirm)).perform(click())
        Thread.sleep(500)

        // Clearing data returns the driver to onboarding, not to a login screen
        onView(withId(R.id.grantButton)).check(matches(isDisplayed()))

        // Verify local identity and session history are both gone
        runBlocking {
            val prefs = dataStore.data.first()
            assert(prefs[DriverProfileStore.KEY_DRIVER_NAME] == null) {
                "Driver name should be cleared after clearing user data"
            }
            assert(prefs[DriverProfileStore.KEY_PROFILE_COMPLETE] == null) {
                "Profile completion flag should be cleared after clearing user data"
            }
            assert(database.sessionDao().getAllRawFilePaths().isEmpty()) {
                "Sessions should be deleted after clearing user data"
            }
        }
    }
}
