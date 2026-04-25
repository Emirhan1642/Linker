package com.linker.app.core.security

import android.os.Build
import java.io.File

/**
 * Root detection utility for enhanced security
 * 
 * SECURITY: Rooted devices can bypass app security measures.
 * This detector helps identify potentially compromised devices.
 * 
 * NOTE: This is not foolproof - determined attackers can bypass detection.
 * Use as one layer of defense-in-depth strategy.
 */
object RootDetector {

    /**
     * Check if device is rooted
     * 
     * Performs multiple checks:
     * 1. Check for su binary
     * 2. Check for common root management apps
     * 3. Check for dangerous system properties
     * 4. Check for test-keys build
     * 
     * @return true if device appears to be rooted
     */
    fun isDeviceRooted(): Boolean {
        return checkForSuBinary() ||
               checkForRootApps() ||
               checkForDangerousProps() ||
               checkForTestKeys()
    }

    /**
     * Check for su binary in common locations
     */
    private fun checkForSuBinary(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su"
        )
        
        return paths.any { path ->
            try {
                File(path).exists()
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * Check for common root management apps
     */
    private fun checkForRootApps(): Boolean {
        val rootApps = arrayOf(
            "com.noshufou.android.su",
            "com.noshufou.android.su.elite",
            "eu.chainfire.supersu",
            "com.koushikdutta.superuser",
            "com.thirdparty.superuser",
            "com.yellowes.su",
            "com.topjohnwu.magisk"
        )
        
        return rootApps.any { packageName ->
            try {
                // Check if package exists
                File("/data/data/$packageName").exists()
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * Check for dangerous system properties
     */
    private fun checkForDangerousProps(): Boolean {
        return try {
            val buildTags = Build.TAGS
            buildTags != null && buildTags.contains("test-keys")
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if build is signed with test keys
     */
    private fun checkForTestKeys(): Boolean {
        val buildTags = Build.TAGS
        return buildTags != null && buildTags.contains("test-keys")
    }

    /**
     * Check if device is running in emulator
     * 
     * Emulators can be used for reverse engineering
     */
    fun isEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic") ||
                Build.FINGERPRINT.startsWith("unknown") ||
                Build.MODEL.contains("google_sdk") ||
                Build.MODEL.contains("Emulator") ||
                Build.MODEL.contains("Android SDK built for x86") ||
                Build.MANUFACTURER.contains("Genymotion") ||
                Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic") ||
                "google_sdk" == Build.PRODUCT)
    }

    /**
     * Get security risk level
     * 
     * @return SecurityRiskLevel enum
     */
    fun getSecurityRiskLevel(): SecurityRiskLevel {
        val isRooted = isDeviceRooted()
        val isEmulator = isEmulator()
        
        return when {
            isRooted && isEmulator -> SecurityRiskLevel.CRITICAL
            isRooted -> SecurityRiskLevel.HIGH
            isEmulator -> SecurityRiskLevel.MEDIUM
            else -> SecurityRiskLevel.LOW
        }
    }
}

/**
 * Security risk levels
 */
enum class SecurityRiskLevel {
    LOW,      // Normal device
    MEDIUM,   // Emulator
    HIGH,     // Rooted device
    CRITICAL  // Rooted emulator
}
