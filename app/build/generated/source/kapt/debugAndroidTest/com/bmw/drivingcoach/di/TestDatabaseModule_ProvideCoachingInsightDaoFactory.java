package com.bmw.drivingcoach.di;

import com.bmw.drivingcoach.data.db.BMWDatabase;
import com.bmw.drivingcoach.data.db.dao.CoachingInsightDao;
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
public final class TestDatabaseModule_ProvideCoachingInsightDaoFactory implements Factory<CoachingInsightDao> {
  private final Provider<BMWDatabase> databaseProvider;

  public TestDatabaseModule_ProvideCoachingInsightDaoFactory(
      Provider<BMWDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public CoachingInsightDao get() {
    return provideCoachingInsightDao(databaseProvider.get());
  }

  public static TestDatabaseModule_ProvideCoachingInsightDaoFactory create(
      Provider<BMWDatabase> databaseProvider) {
    return new TestDatabaseModule_ProvideCoachingInsightDaoFactory(databaseProvider);
  }

  public static CoachingInsightDao provideCoachingInsightDao(BMWDatabase database) {
    return Preconditions.checkNotNullFromProvides(TestDatabaseModule.INSTANCE.provideCoachingInsightDao(database));
  }
}
