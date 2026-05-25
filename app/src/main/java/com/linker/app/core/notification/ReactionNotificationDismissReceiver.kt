package com.linker.app.core.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Clears reaction notification state when notification is dismissed
 */
@AndroidEntryPoint
class ReactionNotificationDismissReceiver : BroadcastReceiver() {
    
    @Inject lateinit var reactionTracker: ReactionTracker
    
    override fun onReceive(context: Context, intent: Intent) {
        val messageId = intent.getStringExtra("messageId") ?: return
        
        NotificationLogger.d("ReactionDismiss: Clearing reactors for message \$messageId")
        reactionTracker.clearReactors(messageId)
    }
}
