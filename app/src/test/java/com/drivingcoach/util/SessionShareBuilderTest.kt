package com.drivingcoach.util

import com.drivingcoach.data.db.entity.LapEntity
import com.drivingcoach.data.db.entity.SessionEntity
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipFile

/**
 * L1 (ASPICE SWE.4) coverage for [SessionShareBuilder] — SRS SH-08, SH-10, SH-11.
 *
 * The builder was deliberately given a directory-and-primitives constructor precisely so
 * that the whole ZIP pipeline is exercisable here, on the JVM, without a device. Only the
 * `Intent`/`FileProvider` wiring needs an instrumented test.
 *
 * The tests that matter most are the read-only ones. Everything else is about the export
 * being *useful*; those are about it being *safe*.
 */
class SessionShareBuilderTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var shareCacheDir: File
    private lateinit var telemetryDir: File
    private var currentTime = 1_700_000_000_000L

    private fun builder() = SessionShareBuilder(
        shareCacheDir = shareCacheDir,
        appVersionName = "2.9",
        appVersionCode = 209,
        deviceModel = "Google Pixel 4",
        sdkInt = 30,
        now = { currentTime }
    )

    @Before
    fun setUp() {
        shareCacheDir = File(temporaryFolder.root, "cache/shared")
        telemetryDir = File(temporaryFolder.root, "files/telemetry")
        telemetryDir.mkdirs()
    }

    private fun writeTelemetryFile(content: String = TELEMETRY_CONTENT): File =
        File(telemetryDir, "session_7.jsonl").apply { writeText(content) }

    private fun session(rawFilePath: String) = SessionEntity(
        id = 7L,
        userId = "user-1",
        trackName = "Kartódromo do Algarve",
        startedAt = 1_699_999_000_000L,
        endedAt = 1_699_999_600_000L,
        rawFilePath = rawFilePath,
        uploadStatus = "DONE",
        processingStatus = "COMPLETE",
        remoteSessionId = "remote-abc",
        startLineLat1 = 37.1,
        startLineLng1 = -8.1,
        startLineLat2 = 37.2,
        startLineLng2 = -8.2
    )

    private fun laps() = listOf(
        LapEntity(
            id = 1, sessionId = 7, lapNumber = 1, startTs = 100, endTs = 45_100,
            durationMs = 45_000, sector1Ms = 15_000, sector2Ms = 15_000,
            sector3Ms = 15_000, isBestLap = false, isLocalOnly = true
        ),
        LapEntity(
            id = 2, sessionId = 7, lapNumber = 2, startTs = 45_100, endTs = 88_100,
            durationMs = 43_000, sector1Ms = 14_000, sector2Ms = 14_000,
            sector3Ms = 15_000, isBestLap = true, isLocalOnly = true
        )
    )

    private fun successFile(result: SessionShareBuilder.ShareResult): File {
        assertTrue("Expected Success but was $result", result is SessionShareBuilder.ShareResult.Success)
        return (result as SessionShareBuilder.ShareResult.Success).file
    }

    // --- Bundle contents (SRS SH-08) ---

    @Test
    fun bundleContainsTelemetryAndMetadataEntries() {
        val telemetry = writeTelemetryFile()

        val zip = successFile(builder().buildTelemetryBundle(session(telemetry.path), laps()))

        ZipFile(zip).use { archive ->
            val names = archive.entries().toList().map { it.name }.sorted()
            assertEquals(
                listOf(SessionShareBuilder.ENTRY_METADATA, SessionShareBuilder.ENTRY_TELEMETRY),
                names
            )
        }
    }

    @Test
    fun telemetryEntryIsAByteForByteCopyOfTheSource() {
        val telemetry = writeTelemetryFile()

        val zip = successFile(builder().buildTelemetryBundle(session(telemetry.path), laps()))

        ZipFile(zip).use { archive ->
            val entry = archive.getEntry(SessionShareBuilder.ENTRY_TELEMETRY)
            val extracted = archive.getInputStream(entry).readBytes()
            assertTrue(
                "Extracted telemetry must be identical to the source file",
                telemetry.readBytes().contentEquals(extracted)
            )
        }
    }

    @Test
    fun metadataCarriesTheDiagnosticContextNeededToInterpretTheTelemetry() {
        val telemetry = writeTelemetryFile()

        val zip = successFile(builder().buildTelemetryBundle(session(telemetry.path), laps()))

        val json = ZipFile(zip).use { archive ->
            val entry = archive.getEntry(SessionShareBuilder.ENTRY_METADATA)
            JsonParser.parseString(archive.getInputStream(entry).readBytes().decodeToString())
        }.asJsonObject

        assertEquals("2.9", json["app"].asJsonObject["versionName"].asString)
        assertEquals(209, json["app"].asJsonObject["versionCode"].asInt)
        assertEquals("Google Pixel 4", json["device"].asJsonObject["model"].asString)
        assertEquals(30, json["device"].asJsonObject["sdkInt"].asInt)

        val sessionJson = json["session"].asJsonObject
        assertEquals(7L, sessionJson["id"].asLong)
        assertEquals("Kartódromo do Algarve", sessionJson["trackName"].asString)
        assertEquals("DONE", sessionJson["uploadStatus"].asString)
        assertEquals("COMPLETE", sessionJson["processingStatus"].asString)
        assertEquals(37.1, sessionJson["startLine"].asJsonObject["lat1"].asDouble, 1e-9)

        assertEquals(2, json["laps"].asJsonArray.size())
        assertEquals(43_000L, json["laps"].asJsonArray[1].asJsonObject["durationMs"].asLong)
        assertTrue(json["laps"].asJsonArray[1].asJsonObject["isBestLap"].asBoolean)
        assertEquals(telemetry.length(), json["telemetry"].asJsonObject["sizeBytes"].asLong)
    }

    @Test
    fun bundleNameIsFilesystemSafeEvenForAwkwardTrackNames() {
        val telemetry = writeTelemetryFile()
        val awkward = session(telemetry.path).copy(trackName = "Estoril / Turn 1: \"fast\"")

        val zip = successFile(builder().buildTelemetryBundle(awkward, laps()))

        assertTrue("Name should start with the bundle prefix: ${zip.name}",
            zip.name.startsWith(SessionShareBuilder.BUNDLE_PREFIX))
        assertTrue("Name must not contain path or quote characters: ${zip.name}",
            zip.name.none { it in "/\\:\"" })
        assertTrue(zip.name.endsWith(".zip"))
    }

    // --- Failures are values, never exceptions (SRS SH-10) ---

    @Test
    fun missingTelemetryFileIsReportedRatherThanThrown() {
        val result = builder().buildTelemetryBundle(
            session(File(telemetryDir, "does_not_exist.jsonl").path),
            laps()
        )

        assertEquals(
            SessionShareBuilder.ShareResult.Failure(
                SessionShareBuilder.Reason.TELEMETRY_FILE_MISSING
            ),
            result
        )
    }

    @Test
    fun aDirectoryMasqueradingAsTheTelemetryFileIsReportedNotThrown() {
        val notAFile = File(telemetryDir, "session_9.jsonl").apply { mkdirs() }

        val result = builder().buildTelemetryBundle(session(notAFile.path), laps())

        assertEquals(
            SessionShareBuilder.ShareResult.Failure(
                SessionShareBuilder.Reason.TELEMETRY_FILE_MISSING
            ),
            result
        )
    }

    @Test
    fun exportSucceedsFromAReadOnlySourceFile() {
        val telemetry = writeTelemetryFile()
        assertTrue("Precondition: source must be read-only", telemetry.setReadOnly())

        val result = builder().buildTelemetryBundle(session(telemetry.path), laps())

        assertTrue("A read-only source must still be exportable", result is SessionShareBuilder.ShareResult.Success)
    }

    // --- Read-only invariant (SRS SH-11) ---

    @Test
    fun exportDoesNotModifyTheSourceTelemetryFile() {
        val telemetry = writeTelemetryFile()
        val bytesBefore = telemetry.readBytes()
        val modifiedBefore = 1_600_000_000_000L
        assertTrue(telemetry.setLastModified(modifiedBefore))

        builder().buildTelemetryBundle(session(telemetry.path), laps())

        assertTrue("Telemetry content must be untouched by an export",
            bytesBefore.contentEquals(telemetry.readBytes()))
        assertEquals("Export must not even restamp the source file",
            modifiedBefore, telemetry.lastModified())
        assertTrue("Export must never delete the source", telemetry.exists())
    }

    @Test
    fun exportWritesNothingOutsideTheShareCache() {
        val telemetry = writeTelemetryFile()
        val telemetryDirBefore = telemetryDir.listFiles()!!.map { it.name }.sorted()

        builder().buildTelemetryBundle(session(telemetry.path), laps())

        assertEquals(
            "The telemetry directory must be untouched",
            telemetryDirBefore,
            telemetryDir.listFiles()!!.map { it.name }.sorted()
        )
    }

    // --- Retention (SRS SH-11: writes confined to the share cache) ---

    @Test
    fun pruneDeletesStaleArtifactsAndKeepsFreshOnes() {
        shareCacheDir.mkdirs()
        val stale = File(shareCacheDir, "${SessionShareBuilder.BUNDLE_PREFIX}old.zip").apply {
            writeText("old")
            setLastModified(currentTime - SessionShareBuilder.RETENTION_MS - 1)
        }
        val staleCard = File(shareCacheDir, "${SessionShareBuilder.CARD_PREFIX}old.png").apply {
            writeText("old")
            setLastModified(currentTime - SessionShareBuilder.RETENTION_MS - 1)
        }
        val fresh = File(shareCacheDir, "${SessionShareBuilder.BUNDLE_PREFIX}new.zip").apply {
            writeText("new")
            setLastModified(currentTime - 1000)
        }

        builder().pruneStaleArtifacts()

        assertFalse("Stale bundle should be pruned", stale.exists())
        assertFalse("Stale card should be pruned", staleCard.exists())
        assertTrue("Fresh bundle must survive", fresh.exists())
    }

    @Test
    fun pruneIgnoresFilesItDidNotCreate() {
        shareCacheDir.mkdirs()
        val foreign = File(shareCacheDir, "someone_elses_file.txt").apply {
            writeText("not ours")
            setLastModified(currentTime - SessionShareBuilder.RETENTION_MS - 1)
        }

        builder().pruneStaleArtifacts()

        assertTrue("Prune must only remove artifacts this class creates", foreign.exists())
    }

    @Test
    fun pruneDoesNotReachOutsideTheShareCacheDirectory() {
        shareCacheDir.mkdirs()
        // A stale file with a matching name, but in the telemetry directory. If prune ever
        // recursed or resolved paths loosely, this is the file that would disappear — and it
        // would take a real user's session data with it.
        val decoy = File(telemetryDir, "${SessionShareBuilder.BUNDLE_PREFIX}decoy.zip").apply {
            writeText("session data")
            setLastModified(currentTime - SessionShareBuilder.RETENTION_MS - 1)
        }
        val nested = File(shareCacheDir, "nested").apply { mkdirs() }
        val nestedStale = File(nested, "${SessionShareBuilder.BUNDLE_PREFIX}nested.zip").apply {
            writeText("nested")
            setLastModified(currentTime - SessionShareBuilder.RETENTION_MS - 1)
        }

        builder().pruneStaleArtifacts()

        assertTrue("Prune must never touch the telemetry directory", decoy.exists())
        assertTrue("Prune must not recurse into subdirectories", nestedStale.exists())
    }

    @Test
    fun buildingABundlePrunesStaleArtifactsFirst() {
        val telemetry = writeTelemetryFile()
        shareCacheDir.mkdirs()
        val stale = File(shareCacheDir, "${SessionShareBuilder.BUNDLE_PREFIX}old.zip").apply {
            writeText("old")
            setLastModified(currentTime - SessionShareBuilder.RETENTION_MS - 1)
        }

        builder().buildTelemetryBundle(session(telemetry.path), laps())

        assertFalse("Each export should leave the cache tidy", stale.exists())
    }

    @Test
    fun bundleIsWrittenIntoTheShareCacheEvenWhenItDoesNotExistYet() {
        val telemetry = writeTelemetryFile()
        assertFalse("Precondition: cache dir absent", shareCacheDir.exists())

        val zip = successFile(builder().buildTelemetryBundle(session(telemetry.path), laps()))

        assertEquals(shareCacheDir.canonicalFile, zip.parentFile.canonicalFile)
    }

    private companion object {
        val TELEMETRY_CONTENT = buildString {
            appendLine("""{"type":"header","trackName":"Kartodromo"}""")
            repeat(20) { i ->
                appendLine("""{"timestampMs":${1_700_000_000_000L + i * 1000},"latitude":37.1,"longitude":-8.1,"speedMs":${20 + i}}""")
            }
        }
    }
}
