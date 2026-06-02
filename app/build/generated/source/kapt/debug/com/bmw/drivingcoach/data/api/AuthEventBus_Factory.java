package com.bmw.drivingcoach.data.api;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

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
public final class AuthEventBus_Factory implements Factory<AuthEventBus> {
  @Override
  public AuthEventBus get() {
    return newInstance();
  }

  public static AuthEventBus_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static AuthEventBus newInstance() {
    return new AuthEventBus();
  }

  private static final class InstanceHolder {
    private static final AuthEventBus_Factory INSTANCE = new AuthEventBus_Factory();
  }
}
