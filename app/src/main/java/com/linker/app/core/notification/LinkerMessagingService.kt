package com.linker.app.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint

/**
 * Firebase Cloud Messaging Service
 * 
 * Handles push notifications from Firebase
 */
@AndroidEntryPoint
class LinkerMessagingService : FirebaseMessagingService() {
    
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // TODO: Send token to backend
    }
    
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        // TODO: Handle incoming messages
        // - Chat messages
        // - Likes/Comments
        // - Follow notifications
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Linker Notifications",
                NotificationManager.IMPORTANCE_HIGH
            )
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    companion object {
        private const val CHANNEL_ID = "linker_notifications"
    }
}
