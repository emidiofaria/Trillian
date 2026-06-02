package com.bmw.drivingcoach.data.api

import com.bmw.drivingcoach.data.api.dto.AuthResponse
import com.bmw.drivingcoach.data.api.dto.LoginRequest
import com.bmw.drivingcoach.data.api.dto.RegisterRequest
import com.bmw.drivingcoach.data.api.dto.SessionDetailDto
import com.bmw.drivingcoach.data.api.dto.SessionDto
import com.bmw.drivingcoach.data.api.dto.UploadResponse
import com.bmw.drivingcoach.data.api.dto.UserDto
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface ApiService {

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @GET("auth/me")
    suspend fun getMe(): Response<UserDto>

    @Multipart
    @POST("telemetry/upload")
    suspend fun uploadTelemetry(
        @Part("sessionId") sessionId: RequestBody,
        @Part("trackName") trackName: RequestBody,
        @Part("startedAt") startedAt: RequestBody,
        @Part("endedAt") endedAt: RequestBody,
        @Part file: MultipartBody.Part
    ): Response<UploadResponse>

    @GET("sessions")
    suspend fun getSessions(): Response<List<SessionDto>>

    @GET("sessions/{id}")
    suspend fun getSession(@Path("id") id: String): Response<SessionDetailDto>
}
