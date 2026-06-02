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
public final class LoginViewModel_Factory implements Factory<LoginViewModel> {
  @Override
  public LoginViewModel get() {
    return newInstance();
  }

  public static LoginViewModel_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static LoginViewModel newInstance() {
    return new LoginViewModel();
  }

  private static final class InstanceHolder {
    private static final LoginViewModel_Factory INSTANCE = new LoginViewModel_Factory();
  }
}
