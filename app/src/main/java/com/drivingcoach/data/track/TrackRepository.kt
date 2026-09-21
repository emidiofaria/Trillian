package com.drivingcoach.data.track

import com.drivingcoach.data.db.dao.TrackDao
import com.drivingcoach.ui.tracksetup.TrackSetupState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

sealed class SaveTrackResult {
    data class Success(val track: Track) : SaveTrackResult()
    data class Rejected(val reason: String) : SaveTrackResult()
}

/**
 * The single place the app asks "what circuits do I know about?".
 *
 * Two stores sit behind it - the read-only catalogue shipped in the APK and the
 * circuits the driver saved - and callers are deliberately not told which is
 * which beyond [Track.isBundled], because selecting a circuit works the same way
 * either way. What differs is only what may be edited.
 */
@Singleton
class TrackRepository @Inject constructor(
    private val bundled: BundledTrackCatalog,
    private val trackDao: TrackDao
) {
    /** Saved circuits first, most recently driven at the top, then the bundled ones. */
    fun observeTracks(): Flow<List<Track>> = trackDao.observeAll().map { saved ->
        saved.map(TrackMapper::toDomain) + bundled.all()
    }

    suspend fun getTrack(id: String): Track? =
        trackDao.getById(id)?.let(TrackMapper::toDomain) ?: bundled.byId(id)

    /** Recorded when a session starts, so the list stays ordered by where you actually drive. */
    suspend fun markUsed(id: String) {
        if (trackDao.getById(id) != null) trackDao.markUsed(id)
    }

    suspend fun rename(id: String, name: String) {
        require(bundled.byId(id) == null) { "Bundled circuits cannot be renamed" }
        trackDao.rename(id, name.trim())
    }

    suspend fun delete(id: String) {
        require(bundled.byId(id) == null) { "Bundled circuits cannot be deleted" }
        trackDao.delete(id)
    }

    /**
     * Saves a start/finish line the driver just captured as a reusable circuit.
     *
     * A line shorter than the one Track Setup would have accepted is refused rather
     * than stored. Saving it would be worse than not offering to save at all: an
     * unusable line captured once becomes an unusable line reused every visit, and
     * the driver has no way to see that from a name in a list.
     */
    suspend fun saveCapturedTrack(
        name: String,
        lat1: Double,
        lng1: Double,
        lat2: Double,
        lng2: Double
    ): SaveTrackResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return SaveTrackResult.Rejected("Give the circuit a name so you can find it again.")
        }

        val line = TrackStartLine(lat1, lng1, lat2, lng2, GeometrySource.CAPTURED_IN_APP)
        val length = line.lengthM()
        if (length < TrackSetupState.MIN_LINE_DISTANCE_M) {
            return SaveTrackResult.Rejected(
                "The two points are only %.1f m apart, which is too close to detect a lap.".format(
                    Locale.US, length
                )
            )
        }

        val track = Track(
            id = newId(trimmed),
            name = trimmed,
            startLine = line,
            isBundled = false
        )
        trackDao.upsert(TrackMapper.toEntity(track))
        return SaveTrackResult.Success(track)
    }

    /**
     * Ids are derived from the name but made unique with a timestamp. Two visits to
     * two different circuits that happen to share a name must not overwrite one
     * another, and the id is what a stored session points at.
     */
    private fun newId(name: String): String {
        val slug = name.lowercase(Locale.US)
            .map { if (it.isLetterOrDigit()) it else '-' }
            .joinToString("")
            .trim('-')
            .replace(Regex("-+"), "-")
            .take(24)
            .ifEmpty { "track" }
        return "user-$slug-${System.currentTimeMillis()}"
    }
}
