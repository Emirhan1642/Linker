package com.linker.app.domain.repository

import com.linker.app.domain.model.User
import com.linker.app.core.util.Result
import kotlinx.coroutines.flow.Flow

interface AuthRepository {

    /** Emits the currently signed-in user, or null when signed out. */
    fun observeCurrentUser(): Flow<User?>

    /** Returns the current user synchronously (nullable). */
    suspend fun getCurrentUser(): User?

    /** Signs in with a Google credential from the One Tap / Credential Manager flow. */
    suspend fun signInWithGoogle(idToken: String): Result<User>

    /** Signs in with email and password. */
    suspend fun signInWithEmail(email: String, password: String): Result<User>

    /** Creates a new account with email and password. */
    suspend fun createAccountWithEmail(
        email: String,
        password: String
    ): Result<User>

    /** Sends an OTP to [phoneNumber] (E.164 format). Returns the verification ID. */
    suspend fun sendPhoneOtp(phoneNumber: String): Result<String>

    /** Verifies the OTP and signs in. */
    suspend fun signInWithPhoneOtp(verificationId: String, otp: String): Result<User>

    /** Sends a password-reset e-mail. */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit>

    /** Completes profile setup for a newly created account. */
    suspend fun completeProfileSetup(
        userId: String,
        username: String,
        displayName: String,
        profileImageLocalPath: String?
    ): Result<User>

    /** Signs the current user out. */
    suspend fun signOut(): Result<Unit>

    /** Deletes the current account permanently. */
    suspend fun deleteAccount(): Result<Unit>

    /** Returns true if the user is currently authenticated. */
    suspend fun isAuthenticated(): Boolean
}
