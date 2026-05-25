package com.linker.app.core.session

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

// [2.5] Session state enum for explicit lifecycle management
enum class SessionState {
    ACTIVE,   // Session is active and valid
    EXPIRED,  // Session has expired (idle too long)
    CLOSED,   // Session explicitly closed and resources released
    INVALID   // Session auth failed / corrupted
}

/**
 * Minimal session for passive accounts
 *
 * Used for notification actions (reply, react, mark as read) without switching active account.
 * Keeps minimal resources to reduce memory footprint.
 *
 * Changes:
 *  - [2.1] Added close() method to explicitly release Firebase resources
 *  - [2.2] Configurable timeoutMs instead of hardcoded SESSION_TIMEOUT_MS
 *  - [2.3] lastActivityAt tracked via AtomicLong; expiry based on last activity, not creation
 *  - [2.4] equals/hashCode overridden to compare only userId (Firebase instances excluded)
 *  - [2.5] SessionState enum with AtomicReference for thread-safe state transitions
 */
data class MinimalAccountSession(
    val userId: String,
    val firebaseAuth: FirebaseAuth,
    val firestore: FirebaseFirestore,
    val createdAt: Long = System.currentTimeMillis(),
    val timeoutMs: Long = DEFAULT_SESSION_TIMEOUT_MS  // [2.2]
) {
    // [2.3] Track last activity time instead of only creation time
    private val lastActivityAt: AtomicLong = AtomicLong(System.currentTimeMillis())

    // [2.5] Thread-safe state tracking
    private val _state: AtomicReference<SessionState> = AtomicReference(SessionState.ACTIVE)

    companion object {
        const val DEFAULT_SESSION_TIMEOUT_MS = 30 * 60 * 1000L  // 30 minutes
        const val SHORT_SESSION_TIMEOUT_MS = 5 * 60 * 1000L     // 5 minutes
        const val LONG_SESSION_TIMEOUT_MS = 60 * 60 * 1000L     // 1 hour
    }

    // [2.5] Get current session state (auto-detects expiry)
    fun getState(): SessionState {
        val current = _state.get()
        if (current == SessionState.ACTIVE && isExpiredInternal()) {
            _state.compareAndSet(SessionState.ACTIVE, SessionState.EXPIRED)
            return SessionState.EXPIRED
        }
        return current
    }

    private fun isExpiredInternal(): Boolean {
        return System.currentTimeMillis() - lastActivityAt.get() > timeoutMs
    }

    // [2.3] Check expiry based on last activity (not creation)
    fun isExpired(): Boolean = getState() == SessionState.EXPIRED

    // [2.1] Check if session is valid (active and not closed)
    fun isValid(): Boolean = getState() == SessionState.ACTIVE

    fun isClosed(): Boolean = getState() == SessionState.CLOSED

    // [2.3] Update last activity timestamp (extends session life)
    fun updateActivity() {
        if (getState() == SessionState.ACTIVE) {
            lastActivityAt.set(System.currentTimeMillis())
        }
    }

    fun getLastActivityTime(): Long = lastActivityAt.get()

    fun getIdleTime(): Long = System.currentTimeMillis() - lastActivityAt.get()

    fun getRemainingTime(): Long {
        val elapsed = System.currentTimeMillis() - lastActivityAt.get()
        return (timeoutMs - elapsed).coerceAtLeast(0)
    }

    fun getExpirationTime(): Long = lastActivityAt.get() + timeoutMs

    // [2.5] Mark session as invalid (e.g., auth failure)
    fun markInvalid() {
        _state.set(SessionState.INVALID)
    }

    // [2.1] Cleanup Firebase resources — call before removing session
    suspend fun close() {
        val current = _state.get()
        if (current == SessionState.CLOSED) {
            android.util.Log.d("MinimalAccountSession", "Session already closed for user")
            return
        }

        try {
            withContext(Dispatchers.IO) {
                // Sign out from Firebase Auth
                try {
                    firebaseAuth.signOut()
                    android.util.Log.d("MinimalAccountSession", "Signed out from Firebase Auth")
                } catch (e: Exception) {
                    android.util.Log.w("MinimalAccountSession", "Error signing out: ${e.message}")
                }

                // Terminate Firestore instance
                try {
                    firestore.terminate().await()
                    android.util.Log.d("MinimalAccountSession", "Terminated Firestore instance")
                } catch (e: Exception) {
                    android.util.Log.w("MinimalAccountSession", "Error terminating Firestore: ${e.message}")
                }
            }

            _state.set(SessionState.CLOSED)
        } catch (e: Exception) {
            _state.set(SessionState.INVALID)
            android.util.Log.e("MinimalAccountSession", "Error closing session: ${e.message}", e)
            throw e
        }
    }

    // [2.4] Override equals/hashCode to compare only userId (Firebase instances excluded)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MinimalAccountSession) return false
        return userId == other.userId
    }

    override fun hashCode(): Int = userId.hashCode()

    // [2.4] toString without logging Firebase instances
    override fun toString(): String {
        return "MinimalAccountSession(userId='***', state=${getState()}, " +
                "createdAt=$createdAt, lastActivity=${lastActivityAt.get()}, " +
                "remainingTime=${getRemainingTime()}ms)"
    }
}
