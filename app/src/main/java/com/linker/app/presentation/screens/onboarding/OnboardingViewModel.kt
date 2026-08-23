package com.linker.app.presentation.screens.onboarding

import androidx.lifecycle.ViewModel
import com.linker.app.core.util.OnboardingPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val onboardingPreferences: OnboardingPreferences
) : ViewModel() {

    fun completeOnboarding() {
        onboardingPreferences.setOnboardingCompleted(true)
    }
}
