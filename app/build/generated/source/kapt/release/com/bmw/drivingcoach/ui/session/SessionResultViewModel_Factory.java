package com.bmw.drivingcoach.ui.session;

import androidx.lifecycle.SavedStateHandle;
import androidx.work.WorkManager;
import com.bmw.drivingcoach.data.db.dao.CoachingInsightDao;
import com.bmw.drivingcoach.data.db.dao.LapDao;
import com.bmw.drivingcoach.data.db.dao.SessionDao;
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
public final class SessionResultViewModel_Factory implements Factory<SessionResultViewModel> {
  private final Provider<SessionDao> sessionDaoProvider;

  private final Provider<LapDao> lapDaoProvider;

  private final Provider<CoachingInsightDao> coachingInsightDaoProvider;

  private final Provider<WorkManager> workManagerProvider;

  private final Provider<SavedStateHandle> savedStateHandleProvider;

  public SessionResultViewModel_Factory(Provider<SessionDao> sessionDaoProvider,
      Provider<LapDao> lapDaoProvider, Provider<CoachingInsightDao> coachingInsightDaoProvider,
      Provider<WorkManager> workManagerProvider,
      Provider<SavedStateHandle> savedStateHandleProvider) {
    this.sessionDaoProvider = sessionDaoProvider;
    this.lapDaoProvider = lapDaoProvider;
    this.coachingInsightDaoProvider = coachingInsightDaoProvider;
    this.workManagerProvider = workManagerProvider;
    this.savedStateHandleProvider = savedStateHandleProvider;
  }

  @Override
  public SessionResultViewModel get() {
    return newInstance(sessionDaoProvider.get(), lapDaoProvider.get(), coachingInsightDaoProvider.get(), workManagerProvider.get(), savedStateHandleProvider.get());
  }

  public static SessionResultViewModel_Factory create(Provider<SessionDao> sessionDaoProvider,
      Provider<LapDao> lapDaoProvider, Provider<CoachingInsightDao> coachingInsightDaoProvider,
      Provider<WorkManager> workManagerProvider,
      Provider<SavedStateHandle> savedStateHandleProvider) {
    return new SessionResultViewModel_Factory(sessionDaoProvider, lapDaoProvider, coachingInsightDaoProvider, workManagerProvider, savedStateHandleProvider);
  }

  public static SessionResultViewModel newInstance(SessionDao sessionDao, LapDao lapDao,
      CoachingInsightDao coachingInsightDao, WorkManager workManager,
      SavedStateHandle savedStateHandle) {
    return new SessionResultViewModel(sessionDao, lapDao, coachingInsightDao, workManager, savedStateHandle);
  }
}
