package com.bmw.drivingcoach.data.api;

import androidx.datastore.core.DataStore;
import androidx.datastore.preferences.core.Preferences;
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
public final class AuthInterceptor_Factory implements Factory<AuthInterceptor> {
  private final Provider<DataStore<Preferences>> dataStoreProvider;

  private final Provider<AuthEventBus> authEventBusProvider;

  public AuthInterceptor_Factory(Provider<DataStore<Preferences>> dataStoreProvider,
      Provider<AuthEventBus> authEventBusProvider) {
    this.dataStoreProvider = dataStoreProvider;
    this.authEventBusProvider = authEventBusProvider;
  }

  @Override
  public AuthInterceptor get() {
    return newInstance(dataStoreProvider.get(), authEventBusProvider.get());
  }

  public static AuthInterceptor_Factory create(Provider<DataStore<Preferences>> dataStoreProvider,
      Provider<AuthEventBus> authEventBusProvider) {
    return new AuthInterceptor_Factory(dataStoreProvider, authEventBusProvider);
  }

  public static AuthInterceptor newInstance(DataStore<Preferences> dataStore,
      AuthEventBus authEventBus) {
    return new AuthInterceptor(dataStore, authEventBus);
  }
}
