package com.linker.app.presentation.screens.settings

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.linker.app.data.ble.BLEMeshManager
import com.linker.app.data.bluetooth.BluetoothManager
import com.linker.app.data.permission.PermissionManager
import com.linker.app.data.queue.MessageQueueProcessor
import com.linker.app.data.service.OfflineMessagingServiceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Offline Messaging Settings Screen
 * 
 * Implements Requirements 10.6-10.8, 15.1-15.9:
 * - Permission state management
 * - Service control
 * - Settings management
 */
data class OfflineMessagingSettingsUiState(
    // Service status
    val isServiceRunning: Boolean = false,
    val connectedNodeCount: Int = 0,
    val pendingMessageCount: Int = 0,
    
    // Settings
    val isOfflineMessagingEnabled: Boolean = false,
    val isBleEnabled: Boolean = true,
    val isWifiDirectEnabled: Boolean = true,
    val showNotification: Boolean = true,
    val maxTtl: Int = 5,
    val batteryUsagePercent: Int = 0,
    
    // UI state
    val isTogglingService: Boolean = false,
    val showTtlPicker: Boolean = false,
    val showClearQueueDialog: Boolean = false,
    val snackbarMessage: String? = null,
    
    // Permission state
    val showPermissionRationale: Boolean = false,
    val showPermissionSettings: Boolean = false,
    val permissionType: String = "",
    
    // Bluetooth state
    val isBluetoothEnabled: Boolean = false,
    val showBluetoothDialog: Boolean = false
)

