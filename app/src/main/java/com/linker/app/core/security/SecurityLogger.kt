package com.linker.app.core.security

import android.os.Build
import android.util.Log

import com.linker.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

@Serializable
data class StructuredLog(
    val timestamp: Long,
    val eventType: String,
    val message: String,
    val userId: String? = null,
    val metadata: Map<String, String>? = null,
    val deviceId: String,
    val appVersion: String
)

data class SecurityEvent(
    val eventType: SecurityLogger.EventType,
    val message: String,
    val userId: String?,
    val metadata: Map<String, String>?,
    val timestamp: Long = System.currentTimeMillis()
)

class SecurityEventException(event: SecurityEvent) : Exception("${event.eventType}: ${event.message}")

enum class LogLevel {
    NONE, ERROR, WARNING, INFO, DEBUG, VERBOSE
}

object SecurityLogger {

    private const val TAG = "SecurityLog"
    private val logLock = Any()
    
    private val securityEventQueue = ConcurrentLinkedQueue<SecurityEvent>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val eventRateLimiter = mutableMapOf<String, RateLimitInfo>()
    
    private data class RateLimitInfo(
        var count: Int = 0,
        var firstOccurrence: Long = System.currentTimeMillis(),
        var lastLogged: Long = 0
    )
    
    private const val RATE_LIMIT_WINDOW_MS = 60_000L // 1 minute
    private const val MAX_EVENTS_PER_WINDOW = 10
    
    private var sessionId = UUID.randomUUID().toString()
    
    var minLogLevel: LogLevel = if (BuildConfig.DEBUG) {
        LogLevel.VERBOSE
    } else {
        LogLevel.WARNING
    }

    enum class EventType {
        AUTH_SUCCESS,
        AUTH_FAILURE,
        ROOT_DETECTED,
        EMULATOR_DETECTED,
        INVALID_INPUT,
        API_KEY_INITIALIZED,
        SESSION_CREATED,
        SESSION_EXPIRED,
        SUSPICIOUS_ACTIVITY,
        SECURITY_CHECK_FAILED,
        CONFIG_ACCESS,
        CREDENTIAL_ENCRYPTION_ATTEMPT,
        CREDENTIAL_ENCRYPTION_SUCCESS,
        CREDENTIAL_ENCRYPTION_FAILED
    }
    
    private fun sanitizeUserId(userId: String?): String? {
        return userId?.let { 
            if (BuildConfig.DEBUG) it else "user_${it.hashCode().toString(16)}"
        }
    }

    private fun sanitizeEmail(email: String?): String? {
        return email?.let {
            if (BuildConfig.DEBUG) {
                it
            } else {
                val parts = it.split("@")
                if (parts.size == 2) {
                    "${parts[0].take(2)}***@${parts[1]}"
                } else {
                    "***@***"
                }
            }
        }
    }
    
    private fun getDeviceId(): String = "device_unknown"
    private fun getSessionId(): String = sessionId

