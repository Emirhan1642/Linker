package com.linker.app.domain.model

/**
 * Represents a stored session for a single Linker account.
 *
 * Each session corresponds to one Firebase-authenticated account and holds
 * the encrypted refresh token needed to switch to that account.
 *
 * ## Security Notes
 * - [uid]            : Plain, non-sensitive — used only as a lookup key.
 * - [encryptedToken] : Firebase refresh token, AES-256-GCM encrypted via Android Keystore.
 *                      Never stored or transmitted in plain-text.
 * - [avatarUrl]      : Public CDN URL, safe to store unencrypted.
 * - [addedAt]        : Epoch ms; used for session age checks.
 *
 * ## Keystore Management
 * The encryption key is generated and stored in Android Keystore with alias
 * `linker_session_key`. The key is hardware-backed on devices that support it.
 * Token is encrypted as `Base64(IV + ciphertext)`.
 *
 * ## Backup / Restore
 * Sessions are **not** included in Auto Backup. After a device restore the
 * user must re-authenticate each account.
 *
 * ## Thread Safety
 * This is an immutable data class — safe to share across threads.
 * Token decryption is performed on a background dispatcher.
 *
 * @property uid Firebase UID — stable, non-secret identifier.
 * @property displayName Display name shown in the account switcher.
 * @property username Username shown below the display name.
 * @property avatarUrl Public profile image URL (nullable).
 * @property encryptedToken AES-256-GCM encrypted Firebase refresh token.
 * @property addedAt Epoch ms when this session was first added.
 * @property lastUsedAt Epoch ms of the last successful switch to this account.
 * @property requiresAuthOnSwitch Whether biometric re-auth is required before switching.
 * @property expiresAt Optional hard expiration timestamp (epoch ms). Null means no hard expiry.
 * @property maxIdleTimeMs Maximum idle time in ms before session is considered stale.
 *
 * @see com.linker.app.domain.model.User
 */
data class AccountSession(
    /** Firebase UID — stable, non-secret identifier. */
    val uid: String,

    /** Display name shown in the account switcher. */
    val displayName: String,

    /** Username shown below the display name. */
    val username: String,

    /** Public profile image URL (nullable). */
    val avatarUrl: String?,

    /**
     * AES-256-GCM encrypted Firebase refresh token.
     * Stored as Base64(IV + ciphertext).
     * Decrypted only at the moment of account switch — never kept in memory longer.
     */
    val encryptedToken: String,

    /** Epoch ms when this session was first added. */
    val addedAt: Long = System.currentTimeMillis(),

    /** Epoch ms of the last successful switch to this account. */
    val lastUsedAt: Long = System.currentTimeMillis(),

    /**
     * Whether biometric / device-credential re-authentication is required
     * before switching to this account.
     * Useful for "work" or sensitive accounts.
     */
    val requiresAuthOnSwitch: Boolean = false,

    /** Optional hard expiration timestamp (epoch ms). Null means no hard expiry. */
    val expiresAt: Long? = null,

    /** Maximum idle time in milliseconds before session is considered stale. */
    val maxIdleTimeMs: Long = DEFAULT_MAX_IDLE_MS
) {
    init {
        require(uid.isNotBlank()) { "UID cannot be blank" }
        require(displayName.isNotBlank()) { "Display name cannot be blank" }
        require(username.isNotBlank()) { "Username cannot be blank" }
        require(encryptedToken.isNotBlank()) { "Encrypted token cannot be blank" }
        require(addedAt > 0) { "addedAt must be positive" }
        require(lastUsedAt > 0) { "lastUsedAt must be positive" }
        require(lastUsedAt >= addedAt) { "lastUsedAt cannot be before addedAt" }
        require(maxIdleTimeMs > 0) { "maxIdleTimeMs must be positive" }
        expiresAt?.let {
            require(it > addedAt) { "expiresAt must be after addedAt" }
        }
    }

    /**
     * Whether this session has passed its hard expiration time.
     * Returns false if [expiresAt] is null (no hard expiry set).
     */
    fun isExpired(): Boolean {
        val now = System.currentTimeMillis()
        return expiresAt?.let { now >= it } ?: false
    }

    /**
     * Whether this session has been idle longer than [maxIdleTimeMs].
     * An idle session should prompt re-authentication.
     */
    fun needsRefresh(): Boolean {
        val now = System.currentTimeMillis()
        return (now - lastUsedAt) > maxIdleTimeMs
    }

    /**
     * Returns a copy with [lastUsedAt] updated to the current time.
     * Call this after a successful account switch.
     */
    fun markAsUsed(): AccountSession {
        return copy(lastUsedAt = System.currentTimeMillis())
    }

    /**
     * Override toString to prevent encrypted token from being logged.
     */
    override fun toString(): String {
        return "AccountSession(" +
                "uid='$uid', " +
                "displayName='$displayName', " +
                "username='$username', " +
                "avatarUrl=$avatarUrl, " +
                "encryptedToken=***REDACTED***, " +
                "addedAt=$addedAt, " +
                "lastUsedAt=$lastUsedAt, " +
                "requiresAuthOnSwitch=$requiresAuthOnSwitch, " +
                "expiresAt=$expiresAt, " +
                "maxIdleTimeMs=$maxIdleTimeMs)"
    }

    companion object {
        /** Default maximum idle time: 30 days */
        const val DEFAULT_MAX_IDLE_DAYS = 30L

        /** Default maximum idle time in milliseconds */
        const val DEFAULT_MAX_IDLE_MS = DEFAULT_MAX_IDLE_DAYS * 24 * 60 * 60 * 1000L

        /** Default token validity: 60 days */
        const val DEFAULT_TOKEN_VALIDITY_DAYS = 60L
    }
}
