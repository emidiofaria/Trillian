package com.drivingcoach.data.telemetry

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter

/**
 * Writes telemetry samples to a JSONL file (one JSON object per line).
 * Thread-safe via Mutex. All IO operations run on Dispatchers.IO.
 * 
 * File format:
 * - Line 1: Header with session metadata and start line coordinates
 * - Lines 2+: Telemetry samples
 */
class TelemetryFileWriter(
    context: Context,
    private val sessionId: Long
) {
    private val gson = Gson()
    private val mutex = Mutex()
    private val file: File
    private var writer: BufferedWriter? = null
    private var isClosed = false
    private var headerWritten = false

    init {
        val telemetryDir = File(context.filesDir, TELEMETRY_DIR)
        if (!telemetryDir.exists()) {
            telemetryDir.mkdirs()
        }
        file = File(telemetryDir, "session_${sessionId}.jsonl")
    }

    /**
     * Writes the header line with session metadata and start line coordinates.
     * Should be called once at the start of recording.
     */
    suspend fun writeHeader(
        startLineLat1: Double?,
        startLineLng1: Double?,
        startLineLat2: Double?,
        startLineLng2: Double?,
        trackName: String? = null
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (isClosed || headerWritten) {
                Log.w(TAG, "Header already written or writer closed for session $sessionId")
                return@withContext
            }

            try {
                if (writer == null) {
                    writer = BufferedWriter(FileWriter(file, false)) // Overwrite for new session
                }
                
                val startLine = if (startLineLat1 != null && startLineLng1 != null &&
                    startLineLat2 != null && startLineLng2 != null) {
                    StartLineData(startLineLat1, startLineLng1, startLineLat2, startLineLng2)
                } else {
                    null
                }
                
                val header = TelemetryHeader(
                    sessionId = sessionId,
                    startLine = startLine,
                    trackName = trackName
                )
                
                val json = gson.toJson(header)
                writer?.apply {
                    write(json)
                    newLine()
                }
                headerWritten = true
                Log.d(TAG, "Wrote header for session $sessionId: startLine=$startLine")
            } catch (e: Exception) {
                Log.e(TAG, "Error writing header for session $sessionId", e)
            }
        }
    }

    /**
     * Writes a single telemetry sample as a JSON line.
     * Thread-safe and runs on IO dispatcher.
     */
    suspend fun writeSample(sample: TelemetrySample) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (isClosed) {
                Log.w(TAG, "Attempted to write to closed writer for session $sessionId")
                return@withContext
            }

            try {
                if (writer == null) {
                    writer = BufferedWriter(FileWriter(file, true))
                }
                val json = gson.toJson(sample)
                writer?.apply {
                    write(json)
                    newLine()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error writing telemetry sample for session $sessionId", e)
            }
        }
    }

    /**
     * Flushes buffered data to disk without closing the writer.
     * Call periodically to minimize data loss on unexpected termination.
     */
    suspend fun flush() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (isClosed) return@withContext

            try {
                writer?.flush()
                Log.d(TAG, "Flushed telemetry writer for session $sessionId")
            } catch (e: Exception) {
                Log.e(TAG, "Error flushing telemetry writer for session $sessionId", e)
            }
        }
    }

    /**
     * Flushes and closes the writer.
     */
    suspend fun close() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (isClosed) return@withContext

            try {
                writer?.apply {
                    flush()
                    close()
                }
                writer = null
                isClosed = true
                Log.d(TAG, "Closed telemetry writer for session $sessionId")
            } catch (e: Exception) {
                Log.e(TAG, "Error closing telemetry writer for session $sessionId", e)
            }
        }
    }

    /**
     * Returns the absolute path to the telemetry file.
     */
    fun getFilePath(): String = file.absolutePath

    /**
     * Returns the File object for the telemetry file.
     */
    fun getFile(): File = file

    companion object {
        private const val TAG = "TelemetryFileWriter"
        const val TELEMETRY_DIR = "telemetry"

        /**
         * Gets the telemetry file path for a given session without creating a writer.
         */
        fun getFilePathForSession(context: Context, sessionId: Long): String {
            return File(
                File(context.filesDir, TELEMETRY_DIR),
                "session_${sessionId}.jsonl"
            ).absolutePath
        }
    }
}
