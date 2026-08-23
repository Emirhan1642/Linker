package com.linker.app.core.util

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnboardingPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "linker_onboarding_prefs"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isOnboardingCompleted(): Boolean {
        return prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }

    fun setOnboardingCompleted(completed: Boolean = true) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
    }
}
