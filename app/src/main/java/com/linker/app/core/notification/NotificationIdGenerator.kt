package com.linker.app.core.notification

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

object NotificationIdGenerator {
    private const val MAX_TRACKED_KEYS = 500
    private val keyToIdMap = ConcurrentHashMap<String, Int>()
    
    fun generateChatNotificationId(
        recipientId: String,
        chatId: String,
        senderId: String,
        isGroup: Boolean
    ): Int {
        val branch = if (isGroup) "group|$chatId" else "user|$senderId"
        val key = "$recipientId|$branch"
        
        return keyToIdMap.getOrPut(key) {
            if (keyToIdMap.size >= MAX_TRACKED_KEYS) {
                // Evict oldest entries if map grows excessively
                keyToIdMap.clear()
            }
            val hash = key.hashCode()
            if (hash == Int.MIN_VALUE) 1 else abs(hash) and 0x7FFF_FFFE
        }
    }
    
    fun releaseId(key: String) {
        keyToIdMap.remove(key)
    }
}
