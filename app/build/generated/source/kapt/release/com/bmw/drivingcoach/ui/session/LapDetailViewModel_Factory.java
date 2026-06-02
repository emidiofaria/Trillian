package com.bmw.drivingcoach.ui.session;

import androidx.lifecycle.SavedStateHandle;
import com.bmw.drivingcoach.data.db.dao.LapDao;
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
public final class LapDetailViewModel_Factory implements Factory<LapDetailViewModel> {
  private final Provider<LapDao> lapDaoProvider;

  private final Provider<SavedStateHandle> savedStateHandleProvider;

  public LapDetailViewModel_Factory(Provider<LapDao> lapDaoProvider,
      Provider<SavedStateHandle> savedStateHandleProvider) {
    this.lapDaoProvider = lapDaoProvider;
    this.savedStateHandleProvider = savedStateHandleProvider;
  }

  @Override
  public LapDetailViewModel get() {
    return newInstance(lapDaoProvider.get(), savedStateHandleProvider.get());
  }

  public static LapDetailViewModel_Factory create(Provider<LapDao> lapDaoProvider,
      Provider<SavedStateHandle> savedStateHandleProvider) {
    return new LapDetailViewModel_Factory(lapDaoProvider, savedStateHandleProvider);
  }

  public static LapDetailViewModel newInstance(LapDao lapDao, SavedStateHandle savedStateHandle) {
    return new LapDetailViewModel(lapDao, savedStateHandle);
  }
}
