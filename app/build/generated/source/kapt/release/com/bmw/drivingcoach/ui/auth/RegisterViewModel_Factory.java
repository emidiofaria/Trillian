package com.bmw.drivingcoach.ui.auth;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

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
public final class RegisterViewModel_Factory implements Factory<RegisterViewModel> {
  @Override
  public RegisterViewModel get() {
    return newInstance();
  }

  public static RegisterViewModel_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static RegisterViewModel newInstance() {
    return new RegisterViewModel();
  }

  private static final class InstanceHolder {
    private static final RegisterViewModel_Factory INSTANCE = new RegisterViewModel_Factory();
  }
}
