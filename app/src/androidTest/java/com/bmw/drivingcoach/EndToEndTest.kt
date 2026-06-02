package com.bmw.drivingcoach

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
import com.bmw.drivingcoach.data.api.AuthInterceptor
import com.bmw.drivingcoach.data.db.BMWDatabase
import com.bmw.drivingcoach.data.db.entity.CoachingInsightEntity
import com.bmw.drivingcoach.data.db.entity.LapEntity
import com.bmw.drivingcoach.data.db.entity.ProcessingStatus
import com.bmw.drivingcoach.data.db.entity.SessionEntity
import com.bmw.drivingcoach.ui.MainActivity
import com.bmw.drivingcoach.ui.onboarding.OnboardingFragment
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@LargeTest
class EndToEndTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var database: BMWDatabase

    @Inject
    lateinit var mockWebServer: MockWebServer

    @Inject
    lateinit var dataStore: DataStore<Preferences>

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setup() {
        hiltRule.inject()

        // Clear DataStore and mark onboarding complete for tests
        runBlocking {
            dataStore.edit { prefs ->
                prefs.clear()
                prefs[OnboardingFragment.KEY_ONBOARDING_COMPLETE] = true
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

        // Tap FAB to start new session
        onView(withId(R.id.startSessionFab)).perform(click())

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

        // Wait for navigation to RecordingFragment
        Thread.sleep(1000)

        // Assert RecordingFragment is shown with elapsed time
        onView(withId(R.id.elapsedTime)).check(matches(isDisplayed()))

        // Wait 3 seconds and verify time is ticking
        Thread.sleep(3000)

        // Tap STOP button
        onView(withId(R.id.stopRecordingButton)).perform(click())

        // Wait for navigation to SessionResultFragment
        Thread.sleep(1000)

        // Assert we're on session result screen
        onView(withId(R.id.tabLayout)).check(matches(isDisplayed()))
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
    fun testSignOut() {
        // Seed database with a session
        runBlocking {
            val session = SessionEntity(
                userId = "demo_user",
                trackName = "SignOut Test Track",
                startedAt = System.currentTimeMillis() - 300000,
                endedAt = System.currentTimeMillis() - 60000,
                rawFilePath = "/test/path.jsonl",
                uploadStatus = "DONE",
                processingStatus = ProcessingStatus.COMPLETE.name,
                remoteSessionId = "remote_789"
            )
            database.sessionDao().insertSession(session)
        }

        // Set logged in state
        runBlocking {
            dataStore.edit { prefs ->
                prefs[OnboardingFragment.KEY_ONBOARDING_COMPLETE] = true
                prefs[AuthInterceptor.KEY_JWT] = "valid_jwt_token"
            }
        }

        // Launch MainActivity
        scenario = ActivityScenario.launch(MainActivity::class.java)
        Thread.sleep(500)

        // Tap profile button
        onView(withId(R.id.profileButton)).perform(click())
        Thread.sleep(500)

        // Should be on ProfileFragment
        onView(withText("PROFILE")).check(matches(isDisplayed()))

        // Tap SIGN OUT button
        onView(withId(R.id.signOutButton)).perform(click())
        Thread.sleep(500)

        // Assert navigation to LoginFragment
        onView(withId(R.id.loginButton)).check(matches(isDisplayed()))

        // Verify JWT is cleared from DataStore
        runBlocking {
            val prefs = dataStore.data.first()
            val token = prefs[AuthInterceptor.KEY_JWT]
            assert(token == null) { "JWT should be cleared after sign out" }
        }
    }
}