@HiltViewModel
class OfflineMessagingSettingsViewModel @Inject constructor(
    private val application: Application,
    private val serviceManager: OfflineMessagingServiceManager,
    private val bleMeshManager: BLEMeshManager,
    private val messageQueueProcessor: MessageQueueProcessor,
    private val permissionManager: PermissionManager,
    private val bluetoothManager: BluetoothManager
) : AndroidViewModel(application) {
    
    private val _uiState = MutableStateFlow(OfflineMessagingSettingsUiState())
    val uiState: StateFlow<OfflineMessagingSettingsUiState> = _uiState.asStateFlow()
    
    init {
        loadSettings()
        observeServiceStatus()
        observeMeshStatus()
        observeQueueStatus()
        checkBluetoothStatus()
        observeBluetoothStateChanges()
        bluetoothManager.startListening()
    }
    
    /**
     * Check Bluetooth status
     */
    private fun checkBluetoothStatus() {
        viewModelScope.launch {
            val isEnabled = bluetoothManager.isBluetoothEnabled()
            _uiState.update { it.copy(isBluetoothEnabled = isEnabled) }
        }
    }
    
    /**
     * Observe Bluetooth state changes
     */
    private fun observeBluetoothStateChanges() {
        viewModelScope.launch {
            bluetoothManager.observeBluetoothState().collect { isEnabled ->
                _uiState.update { it.copy(isBluetoothEnabled = isEnabled) }
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        bluetoothManager.stopListening()
    }
    
    /**
     * Load settings from preferences
     */
    private fun loadSettings() {
        viewModelScope.launch {
            // Load from SharedPreferences or DataStore
            // For now, using default values
            _uiState.update { it.copy(
                isOfflineMessagingEnabled = serviceManager.isServiceRunning(),
                maxTtl = 5 // TODO: Load from preferences
            )}
        }
    }
    
    /**
     * Observe service running status
     */
    private fun observeServiceStatus() {
        viewModelScope.launch {
            serviceManager.isServiceRunning.collect { isRunning ->
                _uiState.update { it.copy(
                    isServiceRunning = isRunning,
                    isOfflineMessagingEnabled = isRunning
                )}
            }
        }
    }
    
    /**
     * Observe mesh network status
     */
    private fun observeMeshStatus() {
        viewModelScope.launch {
            bleMeshManager.observeConnectedPeers().collect { peers ->
                _uiState.update { it.copy(
                    connectedNodeCount = peers.size
                )}
            }
        }
    }
    
    /**
     * Observe message queue status
     */
    private fun observeQueueStatus() {
        viewModelScope.launch {
            messageQueueProcessor.observePendingCount().collect { count ->
                _uiState.update { it.copy(
                    pendingMessageCount = count
                )}
            }
        }
    }
    
    /**
     * Toggle offline messaging on/off
     * @param enabled Whether to enable or disable
     * @param skipPermissionCheck If true, skip permission check (used after permissions are granted)
     */
    fun toggleOfflineMessaging(enabled: Boolean, skipPermissionCheck: Boolean = false) {
        android.util.Log.d("OfflineMessagingVM", "toggleOfflineMessaging called: enabled=$enabled, skipPermissionCheck=$skipPermissionCheck")
        viewModelScope.launch {
            // Check Bluetooth first
            if (enabled && !bluetoothManager.isBluetoothEnabled()) {
                android.util.Log.d("OfflineMessagingVM", "Bluetooth is disabled, showing dialog")
                _uiState.update { it.copy(
                    showBluetoothDialog = true
                )}
                return@launch
            }
            
            // Check permissions
            if (enabled && !skipPermissionCheck && !permissionManager.hasAllPermissions()) {
                android.util.Log.d("OfflineMessagingVM", "Permissions not granted, showing dialog")
                _uiState.update { it.copy(
                    showPermissionRationale = true,
                    permissionType = "bluetooth"
                )}
                return@launch
            }
            
            android.util.Log.d("OfflineMessagingVM", "All checks passed, toggling service")
            _uiState.update { it.copy(isTogglingService = true) }
            
            try {
                if (enabled) {
                    android.util.Log.d("OfflineMessagingVM", "Calling serviceManager.startService()")
                    val started = serviceManager.startService()
                    android.util.Log.d("OfflineMessagingVM", "serviceManager.startService() returned: $started")
                    if (started) {
                        _uiState.update { it.copy(
                            isOfflineMessagingEnabled = true,
                            snackbarMessage = "Offline messaging enabled"
                        )}
                    } else {
                        _uiState.update { it.copy(
                            snackbarMessage = "Failed to start service"
                        )}
                    }
                } else {
                    android.util.Log.d("OfflineMessagingVM", "Calling serviceManager.stopService()")
                    val stopped = serviceManager.stopService()
                    android.util.Log.d("OfflineMessagingVM", "serviceManager.stopService() returned: $stopped")
                    if (stopped) {
                        _uiState.update { it.copy(
                            isOfflineMessagingEnabled = false,
                            snackbarMessage = "Offline messaging disabled"
                        )}
                    } else {
                        _uiState.update { it.copy(
                            snackbarMessage = "Failed to stop service"
                        )}
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("OfflineMessagingVM", "Exception in toggleOfflineMessaging: ${e.message}", e)
                _uiState.update { it.copy(
                    snackbarMessage = "Error: ${e.message}"
                )}
            } finally {
                _uiState.update { it.copy(isTogglingService = false) }
            }
        }
    }
    
    /**
     * Toggle BLE mesh networking
     */
    fun toggleBle(enabled: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBleEnabled = enabled) }
            // TODO: Save to preferences
            
            if (enabled) {
                bleMeshManager.startScanning()
                bleMeshManager.startAdvertising()
            } else {
                bleMeshManager.stopScanning()
                bleMeshManager.stopAdvertising()
            }
        }
    }
    
    /**
     * Toggle Wi-Fi Direct
     */
    fun toggleWifiDirect(enabled: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isWifiDirectEnabled = enabled) }
            // TODO: Save to preferences
        }
    }
    
    /**
     * Toggle notification visibility
     */
    fun toggleNotification(enabled: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(showNotification = enabled) }
            // TODO: Save to preferences and update service notification
        }
    }
    
    /**
     * Show TTL picker dialog
     */
    fun showTtlPicker() {
        _uiState.update { it.copy(showTtlPicker = true) }
    }
    
    /**
     * Dismiss TTL picker dialog
     */
    fun dismissTtlPicker() {
        _uiState.update { it.copy(showTtlPicker = false) }
    }
    
    /**
     * Set maximum TTL
     */
    fun setMaxTtl(ttl: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(
                maxTtl = ttl,
                showTtlPicker = false,
                snackbarMessage = "Maximum hops set to $ttl"
            )}
            // TODO: Save to preferences
        }
    }
    
    /**
     * Show clear queue confirmation dialog
     */
    fun showClearQueueDialog() {
        _uiState.update { it.copy(showClearQueueDialog = true) }
    }
    
    /**
     * Dismiss clear queue dialog
     */
    fun dismissClearQueueDialog() {
        _uiState.update { it.copy(showClearQueueDialog = false) }
    }
    
    /**
     * Clear message queue
     */
    fun clearMessageQueue() {
        viewModelScope.launch {
            try {
                messageQueueProcessor.clearSentMessages()
                _uiState.update { it.copy(
                    showClearQueueDialog = false,
                    snackbarMessage = "Message queue cleared"
                )}
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    showClearQueueDialog = false,
                    snackbarMessage = "Error: ${e.message}"
                )}
            }
        }
    }
    
    /**
     * Check permissions
     */
    fun checkPermissions() {
        val hasAll = permissionManager.hasAllPermissions()
        if (!hasAll) {
            _uiState.update { it.copy(
                showPermissionRationale = true,
                permissionType = when {
                    !permissionManager.hasBluetoothPermissions() -> "bluetooth"
                    !permissionManager.hasLocationPermission() -> "location"
                    !permissionManager.hasNearbyPermission() -> "nearby"
                    else -> "bluetooth"
                }
            )}
        }
    }
    
    /**
     * Request permissions
     */
    fun requestPermissions() {
        // This should be called from the Activity/Fragment
        // ViewModel just updates UI state
        _uiState.update { it.copy(
            showPermissionRationale = false
        )}
    }
    
    /**
     * Handle permission result
     */
    fun onPermissionResult(granted: Boolean, permanentlyDenied: Boolean) {
        if (granted) {
            _uiState.update { it.copy(
                snackbarMessage = "Permission granted"
            )}
            // Try to enable offline messaging again, skipping permission check
            // since we just granted the permissions
            toggleOfflineMessaging(true, skipPermissionCheck = true)
        } else if (permanentlyDenied) {
            _uiState.update { it.copy(
                showPermissionSettings = true
            )}
        } else {
            _uiState.update { it.copy(
                snackbarMessage = "Permission denied"
            )}
        }
    }
    
    /**
     * Dismiss permission rationale dialog
     */
    fun dismissPermissionRationale() {
        _uiState.update { it.copy(showPermissionRationale = false) }
    }
    
    /**
     * Dismiss permission settings dialog
     */
    fun dismissPermissionSettings() {
        _uiState.update { it.copy(showPermissionSettings = false) }
    }
    
    /**
     * Open app settings
     */
    fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", application.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            application.startActivity(intent)
            _uiState.update { it.copy(showPermissionSettings = false) }
        } catch (e: Exception) {
            _uiState.update { it.copy(
                snackbarMessage = "Failed to open settings"
            )}
        }
    }
    
    /**
     * Dismiss snackbar message
     */
    fun dismissSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
    
    /**
     * Enable Bluetooth
     */
    fun enableBluetooth() {
        viewModelScope.launch {
            android.util.Log.d("OfflineMessagingVM", "enableBluetooth() called")
            
            // Check if BLUETOOTH_CONNECT permission is granted
            if (!bluetoothManager.hasBluetoothConnectPermission()) {
                android.util.Log.e("OfflineMessagingVM", "BLUETOOTH_CONNECT permission not granted")
                _uiState.update { it.copy(
                    showBluetoothDialog = false,
                    showPermissionRationale = true,
                    permissionType = "bluetooth",
                    snackbarMessage = "Bluetooth permission required"
                )}
                return@launch
            }
            
            android.util.Log.d("OfflineMessagingVM", "Permission granted, calling enableBluetooth()")
            
            // Send enable request
            val requestSent = bluetoothManager.enableBluetooth()
            android.util.Log.d("OfflineMessagingVM", "enableBluetooth() returned: $requestSent")
            
            if (requestSent) {
                _uiState.update { it.copy(
                    showBluetoothDialog = false,
                    snackbarMessage = "Enabling Bluetooth..."
                )}
                
                // Wait for Bluetooth to actually enable (up to 5 seconds)
                var isEnabled = false
                for (i in 0..10) {
                    kotlinx.coroutines.delay(500)
                    isEnabled = bluetoothManager.isBluetoothEnabled()
                    android.util.Log.d("OfflineMessagingVM", "Check $i: isBluetoothEnabled = $isEnabled")
                    if (isEnabled) break
                }
                
                if (isEnabled) {
                    android.util.Log.d("OfflineMessagingVM", "Bluetooth is now enabled")
                    _uiState.update { it.copy(
                        isBluetoothEnabled = true,
                        snackbarMessage = "Bluetooth enabled"
                    )}
                    // Try to enable offline messaging again
                    toggleOfflineMessaging(true, skipPermissionCheck = true)
                } else {
                    android.util.Log.e("OfflineMessagingVM", "Bluetooth is still disabled after 5 seconds")
                    _uiState.update { it.copy(
                        snackbarMessage = "Opening Bluetooth settings..."
                    )}
                    // Open Bluetooth settings for manual enable
                    bluetoothManager.openBluetoothSettings(application)
                }
            } else {
                android.util.Log.e("OfflineMessagingVM", "enableBluetooth() returned false")
                _uiState.update { it.copy(
                    snackbarMessage = "Failed to enable Bluetooth"
                )}
            }
        }
    }
    
    /**
     * Dismiss Bluetooth dialog
     */
    fun dismissBluetoothDialog() {
        _uiState.update { it.copy(showBluetoothDialog = false) }
    }
}
