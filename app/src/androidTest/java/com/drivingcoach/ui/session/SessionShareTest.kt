package com.drivingcoach.ui.session

import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import androidx.core.os.bundleOf
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra
import androidx.test.espresso.intent.matcher.IntentMatchers.hasType
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.drivingcoach.R
import com.drivingcoach.data.db.dao.LapDao
import com.drivingcoach.data.db.dao.SessionDao
import com.drivingcoach.data.db.entity.LapEntity
import com.drivingcoach.data.db.entity.SessionEntity
import com.drivingcoach.testing.awaitUntil
import com.drivingcoach.testing.launchFragmentInHiltContainer
import com.drivingcoach.util.SessionShareBuilder
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.equalTo
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
 * L2 (ASPICE SWE.5) coverage for the session share surface — SRS SH-07, SH-08, SH-10, SH-11.
 *
 * These tests exist because the developer export is deliberately undiscoverable. It hangs off
 * a long-press on a toolbar action view that has to be located *after* the menu is laid out
 * (see `SessionResultFragment.attachTelemetryExportGesture`). Nothing about that wiring is
 * visible to a compiler or to a unit test, and nobody would notice it silently detaching
 * during a refactor until the moment they actually needed a bug report. This class is the
 * only thing standing between that gesture and a quiet regression.
 *
 * Outgoing intents are stubbed, so no real share sheet ever opens and the assertions read the
 * intent that *would* have been sent.
 */
