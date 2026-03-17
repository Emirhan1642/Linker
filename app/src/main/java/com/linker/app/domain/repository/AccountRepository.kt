package com.linker.app.domain.repository

import com.linker.app.domain.model.AccountSession
import com.linker.app.core.util.Result
import kotlinx.coroutines.flow.Flow

/**
 * Contract for multi-account session management.
 *
 * Responsibilities:
 *  • Persist/remove encrypted account sessions (EncryptedSharedPreferences + Android Keystore).
 *  • Switch the active Firebase Auth session without exposing plain-text tokens.
 *  • Expose a reactive list of stored sessions for the UI.
 */
interface AccountRepository {

    /**
     * Emits the list of stored [AccountSession]s whenever it changes.
     * Never exposes the raw [AccountSession.encryptedToken] to collectors —
     * tokens are zeroed in the emitted copies.
     */
    fun observeSessions(): Flow<List<AccountSession>>

    /** Returns the UID of the currently active account, or null if signed out. */
    suspend fun getActiveUid(): String?

    /**
     * Saves a new session after a successful sign-in.
     * The caller must pass the plain-text Firebase refresh token here — it will be
     * encrypted with AES-256-GCM using a Keystore-backed key before persistence.
     * The plain-text token is cleared from memory after encryption.
     *
     * @param session  Domain session with [AccountSession.encryptedToken] set to
     *                 the *plain* refresh token at call time.  The repository
     *                 replaces it with the ciphertext before writing.
     */
    suspend fun addSession(session: AccountSession): Result<Unit>

    /**
     * Removes the session for [uid] and revokes the corresponding Firebase token.
     * If [uid] is the active account the caller must navigate to Auth first.
     */
    suspend fun removeSession(uid: String): Result<Unit>

    /**
     * Switches Firebase Auth to the account identified by [uid].
     *
     * Steps performed internally:
     *  1. Load the encrypted token for [uid].
     *  2. Decrypt with Android Keystore — token lives in memory < 500 ms.
     *  3. Call `signInWithCustomToken` / refresh the credential.
     *  4. Zero out the plain-text token ByteArray.
     *  5. Update [AccountSession.lastUsedAt].
     *
     * Returns [Result.Error] if biometric auth is required but not yet confirmed.
     */
    suspend fun switchToAccount(uid: String): Result<Unit>

    /**
     * Updates the display metadata (name, username, avatar) for an already-stored
     * session.  Does NOT touch the encrypted token.
     */
    suspend fun updateSessionMetadata(
        uid: String,
        displayName: String,
        username: String,
        avatarUrl: String?
    ): Result<Unit>

    /** Returns all sessions without sensitive fields (tokens zeroed). */
    suspend fun getSessions(): List<AccountSession>
}
