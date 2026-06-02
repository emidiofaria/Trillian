package com.bmw.drivingcoach;

import androidx.datastore.core.DataStore;
import androidx.datastore.preferences.core.Preferences;
import com.bmw.drivingcoach.data.db.BMWDatabase;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import okhttp3.mockwebserver.MockWebServer;

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
public final class EndToEndTest_MembersInjector implements MembersInjector<EndToEndTest> {
  private final Provider<BMWDatabase> databaseProvider;

  private final Provider<MockWebServer> mockWebServerProvider;

  private final Provider<DataStore<Preferences>> dataStoreProvider;

  public EndToEndTest_MembersInjector(Provider<BMWDatabase> databaseProvider,
      Provider<MockWebServer> mockWebServerProvider,
      Provider<DataStore<Preferences>> dataStoreProvider) {
    this.databaseProvider = databaseProvider;
    this.mockWebServerProvider = mockWebServerProvider;
    this.dataStoreProvider = dataStoreProvider;
  }

  public static MembersInjector<EndToEndTest> create(Provider<BMWDatabase> databaseProvider,
      Provider<MockWebServer> mockWebServerProvider,
      Provider<DataStore<Preferences>> dataStoreProvider) {
    return new EndToEndTest_MembersInjector(databaseProvider, mockWebServerProvider, dataStoreProvider);
  }

  @Override
  public void injectMembers(EndToEndTest instance) {
    injectDatabase(instance, databaseProvider.get());
    injectMockWebServer(instance, mockWebServerProvider.get());
    injectDataStore(instance, dataStoreProvider.get());
  }

  @InjectedFieldSignature("com.bmw.drivingcoach.EndToEndTest.database")
  public static void injectDatabase(EndToEndTest instance, BMWDatabase database) {
    instance.database = database;
  }

  @InjectedFieldSignature("com.bmw.drivingcoach.EndToEndTest.mockWebServer")
  public static void injectMockWebServer(EndToEndTest instance, MockWebServer mockWebServer) {
    instance.mockWebServer = mockWebServer;
  }

  @InjectedFieldSignature("com.bmw.drivingcoach.EndToEndTest.dataStore")
  public static void injectDataStore(EndToEndTest instance, DataStore<Preferences> dataStore) {
    instance.dataStore = dataStore;
  }
}
