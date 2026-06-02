package com.bmw.drivingcoach.ui.recording;

import android.app.Application;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class RecordingViewModel_Factory implements Factory<RecordingViewModel> {
  private final Provider<Application> applicationProvider;

  public RecordingViewModel_Factory(Provider<Application> applicationProvider) {
    this.applicationProvider = applicationProvider;
  }

  @Override
  public RecordingViewModel get() {
    return newInstance(applicationProvider.get());
  }

  public static RecordingViewModel_Factory create(Provider<Application> applicationProvider) {
    return new RecordingViewModel_Factory(applicationProvider);
  }

  public static RecordingViewModel newInstance(Application application) {
    return new RecordingViewModel(application);
  }
}
