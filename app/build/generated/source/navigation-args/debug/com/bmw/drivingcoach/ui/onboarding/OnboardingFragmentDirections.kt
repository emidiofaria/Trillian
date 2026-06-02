package com.bmw.drivingcoach.ui.onboarding

import androidx.navigation.ActionOnlyNavDirections
import androidx.navigation.NavDirections
import com.bmw.drivingcoach.R

public class OnboardingFragmentDirections private constructor() {
  public companion object {
    public fun actionOnboardingToHome(): NavDirections =
        ActionOnlyNavDirections(R.id.action_onboarding_to_home)
  }
}
