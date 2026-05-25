package com.linker.app.core.notification

object NotificationConstants {
    const val KEY_TEXT_REPLY = "key_text_reply"
    
    const val EXTRA_CHAT_ID = "extra_chat_id"
    const val EXTRA_MESSAGE_ID = "extra_message_id"
    const val EXTRA_SENDER_ID = "extra_sender_id"
    const val EXTRA_SENDER_NAME = "extra_sender_name"
    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    const val EXTRA_TARGET_ACCOUNT_UID = "extra_target_account_uid"

    const val ACTION_REPLY = "com.linker.app.notification.REPLY"
    const val ACTION_LIKE = "com.linker.app.notification.LIKE"
    const val ACTION_READ = "com.linker.app.notification.READ"

    private const val PENDING_INTENT_MULTIPLIER = 100
    const val REPLY_OFFSET = 1
    const val LIKE_OFFSET = 2
    const val READ_OFFSET = 3

    fun getReplyRequestCode(notificationId: Int) = notificationId * PENDING_INTENT_MULTIPLIER + REPLY_OFFSET
    fun getLikeRequestCode(notificationId: Int) = notificationId * PENDING_INTENT_MULTIPLIER + LIKE_OFFSET
    fun getReadRequestCode(notificationId: Int) = notificationId * PENDING_INTENT_MULTIPLIER + READ_OFFSET
}
