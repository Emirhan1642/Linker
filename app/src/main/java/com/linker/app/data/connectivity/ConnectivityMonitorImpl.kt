package com.linker.app.data.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of ConnectivityMonitor using Android's ConnectivityManager
 * 
 * Monitors network state changes and validates internet connectivity using
 * NetworkCapabilities.NET_CAPABILITY_VALIDATED.
 */
@Singleton
class ConnectivityMonitorImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : ConnectivityMonitor {
    
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) 
        as ConnectivityManager
    
    private val _connectivityState = MutableStateFlow<ConnectivityState>(ConnectivityState.Offline)
    
    private var isMonitoring = false
    
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        
        override fun onAvailable(network: Network) {
            // Network is available, but we need to check capabilities
            updateConnectivityState(network)
        }
        
        override fun onCapabilitiesChanged(
            network: Network,
            capabilities: NetworkCapabilities
        ) {
            val hasInternet = capabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET
            )
            val isValidated = capabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_VALIDATED
            )
            val isMetered = !capabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_NOT_METERED
            )
            
            _connectivityState.value = when {
                hasInternet && isValidated -> ConnectivityState.Online
                hasInternet && !isValidated -> ConnectivityState.Limited(isMetered)
                else -> ConnectivityState.Offline
            }
        }
        
        override fun onLost(network: Network) {
            _connectivityState.value = ConnectivityState.Offline
        }
    }
    
    override fun startMonitoring() {
        if (isMonitoring) return
        
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        connectivityManager.registerNetworkCallback(request, networkCallback)
        isMonitoring = true
        
        // Initialize current state
        updateConnectivityState(connectivityManager.activeNetwork)
    }
    
    override fun stopMonitoring() {
        if (!isMonitoring) return
        
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: IllegalArgumentException) {
            // Callback was not registered, ignore
        }
        isMonitoring = false
    }
    
    override fun isOnline(): Boolean {
        return _connectivityState.value is ConnectivityState.Online
    }
    
    override fun isMetered(): Boolean {
        return when (val state = _connectivityState.value) {
            is ConnectivityState.Limited -> state.isMetered
            is ConnectivityState.Online -> {
                // Check current network capabilities
                val network = connectivityManager.activeNetwork ?: return false
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
                !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            }
            is ConnectivityState.Offline -> false
        }
    }
    
    override fun observeConnectivityState(): Flow<ConnectivityState> {
        return _connectivityState.asStateFlow()
    }
    
    private fun updateConnectivityState(network: Network?) {
        if (network == null) {
            _connectivityState.value = ConnectivityState.Offline
            return
        }
        
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        if (capabilities == null) {
            _connectivityState.value = ConnectivityState.Offline
            return
        }
        
        val hasInternet = capabilities.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_INTERNET
        )
        val isValidated = capabilities.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_VALIDATED
        )
        val isMetered = !capabilities.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_NOT_METERED
        )
        
        _connectivityState.value = when {
            hasInternet && isValidated -> ConnectivityState.Online
            hasInternet && !isValidated -> ConnectivityState.Limited(isMetered)
            else -> ConnectivityState.Offline
        }
    }
}
