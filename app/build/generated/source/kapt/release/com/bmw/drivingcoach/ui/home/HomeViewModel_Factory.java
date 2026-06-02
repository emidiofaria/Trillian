package com.bmw.drivingcoach.ui.home;

import android.content.Context;
import com.bmw.drivingcoach.data.db.dao.LapDao;
import com.bmw.drivingcoach.data.db.dao.SessionDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class HomeViewModel_Factory implements Factory<HomeViewModel> {
  private final Provider<SessionDao> sessionDaoProvider;

  private final Provider<LapDao> lapDaoProvider;

  private final Provider<Context> contextProvider;

  public HomeViewModel_Factory(Provider<SessionDao> sessionDaoProvider,
      Provider<LapDao> lapDaoProvider, Provider<Context> contextProvider) {
    this.sessionDaoProvider = sessionDaoProvider;
    this.lapDaoProvider = lapDaoProvider;
    this.contextProvider = contextProvider;
  }

  @Override
  public HomeViewModel get() {
    return newInstance(sessionDaoProvider.get(), lapDaoProvider.get(), contextProvider.get());
  }

  public static HomeViewModel_Factory create(Provider<SessionDao> sessionDaoProvider,
      Provider<LapDao> lapDaoProvider, Provider<Context> contextProvider) {
    return new HomeViewModel_Factory(sessionDaoProvider, lapDaoProvider, contextProvider);
  }

  public static HomeViewModel newInstance(SessionDao sessionDao, LapDao lapDao, Context context) {
    return new HomeViewModel(sessionDao, lapDao, context);
  }
}
