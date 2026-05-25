package com.linker.app.data.permission

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Testable interface for SharedPreferences used by PermissionManager
 */
interface PermissionPreferences {
    fun hasAskedBefore(permission: String): Boolean
    fun markAsAsked(permission: String)
    fun clear()
}

/**
 * Production implementation for PermissionPreferences
 */
@Singleton
class PermissionPreferencesImpl @Inject constructor(
    @ApplicationContext context: Context
) : PermissionPreferences {
    private val prefs = context.getSharedPreferences("permission_manager", Context.MODE_PRIVATE)
    private val lock = Any()
    
    override fun hasAskedBefore(permission: String): Boolean {
        return synchronized(lock) {
            prefs.getBoolean("asked_$permission", false)
        }
    }
    
    override fun markAsAsked(permission: String) {
        synchronized(lock) {
            prefs.edit { putBoolean("asked_$permission", true) }
        }
    }
    
    override fun clear() {
        synchronized(lock) {
            prefs.edit { clear() }
        }
    }
}
