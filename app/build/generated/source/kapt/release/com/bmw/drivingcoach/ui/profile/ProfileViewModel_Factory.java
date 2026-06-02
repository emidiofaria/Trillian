package com.bmw.drivingcoach.ui.profile;

import androidx.datastore.core.DataStore;
import androidx.datastore.preferences.core.Preferences;
import com.bmw.drivingcoach.data.db.BMWDatabase;
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
public final class ProfileViewModel_Factory implements Factory<ProfileViewModel> {
  private final Provider<SessionDao> sessionDaoProvider;

  private final Provider<LapDao> lapDaoProvider;

  private final Provider<BMWDatabase> databaseProvider;

  private final Provider<DataStore<Preferences>> dataStoreProvider;

  public ProfileViewModel_Factory(Provider<SessionDao> sessionDaoProvider,
      Provider<LapDao> lapDaoProvider, Provider<BMWDatabase> databaseProvider,
      Provider<DataStore<Preferences>> dataStoreProvider) {
    this.sessionDaoProvider = sessionDaoProvider;
    this.lapDaoProvider = lapDaoProvider;
    this.databaseProvider = databaseProvider;
    this.dataStoreProvider = dataStoreProvider;
  }

  @Override
  public ProfileViewModel get() {
    return newInstance(sessionDaoProvider.get(), lapDaoProvider.get(), databaseProvider.get(), dataStoreProvider.get());
  }

  public static ProfileViewModel_Factory create(Provider<SessionDao> sessionDaoProvider,
      Provider<LapDao> lapDaoProvider, Provider<BMWDatabase> databaseProvider,
      Provider<DataStore<Preferences>> dataStoreProvider) {
    return new ProfileViewModel_Factory(sessionDaoProvider, lapDaoProvider, databaseProvider, dataStoreProvider);
  }

  public static ProfileViewModel newInstance(SessionDao sessionDao, LapDao lapDao,
      BMWDatabase database, DataStore<Preferences> dataStore) {
    return new ProfileViewModel(sessionDao, lapDao, database, dataStore);
  }
}
