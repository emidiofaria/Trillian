package com.drivingcoach.util

import android.content.Context
import android.os.Build
import com.drivingcoach.BuildConfig
import com.drivingcoach.data.db.entity.LapEntity
import com.drivingcoach.data.db.entity.SessionEntity
import com.google.gson.GsonBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the artifacts shared from the Session Result screen (SRS SH-07 to SH-11).
 *
 * ## Read-only invariant (SRS SH-11)
 *
 * This class **never writes session data**. It reads the telemetry JSONL and the session/lap
 * records, and the only location it writes to is [shareCacheDir]. That is deliberate and
 * enforced by construction: the builder is handed a cache directory and plain data, never a
 * DAO, never a [com.drivingcoach.data.telemetry.TelemetryFileWriter], so there is no write
 * path to session data available to it at all.
 *
 * The reasoning is simple: this exists to diagnose problems with recorded sessions. A
 * diagnostic tool that can alter the evidence is worse than having no tool, because it
 * destroys confidence in every export it produces.
 *
 * ## Testability
 *
 * The primary constructor takes a directory and primitive build facts rather than a
 * [Context], so the whole ZIP pipeline runs on the JVM at L1 against a temp folder. The
 * secondary `@Inject` constructor supplies the real values. (Dagger cannot inject the [now]
 * lambda, which is why the clock is not simply a default parameter.)
 */
