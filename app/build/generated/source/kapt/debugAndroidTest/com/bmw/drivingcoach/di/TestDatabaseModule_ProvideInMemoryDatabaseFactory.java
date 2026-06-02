package com.bmw.drivingcoach.di;

import android.content.Context;
import com.bmw.drivingcoach.data.db.BMWDatabase;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
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
public final class TestDatabaseModule_ProvideInMemoryDatabaseFactory implements Factory<BMWDatabase> {
  private final Provider<Context> contextProvider;

  public TestDatabaseModule_ProvideInMemoryDatabaseFactory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public BMWDatabase get() {
    return provideInMemoryDatabase(contextProvider.get());
  }

  public static TestDatabaseModule_ProvideInMemoryDatabaseFactory create(
      Provider<Context> contextProvider) {
    return new TestDatabaseModule_ProvideInMemoryDatabaseFactory(contextProvider);
  }

  public static BMWDatabase provideInMemoryDatabase(Context context) {
    return Preconditions.checkNotNullFromProvides(TestDatabaseModule.INSTANCE.provideInMemoryDatabase(context));
  }
}
