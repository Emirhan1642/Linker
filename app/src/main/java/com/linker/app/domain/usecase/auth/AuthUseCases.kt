package com.linker.app.domain.usecase.auth

import com.linker.app.domain.model.User
import com.linker.app.domain.repository.AuthRepository
import com.linker.app.domain.repository.UserRepository
import com.linker.app.core.util.Result
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveAuthStateUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    operator fun invoke(): Flow<User?> = authRepository.observeCurrentUser()
}

class SignInWithGoogleUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(idToken: String): Result<User> {
        if (idToken.isBlank()) return Result.Error("ID token cannot be empty")
        return authRepository.signInWithGoogle(idToken)
    }
}

class SignInWithEmailUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(email: String, password: String): Result<User> {
        if (email.isBlank()) 
            return Result.Error("Email cannot be empty")
        if (password.isEmpty()) 
            return Result.Error("Password cannot be empty")
        
        return authRepository.signInWithEmail(email, password)
    }
}

class CreateAccountWithEmailUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(email: String, password: String): Result<User> {
        if (email.isBlank()) 
            return Result.Error("Email cannot be empty")
        if (!isValidEmail(email)) 
            return Result.Error("Invalid email format")
        
        val passwordValidation = validatePasswordStrength(password)
        if (!passwordValidation.isValid) 
            return Result.Error(passwordValidation.errorMessage)
        
        return authRepository.createAccountWithEmail(email, password)
    }
    
    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
    
    private fun validatePasswordStrength(password: String): PasswordValidation {
        if (password.length < 8) return PasswordValidation(false, "Password must be at least 8 characters")
        if (password.length > 128) return PasswordValidation(false, "Password must be 128 characters or fewer")
        if (!password.any { it.isUpperCase() }) return PasswordValidation(false, "Password must contain at least one uppercase letter")
        if (!password.any { it.isLowerCase() }) return PasswordValidation(false, "Password must contain at least one lowercase letter")
        if (!password.any { it.isDigit() }) return PasswordValidation(false, "Password must contain at least one digit")
        if (!password.any { it in "!@#\$%^&*()_+-=[]{}|;:,.<>?" }) return PasswordValidation(false, "Password must contain at least one special character")
        
        val commonPasswords = setOf("password", "12345678", "qwerty123", "abc123456")
        if (commonPasswords.contains(password.lowercase())) {
            return PasswordValidation(false, "Password is too common")
        }
        return PasswordValidation(true, "")
    }
    
    private data class PasswordValidation(val isValid: Boolean, val errorMessage: String)
}

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

class SendPasswordResetEmailUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(email: String): Result<Unit> {
        if (email.isBlank()) return Result.Error("Email cannot be empty")
        return authRepository.sendPasswordResetEmail(email)
    }
}

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
        if (username.length < 3)  return Result.Error("Username must be at least 3 characters")
        if (username.length > 30) return Result.Error("Username must be 30 characters or fewer")
        if (!username.matches(Regex("^[a-zA-Z0-9._]+\$")))
            return Result.Error("Username can only contain letters, numbers, dots, and underscores")

        val available = userRepository.isUsernameAvailable(username)
        if (available is Result.Success && !available.data)
            return Result.Error("Username '$username' is already taken")

        return authRepository.completeProfileSetup(
            userId, username, displayName, profileImageLocalPath
        )
    }
}

class SignOutUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(): Result<Unit> = authRepository.signOut()
}
