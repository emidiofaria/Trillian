package com.bmw.drivingcoach.data.api;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import retrofit2.Retrofit;

@ScopeMetadata("javax.inject.Singleton")
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
public final class NetworkModule_ProvideTelemetryApiServiceFactory implements Factory<TelemetryApiService> {
  private final Provider<Retrofit> retrofitProvider;

  public NetworkModule_ProvideTelemetryApiServiceFactory(Provider<Retrofit> retrofitProvider) {
    this.retrofitProvider = retrofitProvider;
  }

  @Override
  public TelemetryApiService get() {
    return provideTelemetryApiService(retrofitProvider.get());
  }

  public static NetworkModule_ProvideTelemetryApiServiceFactory create(
      Provider<Retrofit> retrofitProvider) {
    return new NetworkModule_ProvideTelemetryApiServiceFactory(retrofitProvider);
  }

  public static TelemetryApiService provideTelemetryApiService(Retrofit retrofit) {
    return Preconditions.checkNotNullFromProvides(NetworkModule.INSTANCE.provideTelemetryApiService(retrofit));
  }
}
