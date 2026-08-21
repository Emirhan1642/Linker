package com.linker.app.core.security

import android.os.Build
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Root detection utility for enhanced security
 * 
 * SECURITY: Rooted devices can bypass app security measures.
 * This detector helps identify potentially compromised devices.
 * 
 * NOTE: This is not foolproof - determined attackers can bypass detection.
 * Use as one layer of defense-in-depth strategy.
 * 
 * Limitations: 
 * - Cannot detect highly customized kernels or Magisk Hide
 * - Cannot detect hardware level compromises
 * - May produce false positives on custom but non-rooted ROMs
 */
object RootDetector {

    private const val CACHE_DURATION_MS = 60_000L // 1 minute
    
    private var cachedIsRooted: Boolean? = null
    private var lastRootCheckTime = 0L
    
    private var cachedIsEmulator: Boolean? = null
    private var lastEmulatorCheckTime = 0L

    private val SU_PATHS = arrayOf(
        "/data/local/",
        "/data/local/bin/",
        "/data/local/xbin/",
        "/sbin/",
        "/su/bin/",
        "/system/bin/",
        "/system/bin/.ext/",
        "/system/bin/failsafe/",
        "/system/sd/xbin/",
        "/system/usr/we-need-root/",
        "/system/xbin/",
        "/cache/",
        "/data/",
        "/dev/"
    )

    private val KNOWN_ROOT_APPS = arrayOf(
        "com.noshufou.android.su",
        "com.noshufou.android.su.elite",
        "eu.chainfire.supersu",
        "com.koushikdutta.superuser",
        "com.thirdparty.superuser",
        "com.yellowes.su",
        "com.topjohnwu.magisk",
        "com.kingroot.kinguser",
        "com.kingo.root",
        "com.smedialink.oneclickroot",
        "com.zhiqupk.root.global",
        "com.alephzain.framaroot"
    )

    private val KNOWN_DANGEROUS_APPS = arrayOf(
        "com.koushikdutta.rommanager",
        "com.koushikdutta.rommanager.license",
        "com.dimonvideo.luckypatcher",
        "com.chelpus.lackypatch",
        "com.ramdroid.appquarantine",
        "com.ramdroid.appquarantinepro",
        "com.devadvance.rootcloak",
        "com.devadvance.rootcloakplus",
        "de.robv.android.xposed.installer",
        "com.saurik.substrate",
        "com.zachspong.temprootremovejb",
        "com.amphoras.hidemyroot",
        "com.amphoras.hidemyrootadfree",
        "com.formyhm.hiderootPremium",
        "com.formyhm.hideroot"
    )

    fun isDeviceRooted(): Boolean {
        val now = System.currentTimeMillis()
        if (cachedIsRooted != null && now - lastRootCheckTime < CACHE_DURATION_MS) {
            return cachedIsRooted!!
        }

        val rooted = checkForSuBinary() ||
                     checkForRootApps() ||
                     checkForDangerousProps() ||
                     checkSuExists() ||
                     checkForRWPaths()

        if (rooted) {
            SecurityLogger.logRootDetection(SecurityRiskLevel.HIGH)
        }

        cachedIsRooted = rooted
        lastRootCheckTime = now
        return rooted
    }

    private fun checkForSuBinary(): Boolean {
        return SU_PATHS.any { path -> File(path + "su").exists() }
    }
    
    private fun checkForRootApps(): Boolean {
        val allApps = KNOWN_ROOT_APPS + KNOWN_DANGEROUS_APPS
        return allApps.any { packageName ->
            File("/data/data/$packageName").exists() || File("/data/user/0/$packageName").exists()
        }
    }

    private fun checkForDangerousProps(): Boolean {
        val buildTags = Build.TAGS
        return buildTags != null && buildTags.contains("test-keys")
    }

    private fun checkSuExists(): Boolean {
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("/system/xbin/which", "su"))
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                reader.readLine() != null
            }
        } catch (t: Throwable) {
            false
        } finally {
            process?.destroy()
        }
    }
    
    private fun checkForRWPaths(): Boolean {
        val paths = arrayOf(
            "/system",
            "/system/bin",
            "/system/sbin",
            "/system/xbin",
            "/vendor/bin",
            "/sbin",
            "/etc"
        )
        try {
            val process = Runtime.getRuntime().exec("mount")
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val lineStr = line ?: continue
                    val args = lineStr.split(" ")
                    if (args.size < 4) continue
                    val mountPoint = args[1]
                    val mountOptions = args[3]
                    
                    for (path in paths) {
                        if (mountPoint.equals(path, ignoreCase = true)) {
                            val options = mountOptions.split(",")
                            for (option in options) {
                                if (option.equals("rw", ignoreCase = true)) {
                                    return true
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        return false
    }

    fun isEmulator(): Boolean {
        val now = System.currentTimeMillis()
        if (cachedIsEmulator != null && now - lastEmulatorCheckTime < CACHE_DURATION_MS) {
            return cachedIsEmulator!!
        }

        val rating = (if (Build.FINGERPRINT.startsWith("generic") || Build.FINGERPRINT.startsWith("unknown")) 1 else 0) +
            (if (Build.MODEL.contains("google_sdk") || Build.MODEL.contains("Emulator") || Build.MODEL.contains("Android SDK built for x86")) 1 else 0) +
            (if (Build.MANUFACTURER.contains("Genymotion")) 1 else 0) +
            (if (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) 1 else 0) +
            (if ("google_sdk" == Build.PRODUCT) 1 else 0) +
            (if (Build.HARDWARE.contains("goldfish") || Build.HARDWARE.contains("vbox86") || Build.HARDWARE.contains("ranchu")) 1 else 0) +
            (if (Build.BOARD.lowercase().contains("nox") || Build.BOOTLOADER.lowercase().contains("nox")) 1 else 0) +
            (if (Build.HARDWARE.lowercase().contains("nox")) 1 else 0)
            
        val isEm = rating > 2
        
        if (isEm) {
            SecurityLogger.logRootDetection(SecurityRiskLevel.MEDIUM)
        }
        
        cachedIsEmulator = isEm
        lastEmulatorCheckTime = now
        return isEm
    }

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

enum class SecurityRiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
