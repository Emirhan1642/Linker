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
    @ApplicationContext private val context: Context,
    private val permissionPrefs: PermissionPreferences
) : PermissionManager {
    
    companion object {
        private const val TAG = "PermissionManager"
        
        // Permission constants for easier management
        private object Permissions {
            // Bluetooth (API 31+)
            const val BLUETOOTH_SCAN = Manifest.permission.BLUETOOTH_SCAN
            const val BLUETOOTH_CONNECT = Manifest.permission.BLUETOOTH_CONNECT
            const val BLUETOOTH_ADVERTISE = Manifest.permission.BLUETOOTH_ADVERTISE
            
            // Bluetooth (Legacy)
            const val BLUETOOTH = Manifest.permission.BLUETOOTH
            const val BLUETOOTH_ADMIN = Manifest.permission.BLUETOOTH_ADMIN
            
            // Location
            const val ACCESS_FINE_LOCATION = Manifest.permission.ACCESS_FINE_LOCATION
            
            // Nearby (API 33+)
            const val NEARBY_WIFI_DEVICES = Manifest.permission.NEARBY_WIFI_DEVICES
        }
    }

    /**
     * Check if BLUETOOTH_SCAN is used without location access
     * When true, ACCESS_FINE_LOCATION is not required on API 31+
     */
    private fun isBluetoothScanWithoutLocation(): Boolean {
        // Here you could check for neverForLocation flag or configuration.
        // For default, we assume it's false and requires location.
        return false 
    }
    
    override fun hasBluetoothPermissions(): Boolean {
        val permissions = getRequiredBluetoothPermissions()
        val granted = permissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
        
        if (!granted) {
            val missing = permissions.filter {
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }
            android.util.Log.d(TAG, "Missing Bluetooth permissions: ${missing.joinToString()}")
        }
        
        return granted
    }
    
    override fun hasLocationPermission(): Boolean {
        // On API 31+, if BLUETOOTH_SCAN uses neverForLocation flag,
        // location permission is not required
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isBluetoothScanWithoutLocation()) {
            android.util.Log.d(TAG, "Location permission not required (neverForLocation flag)")
            return true
        }

        val permission = getRequiredLocationPermission()
        val granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        
        if (!granted) {
            android.util.Log.d(TAG, "Location permission not granted: $permission")
        }
        
        return granted
    }
    
    override fun hasNearbyPermission(): Boolean {
        val permission = getRequiredNearbyPermission()
        if (permission == null) {
            android.util.Log.d(TAG, "Nearby permission not required on API ${Build.VERSION.SDK_INT}")
            return true
        }
        
        val granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        
        if (!granted) {
            android.util.Log.d(TAG, "Nearby permission not granted: $permission")
        }
        
        return granted
    }
    
    override fun hasAllPermissions(): Boolean {
        val allGranted = hasBluetoothPermissions() && hasLocationPermission() && hasNearbyPermission()
        
        android.util.Log.d(TAG, "All permissions granted: $allGranted")
        
        return allGranted
    }
    
    override fun getRequiredBluetoothPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // API 31+
            // New runtime permissions for Bluetooth
            // Note: BLUETOOTH and BLUETOOTH_ADMIN are deprecated on API 31+
            // but should still be declared in manifest with maxSdkVersion="30"
            arrayOf(
                Permissions.BLUETOOTH_SCAN,
                Permissions.BLUETOOTH_CONNECT,
                Permissions.BLUETOOTH_ADVERTISE
            )
        } else {
            // Legacy permissions for API 30 and below
            arrayOf(
                Permissions.BLUETOOTH,
                Permissions.BLUETOOTH_ADMIN
            )
        }
    }
    
    override fun getRequiredLocationPermission(): String {
        return Permissions.ACCESS_FINE_LOCATION
    }
    
    override fun getRequiredNearbyPermission(): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // API 33+
            Permissions.NEARBY_WIFI_DEVICES
        } else {
            null
        }
    }
    
    override fun getAllRequiredPermissions(): Array<String> {
        val permissions = mutableListOf<String>()
        
        // Add Bluetooth permissions
        permissions.addAll(getRequiredBluetoothPermissions())
        
        // Add Location permission (only if required)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !isBluetoothScanWithoutLocation()) {
            permissions.add(getRequiredLocationPermission())
        }
        
        // Add Nearby permission if required
        getRequiredNearbyPermission()?.let { permissions.add(it) }
        
        return permissions.toTypedArray()
    }

    override fun getPermissionStatus(): Map<String, Boolean> {
        val status = mutableMapOf<String, Boolean>()
        getAllRequiredPermissions().forEach { permission ->
            status[permission] = hasPermission(permission)
        }
        return status
    }

    override fun getMissingPermissions(): Array<String> {
        return getAllRequiredPermissions().filter { permission ->
            !hasPermission(permission)
        }.toTypedArray()
    }

    override fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    override fun getPermissionDisplayName(permission: String): String {
        return when (permission) {
            Permissions.BLUETOOTH_SCAN, Permissions.BLUETOOTH_CONNECT, 
            Permissions.BLUETOOTH_ADVERTISE, Permissions.BLUETOOTH, 
            Permissions.BLUETOOTH_ADMIN -> "Bluetooth"
            Permissions.ACCESS_FINE_LOCATION -> "Location"
            Permissions.NEARBY_WIFI_DEVICES -> "Nearby Devices"
            else -> permission.substringAfterLast('.')
        }
    }

    override fun getPermissionRationale(permission: String): String {
        return when (permission) {
            Permissions.BLUETOOTH_SCAN, Permissions.BLUETOOTH_CONNECT, 
            Permissions.BLUETOOTH_ADVERTISE, Permissions.BLUETOOTH, 
            Permissions.BLUETOOTH_ADMIN -> "Bluetooth is required to find and connect to other devices for offline messaging."
            Permissions.ACCESS_FINE_LOCATION -> "Location access is required for Bluetooth scanning to work properly."
            Permissions.NEARBY_WIFI_DEVICES -> "Nearby devices access is required to communicate via WiFi Direct."
            else -> "This permission is required for the app to function properly."
        }
    }

    // Validation helper
    private fun validatePermissionInput(activity: Activity, permission: String) {
        require(permission.isNotBlank()) {
            "Permission string cannot be blank"
        }
        
        require(!activity.isFinishing) {
            "Activity is finishing, cannot check permissions"
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            require(!activity.isDestroyed) {
                "Activity is destroyed, cannot check permissions"
            }
        }
    }
    
    override fun shouldShowRationale(activity: Activity, permission: String): Boolean {
        validatePermissionInput(activity, permission)
        
        return try {
            ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error checking rationale for $permission", e)
            false
        }
    }
    
    override fun isPermanentlyDenied(activity: Activity, permission: String): Boolean {
        validatePermissionInput(activity, permission)
        
        val isGranted = ContextCompat.checkSelfPermission(
            activity,
            permission
        ) == PackageManager.PERMISSION_GRANTED
        
        if (isGranted) {
            android.util.Log.d(TAG, "Permission already granted: $permission")
            return false
        }
        
        if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
            android.util.Log.d(TAG, "Should show rationale for: $permission")
            return false
        }
        
        // Use injected preferences (mockable and thread-safe)
        val hasAskedBefore = permissionPrefs.hasAskedBefore(permission)
        
        if (!hasAskedBefore) {
            android.util.Log.d(TAG, "First time asking for: $permission")
            permissionPrefs.markAsAsked(permission)
            return false
        }
        
        android.util.Log.w(TAG, "Permission permanently denied: $permission")
        return true
    }
}
