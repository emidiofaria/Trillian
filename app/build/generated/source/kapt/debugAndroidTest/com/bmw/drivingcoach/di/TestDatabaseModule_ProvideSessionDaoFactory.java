package com.bmw.drivingcoach.di;

import com.bmw.drivingcoach.data.db.BMWDatabase;
import com.bmw.drivingcoach.data.db.dao.SessionDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class TestDatabaseModule_ProvideSessionDaoFactory implements Factory<SessionDao> {
  private final Provider<BMWDatabase> databaseProvider;

  public TestDatabaseModule_ProvideSessionDaoFactory(Provider<BMWDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public SessionDao get() {
    return provideSessionDao(databaseProvider.get());
  }

  public static TestDatabaseModule_ProvideSessionDaoFactory create(
      Provider<BMWDatabase> databaseProvider) {
    return new TestDatabaseModule_ProvideSessionDaoFactory(databaseProvider);
  }

  public static SessionDao provideSessionDao(BMWDatabase database) {
    return Preconditions.checkNotNullFromProvides(TestDatabaseModule.INSTANCE.provideSessionDao(database));
  }
}
