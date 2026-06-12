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
import com.linker.app.data.preferences.OfflineMessagingPreferencesRepository
import com.linker.app.data.queue.MessageQueueProcessor
import com.linker.app.data.service.OfflineMessagingServiceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import com.linker.app.R
import com.linker.app.core.util.UiText

/**
 * ViewModel for Offline Messaging Settings Screen
 * 
 * Implements Requirements 10.6-10.8, 15.1-15.9:
 * - Permission state management
 * - Service control
 * - Settings management with DataStore persistence
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
    val snackbarMessage: UiText? = null,
    
    // Permission state
    val showPermissionRationale: Boolean = false,
    val showPermissionSettings: Boolean = false,
    val permissionType: String = "",
    
    // Bluetooth state
    val isBluetoothEnabled: Boolean = false,
    val showBluetoothDialog: Boolean = false
)

/**
 * Preferences keys for offline messaging settings
 * TODO: Implement DataStore integration for persistent settings storage
 * 
 * Recommended approach:
 * 1. Create OfflineMessagingPreferences data class with @Serializable
 * 2. Use DataStore<OfflineMessagingPreferences> for type-safe storage
 * 3. Store settings: maxTtl, isBleEnabled, isWifiDirectEnabled, showNotification
 * 4. Load settings in init{} and observe changes
 * 5. Save settings when user changes them
 * 
 * Example:
 * ```kotlin
 * @Serializable
 * data class OfflineMessagingPreferences(
 *     val maxTtl: Int = 5,
 *     val isBleEnabled: Boolean = true,
 *     val isWifiDirectEnabled: Boolean = true,
 *     val showNotification: Boolean = true
 * )
 * 
 * private val Context.offlineMessagingDataStore by dataStore(
 *     fileName = "offline_messaging_settings.pb",
 *     serializer = OfflineMessagingPreferencesSerializer
 * )
 * ```
 */

