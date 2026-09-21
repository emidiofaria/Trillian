package com.drivingcoach.data.track

import com.drivingcoach.data.db.entity.TrackEntity
import org.json.JSONArray

/**
 * Translation between the stored form of a saved circuit and the one the app works
 * with. Bundled circuits never pass through here - they are read from assets and
 * are read-only by construction.
 */
internal object TrackMapper {

    fun toDomain(entity: TrackEntity): Track = Track(
        id = entity.id,
        name = entity.name,
        location = entity.location,
        startLine = TrackStartLine(
            lat1 = entity.startLineLat1,
            lng1 = entity.startLineLng1,
            lat2 = entity.startLineLat2,
            lng2 = entity.startLineLng2,
            source = entity.startLineSource.toGeometrySource(),
            recordedAt = entity.startLineRecordedAt
        ),
        travelHeadingDeg = entity.travelHeadingDeg,
        lengthM = entity.lengthM,
        cornerCount = entity.cornerCount,
        fastestLapMs = entity.fastestLapMs,
        slowestLapMs = entity.slowestLapMs,
        centreline = entity.centrelineJson?.let { json ->
            Centreline(
                source = entity.centrelineSource.toGeometrySource(),
                method = entity.centrelineMethod,
                surveyedAt = entity.centrelineSurveyedAt,
                surveyedBy = entity.centrelineSurveyedBy,
                points = parsePoints(json)
            )
        },
        isBundled = false
    )

    fun toEntity(track: Track, createdAt: Long = System.currentTimeMillis()): TrackEntity = TrackEntity(
        id = track.id,
        name = track.name,
        location = track.location,
        startLineLat1 = track.startLine.lat1,
        startLineLng1 = track.startLine.lng1,
        startLineLat2 = track.startLine.lat2,
        startLineLng2 = track.startLine.lng2,
        startLineSource = track.startLine.source.name,
        startLineRecordedAt = track.startLine.recordedAt,
        travelHeadingDeg = track.travelHeadingDeg,
        lengthM = track.lengthM,
        cornerCount = track.cornerCount,
        fastestLapMs = track.fastestLapMs,
        slowestLapMs = track.slowestLapMs,
        centrelineSource = track.centreline?.source?.name,
        centrelineMethod = track.centreline?.method,
        centrelineSurveyedAt = track.centreline?.surveyedAt,
        centrelineSurveyedBy = track.centreline?.surveyedBy,
        centrelineJson = track.centreline?.let { writePoints(it.points) },
        createdAt = createdAt
    )

    private fun parsePoints(json: String): List<TrackPoint> {
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val pair = array.getJSONArray(i)
            TrackPoint(pair.getDouble(0), pair.getDouble(1))
        }
    }

    private fun writePoints(points: List<TrackPoint>): String {
        val array = JSONArray()
        points.forEach { point ->
            array.put(JSONArray().put(point.latitude).put(point.longitude))
        }
        return array.toString()
    }

    private fun String?.toGeometrySource(): GeometrySource =
        GeometrySource.entries.firstOrNull { it.name == this } ?: GeometrySource.CAPTURED_IN_APP
}
