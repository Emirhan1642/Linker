package com.linker.app.core.di

import com.linker.app.data.nearby.NearbyConnectionsManager
import com.linker.app.data.nearby.NearbyConnectionsManagerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt DI module for Nearby Connections (Wi-Fi Direct).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NearbyModule {
    
    @Binds
    @Singleton
    abstract fun bindNearbyConnectionsManager(
        impl: NearbyConnectionsManagerImpl
    ): NearbyConnectionsManager
}
