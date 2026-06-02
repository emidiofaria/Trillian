package com.bmw.drivingcoach.data.repository;

import androidx.datastore.core.DataStore;
import androidx.datastore.preferences.core.Preferences;
import com.bmw.drivingcoach.data.api.ApiService;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class AuthRepository_Factory implements Factory<AuthRepository> {
  private final Provider<ApiService> apiServiceProvider;

  private final Provider<DataStore<Preferences>> dataStoreProvider;

  public AuthRepository_Factory(Provider<ApiService> apiServiceProvider,
      Provider<DataStore<Preferences>> dataStoreProvider) {
    this.apiServiceProvider = apiServiceProvider;
    this.dataStoreProvider = dataStoreProvider;
  }

  @Override
  public AuthRepository get() {
    return newInstance(apiServiceProvider.get(), dataStoreProvider.get());
  }

  public static AuthRepository_Factory create(Provider<ApiService> apiServiceProvider,
      Provider<DataStore<Preferences>> dataStoreProvider) {
    return new AuthRepository_Factory(apiServiceProvider, dataStoreProvider);
  }

  public static AuthRepository newInstance(ApiService apiService,
      DataStore<Preferences> dataStore) {
    return new AuthRepository(apiService, dataStore);
  }
}