@HiltViewModel
class OfflineMessagingSettingsViewModel @Inject constructor(
    private val application: Application,
    private val serviceManager: OfflineMessagingServiceManager,
    private val bleMeshManager: BLEMeshManager,
    private val messageQueueProcessor: MessageQueueProcessor,
    private val permissionManager: PermissionManager,
    private val bluetoothManager: BluetoothManager,
    private val preferencesRepository: OfflineMessagingPreferencesRepository
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
     * Load settings from DataStore preferences
     */
    private fun loadSettings() {
        viewModelScope.launch {
            // Load from DataStore
            preferencesRepository.observePreferences().collect { prefs ->
                _uiState.update { it.copy(
                    maxTtl = prefs.maxTtl,
                    isBleEnabled = prefs.isBleEnabled,
                    isWifiDirectEnabled = prefs.isWifiDirectEnabled,
                    showNotification = prefs.showNotification
                )}
            }
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
        Timber.d("toggleOfflineMessaging called: enabled=$enabled, skipPermissionCheck=$skipPermissionCheck")
        viewModelScope.launch {
            // Check Bluetooth first
            if (enabled && !bluetoothManager.isBluetoothEnabled()) {
                Timber.d("Bluetooth is disabled, showing dialog")
                _uiState.update { it.copy(
                    showBluetoothDialog = true
                )}
                return@launch
            }
            
            // Check permissions
            if (enabled && !skipPermissionCheck && !permissionManager.hasAllPermissions()) {
                Timber.d("Permissions not granted, showing dialog")
                _uiState.update { it.copy(
                    showPermissionRationale = true,
                    permissionType = "bluetooth"
                )}
                return@launch
            }
            
            Timber.d("All checks passed, toggling service")
            _uiState.update { it.copy(isTogglingService = true) }
            
            try {
                if (enabled) {
                    Timber.d("Calling serviceManager.startService()")
                    val started = serviceManager.startService()
                    Timber.d("serviceManager.startService() returned: $started")
                    if (started) {
                        _uiState.update { it.copy(
                            isOfflineMessagingEnabled = true,
                            snackbarMessage = UiText.StringResource(R.string.settings_offline_messaging_enabled)
                        )}
                    } else {
                        _uiState.update { it.copy(
                            snackbarMessage = UiText.StringResource(R.string.error_generic)
                        )}
                    }
                } else {
                    Timber.d("Calling serviceManager.stopService()")
                    val stopped = serviceManager.stopService()
                    Timber.d("serviceManager.stopService() returned: $stopped")
                    if (stopped) {
                        _uiState.update { it.copy(
                            isOfflineMessagingEnabled = false,
                            snackbarMessage = UiText.StringResource(R.string.settings_offline_messaging_disabled)
                        )}
                    } else {
                        _uiState.update { it.copy(
                            snackbarMessage = UiText.StringResource(R.string.error_generic)
                        )}
                    }
                }
            } catch (e: SecurityException) {
                Timber.e(e, "SecurityException in toggleOfflineMessaging: ${e.message}")
                _uiState.update { it.copy(
                    snackbarMessage = UiText.DynamicString("Permission denied: ${e.message}")
                )}
            } catch (e: Exception) {
                Timber.e(e, "Exception in toggleOfflineMessaging: ${e.message}")
                _uiState.update { it.copy(
                    snackbarMessage = UiText.DynamicString("Error: ${e.message}")
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
            // Save to DataStore
            preferencesRepository.setBleEnabled(enabled).onFailure {
                _uiState.update { state -> state.copy(snackbarMessage = UiText.StringResource(R.string.error_generic)) }
            }
            
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
            // Save to DataStore
            preferencesRepository.setWifiDirectEnabled(enabled).onFailure {
                _uiState.update { state -> state.copy(snackbarMessage = UiText.StringResource(R.string.error_generic)) }
            }
        }
    }
    
    /**
     * Toggle notification visibility
     */
    fun toggleNotification(enabled: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(showNotification = enabled) }
            // Save to DataStore and update service notification
            preferencesRepository.setShowNotification(enabled).onFailure {
                _uiState.update { state -> state.copy(snackbarMessage = UiText.StringResource(R.string.error_generic)) }
            }
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
                snackbarMessage = UiText.DynamicString("Maximum hops set to $ttl")
            )}
            // Save to DataStore
            preferencesRepository.setMaxTtl(ttl).onFailure {
                _uiState.update { state -> state.copy(snackbarMessage = UiText.StringResource(R.string.error_generic)) }
            }
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
                    snackbarMessage = UiText.DynamicString("Message queue cleared")
                )}
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    showClearQueueDialog = false,
                    snackbarMessage = UiText.DynamicString("Error: ${e.message}")
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
                snackbarMessage = UiText.DynamicString("Permission granted")
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
                snackbarMessage = UiText.DynamicString("Permission denied")
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
                snackbarMessage = UiText.DynamicString("Failed to open settings")
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
            if (!bluetoothManager.hasBluetoothConnectPermission()) {
                _uiState.update { it.copy(
                    showBluetoothDialog = false,
                    showPermissionRationale = true,
                    permissionType = "bluetooth",
                    snackbarMessage = UiText.DynamicString("Bluetooth permission required")
                )}
                return@launch
            }

            _uiState.update { it.copy(
                showBluetoothDialog = false,
                snackbarMessage = UiText.DynamicString("Enabling Bluetooth...")
            )}

            val result = bluetoothManager.enableBluetoothWithTimeout()
            result.onSuccess {
                _uiState.update { state ->
                    state.copy(
                        isBluetoothEnabled = true,
                        snackbarMessage = UiText.DynamicString("Bluetooth enabled")
                    )
                }
                toggleOfflineMessaging(true, skipPermissionCheck = true)
            }.onFailure { error ->
                when (error) {
                    is TimeoutException -> {
                        _uiState.update { it.copy(snackbarMessage = UiText.DynamicString("Opening Bluetooth settings...")) }
                        bluetoothManager.openBluetoothSettings(application)
                    }
                    else -> {
                        _uiState.update { it.copy(snackbarMessage = UiText.DynamicString("Failed to enable Bluetooth")) }
                    }
                }
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
