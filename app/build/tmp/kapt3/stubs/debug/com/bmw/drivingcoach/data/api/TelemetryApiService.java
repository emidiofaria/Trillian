package com.bmw.drivingcoach.data.api;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Response;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Path;

/**
 * API service for telemetry data upload.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\"\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\t\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\bf\u0018\u00002\u00020\u0001J(\u0010\u0002\u001a\b\u0012\u0004\u0012\u00020\u00040\u00032\b\b\u0001\u0010\u0005\u001a\u00020\u00062\b\b\u0001\u0010\u0007\u001a\u00020\bH\u00a7@\u00a2\u0006\u0002\u0010\t\u00a8\u0006\n"}, d2 = {"Lcom/bmw/drivingcoach/data/api/TelemetryApiService;", "", "uploadSession", "Lretrofit2/Response;", "Lcom/bmw/drivingcoach/data/api/UploadResponse;", "sessionId", "", "file", "Lokhttp3/MultipartBody$Part;", "(JLokhttp3/MultipartBody$Part;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "app_debug"})
public abstract interface TelemetryApiService {
    
    /**
     * Uploads a telemetry file for a session.
     *
     * @param sessionId The local session ID
     * @param file The telemetry JSONL file as multipart
     * @return Response containing the server-assigned session ID
     */
    @retrofit2.http.Multipart()
    @retrofit2.http.POST(value = "api/v1/sessions/{sessionId}/telemetry")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object uploadSession(@retrofit2.http.Path(value = "sessionId")
    long sessionId, @retrofit2.http.Part()
    @org.jetbrains.annotations.NotNull()
    okhttp3.MultipartBody.Part file, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super retrofit2.Response<com.bmw.drivingcoach.data.api.UploadResponse>> $completion);
}