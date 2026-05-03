package com.linker.app.core.di

import com.linker.app.data.ble.BLEConnectionPool
import com.linker.app.data.ble.BLEMeshManager
import com.linker.app.data.ble.BLEMeshManagerImpl
import com.linker.app.data.ble.FragmentManager
import com.linker.app.data.ble.MessageBatcher
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * Hilt module for BLE Mesh Network dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BleModule {

    @Binds
    @Singleton
    abstract fun bindBLEMeshManager(
        impl: BLEMeshManagerImpl
    ): BLEMeshManager
    
    companion object {
        @Provides
        @Singleton
        fun provideBLEConnectionPool(): BLEConnectionPool {
            return BLEConnectionPool()
        }

        @Provides
        @Singleton
        fun provideFragmentManager(): FragmentManager {
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            return FragmentManager(
                fragmentTimeout = 30_000L, // 30 seconds
                coroutineScope = scope
            )
        }

        @Provides
        @Singleton
        fun provideMessageBatcher(
            connectivityMonitor: com.linker.app.data.connectivity.ConnectivityMonitor
        ): MessageBatcher {
            return MessageBatcher(connectivityMonitor)
        }
    }
}
