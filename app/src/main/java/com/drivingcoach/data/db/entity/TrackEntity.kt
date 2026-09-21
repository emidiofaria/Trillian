package com.drivingcoach.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A circuit the driver saved on this device.
 *
 * Only user-authored circuits live here. The ones shipped with the app are read
 * from assets, so that updating them is a matter of releasing a new build rather
 * than writing a migration, and so that a driver can never end up with a stale
 * copy of a circuit we have since corrected.
 *
 * The centreline is stored as a JSON array of `[lat, lng]` pairs rather than as a
 * child table. It is written once, read whole, and never queried by point, so a
 * second table with a foreign key would buy nothing but joins.
 */
@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val location: String? = null,
    val startLineLat1: Double,
    val startLineLng1: Double,
    val startLineLat2: Double,
    val startLineLng2: Double,
    val startLineSource: String,
    val startLineRecordedAt: String? = null,
    val travelHeadingDeg: Double? = null,
    val lengthM: Int? = null,
    val cornerCount: Int? = null,
    val fastestLapMs: Long? = null,
    val slowestLapMs: Long? = null,
    val centrelineSource: String? = null,
    val centrelineMethod: String? = null,
    val centrelineSurveyedAt: String? = null,
    val centrelineSurveyedBy: String? = null,
    /** JSON array of `[latitude, longitude]` pairs, or null when no centreline was surveyed. */
    val centrelineJson: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long? = null
)
