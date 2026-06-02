package com.bmw.drivingcoach.di;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockWebServer;
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
public final class TestNetworkModule_ProvideRetrofitFactory implements Factory<Retrofit> {
  private final Provider<OkHttpClient> okHttpClientProvider;

  private final Provider<MockWebServer> mockWebServerProvider;

  public TestNetworkModule_ProvideRetrofitFactory(Provider<OkHttpClient> okHttpClientProvider,
      Provider<MockWebServer> mockWebServerProvider) {
    this.okHttpClientProvider = okHttpClientProvider;
    this.mockWebServerProvider = mockWebServerProvider;
  }

  @Override
  public Retrofit get() {
    return provideRetrofit(okHttpClientProvider.get(), mockWebServerProvider.get());
  }

  public static TestNetworkModule_ProvideRetrofitFactory create(
      Provider<OkHttpClient> okHttpClientProvider, Provider<MockWebServer> mockWebServerProvider) {
    return new TestNetworkModule_ProvideRetrofitFactory(okHttpClientProvider, mockWebServerProvider);
  }

  public static Retrofit provideRetrofit(OkHttpClient okHttpClient, MockWebServer mockWebServer) {
    return Preconditions.checkNotNullFromProvides(TestNetworkModule.INSTANCE.provideRetrofit(okHttpClient, mockWebServer));
  }
}
