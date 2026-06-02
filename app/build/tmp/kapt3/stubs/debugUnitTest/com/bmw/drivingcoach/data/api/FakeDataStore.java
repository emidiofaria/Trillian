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

/**
 * Simple fake DataStore implementation for testing using a map
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000J\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010%\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0006\n\u0002\u0010\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0003J\b\u0010\u000e\u001a\u00020\u0002H\u0002J!\u0010\u000f\u001a\u0004\u0018\u0001H\u0010\"\u0004\b\u0000\u0010\u00102\f\u0010\u0011\u001a\b\u0012\u0004\u0012\u0002H\u00100\f\u00a2\u0006\u0002\u0010\u0012J\'\u0010\u0013\u001a\u00020\u0014\"\u0004\b\u0000\u0010\u00102\f\u0010\u0011\u001a\b\u0012\u0004\u0012\u0002H\u00100\f2\u0006\u0010\u0015\u001a\u0002H\u0010\u00a2\u0006\u0002\u0010\u0016JA\u0010\u0017\u001a\u00020\u000221\u0010\u0018\u001a-\b\u0001\u0012\u0013\u0012\u00110\u0002\u00a2\u0006\f\b\u001a\u0012\b\b\u001b\u0012\u0004\b\b(\u001c\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00020\u001d\u0012\u0006\u0012\u0004\u0018\u00010\r0\u0019H\u0096@\u00a2\u0006\u0002\u0010\u001eR\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u0005X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\b\u0012\u0004\u0012\u00020\u00020\tX\u0082\u0004\u00a2\u0006\u0002\n\u0000R \u0010\n\u001a\u0014\u0012\b\u0012\u0006\u0012\u0002\b\u00030\f\u0012\u0006\u0012\u0004\u0018\u00010\r0\u000bX\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u001f"}, d2 = {"Lcom/bmw/drivingcoach/data/api/FakeDataStore;", "Landroidx/datastore/core/DataStore;", "Landroidx/datastore/preferences/core/Preferences;", "()V", "data", "Lkotlinx/coroutines/flow/Flow;", "getData", "()Lkotlinx/coroutines/flow/Flow;", "prefsFlow", "Lkotlinx/coroutines/flow/MutableStateFlow;", "storage", "", "Landroidx/datastore/preferences/core/Preferences$Key;", "", "createPreferences", "getValue", "T", "key", "(Landroidx/datastore/preferences/core/Preferences$Key;)Ljava/lang/Object;", "setValue", "", "value", "(Landroidx/datastore/preferences/core/Preferences$Key;Ljava/lang/Object;)V", "updateData", "transform", "Lkotlin/Function2;", "Lkotlin/ParameterName;", "name", "t", "Lkotlin/coroutines/Continuation;", "(Lkotlin/jvm/functions/Function2;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "app_debugUnitTest"})
public final class FakeDataStore implements androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> {
    @org.jetbrains.annotations.NotNull()
    private final java.util.Map<androidx.datastore.preferences.core.Preferences.Key<?>, java.lang.Object> storage = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<androidx.datastore.preferences.core.Preferences> prefsFlow = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.Flow<androidx.datastore.preferences.core.Preferences> data = null;
    
    public FakeDataStore() {
        super();
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.NotNull()
    public kotlinx.coroutines.flow.Flow<androidx.datastore.preferences.core.Preferences> getData() {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object updateData(@org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function2<? super androidx.datastore.preferences.core.Preferences, ? super kotlin.coroutines.Continuation<? super androidx.datastore.preferences.core.Preferences>, ? extends java.lang.Object> transform, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super androidx.datastore.preferences.core.Preferences> $completion) {
        return null;
    }
    
    public final <T extends java.lang.Object>void setValue(@org.jetbrains.annotations.NotNull()
    androidx.datastore.preferences.core.Preferences.Key<T> key, T value) {
    }
    
    @kotlin.Suppress(names = {"UNCHECKED_CAST"})
    @org.jetbrains.annotations.Nullable()
    public final <T extends java.lang.Object>T getValue(@org.jetbrains.annotations.NotNull()
    androidx.datastore.preferences.core.Preferences.Key<T> key) {
        return null;
    }
    
    private final androidx.datastore.preferences.core.Preferences createPreferences() {
        return null;
    }
}