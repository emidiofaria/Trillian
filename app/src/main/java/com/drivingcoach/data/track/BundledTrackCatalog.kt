package com.drivingcoach.data.track

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "BundledTrackCatalog"
private const val ASSET_PATH = "tracks/tracks.json"

/**
 * The circuits shipped inside the app, read from `assets/tracks/tracks.json`.
 *
 * Bundled rather than fetched because the app holds no network permission in
 * release builds (NF-20) and because a track the driver cannot reach without a
 * connection is useless at the one moment it is needed - standing in a paddock
 * with no signal.
 *
 * A malformed or missing catalogue is not fatal. The driver can always capture a
 * line by hand, which is what every session did before this existed, so a parse
 * failure costs a convenience rather than the session.
 */
@Singleton
class BundledTrackCatalog @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val tracks: List<Track> by lazy { load() }

    fun all(): List<Track> = tracks

    fun byId(id: String): Track? = tracks.firstOrNull { it.id == id }

    private fun load(): List<Track> = try {
        val json = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        parse(json)
    } catch (e: Exception) {
        Log.e(TAG, "Could not read the bundled track catalogue", e)
        emptyList()
    }

    companion object {
        /** Exposed so the catalogue shipped in the APK can be parsed under test. */
        fun parse(json: String): List<Track> {
            val root = JSONObject(json)
            val array = root.getJSONArray("tracks")
            return (0 until array.length()).mapNotNull { i ->
                runCatching { parseTrack(array.getJSONObject(i)) }
                    .onFailure { Log.e(TAG, "Skipping malformed track at index $i", it) }
                    .getOrNull()
            }
        }

        private fun parseTrack(o: JSONObject): Track {
            val line = o.getJSONObject("startLine")
            return Track(
                id = o.getString("id"),
                name = o.getString("name"),
                location = o.optStringOrNull("location"),
                startLine = TrackStartLine(
                    lat1 = line.getDouble("lat1"),
                    lng1 = line.getDouble("lng1"),
                    lat2 = line.getDouble("lat2"),
                    lng2 = line.getDouble("lng2"),
                    source = line.optStringOrNull("source").toGeometrySource(),
                    recordedAt = line.optStringOrNull("recordedAt")
                ),
                travelHeadingDeg = o.optDoubleOrNull("travelHeadingDeg"),
                lengthM = if (o.has("lengthM")) o.getInt("lengthM") else null,
                cornerCount = if (o.has("cornerCount")) o.getInt("cornerCount") else null,
                fastestLapMs = if (o.has("fastestLapMs")) o.getLong("fastestLapMs") else null,
                slowestLapMs = if (o.has("slowestLapMs")) o.getLong("slowestLapMs") else null,
                centreline = o.optJSONObject("centreline")?.let(::parseCentreline),
                isBundled = true
            )
        }

        private fun parseCentreline(o: JSONObject): Centreline {
            val array = o.getJSONArray("points")
            val points = (0 until array.length()).map { i ->
                val pair = array.getJSONArray(i)
                TrackPoint(pair.getDouble(0), pair.getDouble(1))
            }
            return Centreline(
                source = o.optStringOrNull("source").toGeometrySource(),
                method = o.optStringOrNull("method"),
                surveyedAt = o.optStringOrNull("surveyedAt"),
                surveyedBy = o.optStringOrNull("surveyedBy"),
                points = points
            )
        }

        private fun JSONObject.optStringOrNull(key: String): String? =
            if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

        private fun JSONObject.optDoubleOrNull(key: String): Double? =
            if (has(key) && !isNull(key)) getDouble(key) else null

        /**
         * An unrecognised provenance is recorded as captured-in-app rather than
         * rejected: a catalogue that refuses to load because a future build added a
         * new source would cost the driver their circuit list over a label.
         */
        private fun String?.toGeometrySource(): GeometrySource =
            GeometrySource.entries.firstOrNull { it.name == this }
                ?: GeometrySource.CAPTURED_IN_APP
    }
}
