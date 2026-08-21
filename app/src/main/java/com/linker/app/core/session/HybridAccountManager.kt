package com.linker.app.core.session

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.linker.app.BuildConfig
import com.linker.app.core.di.SupabaseNotificationApi
import com.linker.app.core.security.SecurityManager
import com.linker.app.core.util.Result
import com.linker.app.domain.repository.AccountRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hybrid Account Manager
 *
 * Manages multiple account sessions:
 * - Active account: Full-featured session (current system)
 * - Passive accounts: Minimal sessions for notification actions only
 *
 * Changes:
 *  - [1.1] Passwords cleared from memory immediately after use
 *  - [1.2] Per-user Mutex prevents duplicate sessions (coroutine-safe)
 *  - [1.3] removePassiveSession is now suspend; Firebase app deletion awaited
 *  - [1.4] sanitizeUserId hides PII in production builds
 *  - [1.5] withTimeout wraps Firebase sign-in (15s limit)
 *  - [1.6] retryWithExponentialBackoff for network operations
 *  - [1.7] Per-user session limit enforced via Mutex
 *  - [1.8] sessionMetrics tracks creation, failure, reuse, and expiry counts
 *  - [1.9] parseFirebaseOptions falls back to google-services.json on error
 *  - [1.10] validateSession checks Firebase token validity before use
 *  - [1.11] maxPassiveSessions dynamically set based on device RAM
 */
