package com.drivingcoach.testing

import com.drivingcoach.data.db.dao.LapDao
import com.drivingcoach.data.db.dao.SessionDao
import com.drivingcoach.data.db.entity.LapEntity
import com.drivingcoach.data.db.entity.SessionEntity
import java.io.File

/**
 * Seeds finished sessions for the progression tests (SRS §11.2).
 *
 * The telemetry is a straight run at [SPEED_MS] with one clean fix a second, so the
 * measured distance is exactly speed × duration: 6 laps of a minute come to 14.4 km.
 */
class ProgressionSeed(
    private val sessionDao: SessionDao,
    private val lapDao: LapDao,
    private val telemetryDir: File
) {
    val files = mutableListOf<File>()

    /**
     * @param trackId catalogue or saved-circuit id recorded on the session, if any
     * @param withTelemetry false points the session at a telemetry file that does not
     *   exist, as after the file was deleted, so distance must come from the fallback
     */
    suspend fun session(
        startedAt: Long,
        lapTimesMs: List<Long>,
        trackName: String = TRACK,
        trackId: String? = null,
        withTelemetry: Boolean = true
    ): Long {
        val durationMs = lapTimesMs.sum()
        val file = File(telemetryDir.apply { mkdirs() }, "progression_$startedAt.jsonl")
        if (withTelemetry) file.writeText(straightRun(startedAt, durationMs)) else file.delete()
        files += file

        val id = sessionDao.insertSession(
            SessionEntity(
                userId = "instrumented-user",
                trackName = trackName,
                trackId = trackId,
                startedAt = startedAt,
                endedAt = startedAt + durationMs,
                rawFilePath = file.path,
                uploadStatus = "DONE",
                processingStatus = "COMPLETE"
            )
        )
        var start = startedAt
        val best = lapTimesMs.minOrNull()
        lapDao.insertLaps(
            lapTimesMs.mapIndexed { index, ms ->
                LapEntity(
                    sessionId = id,
                    lapNumber = index + 1,
                    startTs = start,
                    endTs = start + ms,
                    durationMs = ms,
                    sector1Ms = 0L,
                    sector2Ms = 0L,
                    sector3Ms = 0L,
                    isBestLap = ms == best,
                    isLocalOnly = true
                ).also { start += ms }
            }
        )
        return id
    }

    /** The session's telemetry file, whether or not it was written. */
    fun telemetryFileFor(startedAt: Long): File = File(telemetryDir, "progression_$startedAt.jsonl")

    fun cleanUp() = files.forEach { it.delete() }

    private fun straightRun(startedAt: Long, durationMs: Long): String = buildString {
        val seconds = (durationMs / 1000L).toInt()
        val metresPerDegreeLat = 111_320.0
        for (s in 0..seconds) {
            val lat = BASE_LAT + s * SPEED_MS / metresPerDegreeLat
            appendLine(
                """{"timestampMs":${startedAt + s * 1000L},"latitude":$lat,"longitude":$BASE_LNG,""" +
                    """"speedMs":$SPEED_MS,"gpsAccuracyM":4.0}"""
            )
        }
    }

    companion object {
        const val TRACK = "Test Ring"
        const val SPEED_MS = 40.0
        private const val BASE_LAT = 41.0
        private const val BASE_LNG = -8.0

        /** Six identical minute laps: Finisher plus Consistency Gold, and 14.4 km. */
        val STEADY_MINUTES = List(6) { 60_000L }

        /** Six identical laps two seconds quicker, 13.9 km: a personal best on a second visit. */
        val STEADY_QUICKER = List(6) { 58_000L }
    }
}
