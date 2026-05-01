package com.linker.app.data.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for Bluetooth state and operations
 * 
 * Handles:
 * - Checking if Bluetooth is enabled
 * - Enabling/disabling Bluetooth
 * - Observing Bluetooth state changes
 */
interface BluetoothManager {
    /**
     * Check if Bluetooth is enabled
     */
    fun isBluetoothEnabled(): Boolean
    
    /**
     * Check if BLUETOOTH_CONNECT permission is granted
     */
    fun hasBluetoothConnectPermission(): Boolean
    
    /**
     * Enable Bluetooth
     * @return true if enable request was sent (actual enable is asynchronous)
     */
    fun enableBluetooth(): Boolean
    
    /**
     * Disable Bluetooth
     * @return true if Bluetooth was disabled
     */
    fun disableBluetooth(): Boolean
    
    /**
     * Get Bluetooth adapter
     */
    fun getBluetoothAdapter(): BluetoothAdapter?
    
    /**
     * Observe Bluetooth state changes
     */
    fun observeBluetoothState(): StateFlow<Boolean>
    
    /**
     * Start listening to Bluetooth state changes
     */
    fun startListening()
    
    /**
     * Stop listening to Bluetooth state changes
     */
    fun stopListening()
    
    /**
     * Open Bluetooth settings
     */
    fun openBluetoothSettings(context: Context)
}

@Singleton
class BluetoothManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : BluetoothManager {
    
    private val _bluetoothAdapter: BluetoothAdapter? by lazy {
        BluetoothAdapter.getDefaultAdapter()
    }
    
    private val _bluetoothState = MutableStateFlow(false)
    private var isListening = false
    
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                val isEnabled = state == BluetoothAdapter.STATE_ON
                _bluetoothState.value = isEnabled
            }
        }
    }
    
    init {
        // Initialize with current state
        _bluetoothState.value = _bluetoothAdapter?.isEnabled ?: false
    }
    
    override fun isBluetoothEnabled(): Boolean {
        return _bluetoothAdapter?.isEnabled ?: false
    }
    
    override fun hasBluetoothConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // API 31+
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Permission not required on older APIs
        }
    }
    
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun enableBluetooth(): Boolean {
        return try {
            // Check if permission is granted
            if (!hasBluetoothConnectPermission()) {
                android.util.Log.e("BluetoothManager", "BLUETOOTH_CONNECT permission not granted")
                return false
            }
            
            if (_bluetoothAdapter == null) {
                android.util.Log.e("BluetoothManager", "Bluetooth adapter is null")
                return false
            }
            
            if (_bluetoothAdapter!!.isEnabled) {
                android.util.Log.d("BluetoothManager", "Bluetooth is already enabled")
                return true
            }
            
            android.util.Log.d("BluetoothManager", "Attempting to enable Bluetooth")
            // Note: enable() may return false but still sends the enable intent
            // The actual enable happens asynchronously via BroadcastReceiver
            _bluetoothAdapter!!.enable()
            android.util.Log.d("BluetoothManager", "Enable intent sent, returning true")
            // Return true because the intent was sent, even if enable() returned false
            true
        } catch (e: Exception) {
            android.util.Log.e("BluetoothManager", "Exception in enableBluetooth: ${e.message}", e)
            e.printStackTrace()
            false
        }
    }
    
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun disableBluetooth(): Boolean {
        return try {
            // Check if permission is granted
            if (!hasBluetoothConnectPermission()) {
                return false
            }
            
            _bluetoothAdapter?.disable() ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    override fun getBluetoothAdapter(): BluetoothAdapter? {
        return _bluetoothAdapter
    }
    
    override fun observeBluetoothState(): StateFlow<Boolean> {
        return _bluetoothState.asStateFlow()
    }
    
    override fun startListening() {
        if (!isListening) {
            val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(bluetoothReceiver, filter, Context.RECEIVER_EXPORTED)
                } else {
                    context.registerReceiver(bluetoothReceiver, filter)
                }
                isListening = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    override fun stopListening() {
        if (isListening) {
            try {
                context.unregisterReceiver(bluetoothReceiver)
                isListening = false
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    override fun openBluetoothSettings(context: Context) {
        try {
            val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("BluetoothManager", "Failed to open Bluetooth settings: ${e.message}")
            e.printStackTrace()
        }
    }
}
