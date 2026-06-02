package com.bmw.drivingcoach.data.worker;

import android.content.Context;
import androidx.work.WorkerParameters;
import com.bmw.drivingcoach.data.api.TelemetryApiService;
import com.bmw.drivingcoach.data.db.dao.SessionDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast"
})
public final class TelemetryUploadWorker_Factory {
  private final Provider<SessionDao> sessionDaoProvider;

  private final Provider<TelemetryApiService> telemetryApiServiceProvider;

  public TelemetryUploadWorker_Factory(Provider<SessionDao> sessionDaoProvider,
      Provider<TelemetryApiService> telemetryApiServiceProvider) {
    this.sessionDaoProvider = sessionDaoProvider;
    this.telemetryApiServiceProvider = telemetryApiServiceProvider;
  }

  public TelemetryUploadWorker get(Context context, WorkerParameters workerParams) {
    return newInstance(context, workerParams, sessionDaoProvider.get(), telemetryApiServiceProvider.get());
  }

  public static TelemetryUploadWorker_Factory create(Provider<SessionDao> sessionDaoProvider,
      Provider<TelemetryApiService> telemetryApiServiceProvider) {
    return new TelemetryUploadWorker_Factory(sessionDaoProvider, telemetryApiServiceProvider);
  }

  public static TelemetryUploadWorker newInstance(Context context, WorkerParameters workerParams,
      SessionDao sessionDao, TelemetryApiService telemetryApiService) {
    return new TelemetryUploadWorker(context, workerParams, sessionDao, telemetryApiService);
  }
}
