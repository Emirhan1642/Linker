package com.linker.app.domain.repository

sealed class AuthError(message: String) : Exception(message) {
    class NetworkError(message: String = "Network error. Please check your connection.") : AuthError(message)
    class UserNotFound(message: String = "User not found.") : AuthError(message)
    class InvalidCredentials(message: String = "Invalid email or password.") : AuthError(message)
    class AccountCreationFailed(message: String = "Failed to create account.") : AuthError(message)
    class SignInFailed(provider: String, message: String = "Sign-in with $provider failed.") : AuthError(message)
    class SessionExpired(message: String = "Your session has expired. Please sign in again.") : AuthError(message)
}
