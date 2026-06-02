package com.bmw.drivingcoach.service;

import com.bmw.drivingcoach.data.db.dao.SessionDao;
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
public final class TelemetryForegroundService_MembersInjector implements MembersInjector<TelemetryForegroundService> {
  private final Provider<SessionDao> sessionDaoProvider;

  public TelemetryForegroundService_MembersInjector(Provider<SessionDao> sessionDaoProvider) {
    this.sessionDaoProvider = sessionDaoProvider;
  }

  public static MembersInjector<TelemetryForegroundService> create(
      Provider<SessionDao> sessionDaoProvider) {
    return new TelemetryForegroundService_MembersInjector(sessionDaoProvider);
  }

  @Override
  public void injectMembers(TelemetryForegroundService instance) {
    injectSessionDao(instance, sessionDaoProvider.get());
  }

  @InjectedFieldSignature("com.bmw.drivingcoach.service.TelemetryForegroundService.sessionDao")
  public static void injectSessionDao(TelemetryForegroundService instance, SessionDao sessionDao) {
    instance.sessionDao = sessionDao;
  }
}
