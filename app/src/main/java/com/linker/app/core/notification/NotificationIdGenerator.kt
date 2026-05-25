package com.linker.app.core.notification

import java.util.concurrent.ConcurrentHashMap

object NotificationIdGenerator {
    private val usedIds = ConcurrentHashMap.newKeySet<Int>()
    private const val MAX_ATTEMPTS = 10
    
    fun generateChatNotificationId(
        recipientId: String,
        chatId: String,
        senderId: String,
        isGroup: Boolean
    ): Int {
        val branch = if (isGroup) "g|$chatId" else "u|$senderId"
        val key = "$recipientId|$branch"
        
        var attempt = 0
        var id = key.hashCode() and 0x7FFF_FFFE // Keep positive, avoid -1
        
        while (usedIds.contains(id) && attempt < MAX_ATTEMPTS) {
            // Add attempt number to avoid collision
            id = "$key|$attempt".hashCode() and 0x7FFF_FFFE
            attempt++
        }
        
        if (attempt >= MAX_ATTEMPTS) {
            NotificationLogger.w("Failed to generate unique notification ID after $MAX_ATTEMPTS attempts")
        }
        
        usedIds.add(id)
        return id
    }
    
    fun releaseId(id: Int) {
        usedIds.remove(id)
    }
}
