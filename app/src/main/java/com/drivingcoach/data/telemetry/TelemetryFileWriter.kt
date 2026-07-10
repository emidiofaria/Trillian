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

    init {
        val telemetryDir = File(context.filesDir, TELEMETRY_DIR)
        if (!telemetryDir.exists()) {
            telemetryDir.mkdirs()
        }
        file = File(telemetryDir, "session_${sessionId}.jsonl")
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
