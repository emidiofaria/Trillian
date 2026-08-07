package com.drivingcoach.testing

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Background sampler that measures how long the main thread takes to service a posted task.
 *
 * This is an ANR detector in miniature. Espresso is *not* suitable for this job: it waits
 * for the main looper to become idle rather than failing, so a blocking call on the main
 * thread merely makes Espresso slow, not red. Sampling the round-trip latency from a
 * separate thread is what actually distinguishes "busy" from "wedged".
 *
 * Used to verify SRS UI-04.
 */
class MainThreadResponsivenessProbe(
    private val sampleIntervalMs: Long = 50L,
    private val perSampleTimeoutMs: Long = 15_000L
) {

    private val running = AtomicBoolean(false)
    private var thread: Thread? = null

    @Volatile
    var worstLatencyMs: Long = 0L
        private set

    fun start() {
        running.set(true)
        worstLatencyMs = 0L
        thread = Thread {
            while (running.get()) {
                val latency = measureRoundTripMs()
                if (latency > worstLatencyMs) worstLatencyMs = latency
                Thread.sleep(sampleIntervalMs)
            }
        }.also { it.start() }
    }

    fun stop() {
        running.set(false)
        thread?.join(perSampleTimeoutMs + 1_000L)
        thread = null
    }

    private fun measureRoundTripMs(): Long {
        val latch = CountDownLatch(1)
        val postedAt = SystemClock.uptimeMillis()
        Handler(Looper.getMainLooper()).post { latch.countDown() }

        return if (latch.await(perSampleTimeoutMs, TimeUnit.MILLISECONDS)) {
            SystemClock.uptimeMillis() - postedAt
        } else {
            perSampleTimeoutMs
        }
    }
}
