package com.linker.app.core.di

import android.content.Context
import com.linker.app.data.permission.PermissionPreferences
import com.linker.app.data.permission.PermissionPreferencesImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PermissionModule {
    
    @Provides
    @Singleton
    fun providePermissionPreferences(
        @ApplicationContext context: Context
    ): PermissionPreferences {
        return PermissionPreferencesImpl(context)
    }
}