    private fun formatStructuredLog(event: SecurityEvent): String {
        val structured = StructuredLog(
            timestamp = event.timestamp,
            eventType = event.eventType.name,
            message = event.message,
            userId = sanitizeUserId(event.userId),
            metadata = event.metadata,
            deviceId = getDeviceId(),
            appVersion = BuildConfig.VERSION_NAME
        )
        
        return if (BuildConfig.DEBUG) {
            buildString {
                append("[${structured.eventType}] ")
                append("[")
                append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(structured.timestamp)))
                append("] ")
                append(structured.message)
                structured.userId?.let { append(" | User: $it") }
                structured.metadata?.let { metadata ->
                    val metadataStr = metadata.entries.joinToString { "${it.key}=${it.value}" }
                    append(" | Metadata: $metadataStr")
                }
            }
        } else {
            Json.encodeToString(StructuredLog.serializer(), structured)
        }
    }

    private fun shouldLog(eventType: EventType): Boolean {
        val eventLevel = when (eventType) {
            EventType.AUTH_FAILURE,
            EventType.ROOT_DETECTED,
            EventType.SECURITY_CHECK_FAILED,
            EventType.CREDENTIAL_ENCRYPTION_FAILED -> LogLevel.ERROR
            
            EventType.EMULATOR_DETECTED,
            EventType.SUSPICIOUS_ACTIVITY -> LogLevel.WARNING
            
            EventType.AUTH_SUCCESS,
            EventType.SESSION_CREATED,
            EventType.API_KEY_INITIALIZED -> LogLevel.INFO
            
            else -> LogLevel.DEBUG
        }
        
        return eventLevel.ordinal <= minLogLevel.ordinal
    }

    fun logEvent(
        eventType: EventType,
        message: String,
        userId: String? = null,
        metadata: Map<String, String>? = null
    ) {
        synchronized(logLock) {
            if (!shouldLog(eventType)) return
        
            val anonStr = "anonymous"
            val key = "${eventType}_${userId ?: anonStr}"
        val now = System.currentTimeMillis()
        
        val rateLimitInfo = eventRateLimiter.getOrPut(key) { RateLimitInfo(firstOccurrence = now) }
        
        if (now - rateLimitInfo.firstOccurrence > RATE_LIMIT_WINDOW_MS) {
            rateLimitInfo.count = 0
            rateLimitInfo.firstOccurrence = now
        }
        
        rateLimitInfo.count++
        
        if (rateLimitInfo.count > MAX_EVENTS_PER_WINDOW) {
            if (now - rateLimitInfo.lastLogged > RATE_LIMIT_WINDOW_MS) {
                Log.w(TAG, "Rate limit exceeded for $eventType (${rateLimitInfo.count} events)")
                rateLimitInfo.lastLogged = now
            }
            return
        }
        
        rateLimitInfo.lastLogged = now
        
        val enrichedMetadata = (metadata ?: emptyMap()).toMutableMap().apply {
            put("timestamp", now.toString())
            put("app_version", BuildConfig.VERSION_NAME)
            put("device_model", Build.MODEL)
            put("os_version", Build.VERSION.SDK_INT.toString())
            put("session_id", getSessionId())
            
            val sanitizedMetadata = this.mapValues { entry ->
                when (entry.key) {
                    "email", "phone", "address" -> "***"
                    else -> entry.value
                }
            }
            this.clear()
            this.putAll(sanitizedMetadata)
        }
        
        val event = SecurityEvent(eventType, message, userId, enrichedMetadata, now)
        val logMessage = formatStructuredLog(event)

        when (eventType) {
            EventType.AUTH_FAILURE,
            EventType.ROOT_DETECTED,
            EventType.EMULATOR_DETECTED,
            EventType.SUSPICIOUS_ACTIVITY,
            EventType.CREDENTIAL_ENCRYPTION_FAILED,
            EventType.SECURITY_CHECK_FAILED -> {
                Log.w(TAG, logMessage)
                securityEventQueue.offer(event)
                uploadSecurityEvents()
            }
            EventType.INVALID_INPUT -> {
                Log.w(TAG, logMessage)
                securityEventQueue.offer(event)
                uploadSecurityEvents()
            }
            else -> {
                Log.i(TAG, logMessage)
            }
        }
        }
    }

    private fun uploadSecurityEvents() {
        scope.launch {
            try {
                val events = mutableListOf<SecurityEvent>()
                while (securityEventQueue.isNotEmpty() && events.size < 50) {
                    securityEventQueue.poll()?.let { events.add(it) }
                }
                
                if (events.isNotEmpty()) {
                    events.forEach { event ->
                        try {
                            // FirebaseCrashlytics logic removed
                            android.util.Log.e("SecurityEvent", "Security Event: ${event.eventType}")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to log event", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to upload security events", e)
            }
        }
    }
    
    fun logDebug(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message)
        }
    }

    fun logAuthSuccess(userId: String, method: String) {
        logEvent(
            eventType = EventType.AUTH_SUCCESS,
            message = "User authenticated successfully",
            userId = sanitizeUserId(userId),
            metadata = mapOf("method" to method)
        )
    }

    fun logAuthFailure(reason: String, email: String? = null) {
        logEvent(
            eventType = EventType.AUTH_FAILURE,
            message = "Authentication failed: $reason",
            metadata = sanitizeEmail(email)?.let { mapOf("email" to it) }
        )
    }

    fun logRootDetection(riskLevel: SecurityRiskLevel) {
        val eventType = when (riskLevel) {
            SecurityRiskLevel.HIGH, SecurityRiskLevel.CRITICAL -> EventType.ROOT_DETECTED
            SecurityRiskLevel.MEDIUM -> EventType.EMULATOR_DETECTED
            SecurityRiskLevel.LOW -> return
        }

        logEvent(
            eventType = eventType,
            message = "Device security risk detected: $riskLevel"
        )
    }

    fun logInvalidInput(inputType: String, reason: String) {
        logEvent(
            eventType = EventType.INVALID_INPUT,
            message = "Invalid input detected",
            metadata = mapOf(
                "inputType" to inputType,
                "reason" to reason
            )
        )
    }

    fun logApiKeyInitialization() {
        logEvent(
            eventType = EventType.API_KEY_INITIALIZED,
            message = "API keys initialized in secure storage"
        )
    }

    fun logSessionCreated(userId: String) {
        logEvent(
            eventType = EventType.SESSION_CREATED,
            message = "User session created",
            userId = sanitizeUserId(userId)
        )
    }

    fun logSuspiciousActivity(description: String, userId: String? = null) {
        logEvent(
            eventType = EventType.SUSPICIOUS_ACTIVITY,
            message = description,
            userId = sanitizeUserId(userId)
        )
    }
    
    fun logConfigAccess(configKey: String, value: Any) {
        logEvent(
            eventType = EventType.CONFIG_ACCESS,
            message = "Sensitive configuration accessed",
            metadata = mapOf(
                "configKey" to configKey,
                "value" to value.toString()
            )
        )
    }
}
