package com.linker.app.core.di

import com.linker.app.core.security.SecurityManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Security Module
 *
 * Provides SecurityManager for encrypted API key storage
 */
@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides
    @Singleton
    fun provideSecurityManager(securityManager: SecurityManager): SecurityManager {
        return securityManager
    }
}
