package com.linker.app.core.di

import com.linker.app.data.connectivity.ConnectivityMonitor
import com.linker.app.data.connectivity.ConnectivityMonitorImpl
import com.linker.app.data.permission.PermissionManager
import com.linker.app.data.permission.PermissionManagerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for connectivity and permission management
 * 
 * Provides:
 * - ConnectivityMonitor: Network state monitoring
 * - PermissionManager: Runtime permission handling
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ConnectivityModule {
    
    @Binds
    @Singleton
    abstract fun bindConnectivityMonitor(
        impl: ConnectivityMonitorImpl
    ): ConnectivityMonitor
    
    @Binds
    @Singleton
    abstract fun bindPermissionManager(
        impl: PermissionManagerImpl
    ): PermissionManager
}
