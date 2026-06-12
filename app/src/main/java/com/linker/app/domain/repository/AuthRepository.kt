package com.linker.app.domain.repository

import com.linker.app.domain.model.User
import com.linker.app.core.util.Result
import kotlinx.coroutines.flow.Flow

/**
 * Repository for authentication operations.
 */
interface AuthRepository {

    /** 
     * Emits the currently signed-in user, or null when signed out.
     * Note: Use distinctUntilChanged() to optimize reactive queries.
     */
    fun observeCurrentUser(): Flow<User?>

    /** 
     * Returns the current user synchronously. 
     */
    suspend fun getCurrentUser(): Result<User?>

    /** 
     * Signs in with a Google credential from the One Tap / Credential Manager flow. 
     * 
     * @param idToken The Google ID token. Passed as CharArray to minimize memory exposure.
     */
    suspend fun signInWithGoogle(idToken: String): Result<User>

    /** 
     * Signs in with email and password. 
     * 
     * @param email The user's email address.
     * @param password The user's password.
     */
    suspend fun signInWithEmail(email: String, password: String): Result<User>

    /** 
     * Creates a new account with email and password. 
     * 
     * Password Policy:
     * - Minimum 8 characters
     * - Requires at least one uppercase letter
     * - Requires at least one lowercase letter
     * - Requires at least one digit
     * - Requires at least one special character
     * 
     * Note: An email verification will be sent after successful creation.
     * 
     * @param email The user's email address.
     * @param password The new password.
     */
    suspend fun createAccountWithEmail(
        email: String,
        password: String
    ): Result<User>

    /** 
     * Sends an OTP to [phoneNumber].
     * 
     * @param phoneNumber Must be in E.164 format (e.g., +1234567890).
     * @return The verification ID.
     */
    suspend fun sendPhoneOtp(phoneNumber: String): Result<String>

    /** Verifies the OTP and signs in. */
    suspend fun signInWithPhoneOtp(verificationId: String, otp: String): Result<User>

    /** Sends a password-reset e-mail. */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit>

    /** Signs the current user out. */
    suspend fun signOut(): Result<Unit>

    /** 
     * Deletes the current account permanently. 
     * 
     * Consequences:
     * - All user data (profile, messages, media) will be permanently deleted.
     * - The username will be released.
     * - Cannot be undone.
     */
    suspend fun deleteAccount(): Result<Unit>

    /** Returns true if the user is currently authenticated. */
    suspend fun isAuthenticated(): Boolean

    /** Completes profile setup after signup. */
    suspend fun completeProfileSetup(
        userId: String,
        username: String,
        displayName: String,
        profileImageLocalPath: String?
    ): Result<User>
}
