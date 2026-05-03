package com.linker.app.core.di

import com.linker.app.data.preferences.OfflineMessagingPreferencesRepository
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module for DataStore preferences dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object PreferencesModule {
    // OfflineMessagingPreferencesRepository is provided via @Inject constructor
    // No additional configuration needed
}
