package com.linker.app.core.util

import com.linker.app.BuildConfig
import com.linker.app.core.config.OfflineMessagingConfig
// FirebaseCrashlytics import removed — dependency not available
import java.security.MessageDigest
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap

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
 * ✅ ENHANCED: Added multiple masking strategies, audit trail, rate limiting
 */

/**
 * ✅ Multiple masking strategies
 */
enum class MaskingStrategy {
    HASH,        // Show hash of value
    PARTIAL,     // Show first/last chars
    LENGTH,      // Show only length
    FULL         // Show "***" only
}

/**
 * Wrapper to allow instance-like usage: val logger = SecureLogger("TAG")
 */
class SecureLoggerWrapper(private val tag: String) {
    fun d(message: String) = SecureLogger.d(tag, message)
    fun w(message: String, throwable: Throwable? = null) = SecureLogger.w(tag, message, throwable)
    fun e(message: String, throwable: Throwable? = null) = SecureLogger.e(tag, message, throwable)
}

object SecureLogger {
    
    operator fun invoke(tag: String): SecureLoggerWrapper {
        return SecureLoggerWrapper(tag)
    }

    /**
     * ✅ Configurable masking strategy
     */
    var maskingStrategy: MaskingStrategy = MaskingStrategy.HASH
    
    // ✅ Audit trail configuration
    private var auditFile: File? = null
    private val auditDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private var enableAuditTrail: Boolean = !BuildConfig.DEBUG
    
    // ✅ Rate limiting
    private val logRateLimiter = ConcurrentHashMap<String, RateLimitInfo>()
    private const val RATE_LIMIT_WINDOW_MS = 60_000L // 1 minute
    private const val MAX_LOGS_PER_WINDOW = 100
    
    private data class RateLimitInfo(
        var count: Int = 0,
        var windowStart: Long = System.currentTimeMillis(),
        var lastLogged: Long = 0
    )
    
    /**
     * ✅ Initialize audit trail
     */
    fun initializeAuditTrail(context: android.content.Context) {
        if (enableAuditTrail) {
            try {
                val auditDir = File(context.filesDir, "audit")
                if (!auditDir.exists()) {
                    auditDir.mkdirs()
                }
                
                val timestamp = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                auditFile = File(auditDir, "audit_$timestamp.log")
                
                Logger.d("SecureLogger", "Audit trail initialized: ${auditFile?.absolutePath}")
            } catch (e: Exception) {
                Logger.e("SecureLogger", "Failed to initialize audit trail", e)
            }
        }
    }
    
