package com.linker.app.data.connectivity

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.linker.app.BuildConfig
import com.linker.app.core.util.SecureLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
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
) : ConnectivityMonitor, DefaultLifecycleObserver {

    private val config: ConnectivityConfig = ConnectivityConfig()
    
    private val connectivityManager: ConnectivityManager? = try {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    } catch (e: Exception) {
        logError("Error getting ConnectivityManager", e)
        null
    }
    
    private val _connectivityState = MutableStateFlow<ConnectivityState>(ConnectivityState.Offline())
    
    private val _errors = MutableSharedFlow<ConnectivityError>(
        replay = 0,
        extraBufferCapacity = config.errorBufferCapacity
    )
    
    private val _connectionQuality = MutableStateFlow<ConnectionQuality?>(null)
    
    private val _metrics = MutableStateFlow(ConnectivityMetrics(0, 0, 0, 0, 0, 0, 0))
    
    private val isMonitoring = AtomicBoolean(false)
    private val callbackMutex = Mutex()
    private val stateMutex = Mutex()
    
    private val activeNetworks = ConcurrentHashMap<Network, NetworkCapabilities>()
    
    private var stateUpdateJob: Job? = null
    private var errorCallback: ((ConnectivityError) -> Unit)? = null
    
    // Metrics variables
    private val stateChangeCount = AtomicInteger(0)
    private val lastStateChangeTime = AtomicLong(0)
    private val onlineTime = AtomicLong(0)
    private val offlineTime = AtomicLong(0)
    private val limitedTime = AtomicLong(0)
    
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        // Register for app lifecycle
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        
        // Register cleanup on process death
        Runtime.getRuntime().addShutdownHook(Thread {
            cleanup()
        })
    }
    
    override fun onStart(owner: LifecycleOwner) {
        startMonitoring()
    }
    
    override fun onStop(owner: LifecycleOwner) {
        stopMonitoring()
    }
    
    private fun cleanup() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
        stopMonitoring()
        coroutineScope.cancel()
    }
    
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            try {
                logDebug("onAvailable: network=$network")
                val capabilities = connectivityManager?.getNetworkCapabilities(network) ?: return
                activeNetworks[network] = capabilities
                updateConnectivityStateFromActiveNetworks()
            } catch (e: Exception) {
                logError("Error in onAvailable", e)
            }
        }
        
        override fun onCapabilitiesChanged(
            network: Network,
            capabilities: NetworkCapabilities
        ) {
            try {
                activeNetworks[network] = capabilities
                updateConnectivityStateFromActiveNetworks()
                
                // Update Connection Quality
                val (downKbps, upKbps) = getBandwidthInfo(capabilities)
                logDebug("Bandwidth: down=${downKbps}Kbps, up=${upKbps}Kbps")
                _connectionQuality.value = ConnectionQuality(
                    downloadBandwidthKbps = downKbps,
                    uploadBandwidthKbps = upKbps,
                    signalStrength = null // Could be derived if needed
                )
            } catch (e: Exception) {
                logError("Error in onCapabilitiesChanged", e)
                emitError(ConnectivityError.SystemError(e))
                updateStateDebounced(ConnectivityState.Offline())
            }
        }
        
        override fun onLost(network: Network) {
            try {
                logDebug("onLost: network=$network")
                activeNetworks.remove(network)
                updateConnectivityStateFromActiveNetworks()
            } catch (e: Exception) {
                logError("Error in onLost", e)
            }
        }
    }
    
    private fun hasNetworkPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_NETWORK_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    override fun startMonitoring() {
        if (!hasNetworkPermission()) {
            val error = ConnectivityError.PermissionDenied("ACCESS_NETWORK_STATE")
            emitError(error)
            logError("ACCESS_NETWORK_STATE permission not granted")
            return
        }
        
        val manager = connectivityManager
        if (manager == null) {
            logError("ConnectivityManager is null, cannot start monitoring")
            return
        }
        
        if (isMonitoring.compareAndSet(false, true)) {
            runBlocking {
                callbackMutex.withLock {
                    try {
                        val request = NetworkRequest.Builder()
                            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                            .build()
                        
                        manager.registerNetworkCallback(request, networkCallback)
                        
                        // Initialize current state with validation
                        val activeNetwork = manager.activeNetwork
                        if (activeNetwork != null) {
                            val caps = manager.getNetworkCapabilities(activeNetwork)
                            if (caps != null) {
                                activeNetworks[activeNetwork] = caps
                                updateConnectivityStateFromActiveNetworks()
                            } else {
                                updateStateDebounced(ConnectivityState.Offline())
                            }
                        } else {
                            updateStateDebounced(ConnectivityState.Offline())
                        }
                        
                        logDebug("startMonitoring: initial state = ${_connectivityState.value}")
                    } catch (e: Exception) {
                        logError("Error starting monitoring", e)
                        emitError(ConnectivityError.SystemError(e))
                        isMonitoring.set(false)
                        updateStateDebounced(ConnectivityState.Offline())
                    }
                }
            }
        } else {
            logDebug("Monitoring already started")
        }
    }
    
    override fun stopMonitoring() {
        if (isMonitoring.compareAndSet(true, false)) {
            runBlocking {
                callbackMutex.withLock {
                    try {
                        connectivityManager?.unregisterNetworkCallback(networkCallback)
                        activeNetworks.clear()
                        logDebug("Monitoring stopped")
                    } catch (e: IllegalArgumentException) {
                        logWarning("Callback was not registered")
                    } catch (e: Exception) {
                        logError("Error stopping monitoring", e)
                    }
                }
            }
        } else {
            logDebug("Monitoring not started")
        }
    }
    
    override fun isMonitoring(): Boolean {
        return isMonitoring.get()
    }
    
    override fun getCurrentState(): ConnectivityState {
        return _connectivityState.value
    }
    
    override fun isOnline(): Boolean {
        return _connectivityState.value is ConnectivityState.Online
    }
    
    override fun isMetered(): Boolean {
        return _connectivityState.value.isMetered()
    }
    
    override fun observeConnectivityState(): Flow<ConnectivityState> {
        return _connectivityState.asStateFlow()
    }
    
    override fun observeErrors(): Flow<ConnectivityError> {
        return _errors.asSharedFlow()
    }
    
    override fun setErrorCallback(callback: (ConnectivityError) -> Unit) {
        this.errorCallback = callback
    }
    
    override fun getConnectionQuality(): ConnectionQuality? {
        return _connectionQuality.value
    }
    
    override fun observeConnectionQuality(): Flow<ConnectionQuality?> {
        return _connectionQuality.asStateFlow()
    }
    
    override fun hasMinimumQuality(minDownloadKbps: Int, minUploadKbps: Int): Boolean {
        val q = _connectionQuality.value ?: return false
        return q.downloadBandwidthKbps >= minDownloadKbps && q.uploadBandwidthKbps >= minUploadKbps
    }
    
    override suspend fun waitUntilOnline(timeoutMs: Long?): Boolean {
        if (isOnline()) return true
        
        return try {
            withTimeoutOrNull(timeoutMs ?: Long.MAX_VALUE) {
                _connectivityState.first { it is ConnectivityState.Online }
                true
            } ?: false
        } catch (e: TimeoutCancellationException) {
            false
        }
    }
    
    override suspend fun checkConnectivity(): Boolean {
        if (!hasNetworkPermission() || connectivityManager == null) return false
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && 
               caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    
    override suspend fun pingHost(host: String, timeoutMs: Long): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val process = Runtime.getRuntime().exec("/system/bin/ping -c 1 -w ${timeoutMs/1000} $host")
                val exitValue = process.waitFor()
                exitValue == 0
            } catch (e: Exception) {
                false
            }
        }
    }
    
    override fun getNetworkTransport(): ConnectionType {
        val state = _connectivityState.value
        return when (state) {
            is ConnectivityState.Online -> state.connectionType
            is ConnectivityState.Limited -> state.connectionType
            is ConnectivityState.Offline -> ConnectionType.UNKNOWN
        }
    }
    
    override fun isWiFi(): Boolean {
        return getNetworkTransport() == ConnectionType.WIFI
    }
    
    override fun isCellular(): Boolean {
        return getNetworkTransport() == ConnectionType.CELLULAR
    }
    
    override fun isVpnActive(): Boolean {
        return getNetworkTransport() == ConnectionType.VPN
    }
    
    override fun getMetrics(): ConnectivityMetrics {
        return _metrics.value
    }
    
    override fun resetMetrics() {
        stateChangeCount.set(0)
        onlineTime.set(0)
        offlineTime.set(0)
        limitedTime.set(0)
        lastStateChangeTime.set(System.currentTimeMillis())
        publishMetrics()
    }
    
    override fun observeMetrics(): Flow<ConnectivityMetrics> {
        return _metrics.asStateFlow()
    }
    
    // --- Helper Methods ---

    private fun updateConnectivityStateFromActiveNetworks() {
        if (activeNetworks.isEmpty()) {
            updateStateDebounced(ConnectivityState.Offline())
            return
        }
        
        // Find best network (validated > unvalidated, WiFi > Cellular)
        val bestNetworkCaps = activeNetworks.values.maxByOrNull { capabilities ->
            var score = 0
            if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) score += 100
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) score += 10
            if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) score += 5
            score
        }
        
        if (bestNetworkCaps != null) {
            val hasInternet = bestNetworkCaps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isValidated = bestNetworkCaps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            val isMetered = !bestNetworkCaps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            val connectionType = getConnectionType(bestNetworkCaps)
            
            logDebug("updateConnectivityStateFromActiveNetworks: type=$connectionType, hasInternet=$hasInternet, isValidated=$isValidated, isMetered=$isMetered")
            
            val newState = when {
                hasInternet && isValidated -> ConnectivityState.Online(connectionType, isMetered)
                hasInternet && !isValidated -> ConnectivityState.Limited(connectionType, isMetered)
                else -> ConnectivityState.Offline()
            }
            updateStateDebounced(newState)
        }
    }
    
    private fun updateStateDebounced(newState: ConnectivityState) {
        stateUpdateJob?.cancel()
        stateUpdateJob = coroutineScope.launch {
            delay(config.stateUpdateDebounceMs)
            stateMutex.withLock {
                if (_connectivityState.value != newState) {
                    if (config.enableMetrics) {
                        updateStateWithMetrics(newState)
                    } else {
                        _connectivityState.value = newState
                    }
                    logDebug("State updated to: $newState")
                }
            }
        }
    }
    
    private fun updateStateWithMetrics(newState: ConnectivityState) {
        val now = System.currentTimeMillis()
        val lastChange = lastStateChangeTime.get()
        
        if (lastChange > 0) {
            val duration = now - lastChange
            when (_connectivityState.value) {
                is ConnectivityState.Online -> onlineTime.addAndGet(duration)
                is ConnectivityState.Offline -> offlineTime.addAndGet(duration)
                is ConnectivityState.Limited -> limitedTime.addAndGet(duration)
            }
        }
        
        stateChangeCount.incrementAndGet()
        lastStateChangeTime.set(now)
        _connectivityState.value = newState
        publishMetrics()
    }
    
    private fun publishMetrics() {
        val total = stateChangeCount.get()
        val onTime = onlineTime.get()
        val offTime = offlineTime.get()
        val limTime = limitedTime.get()
        
        // Simple average calculation logic
        val avgOn = if (total > 0) onTime / total else 0
        val avgOff = if (total > 0) offTime / total else 0
        
        _metrics.value = ConnectivityMetrics(
            totalStateChanges = total,
            onlineTime = onTime,
            offlineTime = offTime,
            limitedTime = limTime,
            averageOnlineDuration = avgOn,
            averageOfflineDuration = avgOff,
            lastStateChange = lastStateChangeTime.get()
        )
    }

    private fun getConnectionType(capabilities: NetworkCapabilities): ConnectionType {
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectionType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectionType.CELLULAR
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ConnectionType.ETHERNET
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> ConnectionType.VPN
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> ConnectionType.BLUETOOTH
            else -> ConnectionType.UNKNOWN
        }
    }
    
    private fun getBandwidthInfo(capabilities: NetworkCapabilities): Pair<Int, Int> {
        val downKbps = capabilities.linkDownstreamBandwidthKbps
        val upKbps = capabilities.linkUpstreamBandwidthKbps
        return Pair(downKbps, upKbps)
    }
    
    private fun emitError(error: ConnectivityError) {
        _errors.tryEmit(error)
        errorCallback?.invoke(error)
    }

    private fun logDebug(message: String) {
        if (config.enableLogging) {
            SecureLogger.d(TAG, message)
        }
    }
    
    private fun logError(message: String, throwable: Throwable? = null) {
        SecureLogger.e(TAG, message, throwable)
    }
    
    private fun logWarning(message: String) {
        SecureLogger.w(TAG, message)
    }
    
    companion object {
        private const val TAG = "ConnectivityMonitor"
    }
}
