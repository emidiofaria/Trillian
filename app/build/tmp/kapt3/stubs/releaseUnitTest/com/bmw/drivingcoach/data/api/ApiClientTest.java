package com.bmw.drivingcoach.data.api;

import androidx.datastore.core.DataStore;
import androidx.datastore.preferences.core.MutablePreferences;
import androidx.datastore.preferences.core.Preferences;
import com.bmw.drivingcoach.data.api.dto.AuthResponse;
import com.bmw.drivingcoach.data.api.dto.UploadResponse;
import com.bmw.drivingcoach.data.api.dto.UserDto;
import com.bmw.drivingcoach.data.repository.AuthRepository;
import com.bmw.drivingcoach.data.repository.AuthResult;
import com.google.gson.Gson;
import kotlinx.coroutines.flow.Flow;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import java.io.File;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000<\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\u0018\u0002\n\u0002\b\u0007\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\f\u0010\u000f\u001a\u00060\u0010j\u0002`\u0011H\u0007J\f\u0010\u0012\u001a\u00060\u0010j\u0002`\u0011H\u0007J\f\u0010\u0013\u001a\u00060\u0010j\u0002`\u0011H\u0007J\f\u0010\u0014\u001a\u00060\u0010j\u0002`\u0011H\u0007J\b\u0010\u0015\u001a\u00020\u0010H\u0007J\b\u0010\u0016\u001a\u00020\u0010H\u0007J\f\u0010\u0017\u001a\u00060\u0010j\u0002`\u0011H\u0007R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\bX\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\nX\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000b\u001a\u00020\fX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\r\u001a\u00020\u000eX\u0082.\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0018"}, d2 = {"Lcom/bmw/drivingcoach/data/api/ApiClientTest;", "", "()V", "apiService", "Lcom/bmw/drivingcoach/data/api/ApiService;", "authEventBus", "Lcom/bmw/drivingcoach/data/api/AuthEventBus;", "authRepository", "Lcom/bmw/drivingcoach/data/repository/AuthRepository;", "fakeDataStore", "Lcom/bmw/drivingcoach/data/api/FakeDataStore;", "gson", "Lcom/google/gson/Gson;", "mockWebServer", "Lokhttp3/mockwebserver/MockWebServer;", "401 on any request emits session expired via AuthEventBus", "", "Lkotlinx/coroutines/test/TestResult;", "login failure returns error", "login success stores token in DataStore", "register success stores token", "setup", "tearDown", "uploadTelemetry sends correct multipart fields", "app_releaseUnitTest"})
public final class ApiClientTest {
    private okhttp3.mockwebserver.MockWebServer mockWebServer;
    private com.bmw.drivingcoach.data.api.ApiService apiService;
    private com.bmw.drivingcoach.data.api.FakeDataStore fakeDataStore;
    private com.bmw.drivingcoach.data.api.AuthEventBus authEventBus;
    private com.bmw.drivingcoach.data.repository.AuthRepository authRepository;
    @org.jetbrains.annotations.NotNull()
    private final com.google.gson.Gson gson = null;
    
    public ApiClientTest() {
        super();
    }
    
    @org.junit.Before()
    public final void setup() {
    }
    
    @org.junit.After()
    public final void tearDown() {
    }
}