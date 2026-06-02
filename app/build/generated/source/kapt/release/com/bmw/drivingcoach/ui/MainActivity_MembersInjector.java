package com.bmw.drivingcoach.ui;

import androidx.datastore.core.DataStore;
import androidx.datastore.preferences.core.Preferences;
import com.bmw.drivingcoach.data.api.AuthEventBus;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class MainActivity_MembersInjector implements MembersInjector<MainActivity> {
  private final Provider<DataStore<Preferences>> dataStoreProvider;

  private final Provider<AuthEventBus> authEventBusProvider;

  public MainActivity_MembersInjector(Provider<DataStore<Preferences>> dataStoreProvider,
      Provider<AuthEventBus> authEventBusProvider) {
    this.dataStoreProvider = dataStoreProvider;
    this.authEventBusProvider = authEventBusProvider;
  }

  public static MembersInjector<MainActivity> create(
      Provider<DataStore<Preferences>> dataStoreProvider,
      Provider<AuthEventBus> authEventBusProvider) {
    return new MainActivity_MembersInjector(dataStoreProvider, authEventBusProvider);
  }

  @Override
  public void injectMembers(MainActivity instance) {
    injectDataStore(instance, dataStoreProvider.get());
    injectAuthEventBus(instance, authEventBusProvider.get());
  }

  @InjectedFieldSignature("com.bmw.drivingcoach.ui.MainActivity.dataStore")
  public static void injectDataStore(MainActivity instance, DataStore<Preferences> dataStore) {
    instance.dataStore = dataStore;
  }

  @InjectedFieldSignature("com.bmw.drivingcoach.ui.MainActivity.authEventBus")
  public static void injectAuthEventBus(MainActivity instance, AuthEventBus authEventBus) {
    instance.authEventBus = authEventBus;
  }
}
