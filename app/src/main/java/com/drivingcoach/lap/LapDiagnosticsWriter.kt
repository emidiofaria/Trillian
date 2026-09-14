package com.drivingcoach.lap

import android.util.Log
import com.google.gson.GsonBuilder
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes a record of what lap detection decided, alongside the telemetry it read.
 *
 * Incident 09 reported "no laps detected" and was closed without a cause, because
 * nothing survived the run except the message shown to the user. Incident 13 had the
 * same symptom and was only explicable because its raw telemetry happened to be kept
 * by hand. This writer removes that dependence on luck: every detection run leaves a
 * small file next to the session's telemetry saying what the detector saw and why it
 * rejected what it rejected.
 *
 * The file sits beside the telemetry deliberately rather than in the database. A
 * diagnostics column would mean a Room schema migration on an app that is already in
 * users' hands, which is a real risk taken on for a purely diagnostic feature. A
 * sidecar file carries none of that risk: if writing fails, or the file is deleted,
 * or it is never read, nothing about the app's behaviour changes.
 */
@Singleton
class LapDiagnosticsWriter @Inject constructor() {

    companion object {
        private const val TAG = "LapDiagnosticsWriter"

        /** Appended to the telemetry file name to form the sidecar name. */
        const val SUFFIX = ".lapdiag.json"
    }

    private val gson = GsonBuilder().setPrettyPrinting().create()

    /**
     * The path the diagnostics for a given telemetry file are written to.
     *
     * Exposed so callers and tests can find the file without reproducing the naming.
     */
    fun sidecarFor(telemetryFile: File): File =
        File(telemetryFile.parentFile, telemetryFile.name + SUFFIX)

    /**
     * Writes [diagnostics] beside [telemetryFile].
     *
     * Never throws. Diagnostics are an aid to a future investigation, and a failure
     * to record them must not turn a session that was processed successfully into an
     * error the user sees.
     *
     * @return the file written, or null if it could not be written
     */
    fun write(
        telemetryFile: File,
        sessionId: Long,
        outcome: LocalLapDetector.DetectionOutcome
    ): File? {
        val diagnostics = outcome.diagnostics ?: return null
        return try {
            val sidecar = sidecarFor(telemetryFile)
            sidecar.writeText(
                gson.toJson(
                    Record(
                        sessionId = sessionId,
                        telemetryFileName = telemetryFile.name,
                        outcome = describe(outcome.result),
                        diagnostics = diagnostics
                    )
                )
            )
            Log.d(TAG, "Wrote lap diagnostics to ${sidecar.absolutePath}")
            sidecar
        } catch (e: Exception) {
            Log.w(TAG, "Could not write lap diagnostics for session $sessionId", e)
            null
        }
    }

    /** A short, stable label for the outcome, so the file is readable without the app. */
    private fun describe(result: LocalLapDetector.DetectionResult): String = when (result) {
        is LocalLapDetector.DetectionResult.Success -> "success:${result.laps.size}_laps"
        is LocalLapDetector.DetectionResult.InsufficientLaps ->
            "insufficient_laps:${result.lapCount}"
        is LocalLapDetector.DetectionResult.NoStartLine -> "no_start_line"
        is LocalLapDetector.DetectionResult.Error -> "error"
    }

    private data class Record(
        val sessionId: Long,
        val telemetryFileName: String,
        val outcome: String,
        val diagnostics: LocalLapDetector.DetectionDiagnostics
    )
}
