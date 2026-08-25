package com.linker.app.core.util

object ChatUtils {
    /**
     * Generates a consistent, canonical private chat ID for any two user IDs.
     * Uses sorted order to guarantee both participants resolve to the exact same chatId.
     */
    fun getPrivateChatId(userId1: String, userId2: String): String {
        val sortedIds = listOf(userId1, userId2).sorted()
        return "private_${sortedIds[0]}_${sortedIds[1]}"
    }
}