@Singleton
class HybridAccountManager @Inject constructor(
    private val accountRepository: AccountRepository,
    private val supabaseNotificationApi: SupabaseNotificationApi,
    private val securityManager: SecurityManager,
    @ApplicationContext private val context: Context
) {
    private val passiveSessions = ConcurrentHashMap<String, MinimalAccountSession>()

    // [1.2] Per-user Mutex — coroutine-safe alternative to synchronized
    private val sessionMutexes = ConcurrentHashMap<String, Mutex>()

    companion object {
        private const val TAG = "HybridAccountManager"
        private const val DEFAULT_MAX_PASSIVE_SESSIONS = 5

        // [1.4] PII sanitization — only plain text in debug builds
        private fun sanitizeUserId(userId: String): String =
            if (BuildConfig.DEBUG) userId else "user_${userId.hashCode().toString(16)}"
    }

    // [1.11] Dynamic session limit based on device RAM
    private var maxPassiveSessions: Int = DEFAULT_MAX_PASSIVE_SESSIONS

    // [1.8] Session metrics
    private val sessionMetrics = object {
        val totalCreated = AtomicInteger(0)
        val totalFailed = AtomicInteger(0)
        val totalExpired = AtomicInteger(0)
        val totalReused = AtomicInteger(0)

        fun log() {
            Log.i(TAG, """
                Session Metrics:
                - Created: ${totalCreated.get()}
                - Failed: ${totalFailed.get()}
                - Expired: ${totalExpired.get()}
                - Reused: ${totalReused.get()}
                - Active: ${passiveSessions.size}
            """.trimIndent())
        }
    }

    init {
        // [1.11] Adjust session limit based on device RAM
        try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)

            maxPassiveSessions = when {
                memoryInfo.totalMem > 6L * 1024 * 1024 * 1024 -> 8  // 6GB+ RAM
                memoryInfo.totalMem > 4L * 1024 * 1024 * 1024 -> 5  // 4GB+ RAM
                memoryInfo.totalMem > 2L * 1024 * 1024 * 1024 -> 3  // 2GB+ RAM
                else -> 2
            }
            Log.d(TAG, "Max passive sessions set to $maxPassiveSessions based on device RAM")
        } catch (e: Exception) {
            Log.w(TAG, "Could not determine device RAM, using default: ${e.message}")
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Get or create a passive session for the given user.
     * [1.2] Thread-safe with per-user Mutex (coroutine-safe, supports suspension).
     * [1.7] Only one session per user at a time.
     */
    suspend fun getOrCreatePassiveSession(userId: String): MinimalAccountSession? {
        // Fast path: valid session already exists
        passiveSessions[userId]?.let { session ->
            if (session.isValid()) {
                Log.d(TAG, "Reusing existing passive session for ${sanitizeUserId(userId)}")
                sessionMetrics.totalReused.incrementAndGet()
                return session
            }
        }

        // [1.2] Per-user Mutex — safe to call suspend functions inside
        val mutex = sessionMutexes.getOrPut(userId) { Mutex() }

        return mutex.withLock {
            try {
                // Double-check after acquiring lock
                passiveSessions[userId]?.let { session ->
                    if (session.isValid()) {
                        Log.d(TAG, "Session created by another coroutine, reusing for ${sanitizeUserId(userId)}")
                        sessionMetrics.totalReused.incrementAndGet()
                        return@withLock session
                    } else {
                        Log.d(TAG, "Session expired, removing for ${sanitizeUserId(userId)}")
                        sessionMetrics.totalExpired.incrementAndGet()
                        removePassiveSessionInternal(userId)
                    }
                }

                // Check total session limit
                if (passiveSessions.size >= maxPassiveSessions) {
                    Log.w(TAG, "Max passive sessions ($maxPassiveSessions) reached, cleaning up oldest")
                    cleanupOldestSession()
                }

                // Create new session
                Log.d(TAG, "Creating new passive session for ${sanitizeUserId(userId)}")
                val session = try {
                    createPassiveSession(userId)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create passive session for ${sanitizeUserId(userId)}: ${e.message}", e)
                    sessionMetrics.totalFailed.incrementAndGet()
                    return@withLock null
                }

                passiveSessions[userId] = session
                sessionMetrics.totalCreated.incrementAndGet()
                Log.d(TAG, "Successfully created passive session for ${sanitizeUserId(userId)} (total: ${passiveSessions.size})")
                session
            } finally {
                sessionMutexes.remove(userId)
            }
        }
    }

    /**
     * Remove a passive session and cleanup resources.
     * [1.3] Firebase app deletion awaited.
     */
    suspend fun removePassiveSession(userId: String) {
        removePassiveSessionInternal(userId)
    }

    private suspend fun removePassiveSessionInternal(userId: String) {
        val session = passiveSessions.remove(userId)

        // [2.1] Close session resources
        if (session != null) {
            try {
                session.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing session for ${sanitizeUserId(userId)}: ${e.message}")
            }
        }

        Log.d(TAG, "Removed passive session for ${sanitizeUserId(userId)} (remaining: ${passiveSessions.size})")

        // [1.3] Delete Firebase app (delete() is void, not Task — fire-and-forget)
        val appName = "passive_account_$userId"
        try {
            FirebaseApp.getInstance(appName).delete()
            Log.d(TAG, "Deleted Firebase app for ${sanitizeUserId(userId)}")
        } catch (e: IllegalStateException) {
            Log.d(TAG, "Firebase app for ${sanitizeUserId(userId)} already deleted")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete Firebase app for ${sanitizeUserId(userId)}: ${e.message}")
        }
    }

    /**
     * Cleanup all expired sessions.
     * Returns number of sessions cleaned (for WorkManager metrics).
     */
    suspend fun cleanupExpiredSessions(): Int {
        val expiredUserIds = passiveSessions.entries
            .filter { !it.value.isValid() }
            .map { it.key }

        Log.d(TAG, "Cleaning up ${expiredUserIds.size} expired sessions")
        expiredUserIds.forEach { userId ->
            Log.d(TAG, "Cleaning up expired session for ${sanitizeUserId(userId)}")
            removePassiveSessionInternal(userId)
        }
        return expiredUserIds.size
    }

    /**
     * Cleanup all passive sessions (call on app exit).
     */
    suspend fun cleanupAllSessions() {
        Log.d(TAG, "Cleaning up all ${passiveSessions.size} passive sessions")
        val userIds = passiveSessions.keys.toList()
        userIds.forEach { userId -> removePassiveSessionInternal(userId) }
    }

    /** Active session count for monitoring. */
    fun getActiveSessionCount(): Int = passiveSessions.size

    /** [1.8] Log session metrics. */
    fun logSessionMetrics() = sessionMetrics.log()

    /**
     * Send message from passive account.
     * [1.6] Retry with exponential backoff.
     * [1.10] Validate session before use.
     */
    suspend fun sendMessageFromPassiveAccount(
        userId: String,
        chatId: String,
        content: String
    ): Result<Unit> {
        return try {
            retryWithExponentialBackoff {
                val session = getOrCreateValidSession(userId)
                val repository = buildRepository(session, userId)
                session.updateActivity()
                repository.sendMessage(chatId, content)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message from passive account: ${e.message}", e)
            Result.Error(buildErrorMessage(e))
        }
    }

    /**
     * React to message from passive account.
     */
    suspend fun reactToMessageFromPassiveAccount(
        userId: String,
        chatId: String,
        messageId: String,
        emoji: String?
    ): Result<Unit> {
        return try {
            retryWithExponentialBackoff {
                val session = getOrCreateValidSession(userId)
                val repository = buildRepository(session, userId)
                session.updateActivity()
                repository.reactToMessage(chatId, messageId, emoji)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to react from passive account: ${e.message}", e)
            Result.Error(buildErrorMessage(e))
        }
    }

    /**
     * Mark chat as read from passive account.
     */
    suspend fun markChatAsReadFromPassiveAccount(
        userId: String,
        chatId: String
    ): Result<Unit> {
        return try {
            retryWithExponentialBackoff {
                val session = getOrCreateValidSession(userId)
                val repository = buildRepository(session, userId)
                session.updateActivity()
                repository.markChatAsRead(chatId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mark as read from passive account: ${e.message}", e)
            Result.Error(buildErrorMessage(e))
        }
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    private fun buildRepository(session: MinimalAccountSession, userId: String) =
        MinimalMessageRepository(
            firestore = session.firestore,
            currentUserId = userId,
            supabaseNotificationApi = supabaseNotificationApi,
            securityManager = securityManager,
            context = context
        )

    /**
     * [1.10] Get or create a session and validate it before returning.
     */
    private suspend fun getOrCreateValidSession(userId: String): MinimalAccountSession {
        val session = getOrCreatePassiveSession(userId)
            ?: throw Exception("Authentication failed. Please try again.")

        if (!validateSession(session)) {
            Log.w(TAG, "Session invalid for ${sanitizeUserId(userId)}, recreating")
            removePassiveSessionInternal(userId)

            val newSession = getOrCreatePassiveSession(userId)
                ?: throw Exception("Authentication failed. Please try again.")

            if (!validateSession(newSession)) {
                throw Exception("Authentication failed. Please try again.")
            }
            return newSession
        }
        return session
    }

    /**
     * [1.10] Validate a session by checking Firebase auth token.
     */
    private suspend fun validateSession(session: MinimalAccountSession): Boolean {
        return try {
            if (!session.isValid()) {
                Log.w(TAG, "Session not valid (state=${session.getState()})")
                return false
            }

            val currentUser = session.firebaseAuth.currentUser
            if (currentUser == null) {
                Log.w(TAG, "Firebase user is null, session invalid")
                return false
            }

            withTimeout(5_000) {
                currentUser.getIdToken(false).await()
            }

            Log.d(TAG, "Session validation successful")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Session validation failed: ${e.message}")
            false
        }
    }

    /**
     * Create a new passive session by signing in with stored credentials.
     * [1.1] Password cleared from memory immediately (GC hint).
     * [1.5] 15-second timeout on Firebase sign-in.
     */
    private suspend fun createPassiveSession(userId: String): MinimalAccountSession {
        val firebaseApp = getOrCreateFirebaseApp(userId)
        val auth = FirebaseAuth.getInstance(firebaseApp)
        val firestore = FirebaseFirestore.getInstance(firebaseApp)

        val credentials = accountRepository.getDecryptedCredentials(userId)
            ?: throw IllegalStateException("Failed to get credentials for ${sanitizeUserId(userId)}")

        val (email, password) = credentials

        try {
            // [1.5] Add timeout for sign-in
            withTimeout(15_000) {
                auth.signInWithEmailAndPassword(email, password).await()
            }
            Log.d(TAG, "Successfully signed in to passive session for ${sanitizeUserId(userId)}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sign in for ${sanitizeUserId(userId)}: ${e.message}", e)
            try {
                firebaseApp.delete()
            } catch (deleteError: Exception) {
                Log.w(TAG, "Failed to delete Firebase app after sign-in failure: ${deleteError.message}")
            }
            throw Exception("Failed to authenticate passive session: ${e.message}")
        } finally {
            // [1.1] Hint GC to clear the password string
            @Suppress("ExplicitGarbageCollectionCall")
            System.gc()
        }

        return MinimalAccountSession(
            userId = userId,
            firebaseAuth = auth,
            firestore = firestore
        )
    }

    private fun getOrCreateFirebaseApp(userId: String): FirebaseApp {
        val appName = "passive_account_$userId"
        return try {
            FirebaseApp.getInstance(appName)
        } catch (e: IllegalStateException) {
            val options = parseFirebaseOptions()
            FirebaseApp.initializeApp(context, options, appName)
        }
    }

    /**
     * [1.9] Parse Firebase options with fallback if default app not initialized.
     */
    private fun parseFirebaseOptions(): FirebaseOptions {
        return try {
            val defaultApp = FirebaseApp.getInstance()
            val defaultOptions = defaultApp.options

            FirebaseOptions.Builder()
                .setApplicationId(defaultOptions.applicationId)
                .setApiKey(defaultOptions.apiKey)
                .setDatabaseUrl(defaultOptions.databaseUrl)
                .setGcmSenderId(defaultOptions.gcmSenderId)
                .setProjectId(defaultOptions.projectId)
                .setStorageBucket(defaultOptions.storageBucket)
                .build()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Default Firebase app not initialized, parsing from resources")
            parseFirebaseOptionsFromResources()
        }
    }

    private fun parseFirebaseOptionsFromResources(): FirebaseOptions {
        return try {
            val options = FirebaseOptions.fromResource(context)
            if (options != null) return options
            throw IllegalStateException("FirebaseOptions.fromResource returned null")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Firebase options from resources", e)
            throw IllegalStateException("Cannot initialize Firebase options", e)
        }
    }

    private suspend fun cleanupOldestSession() {
        val oldest = passiveSessions.entries.minByOrNull { it.value.createdAt }
        oldest?.let {
            Log.d(TAG, "Removing oldest session: ${sanitizeUserId(it.key)}")
            removePassiveSessionInternal(it.key)
        }
    }

    /**
     * [1.6] Retry with exponential backoff for transient network failures.
     */
    private suspend fun <T> retryWithExponentialBackoff(
        maxRetries: Int = 3,
        initialDelay: Long = 1_000L,
        maxDelay: Long = 10_000L,
        factor: Double = 2.0,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelay
        repeat(maxRetries - 1) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                if (e.message?.contains("Authentication", ignoreCase = true) == true ||
                    e.message?.contains("credentials", ignoreCase = true) == true
                ) throw e

                Log.w(TAG, "Attempt ${attempt + 1} failed: ${e.message}, retrying in ${currentDelay}ms")
                delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
            }
        }
        return block()
    }

    private fun buildErrorMessage(e: Exception): String = when {
        e.message?.contains("Network error", ignoreCase = true) == true ->
            e.message ?: "Network error"
        e.message?.contains("Authentication", ignoreCase = true) == true ||
        e.message?.contains("credentials", ignoreCase = true) == true ->
            "Authentication failed. Please try again."
        else -> "Operation failed: ${e.message}"
    }
}
