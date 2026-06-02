package com.bmw.drivingcoach.data.api;

import com.bmw.drivingcoach.data.api.dto.AuthResponse;
import com.bmw.drivingcoach.data.api.dto.LoginRequest;
import com.bmw.drivingcoach.data.api.dto.RegisterRequest;
import com.bmw.drivingcoach.data.api.dto.SessionDetailDto;
import com.bmw.drivingcoach.data.api.dto.SessionDto;
import com.bmw.drivingcoach.data.api.dto.UploadResponse;
import com.bmw.drivingcoach.data.api.dto.UserDto;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Response;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Path;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000Z\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0002\bf\u0018\u00002\u00020\u0001J\u0014\u0010\u0002\u001a\b\u0012\u0004\u0012\u00020\u00040\u0003H\u00a7@\u00a2\u0006\u0002\u0010\u0005J\u001e\u0010\u0006\u001a\b\u0012\u0004\u0012\u00020\u00070\u00032\b\b\u0001\u0010\b\u001a\u00020\tH\u00a7@\u00a2\u0006\u0002\u0010\nJ\u001a\u0010\u000b\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\r0\f0\u0003H\u00a7@\u00a2\u0006\u0002\u0010\u0005J\u001e\u0010\u000e\u001a\b\u0012\u0004\u0012\u00020\u000f0\u00032\b\b\u0001\u0010\u0010\u001a\u00020\u0011H\u00a7@\u00a2\u0006\u0002\u0010\u0012J\u001e\u0010\u0013\u001a\b\u0012\u0004\u0012\u00020\u000f0\u00032\b\b\u0001\u0010\u0010\u001a\u00020\u0014H\u00a7@\u00a2\u0006\u0002\u0010\u0015JF\u0010\u0016\u001a\b\u0012\u0004\u0012\u00020\u00170\u00032\b\b\u0001\u0010\u0018\u001a\u00020\u00192\b\b\u0001\u0010\u001a\u001a\u00020\u00192\b\b\u0001\u0010\u001b\u001a\u00020\u00192\b\b\u0001\u0010\u001c\u001a\u00020\u00192\b\b\u0001\u0010\u001d\u001a\u00020\u001eH\u00a7@\u00a2\u0006\u0002\u0010\u001f\u00a8\u0006 "}, d2 = {"Lcom/bmw/drivingcoach/data/api/ApiService;", "", "getMe", "Lretrofit2/Response;", "Lcom/bmw/drivingcoach/data/api/dto/UserDto;", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getSession", "Lcom/bmw/drivingcoach/data/api/dto/SessionDetailDto;", "id", "", "(Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getSessions", "", "Lcom/bmw/drivingcoach/data/api/dto/SessionDto;", "login", "Lcom/bmw/drivingcoach/data/api/dto/AuthResponse;", "request", "Lcom/bmw/drivingcoach/data/api/dto/LoginRequest;", "(Lcom/bmw/drivingcoach/data/api/dto/LoginRequest;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "register", "Lcom/bmw/drivingcoach/data/api/dto/RegisterRequest;", "(Lcom/bmw/drivingcoach/data/api/dto/RegisterRequest;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "uploadTelemetry", "Lcom/bmw/drivingcoach/data/api/dto/UploadResponse;", "sessionId", "Lokhttp3/RequestBody;", "trackName", "startedAt", "endedAt", "file", "Lokhttp3/MultipartBody$Part;", "(Lokhttp3/RequestBody;Lokhttp3/RequestBody;Lokhttp3/RequestBody;Lokhttp3/RequestBody;Lokhttp3/MultipartBody$Part;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "app_release"})
public abstract interface ApiService {
    
    @retrofit2.http.POST(value = "auth/register")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object register(@retrofit2.http.Body()
    @org.jetbrains.annotations.NotNull()
    com.bmw.drivingcoach.data.api.dto.RegisterRequest request, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super retrofit2.Response<com.bmw.drivingcoach.data.api.dto.AuthResponse>> $completion);
    
    @retrofit2.http.POST(value = "auth/login")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object login(@retrofit2.http.Body()
    @org.jetbrains.annotations.NotNull()
    com.bmw.drivingcoach.data.api.dto.LoginRequest request, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super retrofit2.Response<com.bmw.drivingcoach.data.api.dto.AuthResponse>> $completion);
    
    @retrofit2.http.GET(value = "auth/me")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getMe(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super retrofit2.Response<com.bmw.drivingcoach.data.api.dto.UserDto>> $completion);
    
    @retrofit2.http.Multipart()
    @retrofit2.http.POST(value = "telemetry/upload")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object uploadTelemetry(@retrofit2.http.Part(value = "sessionId")
    @org.jetbrains.annotations.NotNull()
    okhttp3.RequestBody sessionId, @retrofit2.http.Part(value = "trackName")
    @org.jetbrains.annotations.NotNull()
    okhttp3.RequestBody trackName, @retrofit2.http.Part(value = "startedAt")
    @org.jetbrains.annotations.NotNull()
    okhttp3.RequestBody startedAt, @retrofit2.http.Part(value = "endedAt")
    @org.jetbrains.annotations.NotNull()
    okhttp3.RequestBody endedAt, @retrofit2.http.Part()
    @org.jetbrains.annotations.NotNull()
    okhttp3.MultipartBody.Part file, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super retrofit2.Response<com.bmw.drivingcoach.data.api.dto.UploadResponse>> $completion);
    
    @retrofit2.http.GET(value = "sessions")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getSessions(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super retrofit2.Response<java.util.List<com.bmw.drivingcoach.data.api.dto.SessionDto>>> $completion);
    
    @retrofit2.http.GET(value = "sessions/{id}")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getSession(@retrofit2.http.Path(value = "id")
    @org.jetbrains.annotations.NotNull()
    java.lang.String id, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super retrofit2.Response<com.bmw.drivingcoach.data.api.dto.SessionDetailDto>> $completion);
}