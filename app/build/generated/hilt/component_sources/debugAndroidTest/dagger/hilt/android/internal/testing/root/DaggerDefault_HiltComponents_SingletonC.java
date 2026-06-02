package dagger.hilt.android.internal.testing.root;

import android.app.Activity;
import android.app.Service;
import android.view.View;
import androidx.datastore.core.DataStore;
import androidx.datastore.preferences.core.Preferences;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import androidx.work.WorkManager;
import com.bmw.drivingcoach.BMWDrivingCoachApp;
import com.bmw.drivingcoach.EndToEndTest;
import com.bmw.drivingcoach.EndToEndTest_MembersInjector;
import com.bmw.drivingcoach.data.api.AuthEventBus;
import com.bmw.drivingcoach.data.db.BMWDatabase;
import com.bmw.drivingcoach.data.db.dao.CoachingInsightDao;
import com.bmw.drivingcoach.data.db.dao.LapDao;
import com.bmw.drivingcoach.data.db.dao.SessionDao;
import com.bmw.drivingcoach.di.AppModule_ProvideDataStoreFactory;
import com.bmw.drivingcoach.di.TestDatabaseModule_ProvideCoachingInsightDaoFactory;
import com.bmw.drivingcoach.di.TestDatabaseModule_ProvideInMemoryDatabaseFactory;
import com.bmw.drivingcoach.di.TestDatabaseModule_ProvideLapDaoFactory;
import com.bmw.drivingcoach.di.TestDatabaseModule_ProvideSessionDaoFactory;
import com.bmw.drivingcoach.di.TestNetworkModule_ProvideMockWebServerFactory;
import com.bmw.drivingcoach.di.TestNetworkModule_ProvideWorkManagerFactory;
import com.bmw.drivingcoach.service.TelemetryForegroundService;
import com.bmw.drivingcoach.service.TelemetryForegroundService_MembersInjector;
import com.bmw.drivingcoach.ui.MainActivity;
import com.bmw.drivingcoach.ui.MainActivity_MembersInjector;
import com.bmw.drivingcoach.ui.auth.LoginFragment;
import com.bmw.drivingcoach.ui.auth.LoginViewModel;
import com.bmw.drivingcoach.ui.auth.LoginViewModel_HiltModules;
import com.bmw.drivingcoach.ui.auth.RegisterFragment;
import com.bmw.drivingcoach.ui.auth.RegisterViewModel;
import com.bmw.drivingcoach.ui.auth.RegisterViewModel_HiltModules;
import com.bmw.drivingcoach.ui.home.HomeFragment;
import com.bmw.drivingcoach.ui.home.HomeViewModel;
import com.bmw.drivingcoach.ui.home.HomeViewModel_HiltModules;
import com.bmw.drivingcoach.ui.onboarding.OnboardingFragment;
import com.bmw.drivingcoach.ui.onboarding.OnboardingFragment_MembersInjector;
import com.bmw.drivingcoach.ui.profile.ProfileFragment;
import com.bmw.drivingcoach.ui.profile.ProfileViewModel;
import com.bmw.drivingcoach.ui.profile.ProfileViewModel_HiltModules;
import com.bmw.drivingcoach.ui.recording.RecordingFragment;
import com.bmw.drivingcoach.ui.recording.RecordingFragmentTest;
import com.bmw.drivingcoach.ui.recording.RecordingViewModel;
import com.bmw.drivingcoach.ui.recording.RecordingViewModel_HiltModules;
import com.bmw.drivingcoach.ui.session.LapDetailFragment;
import com.bmw.drivingcoach.ui.session.LapDetailViewModel;
import com.bmw.drivingcoach.ui.session.LapDetailViewModel_HiltModules;
import com.bmw.drivingcoach.ui.session.SessionResultFragment;
import com.bmw.drivingcoach.ui.session.SessionResultViewModel;
import com.bmw.drivingcoach.ui.session.SessionResultViewModel_HiltModules;
import com.bmw.drivingcoach.ui.session.tabs.ChartFragment;
import com.bmw.drivingcoach.ui.session.tabs.CoachFragment;
import com.bmw.drivingcoach.ui.session.tabs.LapsFragment;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import dagger.hilt.android.ActivityRetainedLifecycle;
import dagger.hilt.android.ViewModelLifecycle;
import dagger.hilt.android.internal.builders.ActivityComponentBuilder;
import dagger.hilt.android.internal.builders.ActivityRetainedComponentBuilder;
import dagger.hilt.android.internal.builders.FragmentComponentBuilder;
import dagger.hilt.android.internal.builders.ServiceComponentBuilder;
import dagger.hilt.android.internal.builders.ViewComponentBuilder;
import dagger.hilt.android.internal.builders.ViewModelComponentBuilder;
import dagger.hilt.android.internal.builders.ViewWithFragmentComponentBuilder;
import dagger.hilt.android.internal.lifecycle.DefaultViewModelFactories;
import dagger.hilt.android.internal.lifecycle.DefaultViewModelFactories_InternalFactoryFactory_Factory;
import dagger.hilt.android.internal.managers.ActivityRetainedComponentManager_LifecycleModule_ProvideActivityRetainedLifecycleFactory;
import dagger.hilt.android.internal.managers.SavedStateHandleHolder;
import dagger.hilt.android.internal.modules.ApplicationContextModule;
import dagger.hilt.android.internal.modules.ApplicationContextModule_ProvideApplicationFactory;
import dagger.hilt.android.internal.modules.ApplicationContextModule_ProvideContextFactory;
import dagger.internal.DaggerGenerated;
import dagger.internal.DoubleCheck;
import dagger.internal.IdentifierNameString;
import dagger.internal.KeepFieldType;
import dagger.internal.LazyClassKeyMap;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;
import okhttp3.mockwebserver.MockWebServer;

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
public final class DaggerDefault_HiltComponents_SingletonC {
  private DaggerDefault_HiltComponents_SingletonC() {
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private ApplicationContextModule applicationContextModule;

    private Builder() {
    }

    public Builder applicationContextModule(ApplicationContextModule applicationContextModule) {
      this.applicationContextModule = Preconditions.checkNotNull(applicationContextModule);
      return this;
    }

    public Default_HiltComponents.SingletonC build() {
      Preconditions.checkBuilderRequirement(applicationContextModule, ApplicationContextModule.class);
      return new SingletonCImpl(applicationContextModule);
    }
  }

