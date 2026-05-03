package com.linker.app.data.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit

/**
 * Implementation of PermissionManager with API level-specific permission handling
 * 
 * Handles different permission requirements for:
 * - Android 12+ (API 31+): BLUETOOTH_SCAN, BLUETOOTH_CONNECT, BLUETOOTH_ADVERTISE
 * - Android 13+ (API 33+): NEARBY_WIFI_DEVICES
 * - All versions: ACCESS_FINE_LOCATION (for BLE scanning)
 */
@Singleton
class PermissionManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : PermissionManager {
    
    override fun hasBluetoothPermissions(): Boolean {
        return getRequiredBluetoothPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    override fun hasLocationPermission(): Boolean {
        val permission = getRequiredLocationPermission()
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
    
    override fun hasNearbyPermission(): Boolean {
        val permission = getRequiredNearbyPermission() ?: return true // Not required on older APIs
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
    
    override fun hasAllPermissions(): Boolean {
        return hasBluetoothPermissions() && hasLocationPermission() && hasNearbyPermission()
    }
    
    override fun getRequiredBluetoothPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // API 31+
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }
    }
    
    override fun getRequiredLocationPermission(): String {
        return Manifest.permission.ACCESS_FINE_LOCATION
    }
    
    override fun getRequiredNearbyPermission(): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // API 33+
            Manifest.permission.NEARBY_WIFI_DEVICES
        } else {
            null
        }
    }
    
    override fun getAllRequiredPermissions(): Array<String> {
        val permissions = mutableListOf<String>()
        
        // Add Bluetooth permissions
        permissions.addAll(getRequiredBluetoothPermissions())
        
        // Add Location permission
        permissions.add(getRequiredLocationPermission())
        
        // Add Nearby permission if required
        getRequiredNearbyPermission()?.let { permissions.add(it) }
        
        return permissions.toTypedArray()
    }
    
    override fun shouldShowRationale(activity: Activity, permission: String): Boolean {
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }
    
    override fun isPermanentlyDenied(activity: Activity, permission: String): Boolean {
        // Permission is permanently denied if:
        // 1. Permission is not granted
        // 2. shouldShowRequestPermissionRationale returns false
        // 3. This is not the first time asking (we can't distinguish first time from permanent denial)
        
        val isGranted = ContextCompat.checkSelfPermission(
            activity,
            permission
        ) == PackageManager.PERMISSION_GRANTED
        
        if (isGranted) return false
        
        // If rationale should be shown, it's not permanently denied
        if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
            return false
        }
        
        // Check if we've asked before using SharedPreferences
        val prefs = activity.getSharedPreferences("permission_manager", Context.MODE_PRIVATE)
        val hasAskedBefore = prefs.getBoolean("asked_$permission", false)
        
        // If we haven't asked before, this is the first time, not permanent denial
        if (!hasAskedBefore) {
            // Mark that we've asked
            prefs.edit { putBoolean("asked_$permission", true) }
            return false
        }
        
        // We've asked before and rationale is not shown = permanently denied
        return true
    }
}
