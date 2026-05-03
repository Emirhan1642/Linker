package com.linker.app.core.util

import com.linker.app.BuildConfig
import com.linker.app.core.config.OfflineMessagingConfig

/**
 * Secure logging utility that prevents sensitive data from being logged in production.
 * 
 * Addresses Issue #41 (P3): Disable sensitive data logging in production
 * 
 * Usage:
 * ```
 * SecureLogger.d("MyTag", "User ID: ${SecureLogger.mask(userId)}")
 * SecureLogger.logMessage("MyTag", "Sending message", messageContent)
 * ```
 * 
 * In production builds:
 * - Sensitive data is masked (e.g., "user_***")
 * - Message contents are never logged
 * - Only metadata is logged
 */
object SecureLogger {
    
    /**
     * Mask sensitive data for logging.
     * In production: returns masked version (e.g., "user_***")
     * In debug: returns full value
     */
    fun mask(value: String?): String {
        if (value == null) return "null"
        
        return if (BuildConfig.DEBUG && OfflineMessagingConfig.ENABLE_SENSITIVE_LOGGING) {
            value
        } else {
            // Show first 4 chars + "***" for debugging while protecting privacy
            if (value.length <= 4) {
                "***"
            } else {
                "${value.take(4)}***"
            }
        }
    }
    
    /**
     * Mask user ID for logging.
     * Shows format but protects actual ID.
     */
    fun maskUserId(userId: String?): String {
        return "userId=${mask(userId)}"
    }
    
    /**
     * Mask message ID for logging.
     * Shows format but protects actual ID.
     */
    fun maskMessageId(messageId: String?): String {
        return "msgId=${mask(messageId)}"
    }
    
    /**
     * Mask device address (MAC address) for logging.
     * Shows format but protects actual address.
     */
    fun maskDeviceAddress(address: String?): String {
        return "device=${mask(address)}"
    }
    
    /**
     * Log message metadata without content.
     * NEVER logs message content in production.
     * 
     * @param tag Log tag
     * @param action Action being performed (e.g., "Sending message")
     * @param messageContent Message content (will be masked in production)
     */
    fun logMessage(tag: String, action: String, messageContent: String?) {
        if (BuildConfig.DEBUG && OfflineMessagingConfig.ENABLE_SENSITIVE_LOGGING) {
            Logger.d(tag, "$action: content='$messageContent'")
        } else {
            // Only log metadata, never content
            val contentLength = messageContent?.length ?: 0
            Logger.d(tag, "$action: contentLength=$contentLength")
        }
    }
    
    /**
     * Log BLE packet metadata without payload.
     * NEVER logs encrypted payload in production.
     * 
     * @param tag Log tag
     * @param action Action being performed
     * @param messageId Message ID
     * @param senderId Sender user ID
     * @param recipientId Recipient user ID
     * @param payloadSize Payload size in bytes
     */
    fun logBlePacket(
        tag: String,
        action: String,
        messageId: String?,
        senderId: String?,
        recipientId: String?,
        payloadSize: Int
    ) {
        Logger.d(
            tag,
            "$action: ${maskMessageId(messageId)}, " +
                    "from=${mask(senderId)}, " +
                    "to=${mask(recipientId)}, " +
                    "size=$payloadSize bytes"
        )
    }
    
    /**
     * Log encryption operation without keys or plaintext.
     * NEVER logs keys or plaintext in production.
     * 
     * @param tag Log tag
     * @param action Action being performed (e.g., "Encrypting message")
     * @param recipientId Recipient user ID
     * @param plaintextSize Plaintext size in bytes
     * @param ciphertextSize Ciphertext size in bytes
     */
    fun logEncryption(
        tag: String,
        action: String,
        recipientId: String?,
        plaintextSize: Int,
        ciphertextSize: Int
    ) {
        Logger.d(
            tag,
            "$action: recipient=${mask(recipientId)}, " +
                    "plaintext=$plaintextSize bytes, " +
                    "ciphertext=$ciphertextSize bytes"
        )
    }
    
    /**
     * Log connection event without sensitive details.
     * 
     * @param tag Log tag
     * @param action Action being performed
     * @param deviceAddress Device MAC address
     * @param rssi Signal strength (safe to log)
     */
    fun logConnection(
        tag: String,
        action: String,
        deviceAddress: String?,
        rssi: Int? = null
    ) {
        val rssiStr = rssi?.let { ", rssi=$it dBm" } ?: ""
        Logger.d(tag, "$action: ${maskDeviceAddress(deviceAddress)}$rssiStr")
    }
    
    /**
     * Log sync operation without message content.
     * 
     * @param tag Log tag
     * @param action Action being performed
     * @param messageCount Number of messages
     * @param chatId Chat ID
     */
    fun logSync(
        tag: String,
        action: String,
        messageCount: Int,
        chatId: String?
    ) {
        Logger.d(
            tag,
            "$action: count=$messageCount, chat=${mask(chatId)}"
        )
    }
    
    /**
     * Standard debug log (delegates to Logger).
     * Use this for non-sensitive data.
     */
    fun d(tag: String, message: String) {
        Logger.d(tag, message)
    }
    
    /**
     * Standard error log (delegates to Logger).
     * Errors are always logged.
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Logger.e(tag, message, throwable)
    }
    
    /**
     * Standard warning log (delegates to Logger).
     * Warnings are always logged.
     */
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Logger.w(tag, message, throwable)
    }
}