@Singleton
class SessionShareBuilder(
    private val shareCacheDir: File,
    private val appVersionName: String,
    private val appVersionCode: Int,
    private val deviceModel: String,
    private val sdkInt: Int,
    private val now: () -> Long
) {

    @Inject
    constructor(@ApplicationContext context: Context) : this(
        shareCacheDir = File(context.cacheDir, SHARE_DIR),
        appVersionName = BuildConfig.VERSION_NAME,
        appVersionCode = BuildConfig.VERSION_CODE,
        deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
        sdkInt = Build.VERSION.SDK_INT,
        now = { System.currentTimeMillis() }
    )

    /** Outcome of a share build. Failures are values, never exceptions (SRS SH-10). */
    sealed interface ShareResult {
        data class Success(val file: File) : ShareResult
        data class Failure(val reason: Reason) : ShareResult
    }

    enum class Reason {
        /** The session predates the telemetry file, or it was removed from the device. */
        TELEMETRY_FILE_MISSING,

        /** Any IO problem while assembling the bundle. */
        EXPORT_FAILED
    }

    /**
     * Assembles the developer diagnostic bundle for [session].
     *
     * The ZIP holds the raw telemetry alongside the context needed to interpret it — app
     * version, device, processing status and the detected laps. Those are exactly the
     * questions asked first during any incident analysis, and a bare `.jsonl` cannot answer
     * a single one of them.
     */
    fun buildTelemetryBundle(session: SessionEntity, laps: List<LapEntity>): ShareResult {
        val telemetryFile = File(session.rawFilePath)
        if (!telemetryFile.isFile) {
            return ShareResult.Failure(Reason.TELEMETRY_FILE_MISSING)
        }

        return try {
            pruneStaleArtifacts()
            if (!shareCacheDir.exists() && !shareCacheDir.mkdirs()) {
                return ShareResult.Failure(Reason.EXPORT_FAILED)
            }

            val bundle = File(shareCacheDir, bundleName(session))
            ZipOutputStream(FileOutputStream(bundle).buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(ENTRY_TELEMETRY))
                // Streamed so that a long session cannot be held in memory all at once.
                FileInputStream(telemetryFile).use { input -> input.copyTo(zip) }
                zip.closeEntry()

                zip.putNextEntry(ZipEntry(ENTRY_METADATA))
                zip.write(metadataJson(session, laps, telemetryFile).toByteArray())
                zip.closeEntry()
            }
            ShareResult.Success(bundle)
        } catch (e: Exception) {
            ShareResult.Failure(Reason.EXPORT_FAILED)
        }
    }

    /**
     * Registers an already-written share artifact (the PNG card) so it participates in the
     * same retention policy as bundles.
     */
    fun shareCacheDir(): File {
        if (!shareCacheDir.exists()) shareCacheDir.mkdirs()
        return shareCacheDir
    }

    /**
     * Deletes share artifacts older than [RETENTION_MS].
     *
     * Scoped deliberately: only the immediate children of [shareCacheDir], and only files
     * this class is responsible for creating. It never recurses and never leaves that
     * directory, so it cannot reach `filesDir` where the telemetry lives (SRS SH-11).
     *
     * Without this the directory grows without bound — it already does today for share
     * cards, which went unnoticed only because they are small. Telemetry bundles are not.
     */
    fun pruneStaleArtifacts() {
        val cutoff = now() - RETENTION_MS
        shareCacheDir.listFiles()
            ?.filter { it.isFile && it.isOurArtifact() && it.lastModified() < cutoff }
            ?.forEach { runCatching { it.delete() } }
    }

    private fun File.isOurArtifact(): Boolean =
        name.startsWith(BUNDLE_PREFIX) || name.startsWith(CARD_PREFIX)

    private fun metadataJson(
        session: SessionEntity,
        laps: List<LapEntity>,
        telemetryFile: File
    ): String {
        val metadata = mapOf(
            "exportedAtMs" to now(),
            "app" to mapOf(
                "versionName" to appVersionName,
                "versionCode" to appVersionCode
            ),
            "device" to mapOf(
                "model" to deviceModel,
                "sdkInt" to sdkInt
            ),
            "session" to mapOf(
                "id" to session.id,
                "trackName" to session.trackName,
                "startedAt" to session.startedAt,
                "endedAt" to session.endedAt,
                "uploadStatus" to session.uploadStatus,
                "processingStatus" to session.processingStatus,
                "remoteSessionId" to session.remoteSessionId,
                "startLine" to mapOf(
                    "lat1" to session.startLineLat1,
                    "lng1" to session.startLineLng1,
                    "lat2" to session.startLineLat2,
                    "lng2" to session.startLineLng2
                )
            ),
            "telemetry" to mapOf(
                "fileName" to telemetryFile.name,
                "sizeBytes" to telemetryFile.length()
            ),
            "laps" to laps.map { lap ->
                mapOf(
                    "lapNumber" to lap.lapNumber,
                    "startTs" to lap.startTs,
                    "endTs" to lap.endTs,
                    "durationMs" to lap.durationMs,
                    "sector1Ms" to lap.sector1Ms,
                    "sector2Ms" to lap.sector2Ms,
                    "sector3Ms" to lap.sector3Ms,
                    "isBestLap" to lap.isBestLap,
                    "isLocalOnly" to lap.isLocalOnly
                )
            }
        )
        return GSON.toJson(metadata)
    }

    private fun bundleName(session: SessionEntity): String {
        val track = session.trackName
            .replace(UNSAFE_FILENAME_CHARS, "_")
            .trim('_')
            .ifEmpty { "session" }
            .take(MAX_TRACK_NAME_CHARS)
        val date = DATE_FORMAT.format(Date(session.startedAt))
        return "$BUNDLE_PREFIX${track}_$date.zip"
    }

    companion object {
        const val SHARE_DIR = "shared"
        const val BUNDLE_PREFIX = "telemetry_"
        const val CARD_PREFIX = "session_share_"
        const val ENTRY_TELEMETRY = "telemetry.jsonl"
        const val ENTRY_METADATA = "session.json"
        const val MIME_ZIP = "application/zip"
        const val MIME_PNG = "image/png"

        /** Share artifacts are disposable; a day is ample for the user to complete a share. */
        const val RETENTION_MS = 24 * 60 * 60 * 1000L

        private const val MAX_TRACK_NAME_CHARS = 40
        private val UNSAFE_FILENAME_CHARS = Regex("[^A-Za-z0-9-]+")
        private val DATE_FORMAT = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US)
        private val GSON = GsonBuilder().setPrettyPrinting().create()
    }
}
