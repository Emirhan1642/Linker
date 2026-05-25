package com.linker.app.core.notification

data class NotificationMessage(
    val senderName: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isFromCurrentUser: Boolean = false
) {
    companion object {
        fun parse(message: String, isGroupChat: Boolean, defaultSender: String): NotificationMessage {
            return when {
                message.startsWith("Siz: ") -> NotificationMessage(
                    senderName = "Siz",
                    text = message.substring(5),
                    isFromCurrentUser = true
                )
                isGroupChat && message.contains(": ") -> {
                    val colonIndex = message.indexOf(": ")
                    NotificationMessage(
                        senderName = message.substring(0, colonIndex),
                        text = message.substring(colonIndex + 2),
                        isFromCurrentUser = false
                    )
                }
                else -> NotificationMessage(
                    senderName = defaultSender,
                    text = message,
                    isFromCurrentUser = false
                )
            }
        }
    }
}
