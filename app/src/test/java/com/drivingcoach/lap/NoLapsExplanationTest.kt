package com.drivingcoach.lap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The words a driver reads when a session produced nothing.
 *
 * Incident 14 ended with a driver who had completed three laps being told to
 * "Complete at least 2 laps." The app held every number needed to say something
 * useful and said none of it. These tests exist because that wording survived
 * for months without anything asserting on it.
 */
class NoLapsExplanationTest {

    private fun diagnostics(
        rejections: List<LocalLapDetector.RejectedCrossing> = emptyList(),
        captureSpeed: Double? = null,
        repeats: Boolean? = null
    ) = LocalLapDetector.DetectionDiagnostics(
        sampleCount = 1200,
        durationMs = 400_000,
        observedSampleRateHz = 3.0,
        startLineLengthM = 8.15,
        startLineBearingDeg = 171.6,
        angleBetweenLineAndTravelDeg = 80.0,
        acceptedCrossings = emptyList(),
        rejectedCrossings = rejections,
        lapCount = 0,
        captureWindowSpeedMs = captureSpeed,
        pathRepeats = repeats
    )

    private fun wide(offsetM: Double) = LocalLapDetector.RejectedCrossing(
        timestampMs = 0L,
        reason = LocalLapDetector.RejectionReason.TOO_FAR_TO_THE_SIDE,
        detail = "wide",
        lateralOffsetM = offsetM
    )

    @Test
    fun `never tells a driver to complete laps they may already have driven`() {
        // The specific sentence this work exists to remove.
        val cases = listOf(
            diagnostics(),
            diagnostics(rejections = listOf(wide(16.1))),
            diagnostics(rejections = listOf(wide(16.1)), captureSpeed = 0.1),
            diagnostics(repeats = true)
        )

        cases.forEach { d ->
            assertFalse(
                "the old wording must not survive anywhere: ${NoLapsExplanation.of(d)}",
                NoLapsExplanation.of(d).contains("Complete at least 2 laps")
            )
        }
    }

    @Test
    fun `reports how many passes were seen and how far to the side they went`() {
        // Incident 14's real numbers: four passes, the nearest 16.1 m out.
        val message = NoLapsExplanation.of(
            diagnostics(rejections = listOf(wide(16.1), wide(24.7), wide(30.2), wide(30.2)))
        )

        assertTrue("must count the passes: $message", message.contains("4 passes"))
        assertTrue("must quote the nearest one, not the worst: $message", message.contains("16m"))
        assertFalse("must not quote the worst as if it were typical", message.contains("30m"))
    }

    @Test
    fun `says the line was captured standing still only when it was`() {
        val stationary = NoLapsExplanation.of(
            diagnostics(rejections = listOf(wide(16.1)), captureSpeed = 0.1)
        )
        val moving = NoLapsExplanation.of(
            diagnostics(rejections = listOf(wide(16.1)), captureSpeed = 12.0)
        )

        assertTrue("$stationary", stationary.contains("while you were stopped"))
        assertFalse(
            "a line captured at speed was not displaced this way, and blaming it would " +
                "send the driver to fix something that is not broken: $moving",
            moving.contains("while you were stopped")
        )
    }

    @Test
    fun `invents no distance when nothing came near the start point`() {
        // With no passes recorded there is no measured offset. Naming one would be
        // fabricating evidence, which is worse than saying little.
        val message = NoLapsExplanation.of(diagnostics(repeats = true))

        assertFalse("no made-up distance: $message", Regex("\\d+m").containsMatchIn(message))
        assertTrue(
            "but it must still say the driver was lapping: $message",
            message.contains("repeating circuit")
        )
    }

    @Test
    fun `falls back to the bare statement when there is nothing to add`() {
        assertEquals("No laps detected.", NoLapsExplanation.of(diagnostics()))
        assertEquals("No laps detected.", NoLapsExplanation.of(null))
    }

    @Test
    fun `the advice must be something the app lets you do`() {
        // Track Setup captures both start-line points on foot, standing at the track
        // edges -- "Walk to each edge of the track ... and capture two GPS points".
        // There is no capture-while-moving control anywhere in the app.
        //
        // v2.96 shipped this message ending "Set the start line again while driving
        // past it": a correct diagnosis followed by an instruction the driver cannot
        // carry out, which leaves them with nothing to do. Diagnosing a problem and
        // then misdirecting the fix is worse than saying nothing, because it spends
        // the driver's trust.
        val cases = listOf(
            diagnostics(rejections = listOf(wide(16.1))),
            diagnostics(rejections = listOf(wide(16.1)), captureSpeed = 0.1),
            diagnostics(repeats = true)
        )

        cases.forEach { d ->
            val message = NoLapsExplanation.of(d)
            listOf("while driving", "as you drive", "driving past", "while moving").forEach { banned ->
                assertFalse(
                    "advice the UI offers no way to follow ($banned): $message",
                    message.contains(banned, ignoreCase = true)
                )
            }
        }
    }

    @Test
    fun `counts a single pass in the singular`() {
        val message = NoLapsExplanation.of(diagnostics(rejections = listOf(wide(18.0))))

        assertTrue("$message", message.contains("1 pass went"))
    }
}
