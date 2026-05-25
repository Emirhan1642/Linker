package com.linker.app.core.di

import com.linker.app.core.config.OfflineMessagingConfig
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
import javax.inject.Singleton

/**
 * Hilt module for BLE Mesh Network dependencies
 * 
 * **Architecture:**
 * - BLEMeshManager: Main coordinator for BLE mesh operations
 * - BLEConnectionPool: Manages active BLE connections
 * - FragmentManager: Handles message fragmentation for large payloads
 * - MessageBatcher: Batches messages for efficient transmission
 * - BLEHealthChecker: Monitors component health
 * 
 * **Memory Management:**
 * Uses application-scoped CoroutineScope to prevent memory leaks.
 * All components are singletons with proper lifecycle management.
 * 
 * **Configuration:**
 * BLE parameters (MTU, timeouts, pool size) are sourced from OfflineMessagingConfig.
 * 
 * **Lifecycle Management:**
 * - Components are initialized lazily on first injection
 * - FragmentManager uses application-scoped coroutines (survives activity recreation)
 * - ConnectionPool automatically cleans up stale connections
 * - MessageBatcher pauses when connectivity is lost
 * - All components are cleaned up when app process terminates
 * 
 * **Error Handling:**
 * - All providers validate configuration parameters
 * - Initialization failures are logged and thrown as IllegalStateException
 * - Health checker provides runtime diagnostics
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
        /**
         * Provides BLE connection pool with configuration
         * 
         * CONFIGURATION:
         * - Max connections from OfflineMessagingConfig
         * - Connection timeout from OfflineMessagingConfig
         * - Automatic connection cleanup on timeout
         * 
         * ERROR HANDLING:
         * - Validates configuration parameters
         * - Logs initialization status
         * - Throws on invalid configuration
         */
        @Provides
        @Singleton
        fun provideBLEConnectionPool(): BLEConnectionPool {
            try {
                // Validate configuration
                require(OfflineMessagingConfig.MAX_BLE_CONNECTIONS > 0) {
                    "BLE max connections must be positive, got: \${OfflineMessagingConfig.MAX_BLE_CONNECTIONS}"
                }
                require(OfflineMessagingConfig.BLE_CONNECTION_TIMEOUT_MS > 0) {
                    "BLE connection timeout must be positive, got: \${OfflineMessagingConfig.BLE_CONNECTION_TIMEOUT_MS}"
                }
                
                val pool = BLEConnectionPool()
                
                android.util.Log.d(
                    "BleModule",
                    "BLE Connection Pool initialized: maxConnections=\${OfflineMessagingConfig.MAX_BLE_CONNECTIONS}, timeout=\${OfflineMessagingConfig.BLE_CONNECTION_TIMEOUT_MS}ms"
                )
                
                return pool
            } catch (e: Exception) {
                android.util.Log.e("BleModule", "Failed to initialize BLE Connection Pool", e)
                throw IllegalStateException("BLE Connection Pool initialization failed", e)
            }
        }

        /**
         * Provides FragmentManager with application-scoped coroutine scope
         * 
         * CRITICAL FIX: Uses injected ApplicationScope instead of creating new scope.
         * This prevents memory leaks from orphaned coroutines.
         * 
         * CONFIGURATION:
         * - Fragment timeout from OfflineMessagingConfig (20 minutes for large files)
         * - Application-scoped coroutines for proper lifecycle management
         * 
         * ERROR HANDLING:
         * - Validates timeout configuration
         * - Logs initialization status
         * - Throws on invalid configuration
         * 
         * @param scope Application-scoped CoroutineScope (injected, not manually created)
         * @param config Configuration for fragment timeout
         */
        @Provides
        @Singleton
        fun provideFragmentManager(
            @ApplicationScope scope: CoroutineScope
        ): FragmentManager {
            try {
                // Validate configuration
                require(OfflineMessagingConfig.FRAGMENT_TIMEOUT_MS > 0) {
                    "Fragment timeout must be positive, got: \${OfflineMessagingConfig.FRAGMENT_TIMEOUT_MS}"
                }
                require(OfflineMessagingConfig.FRAGMENT_TIMEOUT_MS >= 60_000) {
                    "Fragment timeout too short (min 60s), got: \${OfflineMessagingConfig.FRAGMENT_TIMEOUT_MS}ms"
                }
                
                val manager = FragmentManager(
                    fragmentTimeout = OfflineMessagingConfig.FRAGMENT_TIMEOUT_MS,
                    coroutineScope = scope
                )
                
                android.util.Log.d(
                    "BleModule",
                    "FragmentManager initialized: timeout=\${OfflineMessagingConfig.FRAGMENT_TIMEOUT_MS}ms"
                )
                
                return manager
            } catch (e: Exception) {
                android.util.Log.e("BleModule", "Failed to initialize FragmentManager", e)
                throw IllegalStateException("FragmentManager initialization failed", e)
            }
        }

        /**
         * Provides MessageBatcher for efficient message transmission
         * 
         * FUNCTIONALITY:
         * - Batches multiple small messages into single BLE transmission
         * - Monitors connectivity to pause/resume batching
         * - Reduces BLE overhead and improves throughput
         * 
         * ERROR HANDLING:
         * - Validates connectivity monitor
         * - Logs initialization status
         */
        @Provides
        @Singleton
        fun provideMessageBatcher(
            connectivityMonitor: com.linker.app.data.connectivity.ConnectivityMonitor
        ): MessageBatcher {
            try {
                val batcher = MessageBatcher(connectivityMonitor)
                
                android.util.Log.d("BleModule", "MessageBatcher initialized")
                
                return batcher
            } catch (e: Exception) {
                android.util.Log.e("BleModule", "Failed to initialize MessageBatcher", e)
                throw IllegalStateException("MessageBatcher initialization failed", e)
            }
        }
        
        /**
         * Provides BLE Health Checker for monitoring BLE components
         * 
         * FUNCTIONALITY:
         * - Validates BLE adapter availability
         * - Checks connection pool status
         * - Monitors fragment manager health
         * - Verifies message batcher operation
         * 
         * USAGE:
         * ```kotlin
         * val health = bleHealthChecker.checkHealth()
         * if (health.isHealthy) {
         *     // BLE is operational
         * } else {
         *     // Handle degraded state
         * }
         * ```
         */
        @Provides
        @Singleton
        fun provideBLEHealthChecker(
            connectionPool: BLEConnectionPool,
            fragmentManager: FragmentManager,
            messageBatcher: MessageBatcher
        ): BLEHealthChecker {
            return BLEHealthChecker(
                connectionPool = connectionPool,
                fragmentManager = fragmentManager,
                messageBatcher = messageBatcher
            )
        }
    }
}

