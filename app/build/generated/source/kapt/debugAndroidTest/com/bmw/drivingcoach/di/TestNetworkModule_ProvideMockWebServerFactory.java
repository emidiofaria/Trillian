package com.bmw.drivingcoach.di;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import okhttp3.mockwebserver.MockWebServer;

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
public final class TestNetworkModule_ProvideMockWebServerFactory implements Factory<MockWebServer> {
  @Override
  public MockWebServer get() {
    return provideMockWebServer();
  }

  public static TestNetworkModule_ProvideMockWebServerFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static MockWebServer provideMockWebServer() {
    return Preconditions.checkNotNullFromProvides(TestNetworkModule.INSTANCE.provideMockWebServer());
  }

  private static final class InstanceHolder {
    private static final TestNetworkModule_ProvideMockWebServerFactory INSTANCE = new TestNetworkModule_ProvideMockWebServerFactory();
  }
}
