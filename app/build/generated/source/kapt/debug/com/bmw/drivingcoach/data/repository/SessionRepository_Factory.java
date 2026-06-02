package com.bmw.drivingcoach.data.repository;

import com.bmw.drivingcoach.data.api.ApiService;
import com.bmw.drivingcoach.data.db.dao.CoachingInsightDao;
import com.bmw.drivingcoach.data.db.dao.LapDao;
import com.bmw.drivingcoach.data.db.dao.SessionDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class SessionRepository_Factory implements Factory<SessionRepository> {
  private final Provider<ApiService> apiServiceProvider;

  private final Provider<SessionDao> sessionDaoProvider;

  private final Provider<LapDao> lapDaoProvider;

  private final Provider<CoachingInsightDao> coachingInsightDaoProvider;

  public SessionRepository_Factory(Provider<ApiService> apiServiceProvider,
      Provider<SessionDao> sessionDaoProvider, Provider<LapDao> lapDaoProvider,
      Provider<CoachingInsightDao> coachingInsightDaoProvider) {
    this.apiServiceProvider = apiServiceProvider;
    this.sessionDaoProvider = sessionDaoProvider;
    this.lapDaoProvider = lapDaoProvider;
    this.coachingInsightDaoProvider = coachingInsightDaoProvider;
  }

  @Override
  public SessionRepository get() {
    return newInstance(apiServiceProvider.get(), sessionDaoProvider.get(), lapDaoProvider.get(), coachingInsightDaoProvider.get());
  }

  public static SessionRepository_Factory create(Provider<ApiService> apiServiceProvider,
      Provider<SessionDao> sessionDaoProvider, Provider<LapDao> lapDaoProvider,
      Provider<CoachingInsightDao> coachingInsightDaoProvider) {
    return new SessionRepository_Factory(apiServiceProvider, sessionDaoProvider, lapDaoProvider, coachingInsightDaoProvider);
  }

  public static SessionRepository newInstance(ApiService apiService, SessionDao sessionDao,
      LapDao lapDao, CoachingInsightDao coachingInsightDao) {
    return new SessionRepository(apiService, sessionDao, lapDao, coachingInsightDao);
  }
}
