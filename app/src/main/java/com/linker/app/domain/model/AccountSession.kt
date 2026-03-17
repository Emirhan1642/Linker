package com.linker.app.domain.model

/**
 * Represents a stored session for a single Linker account.
 *
 * SECURITY NOTES:
 * - [firebaseUid]   : plain, non-sensitive — used only as a lookup key.
 * - [encryptedToken]: Firebase refresh token, AES-256-GCM encrypted via Android Keystore.
 *                     Never stored or transmitted in plain-text.
 * - [avatarUrl]     : public CDN URL, safe to store unencrypted.
 * - [addedAt]       : epoch ms; used for session age checks.
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
    val requiresAuthOnSwitch: Boolean = false
)
