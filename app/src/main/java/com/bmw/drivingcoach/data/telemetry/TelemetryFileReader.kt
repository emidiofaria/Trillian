package com.bmw.drivingcoach.data.telemetry

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

/**
 * Reads telemetry samples from a JSONL file.
 * Handles malformed lines gracefully by skipping them with a warning.
 */
object TelemetryFileReader {
    private const val TAG = "TelemetryFileReader"
    private val gson = Gson()

    /**
     * Reads all telemetry samples from a JSONL file.
     * Returns an empty list if the file does not exist.
     * Skips malformed lines with a warning log.
     */
    suspend fun readAll(filePath: String): List<TelemetrySample> = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists()) {
            Log.w(TAG, "Telemetry file does not exist: $filePath")
            return@withContext emptyList()
        }

        val samples = mutableListOf<TelemetrySample>()
        var lineNumber = 0

        try {
            BufferedReader(FileReader(file)).use { reader ->
                reader.forEachLine { line ->
                    lineNumber++
                    if (line.isBlank()) return@forEachLine

                    try {
                        val sample = gson.fromJson(line, TelemetrySample::class.java)
                        if (sample != null) {
                            samples.add(sample)
                        }
                    } catch (e: JsonSyntaxException) {
                        Log.w(TAG, "Malformed JSON at line $lineNumber in $filePath: ${e.message}")
                    } catch (e: Exception) {
                        Log.w(TAG, "Error parsing line $lineNumber in $filePath: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading telemetry file: $filePath", e)
        }

        Log.d(TAG, "Read ${samples.size} samples from $filePath")
        samples
    }

    /**
     * Counts the number of samples in a file without loading them all into memory.
     */
    suspend fun countSamples(filePath: String): Int = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists()) return@withContext 0

        var count = 0
        try {
            BufferedReader(FileReader(file)).use { reader ->
                reader.forEachLine { line ->
                    if (line.isNotBlank()) {
                        try {
                            gson.fromJson(line, TelemetrySample::class.java)
                            count++
                        } catch (_: Exception) {
                            // Skip malformed lines
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error counting samples in file: $filePath", e)
        }
        count
    }

    /**
     * Reads samples within a time range.
     */
    suspend fun readRange(
        filePath: String,
        startMs: Long,
        endMs: Long
    ): List<TelemetrySample> = withContext(Dispatchers.IO) {
        readAll(filePath).filter { it.timestampMs in startMs..endMs }
    }
}
