package com.linker.app.data.connectivity

import kotlinx.coroutines.flow.Flow

/**
 * Interface for monitoring network connectivity state
 * 
 * Provides real-time updates on network availability and internet validation.
 * Used to determine whether to use online or offline delivery methods.
 */
interface ConnectivityMonitor {
    
    /**
     * Start monitoring network connectivity changes
     */
    fun startMonitoring()
    
    /**
     * Stop monitoring network connectivity changes
     */
    fun stopMonitoring()
    
    /**
     * Check if device currently has validated internet connection
     * @return true if online with validated internet, false otherwise
     */
    fun isOnline(): Boolean
    
    /**
     * Check if current connection is metered (e.g., mobile data)
     * @return true if connection is metered, false otherwise
     */
    fun isMetered(): Boolean
    
    /**
     * Observe connectivity state changes
     * @return Flow emitting connectivity state updates
     */
    fun observeConnectivityState(): Flow<ConnectivityState>
}
