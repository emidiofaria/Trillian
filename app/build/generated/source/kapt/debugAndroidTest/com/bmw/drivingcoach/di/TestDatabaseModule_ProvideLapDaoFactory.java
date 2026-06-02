package com.bmw.drivingcoach.di;

import com.bmw.drivingcoach.data.db.BMWDatabase;
import com.bmw.drivingcoach.data.db.dao.LapDao;
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
public final class TestDatabaseModule_ProvideLapDaoFactory implements Factory<LapDao> {
  private final Provider<BMWDatabase> databaseProvider;

  public TestDatabaseModule_ProvideLapDaoFactory(Provider<BMWDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public LapDao get() {
    return provideLapDao(databaseProvider.get());
  }

  public static TestDatabaseModule_ProvideLapDaoFactory create(
      Provider<BMWDatabase> databaseProvider) {
    return new TestDatabaseModule_ProvideLapDaoFactory(databaseProvider);
  }

  public static LapDao provideLapDao(BMWDatabase database) {
    return Preconditions.checkNotNullFromProvides(TestDatabaseModule.INSTANCE.provideLapDao(database));
  }
}