    /**
     * ✅ Hash value for consistent masking
     */
    private fun hashValue(value: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(value.toByteArray())
            // Take first 8 chars of hex for readability
            hash.joinToString("") { "%02x".format(it) }.take(8)
        } catch (e: Exception) {
            "error"
        }
    }
    
    /**
     * ✅ Check if logging should be rate limited
     */
    private fun shouldLog(tag: String, operation: String): Boolean {
        val key = "$tag:$operation"
        val now = System.currentTimeMillis()
        
        val rateLimitInfo = logRateLimiter.getOrPut(key) { RateLimitInfo() }
        
        // Reset window if expired
        if (now - rateLimitInfo.windowStart > RATE_LIMIT_WINDOW_MS) {
            rateLimitInfo.count = 0
            rateLimitInfo.windowStart = now
        }
        
        rateLimitInfo.count++
        
        // Check rate limit
        if (rateLimitInfo.count > MAX_LOGS_PER_WINDOW) {
            // Log rate limit exceeded only once per window
            if (now - rateLimitInfo.lastLogged > RATE_LIMIT_WINDOW_MS) {
                Logger.w("SecureLogger", "Rate limit exceeded for $tag:$operation (${rateLimitInfo.count} logs)")
                rateLimitInfo.lastLogged = now
            }
            return false
        }
        
        rateLimitInfo.lastLogged = now
        return true
    }
    
    /**
     * ✅ Write to audit trail
     */
    private fun writeAudit(
        operation: String,
        tag: String,
        details: Map<String, String>
    ) {
        if (!enableAuditTrail || auditFile == null) return
        
        try {
            val timestamp = auditDateFormat.format(Date())
            val auditEntry = buildString {
                append("$timestamp | $operation | $tag")
                details.forEach { (key, value) ->
                    append(" | $key=$value")
                }
                append("\n")
            }
            
            FileWriter(auditFile, true).use { writer ->
                writer.append(auditEntry)
            }
        } catch (e: Exception) {
            // Silently fail to avoid infinite loop
        }
    }
    
    /**
     * ✅ Improved masking with multiple strategies
     */
    fun mask(value: String?, strategy: MaskingStrategy = maskingStrategy): String {
        if (value == null) return "null"
        
        return if (BuildConfig.DEBUG && OfflineMessagingConfig.ENABLE_SENSITIVE_LOGGING) {
            value
        } else {
            when (strategy) {
                MaskingStrategy.HASH -> {
                    // Show hash for debugging while protecting privacy
                    val hash = hashValue(value)
                    "hash_$hash"
                }
                MaskingStrategy.PARTIAL -> {
                    // Show first 2 and last 2 chars
                    when {
                        value.length <= 4 -> "***"
                        value.length <= 8 -> "${value.take(2)}***"
                        else -> "${value.take(2)}***${value.takeLast(2)}"
                    }
                }
                MaskingStrategy.LENGTH -> {
                    // Show only length
                    "len_${value.length}"
                }
                MaskingStrategy.FULL -> {
                    // Show nothing
                    "***"
                }
            }
        }
    }
    
    /**
     * ✅ Mask with specific strategy
     */
    fun maskUserId(userId: String?, strategy: MaskingStrategy = MaskingStrategy.HASH): String {
        return "userId=${mask(userId, strategy)}"
    }
    
    fun maskMessageId(messageId: String?, strategy: MaskingStrategy = MaskingStrategy.HASH): String {
        return "msgId=${mask(messageId, strategy)}"
    }
    
    fun maskDeviceAddress(address: String?, strategy: MaskingStrategy = MaskingStrategy.HASH): String {
        return "device=${mask(address, strategy)}"
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
        // ✅ Check rate limit
        if (!shouldLog(tag, "logMessage")) return
        
        if (BuildConfig.DEBUG && OfflineMessagingConfig.ENABLE_SENSITIVE_LOGGING) {
            Logger.d(tag, "$action: content='$messageContent'")
        } else {
            // Only log metadata, never content
            val contentLength = messageContent?.length ?: 0
            Logger.d(tag, "$action: contentLength=$contentLength")
        }
        
        // ✅ Write to audit trail
        writeAudit(
            operation = "LOG_MESSAGE",
            tag = tag,
            details = mapOf(
                "action" to action,
                "content_length" to (messageContent?.length ?: 0).toString(),
                "thread" to Thread.currentThread().name
            )
        )
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
        // ✅ Check rate limit
        if (!shouldLog(tag, "logBlePacket")) return
        
        Logger.d(
            tag,
            "$action: ${maskMessageId(messageId)}, " +
                    "from=${mask(senderId)}, " +
                    "to=${mask(recipientId)}, " +
                    "size=$payloadSize bytes"
        )
        
        // ✅ Write to audit trail
        writeAudit(
            operation = "LOG_BLE_PACKET",
            tag = tag,
            details = mapOf(
                "action" to action,
                "message_id" to mask(messageId),
                "sender_id" to mask(senderId),
                "recipient_id" to mask(recipientId),
                "payload_size" to payloadSize.toString()
            )
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
        // ✅ Check rate limit
        if (!shouldLog(tag, "logEncryption")) return
        
        Logger.d(
            tag,
            "$action: recipient=${mask(recipientId)}, " +
                    "plaintext=$plaintextSize bytes, " +
                    "ciphertext=$ciphertextSize bytes"
        )
        
        // ✅ Write to audit trail
        writeAudit(
            operation = "LOG_ENCRYPTION",
            tag = tag,
            details = mapOf(
                "action" to action,
                "recipient_id" to mask(recipientId),
                "plaintext_size" to plaintextSize.toString(),
                "ciphertext_size" to ciphertextSize.toString()
            )
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
     * ✅ Get audit file for compliance/investigation
     */
    fun getAuditFile(): File? = auditFile
    
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
