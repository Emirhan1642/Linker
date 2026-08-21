package com.linker.app.domain.repository

import com.linker.app.domain.model.AccountSession
import com.linker.app.core.util.Result
import kotlinx.coroutines.flow.Flow

/**
 * Account operation errors
 */
sealed class AccountError : Exception() {
    data class SessionNotFound(val uid: String) : AccountError()
    data class EncryptionFailed(val reason: String) : AccountError()
    data class DecryptionFailed(val reason: String) : AccountError()
    data class BiometricRequired(val uid: String) : AccountError()
    data class BiometricFailed(val reason: String) : AccountError()
    data class KeystoreError(val reason: String) : AccountError()
    data class FirebaseAuthError(val code: String, override val message: String) : AccountError()
    data class InvalidSession(val reason: String) : AccountError()
    data class DuplicateSession(val uid: String) : AccountError()
    object NoActiveSession : AccountError()
}

/**
 * Session metadata update
 */
data class SessionMetadataUpdate(
    val displayName: String? = null,
    val username: String? = null,
    val avatarUrl: String? = null
)

/**
 * Session validation report
 */
data class SessionValidationReport(
    val totalSessions: Int,
    val validSessions: Int,
    val invalidSessions: Int,
    val removedSessions: List<String>
)

/**
 * Session import report
 */
data class SessionImportReport(
    val totalImported: Int,
    val successfulImports: Int,
    val failedImports: Int,
    val requiresReauth: List<String>
)

/**
 * Exportable session metadata
 */
data class SessionMetadata(
    val uid: String,
    val displayName: String,
    val username: String,
    val avatarUrl: String?,
    val addedAt: Long,
    val lastUsedAt: Long,
    val requiresAuthOnSwitch: Boolean
)

/**
 * Session operation metrics
 */
data class SessionMetrics(
    val totalSessions: Int,
    val activeSessions: Int,
    val switchCount: Int,
    val failedSwitchCount: Int,
    val averageSwitchTimeMs: Long,
    val encryptionCount: Int,
    val decryptionCount: Int,
    val biometricAuthCount: Int,
    val biometricAuthFailCount: Int,
    val lastOperationTimestamp: Long
)

enum class OperationType {
    ADD_SESSION,
    REMOVE_SESSION,
    SWITCH_ACCOUNT,
    UPDATE_METADATA,
    ENCRYPT_TOKEN,
    DECRYPT_TOKEN,
    BIOMETRIC_AUTH
}

/**
 * Session operation record
 */
data class SessionOperation(
    val operationType: OperationType,
    val uid: String,
    val timestamp: Long,
    val durationMs: Long,
    val success: Boolean,
    val errorType: String?
)

/**
 * Sealed class representing an authenticated session
 * Credentials are encapsulated and never exposed
 */
abstract class AuthenticatedSession {
    abstract val uid: String
    abstract val displayName: String
    
    /**
     * Execute an authenticated operation
     * Credentials are used internally and cleared after use
     */
    abstract suspend fun <T> executeAuthenticated(
        operation: suspend (credential: Any) -> T
    ): Result<T>
}

/**
 * Contract for multi-account session management.
 *
 * ## Security Architecture
 * 
 * ### Encryption
 * - Algorithm: AES-256-GCM (Galois/Counter Mode)
 * - Key Storage: Android Keystore (StrongBox if available, otherwise TEE)
 * - Key Alias: "linker_session_key_{uid}" (per-user key)
 * - IV: 12 bytes (GCM standard), randomly generated per encryption
 * - Auth Tag: 16 bytes (128-bit)
 * 
 * ### Token Management
 * - Firebase refresh tokens are encrypted before persistence
 * - Plain-text tokens live in memory < 500ms during encryption/decryption
 * - Tokens are zeroed from memory after use (ByteArray.fill(0))
 * - Token lifetime: Managed by Firebase (typically 1 hour access, 30 days refresh)
 * 
 * ### Key Management
 * - Keys are generated on first use per user
 * - Keys are NOT exportable (Keystore protection)
 * - Keys are invalidated on user removal
 * 
 * ### Biometric Authentication
 * - Optional per-session (AccountSession.requiresAuthOnSwitch)
 * - Uses BiometricPrompt API (Android 9+)
 * - Fallback to device credential
 */
interface AccountRepository {

    /**
     * Emits the list of stored sessions whenever it changes.
     * Tokens are zeroed in emitted copies.
     */
    fun observeSessions(): Flow<List<AccountSession>>

    /**
     * Returns the UID of the currently active account.
     */
    suspend fun getActiveUid(): Result<String>

    /**
     * Saves a new session after successful sign-in.
     * Plain-text token is encrypted before persistence.
     */
    suspend fun addSession(session: AccountSession): Result<Unit>

    /**
     * Removes the session for [uid] and revokes Firebase token.
     */
    suspend fun removeSession(uid: String): Result<Unit>

    /**
     * Remove multiple sessions in a single transaction.
     */
    suspend fun removeSessions(uids: List<String>): Result<Int>

    /**
     * Switches Firebase Auth to the account identified by [uid].
     */
    suspend fun switchToAccount(uid: String): Result<Unit>

    /**
     * Updates display metadata for a stored session.
     */
    suspend fun updateSessionMetadata(
        uid: String,
        displayName: String,
        username: String,
        avatarUrl: String?
    ): Result<Unit>

    /**
     * Update metadata for multiple sessions.
     */
    suspend fun updateSessionsMetadata(
        updates: Map<String, SessionMetadataUpdate>
    ): Result<Int>

    /**
     * Returns all sessions without sensitive fields.
     */
    suspend fun getSessions(): Result<List<AccountSession>>
    
    /**
     * Validate all stored sessions.
     * Removes invalid/expired sessions.
     */
    suspend fun validateSessions(): Result<SessionValidationReport>
    
    /**
     * Export session metadata for backup.
     * Excludes encrypted tokens (not exportable).
     */
    suspend fun exportSessionMetadata(): Result<List<SessionMetadata>>
    
    /**
     * Import session metadata from backup.
     * Users must re-authenticate to restore tokens.
     */
    suspend fun importSessionMetadata(
        metadata: List<SessionMetadata>
    ): Result<SessionImportReport>

    /**
     * Observe session operation metrics.
     */
    fun observeSessionMetrics(): Flow<SessionMetrics>
    
    /**
     * Get session operation history.
     */
    suspend fun getSessionHistory(limit: Int = 100): Result<List<SessionOperation>>

    /**
     * Authenticate and create passive session.
     * Credentials never exposed to caller.
     */
    suspend fun authenticatePassiveSession(
        uid: String,
        onAuthenticated: suspend (AuthenticatedSession) -> Unit
    ): Result<Unit>

    /**
     * Retrieve decrypted credentials for internal session manager.
     */
    suspend fun getDecryptedCredentials(uid: String): Pair<String, String>?
}
