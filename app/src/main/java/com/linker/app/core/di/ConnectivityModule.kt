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
 * - ConnectivityMonitor: Network state monitoring (WiFi, Cellular, BLE, WiFi Direct)
 * - PermissionManager: Runtime permission handling
 * 
 * **Connectivity Monitoring:**
 * - Real-time network state changes
 * - Connection type detection (WiFi, Cellular, None)
 * - BLE and WiFi Direct availability
 * - Bandwidth estimation
 * 
 * **Permission Management:**
 * - Runtime permission requests
 * - Permission status checking
 * - Rationale display for denied permissions
 * - Settings navigation for permanently denied permissions
 * 
 * **Architecture:**
 * Both implementations use @Inject constructor, so no additional
 * configuration is needed in this module.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ConnectivityModule {
    
    /**
     * Binds ConnectivityMonitor implementation
     * 
     * FUNCTIONALITY:
     * - Monitors network connectivity changes
     * - Detects WiFi, Cellular, BLE, WiFi Direct availability
     * - Provides Flow-based reactive updates
     * - Automatic cleanup on app termination
     */
    @Binds
    @Singleton
    abstract fun bindConnectivityMonitor(
        impl: ConnectivityMonitorImpl
    ): ConnectivityMonitor
    
    /**
     * Binds PermissionManager implementation
     * 
     * FUNCTIONALITY:
     * - Handles runtime permission requests
     * - Checks permission status
     * - Manages permission rationale
     * - Navigates to app settings for denied permissions
     */
    @Binds
    @Singleton
    abstract fun bindPermissionManager(
        impl: PermissionManagerImpl
    ): PermissionManager
}
