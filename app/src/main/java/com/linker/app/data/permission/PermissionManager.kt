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
     * Get detailed status of all required permissions
     * @return Map of permission to granted status
     */
    fun getPermissionStatus(): Map<String, Boolean>

    /**
     * Get list of missing (not granted) permissions
     * @return Array of permission strings that are not granted
     */
    fun getMissingPermissions(): Array<String>

    /**
     * Check if specific permission is granted
     * @param permission Permission to check
     * @return true if permission is granted
     */
    fun hasPermission(permission: String): Boolean

    /**
     * Get human-readable name for permission
     * Useful for showing permission names in UI
     * 
     * @param permission Permission string (e.g., Manifest.permission.BLUETOOTH_SCAN)
     * @return Human-readable name (e.g., "Bluetooth Scanning")
     */
    fun getPermissionDisplayName(permission: String): String

    /**
     * Get explanation text for why this permission is needed
     * Useful for rationale dialogs
     * 
     * @param permission Permission string
     * @return Explanation text for the permission
     */
    fun getPermissionRationale(permission: String): String

    /**
     * Check if permission rationale should be shown to the user.
     * 
     * Returns true when:
     * - Permission was previously denied by the user
     * - Permission is not permanently denied (user didn't select "Don't ask again")
     * 
     * Use this to show an explanation dialog before requesting the permission again.
     * 
     * @param activity Activity context (must not be null or finishing)
     * @param permission Permission to check (e.g., Manifest.permission.BLUETOOTH_SCAN)
     * @return true if rationale dialog should be shown before requesting permission
     * 
     * @throws IllegalArgumentException if permission string is invalid
     * @throws IllegalStateException if activity is finishing or destroyed
     * 
     * @see isPermanentlyDenied for checking if user selected "Don't ask again"
     */
    fun shouldShowRationale(activity: Activity, permission: String): Boolean
    
    /**
     * Check if permission was permanently denied by the user.
     * 
     * Returns true when:
     * - Permission is not granted
     * - User selected "Don't ask again" in the permission dialog
     * - Permission has been requested at least once before
     * 
     * When permanently denied, you should:
     * 1. Show a dialog explaining why the permission is needed
     * 2. Provide a button to open app settings
     * 3. Guide user to manually enable the permission
     * 
     * Example usage:
     * ```kotlin
     * if (permissionManager.isPermanentlyDenied(activity, permission)) {
     *     showSettingsDialog()
     * } else if (permissionManager.shouldShowRationale(activity, permission)) {
     *     showRationaleDialog()
     * } else {
     *     requestPermission()
     * }
     * ```
     * 
     * @param activity Activity context (must not be null or finishing)
     * @param permission Permission to check (e.g., Manifest.permission.ACCESS_FINE_LOCATION)
     * @return true if permission was permanently denied
     * 
     * @throws IllegalArgumentException if permission string is invalid
     * @throws IllegalStateException if activity is finishing or destroyed
     * 
     * @see shouldShowRationale for checking if rationale should be shown
     */
    fun isPermanentlyDenied(activity: Activity, permission: String): Boolean
}
