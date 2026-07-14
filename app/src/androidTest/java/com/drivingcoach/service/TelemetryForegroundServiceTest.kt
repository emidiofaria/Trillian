package com.drivingcoach.service

import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ServiceTestRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeoutException

/**
 * Instrumented tests for TelemetryForegroundService.
 * 
 * These tests require a device or emulator with location permissions granted.
 * Run with: ./gradlew connectedAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class TelemetryForegroundServiceTest {

    @get:Rule
    val serviceRule = ServiceTestRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun serviceStartsAndPostsRecordingStateWithin2Seconds() {
        // Create intent with start recording action
        val intent = Intent(context, TelemetryForegroundService::class.java).apply {
            action = TelemetryForegroundService.ACTION_START_RECORDING
            putExtra(TelemetryForegroundService.EXTRA_SESSION_ID, 1L)
        }

        // Bind to the service
        val binder: IBinder = serviceRule.bindService(intent)
        val service = (binder as TelemetryForegroundService.TelemetryBinder).getService()
        val stateFlow = (binder).getStateFlow()

        // Wait for Recording state within 2 seconds
        runBlocking {
            try {
                withTimeout(2000) {
                    // Keep checking until we get Recording state
                    var state = stateFlow.value
                    var attempts = 0
                    while (state !is RecordingState.Recording && attempts < 20) {
                        kotlinx.coroutines.delay(100)
                        state = stateFlow.value
                        attempts++
                    }
                    
                    assertTrue(
                        "Expected Recording state within 2 seconds, got: $state",
                        state is RecordingState.Recording
                    )
                }
            } catch (e: TimeoutException) {
                fail("Timeout waiting for Recording state: ${stateFlow.value}")
            }
        }
    }

    @Test
    fun stoppingServiceTransitionsToIdleState() {
        // Start recording first
        val startIntent = Intent(context, TelemetryForegroundService::class.java).apply {
            action = TelemetryForegroundService.ACTION_START_RECORDING
            putExtra(TelemetryForegroundService.EXTRA_SESSION_ID, 2L)
        }

        val binder: IBinder = serviceRule.bindService(startIntent)
        val service = (binder as TelemetryForegroundService.TelemetryBinder).getService()
        val stateFlow = binder.getStateFlow()

        runBlocking {
            // Wait for recording to start
            withTimeout(2000) {
                var state = stateFlow.value
                while (state !is RecordingState.Recording) {
                    kotlinx.coroutines.delay(100)
                    state = stateFlow.value
                }
            }

            // Send stop command
            val stopIntent = Intent(context, TelemetryForegroundService::class.java).apply {
                action = TelemetryForegroundService.ACTION_STOP_RECORDING
            }
            context.startService(stopIntent)

            // Wait for Idle state
            withTimeout(3000) {
                var state = stateFlow.value
                var attempts = 0
                while (state !is RecordingState.Idle && attempts < 30) {
                    kotlinx.coroutines.delay(100)
                    state = stateFlow.value
                    attempts++
                }
                
                assertTrue(
                    "Expected Idle state after stop, got: $state",
                    state is RecordingState.Idle
                )
            }
        }
    }

    @Test
    fun elapsedMsIncreasesOverTime() {
        val intent = Intent(context, TelemetryForegroundService::class.java).apply {
            action = TelemetryForegroundService.ACTION_START_RECORDING
            putExtra(TelemetryForegroundService.EXTRA_SESSION_ID, 3L)
        }

        val binder: IBinder = serviceRule.bindService(intent)
        val stateFlow = (binder as TelemetryForegroundService.TelemetryBinder).getStateFlow()

        runBlocking {
            // Wait for recording to start
            withTimeout(2000) {
                var state = stateFlow.value
                while (state !is RecordingState.Recording) {
                    kotlinx.coroutines.delay(100)
                    state = stateFlow.value
                }
            }

            // Get initial elapsed time
            val initialState = stateFlow.value as RecordingState.Recording
            val initialElapsed = initialState.elapsedMs

            // Wait a bit
            kotlinx.coroutines.delay(1500)

            // Get new elapsed time
            val newState = stateFlow.value
            assertTrue("State should still be Recording", newState is RecordingState.Recording)
            
            val newElapsed = (newState as RecordingState.Recording).elapsedMs
            assertTrue(
                "Elapsed time should increase: initial=$initialElapsed, new=$newElapsed",
                newElapsed > initialElapsed
            )
            assertTrue(
                "Elapsed should have increased by at least 1 second",
                newElapsed - initialElapsed >= 1000
            )
        }
    }

    @Test
    fun serviceReturnsValidBinder() {
        val intent = Intent(context, TelemetryForegroundService::class.java)
        val binder: IBinder = serviceRule.bindService(intent)
        
        assertNotNull("Binder should not be null", binder)
        assertTrue(
            "Binder should be TelemetryBinder",
            binder is TelemetryForegroundService.TelemetryBinder
        )
        
        val service = (binder as TelemetryForegroundService.TelemetryBinder).getService()
        assertNotNull("Service should not be null", service)
    }

    @Test
    fun initialStateIsIdle() {
        val intent = Intent(context, TelemetryForegroundService::class.java)
        val binder: IBinder = serviceRule.bindService(intent)
        
        val stateFlow = (binder as TelemetryForegroundService.TelemetryBinder).getStateFlow()
        val state = stateFlow.value
        
        assertTrue("Initial state should be Idle, got: $state", state is RecordingState.Idle)
    }
}
