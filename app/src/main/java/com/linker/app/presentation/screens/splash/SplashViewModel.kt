package com.linker.app.presentation.screens.splash

import androidx.lifecycle.ViewModel
import com.linker.app.core.util.OnboardingPreferences
import com.linker.app.domain.model.AccountSession
import com.linker.app.domain.repository.AccountRepository
import com.linker.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val onboardingPreferences: OnboardingPreferences
) : ViewModel() {

    suspend fun resolveStartDestination(): SplashDestination {
        val result = userRepository.getCurrentUser()
        val user = (result as? com.linker.app.core.util.Result.Success)?.data
        if (user != null && user.hasCompletedOnboarding()) {
            return SplashDestination.HOME
        }

        return if (!onboardingPreferences.isOnboardingCompleted()) {
            SplashDestination.ONBOARDING
        } else {
            SplashDestination.AUTH
        }
    }
}
