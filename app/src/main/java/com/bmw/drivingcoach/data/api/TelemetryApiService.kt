package com.bmw.drivingcoach.data.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

/**
 * API service for telemetry data upload.
 */
interface TelemetryApiService {

    /**
     * Uploads a telemetry file for a session.
     * 
     * @param sessionId The local session ID
     * @param file The telemetry JSONL file as multipart
     * @return Response containing the server-assigned session ID
     */
    @Multipart
    @POST("api/v1/sessions/{sessionId}/telemetry")
    suspend fun uploadSession(
        @Path("sessionId") sessionId: Long,
        @Part file: MultipartBody.Part
    ): Response<UploadResponse>
}

/**
 * Response from the upload endpoint.
 */
data class UploadResponse(
    val remoteSessionId: String,
    val message: String? = null
)
