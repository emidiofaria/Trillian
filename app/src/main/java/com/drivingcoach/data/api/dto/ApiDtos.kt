package com.drivingcoach.data.api.dto

import com.google.gson.annotations.SerializedName

data class RegisterRequest(
    val email: String,
    val password: String,
    @SerializedName("display_name")
    val displayName: String
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class AuthResponse(
    val token: String,
    @SerializedName("userId")
    val userId: String
)

data class UserDto(
    val id: String,
    val email: String,
    @SerializedName("display_name")
    val displayName: String?
)

data class SessionDto(
    val id: String,
    @SerializedName("track_name")
    val trackName: String,
    @SerializedName("started_at")
    val startedAt: Long,
    @SerializedName("ended_at")
    val endedAt: Long?,
    @SerializedName("processing_status")
    val processingStatus: String,
    @SerializedName("best_lap_ms")
    val bestLapMs: Long?,
    @SerializedName("lap_count")
    val lapCount: Int?
)

data class LapDto(
    val id: String,
    @SerializedName("lap_number")
    val lapNumber: Int,
    @SerializedName("start_ts")
    val startTs: Long,
    @SerializedName("end_ts")
    val endTs: Long,
    @SerializedName("duration_ms")
    val durationMs: Long,
    @SerializedName("sector_1_ms")
    val sector1Ms: Long?,
    @SerializedName("sector_2_ms")
    val sector2Ms: Long?,
    @SerializedName("sector_3_ms")
    val sector3Ms: Long?,
    @SerializedName("is_best_lap")
    val isBestLap: Boolean
)

data class InsightDto(
    val id: String,
    val headline: String,
    val detail: String,
    @SerializedName("generated_at")
    val generatedAt: Long
)

data class SessionDetailDto(
    val session: SessionDto,
    val laps: List<LapDto>,
    val insights: List<InsightDto>
)

data class UploadResponse(
    @SerializedName("sessionId")
    val sessionId: String,
    val status: String
)
