package com.linker.app.core.notification

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReactionTracker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences(
        "reaction_notifications",
        Context.MODE_PRIVATE
    )
    
    private val lock = Any()
    
    fun addReactor(messageId: String, senderId: String): Int {
        synchronized(lock) {
            val key = "reactors_$messageId"
            val reactors = prefs.getStringSet(key, mutableSetOf())
                ?.toMutableSet() ?: mutableSetOf()
            
            val wasEmpty = reactors.isEmpty()
            reactors.add(senderId)
            prefs.edit().putStringSet(key, reactors).apply()
            
            // Store timestamp for cleanup
            val timestampKey = "timestamp_$messageId"
            prefs.edit().putLong(timestampKey, System.currentTimeMillis()).apply()
            
            NotificationLogger.d("ReactionTracker: Reactor count ${reactors.size} (was empty: $wasEmpty) for $messageId")
            return reactors.size
        }
    }
    
    fun clearReactors(messageId: String) {
        synchronized(lock) {
            prefs.edit()
                .remove("reactors_$messageId")
                .remove("timestamp_$messageId")
                .apply()
        }
    }
    
    fun cleanupOldReactions() {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val maxAge = 7 * 24 * 60 * 60 * 1000L // 7 days
            
            val allKeys = prefs.all.keys
            val editor = prefs.edit()
            
            allKeys.filter { it.startsWith("timestamp_") }.forEach { timestampKey ->
                val timestamp = prefs.getLong(timestampKey, 0)
                if (now - timestamp > maxAge) {
                    val messageId = timestampKey.removePrefix("timestamp_")
                    editor.remove("reactors_$messageId")
                    editor.remove(timestampKey)
                }
            }
            
            editor.apply()
        }
    }
}
