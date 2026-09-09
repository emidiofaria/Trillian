package com.drivingcoach.lap

import org.json.JSONObject
import java.io.File

/**
 * Loads recorded sessions from test resources and replays them through
 * [LocalLapDetector].
 *
 * Lap detection has failed four times in the field (incidents 02, 03, 09, 13) and
 * until now every fix was reasoned about analytically, because nothing could run
 * a real recorded session through the detector and check the answer. This harness
 * is that missing mechanism: a fixture is a real session plus the lap times its
 * driver actually drove, so a change to the algorithm can be judged against
 * reality rather than against an argument.
 *
 * Fixtures live in `src/test/resources/lapfixtures/<name>/` and consist of the
 * `telemetry.jsonl` and `session.json` exported from the device, unmodified.
 */
object LapReplayHarness {

    /** A recorded session, materialised on disk so [LocalLapDetector] can read it. */
    data class Fixture(
        val name: String,
        val telemetryFile: File,
        val startLine: LocalLapDetector.StartLine,
        val trackName: String,
        val appVersionName: String,
        val deviceModel: String,
        val lapsRecordedByApp: Int
    )

    /**
     * Copies the named fixture out of the test resources into [tempDir] and reads
     * its start line from the exported `session.json`.
     *
     * The telemetry is copied rather than read in place because [LocalLapDetector]
     * takes a [File]; the copy is byte-identical to what the device exported.
     */
    fun load(name: String, tempDir: File): Fixture {
        val base = "/lapfixtures/$name"

        val telemetry = File(tempDir, "telemetry.jsonl")
        telemetry.outputStream().use { out ->
            requireNotNull(javaClass.getResourceAsStream("$base/telemetry.jsonl")) {
                "Fixture telemetry not found: $base/telemetry.jsonl"
            }.use { it.copyTo(out) }
        }

        val sessionJson = requireNotNull(javaClass.getResourceAsStream("$base/session.json")) {
            "Fixture metadata not found: $base/session.json"
        }.bufferedReader().use { it.readText() }

        val root = JSONObject(sessionJson)
        val session = root.getJSONObject("session")
        val line = session.getJSONObject("startLine")

        return Fixture(
            name = name,
            telemetryFile = telemetry,
            startLine = LocalLapDetector.StartLine(
                lat1 = line.getDouble("lat1"),
                lng1 = line.getDouble("lng1"),
                lat2 = line.getDouble("lat2"),
                lng2 = line.getDouble("lng2")
            ),
            trackName = session.getString("trackName"),
            appVersionName = root.getJSONObject("app").getString("versionName"),
            deviceModel = root.getJSONObject("device").getString("model"),
            lapsRecordedByApp = root.getJSONArray("laps").length()
        )
    }

    /** Runs the detector over a fixture, exactly as the app does after a session. */
    fun detect(
        fixture: Fixture,
        detector: LocalLapDetector = LocalLapDetector()
    ): LocalLapDetector.DetectionResult = detector.detectLaps(fixture.telemetryFile, fixture.startLine)

    /** Lap durations in seconds, for readable assertions and failure messages. */
    fun lapSeconds(result: LocalLapDetector.DetectionResult): List<Double> =
        when (result) {
            is LocalLapDetector.DetectionResult.Success ->
                result.laps.map { it.durationMs / 1000.0 }
            else -> emptyList()
        }

    /** A one-line description of any result, so failures explain themselves. */
    fun describe(result: LocalLapDetector.DetectionResult): String = when (result) {
        is LocalLapDetector.DetectionResult.Success ->
            "Success(${result.laps.size} laps: " +
                result.laps.joinToString { "%.2fs".format(it.durationMs / 1000.0) } + ")"
        is LocalLapDetector.DetectionResult.InsufficientLaps ->
            "InsufficientLaps(${result.lapCount}) -> user sees " +
                if (result.lapCount > 0) {
                    "\"Only ${result.lapCount} lap detected. Minimum 2 laps needed for comparison.\""
                } else {
                    "\"No laps detected. Complete at least 2 laps.\""
                }
        is LocalLapDetector.DetectionResult.NoStartLine -> "NoStartLine(${result.message})"
        is LocalLapDetector.DetectionResult.Error -> "Error(${result.message})"
    }
}