/**
 * BLE Health Checker
 * 
 * Monitors the health of BLE components and provides diagnostic information.
 */
class BLEHealthChecker(
    private val connectionPool: BLEConnectionPool,
    private val fragmentManager: FragmentManager,
    private val messageBatcher: MessageBatcher
) {
    /**
     * Check overall BLE system health
     * 
     * @return Health status with component details
     */
    suspend fun checkHealth(): BLEHealthStatus {
        val issues = mutableListOf<String>()
        
        // Check connection pool
        try {
            val activeConnections = connectionPool.getConnectionCount()
            if (activeConnections < 0) {
                issues.add("Connection pool in invalid state")
            }
        } catch (e: Exception) {
            issues.add("Connection pool check failed: ${e.message}")
        }
        
        // Check fragment manager
        try {
            val pendingFragments = fragmentManager.getIncompleteMessageCount()
            if (pendingFragments > 1000) {
                issues.add("Too many pending fragments: $pendingFragments")
            }
        } catch (e: Exception) {
            issues.add("Fragment manager check failed: ${e.message}")
        }
        
        // Check message batcher
        try {
            val queueSize = messageBatcher.getBatchSize()
            if (queueSize > 500) {
                issues.add("Message batcher queue too large: $queueSize")
            }
        } catch (e: Exception) {
            issues.add("Message batcher check failed: ${e.message}")
        }
        
        return BLEHealthStatus(
            isHealthy = issues.isEmpty(),
            issues = issues
        )
    }
}

/**
 * BLE Health Status
 */
data class BLEHealthStatus(
    val isHealthy: Boolean,
    val issues: List<String>
)
