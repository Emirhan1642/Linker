package com.linker.app.data.ble

import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adaptive scanning strategy based on battery level and screen state.
 * 
 * Implements Requirements 9.1-9.7:
 * - SCAN_MODE_LOW_POWER (60s interval) when battery < 15%
 * - SCAN_MODE_BALANCED (30s interval) when screen off
 * - SCAN_MODE_LOW_LATENCY (continuous) when screen on
 * - Observes battery level and screen state changes
 */
@Singleton
class AdaptiveScanningStrategy @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    private val _scanSettings = MutableStateFlow(calculateOptimalScanSettings())
    val scanSettings: StateFlow<ScanSettings> = _scanSettings.asStateFlow()
    
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    
    private var isScreenOn = powerManager.isInteractive
    private var batteryLevel = getCurrentBatteryLevel()
    
    companion object {
        private const val LOW_BATTERY_THRESHOLD = 15 // 15%
    }
    
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_BATTERY_CHANGED -> {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    
                    if (level >= 0 && scale > 0) {
                        batteryLevel = (level * 100) / scale
                        updateScanSettings()
                    }
                }
            }
        }
    }
    
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    updateScanSettings()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    updateScanSettings()
                }
            }
        }
    }
    
    /**
     * Start monitoring battery and screen state.
     */
    fun startMonitoring() {
        // Register battery receiver
        val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(batteryReceiver, batteryFilter)
        
        // Register screen receiver
        val screenFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        context.registerReceiver(screenReceiver, screenFilter)
        
        // Initial update
        updateScanSettings()
    }
    
    /**
     * Stop monitoring battery and screen state.
     */
    fun stopMonitoring() {
        try {
            context.unregisterReceiver(batteryReceiver)
            context.unregisterReceiver(screenReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver not registered, ignore
        }
    }
    
    /**
     * Calculate optimal scan settings based on current conditions.
     */
    fun calculateOptimalScanSettings(): ScanSettings {
        return when {
            // Low battery: Use low power mode (60s interval)
            batteryLevel < LOW_BATTERY_THRESHOLD -> {
                ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                    .setReportDelay(0)
                    .build()
            }
            
            // Screen off: Use balanced mode (30s interval)
            !isScreenOn -> {
                ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                    .setReportDelay(0)
                    .build()
            }
            
            // Screen on: Use low latency mode (continuous)
            else -> {
                ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .setReportDelay(0)
                    .build()
            }
        }
    }
    
    /**
     * Update scan settings based on current conditions.
     */
    private fun updateScanSettings() {
        _scanSettings.value = calculateOptimalScanSettings()
    }
    
    /**
     * Get current battery level percentage.
     */
    private fun getCurrentBatteryLevel(): Int {
        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        
        return if (level >= 0 && scale > 0) {
            (level * 100) / scale
        } else {
            100 // Default to full battery if unable to read
        }
    }
    
    /**
     * Get current scan mode description for debugging.
     */
    fun getCurrentScanMode(): String {
        return when {
            batteryLevel < LOW_BATTERY_THRESHOLD -> "LOW_POWER (battery: $batteryLevel%)"
            !isScreenOn -> "BALANCED (screen off)"
            else -> "LOW_LATENCY (screen on)"
        }
    }
}
