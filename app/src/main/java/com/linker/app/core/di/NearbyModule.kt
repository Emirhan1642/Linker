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
 * 
 * **Functionality:**
 * - Wi-Fi Direct peer discovery
 * - Direct device-to-device connections
 * - High-bandwidth data transfer
 * - Automatic connection management
 * 
 * **Configuration:**
 * NearbyConnectionsManager uses OfflineMessagingConfig for:
 * - WIFI_DIRECT_SERVICE_ID_PREFIX: Service identifier for discovery
 * - CONNECTION_TIMEOUT_MS: Timeout for connection attempts
 * - TRANSFER_TIMEOUT_MS: Timeout for data transfers
 * 
 * **Use Cases:**
 * - Large file transfers (photos, videos)
 * - Bulk message synchronization
 * - Fallback when BLE is unavailable
 * - High-speed local communication
 * 
 * **Architecture:**
 * Implementation uses @Inject constructor with OfflineMessagingConfig
 * dependency, so no additional provider is needed.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NearbyModule {
    
    /**
     * Binds NearbyConnectionsManager implementation
     * 
     * CONFIGURATION:
     * - Service ID from OfflineMessagingConfig
     * - Connection strategy: P2P_STAR (one-to-many)
     * - Automatic reconnection on failure
     * - Bandwidth: HIGH (WiFi Direct)
     */
    @Binds
    @Singleton
    abstract fun bindNearbyConnectionsManager(
        impl: NearbyConnectionsManagerImpl
    ): NearbyConnectionsManager
}
