package com.linker.app.core.notification

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationStateRecovery @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun recoverMessagesFromNotification(
        notificationId: Int
    ): List<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return emptyList()
        }
        
        return try {
            val notificationManager = context.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager
            
            val activeNotifications = notificationManager.activeNotifications
            val existingNotification = activeNotifications
                .firstOrNull { it.id == notificationId }
                ?: return emptyList()
            
            extractMessagesFromNotification(existingNotification.notification)
        } catch (e: Exception) {
            NotificationLogger.e("Failed to recover messages", e)
            emptyList()
        }
    }
    
    private fun extractMessagesFromNotification(
        notification: Notification
    ): List<String> {
        val messagingStyle = NotificationCompat.MessagingStyle
            .extractMessagingStyleFromNotification(notification)
            ?: return emptyList()
        
        return messagingStyle.messages.mapNotNull { message ->
            val senderName = message.person?.name?.toString() ?: ""
            val text = message.text?.toString() ?: ""
            
            when {
                text.isBlank() -> null
                senderName.equals("Siz", ignoreCase = true) || senderName.equals("You", ignoreCase = true) -> "$senderName: $text"
                senderName.isNotBlank() -> "$senderName: $text"
                else -> text
            }
        }
    }
}
