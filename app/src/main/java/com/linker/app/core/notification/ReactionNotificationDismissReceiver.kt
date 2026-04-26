package com.linker.app.core.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Clears reaction notification state when notification is dismissed
 */
class ReactionNotificationDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val messageId = intent.getStringExtra("messageId") ?: return
        
        Log.d(TAG, "Clearing reactors for message $messageId")
        
        val prefs = context.getSharedPreferences("reaction_notifications", Context.MODE_PRIVATE)
        val reactorsKey = "reactors_$messageId"
        prefs.edit().remove(reactorsKey).apply()
    }
    
    companion object {
        private const val TAG = "ReactionDismiss"
    }
}
