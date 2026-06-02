package com.bmw.drivingcoach.data.worker;

import android.content.Context;
import androidx.work.WorkerParameters;
import dagger.internal.DaggerGenerated;
import dagger.internal.InstanceFactory;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class TelemetryUploadWorker_AssistedFactory_Impl implements TelemetryUploadWorker_AssistedFactory {
  private final TelemetryUploadWorker_Factory delegateFactory;

  TelemetryUploadWorker_AssistedFactory_Impl(TelemetryUploadWorker_Factory delegateFactory) {
    this.delegateFactory = delegateFactory;
  }

  @Override
  public TelemetryUploadWorker create(Context arg0, WorkerParameters arg1) {
    return delegateFactory.get(arg0, arg1);
  }

  public static Provider<TelemetryUploadWorker_AssistedFactory> create(
      TelemetryUploadWorker_Factory delegateFactory) {
    return InstanceFactory.create(new TelemetryUploadWorker_AssistedFactory_Impl(delegateFactory));
  }

  public static dagger.internal.Provider<TelemetryUploadWorker_AssistedFactory> createFactoryProvider(
      TelemetryUploadWorker_Factory delegateFactory) {
    return InstanceFactory.create(new TelemetryUploadWorker_AssistedFactory_Impl(delegateFactory));
  }
}