@LargeTest
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SessionShareTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var sessionDao: SessionDao

    @Inject
    lateinit var lapDao: LapDao

    private lateinit var telemetryFile: File
    private var sessionId: Long = 0L
    private var intentsReady = false

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @After
    fun tearDown() {
        if (intentsReady) {
            Intents.release()
            intentsReady = false
        }
        if (::telemetryFile.isInitialized) telemetryFile.delete()
        shareCacheDir().deleteRecursively()
    }

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun shareCacheDir() = File(context().cacheDir, SessionShareBuilder.SHARE_DIR)

    private fun seedSession(withTelemetryFile: Boolean): SessionEntity = runBlocking {
        val telemetryDir = File(context().filesDir, "telemetry").apply { mkdirs() }
        telemetryFile = File(telemetryDir, "session_share_test.jsonl")
        if (withTelemetryFile) telemetryFile.writeText(TELEMETRY_CONTENT) else telemetryFile.delete()

        sessionId = sessionDao.insertSession(
            SessionEntity(
                userId = "instrumented-user",
                trackName = "Braga Kartodromo",
                startedAt = 1_699_999_000_000L,
                endedAt = 1_699_999_600_000L,
                rawFilePath = telemetryFile.path,
                // A completed, uploaded session on purpose: any in-progress state puts an
                // indeterminate spinner on the upload status bar, and a never-ending
                // animation would stop Espresso's UI thread from ever going idle.
                uploadStatus = "DONE",
                processingStatus = "COMPLETE",
                startLineLat1 = 41.5,
                startLineLng1 = -8.4,
                startLineLat2 = 41.6,
                startLineLng2 = -8.5
            )
        )

        lapDao.insertLaps(
            listOf(
                LapEntity(
                    sessionId = sessionId, lapNumber = 1, startTs = 0, endTs = 45_000,
                    durationMs = 45_000, isBestLap = true, isLocalOnly = false
                )
            )
        )
        sessionDao.getSessionByIdSync(sessionId)!!
    }

    private fun launchResultScreen() {
        launchFragmentInHiltContainer<SessionResultFragment>(
            fragmentArgs = bundleOf("sessionId" to sessionId)
        )
        // Espresso already blocks until the UI thread is idle, so this single check is what
        // synchronises the test with the asynchronous menu inflation *and* the post() that
        // attaches the long-press listener. It must not be wrapped in a polling loop: onView
        // blocks internally, so a loop would swallow its timeout and hang indefinitely.
        onView(withId(R.id.action_share)).check(matches(isDisplayed()))

        // Intent recording starts only now, and only for the chooser. Starting it earlier --
        // or stubbing `anyIntent()` -- would also stub the intent that launches the test host
        // activity above, so the screen would never appear and the test would block forever
        // instead of failing.
        Intents.init()
        intentsReady = true
        Intents.intending(hasAction(Intent.ACTION_CHOOSER))
            .respondWith(Instrumentation.ActivityResult(0, null))
    }

    // --- The gesture surface (SRS SH-07) ---

    @Test
    fun shareActionIsPresentOnTheSessionResultToolbar() {
        seedSession(withTelemetryFile = true)
        launchResultScreen()

        onView(withId(R.id.action_share)).check(matches(isDisplayed()))
    }

    @Test
    fun tappingShareSendsTheSessionCardImage() {
        seedSession(withTelemetryFile = true)
        launchResultScreen()

        onView(withId(R.id.action_share)).perform(click())

        awaitSendIntent(SessionShareBuilder.MIME_PNG)
    }

    @Test
    fun longPressingShareExportsTheTelemetryBundle() {
        seedSession(withTelemetryFile = true)
        launchResultScreen()

        performLongPressOnShare()

        awaitSendIntent(SessionShareBuilder.MIME_ZIP)
    }

    @Test
    fun longPressIsConsumedSoNoTooltipIsShown() {
        seedSession(withTelemetryFile = true)
        launchResultScreen()

        // `performLongClick` returns true only when a listener handled the event, and that
        // return value *is* the tooltip suppression: an unconsumed long-press is precisely
        // what makes the platform fall back to showing the item's title in a tooltip.
        assertTrue(
            "Long-press must be consumed by the export listener; if it is not, the platform " +
                "shows a 'Share' tooltip and the export gesture is not wired up at all",
            performLongPressOnShare()
        )
    }

    // --- Failure is visible, never silent (SRS SH-10) ---

    @Test
    fun missingTelemetryFileSurfacesAnErrorInsteadOfFailingSilently() {
        seedSession(withTelemetryFile = false)
        launchResultScreen()

        performLongPressOnShare()

        val expected = context().getString(R.string.share_error_telemetry_missing)
        awaitUntil("the missing-telemetry error to be shown") {
            runCatching {
                onView(withText(expected)).check(matches(isDisplayed()))
            }.isSuccess
        }
    }

    // --- Read-only invariant (SRS SH-11) ---

    @Test
    fun exportingLeavesTheRecordedSessionUntouched() {
        val seeded = seedSession(withTelemetryFile = true)
        val bytesBefore = telemetryFile.readBytes()
        launchResultScreen()

        performLongPressOnShare()
        awaitSendIntent(SessionShareBuilder.MIME_ZIP)

        assertTrue("Telemetry file must survive an export", telemetryFile.exists())
        assertTrue(
            "Export must not rewrite the recorded telemetry",
            bytesBefore.contentEquals(telemetryFile.readBytes())
        )

        val after = runBlocking { sessionDao.getSessionByIdSync(sessionId) }
        assertNotNull("Export must not delete the session row", after)
        assertEquals("Export must not alter the session record", seeded, after)

        val lapsAfter = runBlocking { lapDao.getLapsForSession(sessionId).first() }
        assertEquals("Export must not alter lap records", 1, lapsAfter.size)
        assertEquals(45_000L, lapsAfter.first().durationMs)
    }

    // --- helpers ---

    /** Returns whether the long-press was consumed, which also proves the listener is attached. */
    private fun performLongPressOnShare(): Boolean {
        var consumed = false
        onView(withId(R.id.action_share)).check { view, _ ->
            consumed = view.performLongClick()
        }
        return consumed
    }

    private fun awaitSendIntent(mimeType: String) {
        // The bundle is assembled on Dispatchers.IO, so the intent lands slightly after the
        // gesture returns.
        awaitUntil("an ACTION_SEND intent of type $mimeType") {
            runCatching {
                intended(
                    allOf(
                        hasAction(Intent.ACTION_CHOOSER),
                        hasExtra(
                            equalTo(Intent.EXTRA_INTENT),
                            allOf(hasAction(Intent.ACTION_SEND), hasType(mimeType))
                        )
                    )
                )
            }.isSuccess
        }
    }

    private companion object {
        val TELEMETRY_CONTENT = buildString {
            appendLine("""{"type":"header","trackName":"Braga Kartodromo"}""")
            repeat(10) { i ->
                appendLine("""{"timestampMs":${1_699_999_000_000L + i * 1000},"latitude":41.5,"longitude":-8.4}""")
            }
        }
    }
}
