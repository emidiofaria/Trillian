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
     * @param startLineLat1 Optional start line latitude point 1
     * @param startLineLng1 Optional start line longitude point 1
     * @param startLineLat2 Optional start line latitude point 2
     * @param startLineLng2 Optional start line longitude point 2
     * @return Response containing the server-assigned session ID
     */
    @Multipart
    @POST("api/v1/sessions/{sessionId}/telemetry")
    suspend fun uploadSession(
        @Path("sessionId") sessionId: Long,
        @Part file: MultipartBody.Part,
        @Part("startLineLat1") startLineLat1: RequestBody? = null,
        @Part("startLineLng1") startLineLng1: RequestBody? = null,
        @Part("startLineLat2") startLineLat2: RequestBody? = null,
        @Part("startLineLng2") startLineLng2: RequestBody? = null
    ): Response<UploadResponse>
}

/**
 * Response from the upload endpoint.
 */
data class UploadResponse(
    val remoteSessionId: String,
    val message: String? = null
)
