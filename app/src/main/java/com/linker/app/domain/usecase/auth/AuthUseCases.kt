package com.linker.app.domain.usecase.auth

import com.linker.app.domain.model.User
import com.linker.app.domain.repository.AuthRepository
import com.linker.app.domain.repository.UserRepository
import com.linker.app.core.util.Result
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

// ─── Observe Auth State ────────────────────────────────────────────────────────

class ObserveAuthStateUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    operator fun invoke(): Flow<User?> = authRepository.observeCurrentUser()
}

// ─── Sign In with Google ───────────────────────────────────────────────────────

class SignInWithGoogleUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(idToken: String): Result<User> =
        authRepository.signInWithGoogle(idToken)
}

// ─── Sign In with Email ────────────────────────────────────────────────────────

class SignInWithEmailUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(email: String, password: String): Result<User> {
        if (email.isBlank() || password.isBlank())
            return Result.Error("Email and password cannot be empty")
        return authRepository.signInWithEmail(email, password)
    }
}

// ─── Create Account with Email ─────────────────────────────────────────────────

class CreateAccountWithEmailUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(email: String, password: String): Result<User> {
        if (email.isBlank()) return Result.Error("Email cannot be empty")
        if (password.length < 8) return Result.Error("Password must be at least 8 characters")
        return authRepository.createAccountWithEmail(email, password)
    }
}

// ─── Phone OTP ─────────────────────────────────────────────────────────────────

class SendPhoneOtpUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(phoneNumber: String): Result<String> {
        if (!phoneNumber.startsWith("+")) return Result.Error("Phone number must include country code")
        return authRepository.sendPhoneOtp(phoneNumber)
    }
}

class VerifyPhoneOtpUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(
        verificationId: String,
        otp: String
    ): Result<User> {
        if (otp.length != 6) return Result.Error("OTP must be 6 digits")
        return authRepository.signInWithPhoneOtp(verificationId, otp)
    }
}

// ─── Password Reset ────────────────────────────────────────────────────────────

class SendPasswordResetEmailUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(email: String): Result<Unit> {
        if (email.isBlank()) return Result.Error("Email cannot be empty")
        return authRepository.sendPasswordResetEmail(email)
    }
}

// ─── Profile Setup ─────────────────────────────────────────────────────────────

class CompleteProfileSetupUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(
        userId: String,
        username: String,
        displayName: String,
        profileImageLocalPath: String?
    ): Result<User> {
        // Basic validation
        if (username.length < 3)  return Result.Error("Username must be at least 3 characters")
        if (username.length > 30) return Result.Error("Username must be 30 characters or fewer")
        if (!username.matches(Regex("^[a-zA-Z0-9._]+$")))
            return Result.Error("Username can only contain letters, numbers, dots, and underscores")

        // Check availability
        val available = userRepository.isUsernameAvailable(username)
        if (available is Result.Success && !available.data)
            return Result.Error("Username '$username' is already taken")

        return authRepository.completeProfileSetup(
            userId, username, displayName, profileImageLocalPath
        )
    }
}

// ─── Sign Out ──────────────────────────────────────────────────────────────────

class SignOutUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(): Result<Unit> = authRepository.signOut()
}
