package com.linker.app.data.permission

import android.app.Activity

/**
 * Interface for managing runtime permissions for offline messaging features
 * 
 * Handles BLE, Location, and Nearby Connections permissions with API level checks.
 */
interface PermissionManager {
    
    /**
     * Check if all required BLE permissions are granted
     * @return true if all BLE permissions are granted
     */
    fun hasBluetoothPermissions(): Boolean
    
    /**
     * Check if location permission is granted (required for BLE scanning)
     * @return true if location permission is granted
     */
    fun hasLocationPermission(): Boolean
    
    /**
     * Check if Nearby Connections permission is granted (Android 13+)
     * @return true if permission is granted or not required
     */
    fun hasNearbyPermission(): Boolean
    
    /**
     * Check if all required permissions for offline messaging are granted
     * @return true if all permissions are granted
     */
    fun hasAllPermissions(): Boolean
    
    /**
     * Get list of required BLE permissions based on API level
     * @return Array of permission strings
     */
    fun getRequiredBluetoothPermissions(): Array<String>
    
    /**
     * Get required location permission
     * @return Location permission string
     */
    fun getRequiredLocationPermission(): String
    
    /**
     * Get required Nearby Connections permission (Android 13+)
     * @return Nearby permission string or null if not required
     */
    fun getRequiredNearbyPermission(): String?
    
    /**
     * Get all required permissions for offline messaging
     * @return Array of all required permission strings
     */
    fun getAllRequiredPermissions(): Array<String>
    
    /**
     * Check if permission rationale should be shown
     * @param activity Activity context
     * @param permission Permission to check
     * @return true if rationale should be shown
     */
    fun shouldShowRationale(activity: Activity, permission: String): Boolean
    
    /**
     * Check if permission was permanently denied
     * @param activity Activity context
     * @param permission Permission to check
     * @return true if permission was permanently denied
     */
    fun isPermanentlyDenied(activity: Activity, permission: String): Boolean
}
