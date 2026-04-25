package com.linker.app.core.security

import android.util.Log

/**
 * Security event logging utility
 * 
 * SECURITY: Centralized logging for security-related events.
 * Helps with monitoring, auditing, and incident response.
 * 
 * PRODUCTION NOTES:
 * - For production, consider sending logs to a backend service
 * - Implement log rotation and retention policies
 * - Add user consent for security logging (GDPR compliance)
 * - Consider using Firebase Crashlytics for non-fatal events
 */
object SecurityLogger {

    private const val TAG = "SecurityLog"

    /**
     * Security event types
     */
    enum class EventType {
        AUTH_SUCCESS,           // Successful authentication
        AUTH_FAILURE,           // Failed authentication attempt
        ROOT_DETECTED,          // Rooted device detected
        EMULATOR_DETECTED,      // Emulator detected
        INVALID_INPUT,          // Invalid input detected (potential attack)
        API_KEY_INITIALIZED,    // API keys initialized in secure storage
        SESSION_CREATED,        // User session created
        SESSION_EXPIRED,        // User session expired
        SUSPICIOUS_ACTIVITY,    // Suspicious activity detected
        SECURITY_CHECK_FAILED   // Security check failed
    }

    /**
     * Log security event
     * 
     * @param eventType Type of security event
     * @param message Event description
     * @param userId Optional user ID (for user-specific events)
     * @param metadata Optional additional metadata
     */
    fun logEvent(
        eventType: EventType,
        message: String,
        userId: String? = null,
        metadata: Map<String, String>? = null
    ) {
        val logMessage = buildString {
            append("[${eventType.name}] ")
            append(message)
            userId?.let { append(" | User: $it") }
            metadata?.let { 
                append(" | Metadata: ${it.entries.joinToString { "${it.key}=${it.value}" }}")
            }
        }

        when (eventType) {
            EventType.AUTH_FAILURE,
            EventType.ROOT_DETECTED,
            EventType.EMULATOR_DETECTED,
            EventType.SUSPICIOUS_ACTIVITY,
            EventType.SECURITY_CHECK_FAILED -> {
                Log.w(TAG, logMessage)
                // For production: Send to backend monitoring service
            }
            EventType.INVALID_INPUT -> {
                Log.w(TAG, logMessage)
                // For production: Track potential attack patterns
            }
            else -> {
                Log.i(TAG, logMessage)
            }
        }

        // For production: Send to analytics/monitoring service
        // Example: FirebaseCrashlytics.getInstance().log(logMessage)
    }

    /**
     * Log authentication success
     */
    fun logAuthSuccess(userId: String, method: String) {
        logEvent(
            eventType = EventType.AUTH_SUCCESS,
            message = "User authenticated successfully",
            userId = userId,
            metadata = mapOf("method" to method)
        )
    }

    /**
     * Log authentication failure
     */
    fun logAuthFailure(reason: String, email: String? = null) {
        logEvent(
            eventType = EventType.AUTH_FAILURE,
            message = "Authentication failed: $reason",
            metadata = email?.let { mapOf("email" to it) }
        )
    }

    /**
     * Log root detection
     */
    fun logRootDetection(riskLevel: SecurityRiskLevel) {
        val eventType = when (riskLevel) {
            SecurityRiskLevel.HIGH, SecurityRiskLevel.CRITICAL -> EventType.ROOT_DETECTED
            SecurityRiskLevel.MEDIUM -> EventType.EMULATOR_DETECTED
            SecurityRiskLevel.LOW -> return // No need to log normal devices
        }

        logEvent(
            eventType = eventType,
            message = "Device security risk detected: $riskLevel"
        )
    }

    /**
     * Log invalid input (potential attack)
     */
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

    /**
     * Log API key initialization
     */
    fun logApiKeyInitialization() {
        logEvent(
            eventType = EventType.API_KEY_INITIALIZED,
            message = "API keys initialized in secure storage"
        )
    }

    /**
     * Log session creation
     */
    fun logSessionCreated(userId: String) {
        logEvent(
            eventType = EventType.SESSION_CREATED,
            message = "User session created",
            userId = userId
        )
    }

    /**
     * Log suspicious activity
     */
    fun logSuspiciousActivity(description: String, userId: String? = null) {
        logEvent(
            eventType = EventType.SUSPICIOUS_ACTIVITY,
            message = description,
            userId = userId
        )
    }
}
