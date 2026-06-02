package com.bmw.drivingcoach.ui.onboarding;

import androidx.datastore.core.DataStore;
import androidx.datastore.preferences.core.Preferences;
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
public final class OnboardingFragment_MembersInjector implements MembersInjector<OnboardingFragment> {
  private final Provider<DataStore<Preferences>> dataStoreProvider;

  public OnboardingFragment_MembersInjector(Provider<DataStore<Preferences>> dataStoreProvider) {
    this.dataStoreProvider = dataStoreProvider;
  }

  public static MembersInjector<OnboardingFragment> create(
      Provider<DataStore<Preferences>> dataStoreProvider) {
    return new OnboardingFragment_MembersInjector(dataStoreProvider);
  }

  @Override
  public void injectMembers(OnboardingFragment instance) {
    injectDataStore(instance, dataStoreProvider.get());
  }

  @InjectedFieldSignature("com.bmw.drivingcoach.ui.onboarding.OnboardingFragment.dataStore")
  public static void injectDataStore(OnboardingFragment instance,
      DataStore<Preferences> dataStore) {
    instance.dataStore = dataStore;
  }
}