  private static final class ActivityRetainedCBuilder implements Default_HiltComponents.ActivityRetainedC.Builder {
    private final SingletonCImpl singletonCImpl;

    private SavedStateHandleHolder savedStateHandleHolder;

    private ActivityRetainedCBuilder(SingletonCImpl singletonCImpl) {
      this.singletonCImpl = singletonCImpl;
    }

    @Override
    public ActivityRetainedCBuilder savedStateHandleHolder(
        SavedStateHandleHolder savedStateHandleHolder) {
      this.savedStateHandleHolder = Preconditions.checkNotNull(savedStateHandleHolder);
      return this;
    }

    @Override
    public Default_HiltComponents.ActivityRetainedC build() {
      Preconditions.checkBuilderRequirement(savedStateHandleHolder, SavedStateHandleHolder.class);
      return new ActivityRetainedCImpl(singletonCImpl, savedStateHandleHolder);
    }
  }

  private static final class ActivityCBuilder implements Default_HiltComponents.ActivityC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private Activity activity;

    private ActivityCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
    }

    @Override
    public ActivityCBuilder activity(Activity activity) {
      this.activity = Preconditions.checkNotNull(activity);
      return this;
    }

    @Override
    public Default_HiltComponents.ActivityC build() {
      Preconditions.checkBuilderRequirement(activity, Activity.class);
      return new ActivityCImpl(singletonCImpl, activityRetainedCImpl, activity);
    }
  }

  private static final class FragmentCBuilder implements Default_HiltComponents.FragmentC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private Fragment fragment;

    private FragmentCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
    }

    @Override
    public FragmentCBuilder fragment(Fragment fragment) {
      this.fragment = Preconditions.checkNotNull(fragment);
      return this;
    }

    @Override
    public Default_HiltComponents.FragmentC build() {
      Preconditions.checkBuilderRequirement(fragment, Fragment.class);
      return new FragmentCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, fragment);
    }
  }

  private static final class ViewWithFragmentCBuilder implements Default_HiltComponents.ViewWithFragmentC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl;

    private View view;

    private ViewWithFragmentCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl,
        FragmentCImpl fragmentCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
      this.fragmentCImpl = fragmentCImpl;
    }

    @Override
    public ViewWithFragmentCBuilder view(View view) {
      this.view = Preconditions.checkNotNull(view);
      return this;
    }

    @Override
    public Default_HiltComponents.ViewWithFragmentC build() {
      Preconditions.checkBuilderRequirement(view, View.class);
      return new ViewWithFragmentCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, fragmentCImpl, view);
    }
  }

  private static final class ViewCBuilder implements Default_HiltComponents.ViewC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private View view;

    private ViewCBuilder(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        ActivityCImpl activityCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
    }

    @Override
    public ViewCBuilder view(View view) {
      this.view = Preconditions.checkNotNull(view);
      return this;
    }

    @Override
    public Default_HiltComponents.ViewC build() {
      Preconditions.checkBuilderRequirement(view, View.class);
      return new ViewCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, view);
    }
  }

  private static final class ViewModelCBuilder implements Default_HiltComponents.ViewModelC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private SavedStateHandle savedStateHandle;

    private ViewModelLifecycle viewModelLifecycle;

    private ViewModelCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
    }

    @Override
    public ViewModelCBuilder savedStateHandle(SavedStateHandle handle) {
      this.savedStateHandle = Preconditions.checkNotNull(handle);
      return this;
    }

    @Override
    public ViewModelCBuilder viewModelLifecycle(ViewModelLifecycle viewModelLifecycle) {
      this.viewModelLifecycle = Preconditions.checkNotNull(viewModelLifecycle);
      return this;
    }

    @Override
    public Default_HiltComponents.ViewModelC build() {
      Preconditions.checkBuilderRequirement(savedStateHandle, SavedStateHandle.class);
      Preconditions.checkBuilderRequirement(viewModelLifecycle, ViewModelLifecycle.class);
      return new ViewModelCImpl(singletonCImpl, activityRetainedCImpl, savedStateHandle, viewModelLifecycle);
    }
  }

  private static final class ServiceCBuilder implements Default_HiltComponents.ServiceC.Builder {
    private final SingletonCImpl singletonCImpl;

    private Service service;

    private ServiceCBuilder(SingletonCImpl singletonCImpl) {
      this.singletonCImpl = singletonCImpl;
    }

    @Override
    public ServiceCBuilder service(Service service) {
      this.service = Preconditions.checkNotNull(service);
      return this;
    }

    @Override
    public Default_HiltComponents.ServiceC build() {
      Preconditions.checkBuilderRequirement(service, Service.class);
      return new ServiceCImpl(singletonCImpl, service);
    }
  }

  private static final class ViewWithFragmentCImpl extends Default_HiltComponents.ViewWithFragmentC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl;

    private final ViewWithFragmentCImpl viewWithFragmentCImpl = this;

    private ViewWithFragmentCImpl(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl,
        FragmentCImpl fragmentCImpl, View viewParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
      this.fragmentCImpl = fragmentCImpl;


    }
  }

  private static final class FragmentCImpl extends Default_HiltComponents.FragmentC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl = this;

    private FragmentCImpl(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl,
        Fragment fragmentParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;


    }

    @Override
    public void injectLoginFragment(LoginFragment loginFragment) {
    }

    @Override
    public void injectRegisterFragment(RegisterFragment registerFragment) {
    }

    @Override
    public void injectHomeFragment(HomeFragment homeFragment) {
    }

    @Override
    public void injectOnboardingFragment(OnboardingFragment onboardingFragment) {
      injectOnboardingFragment2(onboardingFragment);
    }

    @Override
    public void injectProfileFragment(ProfileFragment profileFragment) {
    }

    @Override
    public void injectRecordingFragment(RecordingFragment recordingFragment) {
    }

    @Override
    public void injectLapDetailFragment(LapDetailFragment lapDetailFragment) {
    }

    @Override
    public void injectSessionResultFragment(SessionResultFragment sessionResultFragment) {
    }

    @Override
    public void injectChartFragment(ChartFragment chartFragment) {
    }

    @Override
    public void injectCoachFragment(CoachFragment coachFragment) {
    }

    @Override
    public void injectLapsFragment(LapsFragment lapsFragment) {
    }

    @Override
    public DefaultViewModelFactories.InternalFactoryFactory getHiltInternalFactoryFactory() {
      return activityCImpl.getHiltInternalFactoryFactory();
    }

    @Override
    public ViewWithFragmentComponentBuilder viewWithFragmentComponentBuilder() {
      return new ViewWithFragmentCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl, fragmentCImpl);
    }

    @CanIgnoreReturnValue
    private OnboardingFragment injectOnboardingFragment2(OnboardingFragment instance) {
      OnboardingFragment_MembersInjector.injectDataStore(instance, singletonCImpl.provideDataStoreProvider.get());
      return instance;
    }
  }

  private static final class ViewCImpl extends Default_HiltComponents.ViewC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final ViewCImpl viewCImpl = this;

    private ViewCImpl(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        ActivityCImpl activityCImpl, View viewParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;


    }
  }

  private static final class ActivityCImpl extends Default_HiltComponents.ActivityC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl = this;

    private ActivityCImpl(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, Activity activityParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;


    }

    @Override
    public void injectMainActivity(MainActivity mainActivity) {
      injectMainActivity2(mainActivity);
    }

    @Override
    public DefaultViewModelFactories.InternalFactoryFactory getHiltInternalFactoryFactory() {
      return DefaultViewModelFactories_InternalFactoryFactory_Factory.newInstance(getViewModelKeys(), new ViewModelCBuilder(singletonCImpl, activityRetainedCImpl));
    }

    @Override
    public Map<Class<?>, Boolean> getViewModelKeys() {
      return LazyClassKeyMap.<Boolean>of(ImmutableMap.<String, Boolean>builderWithExpectedSize(7).put(LazyClassKeyProvider.com_bmw_drivingcoach_ui_home_HomeViewModel, HomeViewModel_HiltModules.KeyModule.provide()).put(LazyClassKeyProvider.com_bmw_drivingcoach_ui_session_LapDetailViewModel, LapDetailViewModel_HiltModules.KeyModule.provide()).put(LazyClassKeyProvider.com_bmw_drivingcoach_ui_auth_LoginViewModel, LoginViewModel_HiltModules.KeyModule.provide()).put(LazyClassKeyProvider.com_bmw_drivingcoach_ui_profile_ProfileViewModel, ProfileViewModel_HiltModules.KeyModule.provide()).put(LazyClassKeyProvider.com_bmw_drivingcoach_ui_recording_RecordingViewModel, RecordingViewModel_HiltModules.KeyModule.provide()).put(LazyClassKeyProvider.com_bmw_drivingcoach_ui_auth_RegisterViewModel, RegisterViewModel_HiltModules.KeyModule.provide()).put(LazyClassKeyProvider.com_bmw_drivingcoach_ui_session_SessionResultViewModel, SessionResultViewModel_HiltModules.KeyModule.provide()).build());
    }

    @Override
    public ViewModelComponentBuilder getViewModelComponentBuilder() {
      return new ViewModelCBuilder(singletonCImpl, activityRetainedCImpl);
    }

    @Override
    public FragmentComponentBuilder fragmentComponentBuilder() {
      return new FragmentCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl);
    }

    @Override
    public ViewComponentBuilder viewComponentBuilder() {
      return new ViewCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl);
    }

    @CanIgnoreReturnValue
    private MainActivity injectMainActivity2(MainActivity instance) {
      MainActivity_MembersInjector.injectDataStore(instance, singletonCImpl.provideDataStoreProvider.get());
      MainActivity_MembersInjector.injectAuthEventBus(instance, singletonCImpl.authEventBusProvider.get());
      return instance;
    }

    @IdentifierNameString
    private static final class LazyClassKeyProvider {
      static String com_bmw_drivingcoach_ui_home_HomeViewModel = "com.bmw.drivingcoach.ui.home.HomeViewModel";

      static String com_bmw_drivingcoach_ui_auth_RegisterViewModel = "com.bmw.drivingcoach.ui.auth.RegisterViewModel";

      static String com_bmw_drivingcoach_ui_auth_LoginViewModel = "com.bmw.drivingcoach.ui.auth.LoginViewModel";

      static String com_bmw_drivingcoach_ui_recording_RecordingViewModel = "com.bmw.drivingcoach.ui.recording.RecordingViewModel";

      static String com_bmw_drivingcoach_ui_profile_ProfileViewModel = "com.bmw.drivingcoach.ui.profile.ProfileViewModel";

      static String com_bmw_drivingcoach_ui_session_LapDetailViewModel = "com.bmw.drivingcoach.ui.session.LapDetailViewModel";

      static String com_bmw_drivingcoach_ui_session_SessionResultViewModel = "com.bmw.drivingcoach.ui.session.SessionResultViewModel";

      @KeepFieldType
      HomeViewModel com_bmw_drivingcoach_ui_home_HomeViewModel2;

      @KeepFieldType
      RegisterViewModel com_bmw_drivingcoach_ui_auth_RegisterViewModel2;

      @KeepFieldType
      LoginViewModel com_bmw_drivingcoach_ui_auth_LoginViewModel2;

      @KeepFieldType
      RecordingViewModel com_bmw_drivingcoach_ui_recording_RecordingViewModel2;

      @KeepFieldType
      ProfileViewModel com_bmw_drivingcoach_ui_profile_ProfileViewModel2;

      @KeepFieldType
      LapDetailViewModel com_bmw_drivingcoach_ui_session_LapDetailViewModel2;

      @KeepFieldType
      SessionResultViewModel com_bmw_drivingcoach_ui_session_SessionResultViewModel2;
    }
  }

  private static final class ViewModelCImpl extends Default_HiltComponents.ViewModelC {
    private final SavedStateHandle savedStateHandle;

    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ViewModelCImpl viewModelCImpl = this;

    private Provider<HomeViewModel> homeViewModelProvider;

    private Provider<LapDetailViewModel> lapDetailViewModelProvider;

    private Provider<LoginViewModel> loginViewModelProvider;

    private Provider<ProfileViewModel> profileViewModelProvider;

    private Provider<RecordingViewModel> recordingViewModelProvider;

    private Provider<RegisterViewModel> registerViewModelProvider;

    private Provider<SessionResultViewModel> sessionResultViewModelProvider;

    private ViewModelCImpl(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, SavedStateHandle savedStateHandleParam,
        ViewModelLifecycle viewModelLifecycleParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.savedStateHandle = savedStateHandleParam;
      initialize(savedStateHandleParam, viewModelLifecycleParam);

    }

    @SuppressWarnings("unchecked")
    private void initialize(final SavedStateHandle savedStateHandleParam,
        final ViewModelLifecycle viewModelLifecycleParam) {
      this.homeViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 0);
      this.lapDetailViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 1);
      this.loginViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 2);
      this.profileViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 3);
      this.recordingViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 4);
      this.registerViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 5);
      this.sessionResultViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 6);
    }

    @Override
    public Map<Class<?>, javax.inject.Provider<ViewModel>> getHiltViewModelMap() {
      return LazyClassKeyMap.<javax.inject.Provider<ViewModel>>of(ImmutableMap.<String, javax.inject.Provider<ViewModel>>builderWithExpectedSize(7).put(LazyClassKeyProvider.com_bmw_drivingcoach_ui_home_HomeViewModel, ((Provider) homeViewModelProvider)).put(LazyClassKeyProvider.com_bmw_drivingcoach_ui_session_LapDetailViewModel, ((Provider) lapDetailViewModelProvider)).put(LazyClassKeyProvider.com_bmw_drivingcoach_ui_auth_LoginViewModel, ((Provider) loginViewModelProvider)).put(LazyClassKeyProvider.com_bmw_drivingcoach_ui_profile_ProfileViewModel, ((Provider) profileViewModelProvider)).put(LazyClassKeyProvider.com_bmw_drivingcoach_ui_recording_RecordingViewModel, ((Provider) recordingViewModelProvider)).put(LazyClassKeyProvider.com_bmw_drivingcoach_ui_auth_RegisterViewModel, ((Provider) registerViewModelProvider)).put(LazyClassKeyProvider.com_bmw_drivingcoach_ui_session_SessionResultViewModel, ((Provider) sessionResultViewModelProvider)).build());
    }

    @Override
    public Map<Class<?>, Object> getHiltViewModelAssistedMap() {
      return ImmutableMap.<Class<?>, Object>of();
    }

    @IdentifierNameString
    private static final class LazyClassKeyProvider {
      static String com_bmw_drivingcoach_ui_auth_RegisterViewModel = "com.bmw.drivingcoach.ui.auth.RegisterViewModel";

      static String com_bmw_drivingcoach_ui_profile_ProfileViewModel = "com.bmw.drivingcoach.ui.profile.ProfileViewModel";

      static String com_bmw_drivingcoach_ui_session_LapDetailViewModel = "com.bmw.drivingcoach.ui.session.LapDetailViewModel";

      static String com_bmw_drivingcoach_ui_recording_RecordingViewModel = "com.bmw.drivingcoach.ui.recording.RecordingViewModel";

      static String com_bmw_drivingcoach_ui_session_SessionResultViewModel = "com.bmw.drivingcoach.ui.session.SessionResultViewModel";

      static String com_bmw_drivingcoach_ui_auth_LoginViewModel = "com.bmw.drivingcoach.ui.auth.LoginViewModel";

      static String com_bmw_drivingcoach_ui_home_HomeViewModel = "com.bmw.drivingcoach.ui.home.HomeViewModel";

      @KeepFieldType
      RegisterViewModel com_bmw_drivingcoach_ui_auth_RegisterViewModel2;

      @KeepFieldType
      ProfileViewModel com_bmw_drivingcoach_ui_profile_ProfileViewModel2;

      @KeepFieldType
      LapDetailViewModel com_bmw_drivingcoach_ui_session_LapDetailViewModel2;

      @KeepFieldType
      RecordingViewModel com_bmw_drivingcoach_ui_recording_RecordingViewModel2;

      @KeepFieldType
      SessionResultViewModel com_bmw_drivingcoach_ui_session_SessionResultViewModel2;

      @KeepFieldType
      LoginViewModel com_bmw_drivingcoach_ui_auth_LoginViewModel2;

      @KeepFieldType
      HomeViewModel com_bmw_drivingcoach_ui_home_HomeViewModel2;
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final ActivityRetainedCImpl activityRetainedCImpl;

      private final ViewModelCImpl viewModelCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
          ViewModelCImpl viewModelCImpl, int id) {
        this.singletonCImpl = singletonCImpl;
        this.activityRetainedCImpl = activityRetainedCImpl;
        this.viewModelCImpl = viewModelCImpl;
        this.id = id;
      }

      @SuppressWarnings("unchecked")
      @Override
      public T get() {
        switch (id) {
          case 0: // com.bmw.drivingcoach.ui.home.HomeViewModel 
          return (T) new HomeViewModel(singletonCImpl.sessionDao(), singletonCImpl.lapDao(), ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 1: // com.bmw.drivingcoach.ui.session.LapDetailViewModel 
          return (T) new LapDetailViewModel(singletonCImpl.lapDao(), viewModelCImpl.savedStateHandle);

          case 2: // com.bmw.drivingcoach.ui.auth.LoginViewModel 
          return (T) new LoginViewModel();

          case 3: // com.bmw.drivingcoach.ui.profile.ProfileViewModel 
          return (T) new ProfileViewModel(singletonCImpl.sessionDao(), singletonCImpl.lapDao(), singletonCImpl.provideInMemoryDatabaseProvider.get(), singletonCImpl.provideDataStoreProvider.get());

          case 4: // com.bmw.drivingcoach.ui.recording.RecordingViewModel 
          return (T) new RecordingViewModel(ApplicationContextModule_ProvideApplicationFactory.provideApplication(singletonCImpl.applicationContextModule));

          case 5: // com.bmw.drivingcoach.ui.auth.RegisterViewModel 
          return (T) new RegisterViewModel();

          case 6: // com.bmw.drivingcoach.ui.session.SessionResultViewModel 
          return (T) new SessionResultViewModel(singletonCImpl.sessionDao(), singletonCImpl.lapDao(), singletonCImpl.coachingInsightDao(), singletonCImpl.provideWorkManagerProvider.get(), viewModelCImpl.savedStateHandle);

          default: throw new AssertionError(id);
        }
      }
    }
  }

  private static final class ActivityRetainedCImpl extends Default_HiltComponents.ActivityRetainedC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl = this;

    private Provider<ActivityRetainedLifecycle> provideActivityRetainedLifecycleProvider;

    private ActivityRetainedCImpl(SingletonCImpl singletonCImpl,
        SavedStateHandleHolder savedStateHandleHolderParam) {
      this.singletonCImpl = singletonCImpl;

      initialize(savedStateHandleHolderParam);

    }

    @SuppressWarnings("unchecked")
    private void initialize(final SavedStateHandleHolder savedStateHandleHolderParam) {
      this.provideActivityRetainedLifecycleProvider = DoubleCheck.provider(new SwitchingProvider<ActivityRetainedLifecycle>(singletonCImpl, activityRetainedCImpl, 0));
    }

    @Override
    public ActivityComponentBuilder activityComponentBuilder() {
      return new ActivityCBuilder(singletonCImpl, activityRetainedCImpl);
    }

    @Override
    public ActivityRetainedLifecycle getActivityRetainedLifecycle() {
      return provideActivityRetainedLifecycleProvider.get();
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final ActivityRetainedCImpl activityRetainedCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
          int id) {
        this.singletonCImpl = singletonCImpl;
        this.activityRetainedCImpl = activityRetainedCImpl;
        this.id = id;
      }

      @SuppressWarnings("unchecked")
      @Override
      public T get() {
        switch (id) {
          case 0: // dagger.hilt.android.ActivityRetainedLifecycle 
          return (T) ActivityRetainedComponentManager_LifecycleModule_ProvideActivityRetainedLifecycleFactory.provideActivityRetainedLifecycle();

          default: throw new AssertionError(id);
        }
      }
    }
  }

  private static final class ServiceCImpl extends Default_HiltComponents.ServiceC {
    private final SingletonCImpl singletonCImpl;

    private final ServiceCImpl serviceCImpl = this;

    private ServiceCImpl(SingletonCImpl singletonCImpl, Service serviceParam) {
      this.singletonCImpl = singletonCImpl;


    }

    @Override
    public void injectTelemetryForegroundService(
        TelemetryForegroundService telemetryForegroundService) {
      injectTelemetryForegroundService2(telemetryForegroundService);
    }

    @CanIgnoreReturnValue
    private TelemetryForegroundService injectTelemetryForegroundService2(
        TelemetryForegroundService instance) {
      TelemetryForegroundService_MembersInjector.injectSessionDao(instance, singletonCImpl.sessionDao());
      return instance;
    }
  }

  private static final class SingletonCImpl extends Default_HiltComponents.SingletonC {
    private final ApplicationContextModule applicationContextModule;

    private final SingletonCImpl singletonCImpl = this;

    private Provider<BMWDatabase> provideInMemoryDatabaseProvider;

    private Provider<MockWebServer> provideMockWebServerProvider;

    private Provider<DataStore<Preferences>> provideDataStoreProvider;

    private Provider<AuthEventBus> authEventBusProvider;

    private Provider<WorkManager> provideWorkManagerProvider;

    private SingletonCImpl(ApplicationContextModule applicationContextModuleParam) {
      this.applicationContextModule = applicationContextModuleParam;
      initialize(applicationContextModuleParam);

    }

    private SessionDao sessionDao() {
      return TestDatabaseModule_ProvideSessionDaoFactory.provideSessionDao(provideInMemoryDatabaseProvider.get());
    }

    private LapDao lapDao() {
      return TestDatabaseModule_ProvideLapDaoFactory.provideLapDao(provideInMemoryDatabaseProvider.get());
    }

    private CoachingInsightDao coachingInsightDao() {
      return TestDatabaseModule_ProvideCoachingInsightDaoFactory.provideCoachingInsightDao(provideInMemoryDatabaseProvider.get());
    }

    @SuppressWarnings("unchecked")
    private void initialize(final ApplicationContextModule applicationContextModuleParam) {
      this.provideInMemoryDatabaseProvider = DoubleCheck.provider(new SwitchingProvider<BMWDatabase>(singletonCImpl, 0));
      this.provideMockWebServerProvider = DoubleCheck.provider(new SwitchingProvider<MockWebServer>(singletonCImpl, 1));
      this.provideDataStoreProvider = DoubleCheck.provider(new SwitchingProvider<DataStore<Preferences>>(singletonCImpl, 2));
      this.authEventBusProvider = DoubleCheck.provider(new SwitchingProvider<AuthEventBus>(singletonCImpl, 3));
      this.provideWorkManagerProvider = DoubleCheck.provider(new SwitchingProvider<WorkManager>(singletonCImpl, 4));
    }

    @Override
    public void injectBMWDrivingCoachApp(BMWDrivingCoachApp bMWDrivingCoachApp) {
    }

    @Override
    public void injectTest(EndToEndTest endToEndTest) {
      injectEndToEndTest(endToEndTest);
    }

    @Override
    public void injectTest(RecordingFragmentTest recordingFragmentTest) {
    }

    @Override
    public Set<Boolean> getDisableFragmentGetContextFix() {
      return ImmutableSet.<Boolean>of();
    }

    @Override
    public ActivityRetainedComponentBuilder retainedComponentBuilder() {
      return new ActivityRetainedCBuilder(singletonCImpl);
    }

    @Override
    public ServiceComponentBuilder serviceComponentBuilder() {
      return new ServiceCBuilder(singletonCImpl);
    }

    @CanIgnoreReturnValue
    private EndToEndTest injectEndToEndTest(EndToEndTest instance) {
      EndToEndTest_MembersInjector.injectDatabase(instance, provideInMemoryDatabaseProvider.get());
      EndToEndTest_MembersInjector.injectMockWebServer(instance, provideMockWebServerProvider.get());
      EndToEndTest_MembersInjector.injectDataStore(instance, provideDataStoreProvider.get());
      return instance;
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, int id) {
        this.singletonCImpl = singletonCImpl;
        this.id = id;
      }

      @SuppressWarnings("unchecked")
      @Override
      public T get() {
        switch (id) {
          case 0: // com.bmw.drivingcoach.data.db.BMWDatabase 
          return (T) TestDatabaseModule_ProvideInMemoryDatabaseFactory.provideInMemoryDatabase(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 1: // okhttp3.mockwebserver.MockWebServer 
          return (T) TestNetworkModule_ProvideMockWebServerFactory.provideMockWebServer();

          case 2: // androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> 
          return (T) AppModule_ProvideDataStoreFactory.provideDataStore(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 3: // com.bmw.drivingcoach.data.api.AuthEventBus 
          return (T) new AuthEventBus();

          case 4: // androidx.work.WorkManager 
          return (T) TestNetworkModule_ProvideWorkManagerFactory.provideWorkManager(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          default: throw new AssertionError(id);
        }
      }
    }
  }
}
