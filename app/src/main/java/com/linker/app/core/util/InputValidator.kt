package com.linker.app.core.util

import android.util.Patterns

/**
 * Input validation utilities for security and data integrity
 * 
 * SECURITY: All user inputs should be validated before processing
 * to prevent injection attacks, XSS, and data corruption.
 */
object InputValidator {

    // ── Email Validation ────────────────────────────────────────────────────
    
    /**
     * Validate email format
     * 
     * @param email Email address to validate
     * @return true if valid email format
     */
    fun isValidEmail(email: String): Boolean {
        return email.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    // ── Password Validation ─────────────────────────────────────────────────
    
    /**
     * Validate password strength
     * 
     * Requirements:
     * - Minimum 8 characters
     * - At least one uppercase letter
     * - At least one lowercase letter
     * - At least one digit
     * 
     * @param password Password to validate
     * @return ValidationResult with success status and error message
     */
    fun validatePassword(password: String): ValidationResult {
        return when {
            password.length < 8 -> 
                ValidationResult(false, "Password must be at least 8 characters")
            !password.any { it.isUpperCase() } -> 
                ValidationResult(false, "Password must contain at least one uppercase letter")
            !password.any { it.isLowerCase() } -> 
                ValidationResult(false, "Password must contain at least one lowercase letter")
            !password.any { it.isDigit() } -> 
                ValidationResult(false, "Password must contain at least one digit")
            else -> 
                ValidationResult(true, "Password is valid")
        }
    }

    // ── Username Validation ─────────────────────────────────────────────────
    
    /**
     * Validate username format
     * 
     * Requirements:
     * - 3-30 characters
     * - Only alphanumeric, underscore, and dot
     * - Cannot start or end with dot
     * - Cannot have consecutive dots
     * 
     * @param username Username to validate
     * @return ValidationResult with success status and error message
     */
    fun validateUsername(username: String): ValidationResult {
        val trimmed = username.trim()
        return when {
            trimmed.length < 3 -> 
                ValidationResult(false, "Username must be at least 3 characters")
            trimmed.length > 30 -> 
                ValidationResult(false, "Username must be at most 30 characters")
            !trimmed.matches(Regex("^[a-zA-Z0-9_.]+$")) -> 
                ValidationResult(false, "Username can only contain letters, numbers, underscore, and dot")
            trimmed.startsWith(".") || trimmed.endsWith(".") -> 
                ValidationResult(false, "Username cannot start or end with a dot")
            trimmed.contains("..") -> 
                ValidationResult(false, "Username cannot have consecutive dots")
            else -> 
                ValidationResult(true, "Username is valid")
        }
    }

    // ── Phone Number Validation ─────────────────────────────────────────────
    
    /**
     * Validate phone number format
     * 
     * Accepts international format with country code
     * Example: +905551234567
     * 
     * @param phoneNumber Phone number to validate
     * @return true if valid phone format
     */
    fun isValidPhoneNumber(phoneNumber: String): Boolean {
        val cleaned = phoneNumber.replace(Regex("[\\s-()]"), "")
        return cleaned.matches(Regex("^\\+?[1-9]\\d{1,14}$"))
    }

    // ── Text Content Validation ────────────────────────────────────────────
    
    /**
     * Sanitize text content to prevent XSS and injection attacks
     * 
     * Removes potentially dangerous characters and patterns
     * 
     * @param text Text to sanitize
     * @return Sanitized text
     */
    fun sanitizeText(text: String): String {
        return text
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;")
            .replace("/", "&#x2F;")
            .trim()
    }

    /**
     * Validate message content length
     * 
     * @param content Message content
     * @param maxLength Maximum allowed length (default 5000)
     * @return ValidationResult
     */
    fun validateMessageContent(content: String, maxLength: Int = 5000): ValidationResult {
        return when {
            content.isBlank() -> 
                ValidationResult(false, "Message cannot be empty")
            content.length > maxLength -> 
                ValidationResult(false, "Message is too long (max $maxLength characters)")
            else -> 
                ValidationResult(true, "Message is valid")
        }
    }

    // ── URL Validation ──────────────────────────────────────────────────────
    
    /**
     * Validate URL format
     * 
     * @param url URL to validate
     * @return true if valid URL format
     */
    fun isValidUrl(url: String): Boolean {
        return url.isNotBlank() && Patterns.WEB_URL.matcher(url).matches()
    }

    /**
     * Check if URL is from allowed domain
     * 
     * @param url URL to check
     * @param allowedDomains List of allowed domains
     * @return true if URL is from allowed domain
     */
    fun isAllowedDomain(url: String, allowedDomains: List<String>): Boolean {
        return try {
            val domain = java.net.URL(url).host
            allowedDomains.any { domain.endsWith(it) }
        } catch (e: Exception) {
            false
        }
    }

    // ── File Validation ─────────────────────────────────────────────────────
    
    /**
     * Validate file size
     * 
     * @param sizeInBytes File size in bytes
     * @param maxSizeInMB Maximum allowed size in MB
     * @return ValidationResult
     */
    fun validateFileSize(sizeInBytes: Long, maxSizeInMB: Int): ValidationResult {
        val maxSizeInBytes = maxSizeInMB * 1024 * 1024L
        return if (sizeInBytes <= maxSizeInBytes) {
            ValidationResult(true, "File size is valid")
        } else {
            ValidationResult(false, "File size exceeds $maxSizeInMB MB limit")
        }
    }

    /**
     * Validate file extension
     * 
     * @param fileName File name
     * @param allowedExtensions List of allowed extensions (e.g., ["jpg", "png"])
     * @return ValidationResult
     */
    fun validateFileExtension(fileName: String, allowedExtensions: List<String>): ValidationResult {
        val extension = fileName.substringAfterLast(".", "").lowercase()
        return if (extension in allowedExtensions) {
            ValidationResult(true, "File type is valid")
        } else {
            ValidationResult(false, "File type not allowed. Allowed: ${allowedExtensions.joinToString()}")
        }
    }
}

/**
 * Validation result data class
 * 
 * @property isValid Whether validation passed
 * @property message Validation message (error or success)
 */
data class ValidationResult(
    val isValid: Boolean,
    val message: String
)
