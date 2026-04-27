package com.linker.app.data.connectivity

/**
 * Represents the current network connectivity state
 */
sealed class ConnectivityState {
    /**
     * Device has internet connection and it's validated
     */
    object Online : ConnectivityState()
    
    /**
     * Device has no network connection
     */
    object Offline : ConnectivityState()
    
    /**
     * Device is connected to network but internet is not validated
     * @param isMetered Whether the connection is metered (e.g., mobile data)
     */
    data class Limited(val isMetered: Boolean) : ConnectivityState()
}
