package com.bmw.drivingcoach.data.worker;

import android.content.Context;
import android.util.Log;
import androidx.hilt.work.HiltWorker;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.CoroutineWorker;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkerParameters;
import com.bmw.drivingcoach.data.api.TelemetryApiService;
import com.bmw.drivingcoach.data.db.dao.SessionDao;
import com.bmw.drivingcoach.data.db.entity.UploadStatus;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;
import okhttp3.MultipartBody;
import java.io.File;
import java.util.concurrent.TimeUnit;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000@\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\t\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u0007\u0018\u0000 \u00152\u00020\u0001:\u0001\u0015B+\b\u0007\u0012\b\b\u0001\u0010\u0002\u001a\u00020\u0003\u0012\b\b\u0001\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\u0006\u0010\b\u001a\u00020\t\u00a2\u0006\u0002\u0010\nJ\u000e\u0010\u000b\u001a\u00020\fH\u0096@\u00a2\u0006\u0002\u0010\rJ\u001e\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u0010\u001a\u00020\u00112\u0006\u0010\u0012\u001a\u00020\u0013H\u0082@\u00a2\u0006\u0002\u0010\u0014R\u000e\u0010\u0006\u001a\u00020\u0007X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\tX\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0016"}, d2 = {"Lcom/bmw/drivingcoach/data/worker/TelemetryUploadWorker;", "Landroidx/work/CoroutineWorker;", "context", "Landroid/content/Context;", "workerParams", "Landroidx/work/WorkerParameters;", "sessionDao", "Lcom/bmw/drivingcoach/data/db/dao/SessionDao;", "telemetryApiService", "Lcom/bmw/drivingcoach/data/api/TelemetryApiService;", "(Landroid/content/Context;Landroidx/work/WorkerParameters;Lcom/bmw/drivingcoach/data/db/dao/SessionDao;Lcom/bmw/drivingcoach/data/api/TelemetryApiService;)V", "doWork", "Landroidx/work/ListenableWorker$Result;", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "updateStatus", "", "sessionId", "", "status", "Lcom/bmw/drivingcoach/data/db/entity/UploadStatus;", "(JLcom/bmw/drivingcoach/data/db/entity/UploadStatus;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "Companion", "app_debug"})
@androidx.hilt.work.HiltWorker()
public final class TelemetryUploadWorker extends androidx.work.CoroutineWorker {
    @org.jetbrains.annotations.NotNull()
    private final com.bmw.drivingcoach.data.db.dao.SessionDao sessionDao = null;
    @org.jetbrains.annotations.NotNull()
    private final com.bmw.drivingcoach.data.api.TelemetryApiService telemetryApiService = null;
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String TAG = "TelemetryUploadWorker";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String KEY_SESSION_ID = "session_id";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String TAG_TELEMETRY_UPLOAD = "telemetry_upload";
    private static final long INITIAL_BACKOFF_SECONDS = 10L;
    private static final int MAX_RETRIES = 5;
    @org.jetbrains.annotations.NotNull()
    public static final com.bmw.drivingcoach.data.worker.TelemetryUploadWorker.Companion Companion = null;
    
    @dagger.assisted.AssistedInject()
    public TelemetryUploadWorker(@dagger.assisted.Assisted()
    @org.jetbrains.annotations.NotNull()
    android.content.Context context, @dagger.assisted.Assisted()
    @org.jetbrains.annotations.NotNull()
    androidx.work.WorkerParameters workerParams, @org.jetbrains.annotations.NotNull()
    com.bmw.drivingcoach.data.db.dao.SessionDao sessionDao, @org.jetbrains.annotations.NotNull()
    com.bmw.drivingcoach.data.api.TelemetryApiService telemetryApiService) {
        super(null, null);
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object doWork(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super androidx.work.ListenableWorker.Result> $completion) {
        return null;
    }
    
    private final java.lang.Object updateStatus(long sessionId, com.bmw.drivingcoach.data.db.entity.UploadStatus status, kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000(\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\t\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0002\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\u000b\u001a\u00020\f2\u0006\u0010\r\u001a\u00020\u0004R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\bX\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\u0006X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\u0006X\u0086T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u000e"}, d2 = {"Lcom/bmw/drivingcoach/data/worker/TelemetryUploadWorker$Companion;", "", "()V", "INITIAL_BACKOFF_SECONDS", "", "KEY_SESSION_ID", "", "MAX_RETRIES", "", "TAG", "TAG_TELEMETRY_UPLOAD", "buildRequest", "Landroidx/work/OneTimeWorkRequest;", "sessionId", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
        
        /**
         * Creates a OneTimeWorkRequest for uploading telemetry data.
         */
        @org.jetbrains.annotations.NotNull()
        public final androidx.work.OneTimeWorkRequest buildRequest(long sessionId) {
            return null;
        }
    }
}