package com.linker.app.core.di

import com.linker.app.data.encryption.EncryptionManager
import com.linker.app.data.encryption.EncryptionManagerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for encryption components
 * 
 * Provides Signal Protocol encryption manager for end-to-end encryption.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class EncryptionModule {
    
    @Binds
    @Singleton
    abstract fun bindEncryptionManager(
        impl: EncryptionManagerImpl
    ): EncryptionManager
}
