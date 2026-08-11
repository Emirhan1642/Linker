package com.linker.app.core.security

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.linker.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import okhttp3.CertificatePinner
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Certificate pin configuration
 * 
 * IMPORTANT: Update these pins regularly!
 * Check certificate expiration: https://crt.sh
 * 
 * Last updated: 2026-05-24
 * Next review: 2026-11-24 (6 months)
 */
object CertificatePins {
    // Google Trust Services (GTS) Root R1
    const val GTS_ROOT_R1 = "sha256/hxqRlPTu1bMS/0DITB1SSu0vd4u/8l8TjPgfaAp63Gc="
    
    // Google Trust Services (GTS) Root R2 (backup)
    const val GTS_ROOT_R2 = "sha256/f8NnEFh3BqcHPcJqIKvnT8K8YWVnKvWWXvRvBJvqCCk="
    
    // Google Trust Services (GTS) Root R4 (new)
    const val GTS_ROOT_R4 = "sha256/mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c="
    
    // Google Trust Services WE1 (Intermediate)
    const val GTS_WE1 = "sha256/kIdp6NNEd8wsugYyyIYFsi1ylMCED3hZbSR8ZFsa/A4="
    
    // GlobalSign Root CA - R2 (backup)
    const val GLOBALSIGN_ROOT_R2 = "sha256/r/mIkG3eEpVdm+u/ko/cwxzOMo1bk4TyHIlByibiA5E="
    
    // Supabase (Let's Encrypt / Cloudflare Edge)
    const val SUPABASE_ROOT_CA = "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M=" // Let's Encrypt ISRG Root X1
    const val SUPABASE_BACKUP_CA = "sha256/jQJTbIh0grw0/1TkHSumWb+Fs0Ggogr621gT3PvPKG0=" // ISRG Root X1 Backup
    const val SUPABASE_EDGE_CERT = "sha256/p51goejPCgGH+Oog/MU2k6PObcEfTrrr73jUcuWJ7w0=" // Supabase Leaf/Edge
    
    // Cloudinary
    const val CLOUDINARY_ROOT_CA = "sha256/Y9mvm0exBk1JoQ57f9Vm28jKo5lFm/woKcVxrYxu80o=" 
    const val CLOUDINARY_BACKUP_CA = "sha256/jQJTbIh0grw0/1TkHSumWb+Fs0Ggogr621gT3PvPKG0=" 
}

class CertificatePinMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val pinExpirationDates = mapOf(
        CertificatePins.GTS_ROOT_R1 to 2099836800000L, // 2036-06-22
        CertificatePins.GTS_ROOT_R2 to 2099836800000L, // 2036-06-22
        CertificatePins.GTS_ROOT_R4 to 2099836800000L, // 2036-06-22
        CertificatePins.GTS_WE1 to 2099836800000L, // 2036-06-22
        CertificatePins.GLOBALSIGN_ROOT_R2 to 1832889600000L, // 2028-01-28
        CertificatePins.SUPABASE_ROOT_CA to 1969065600000L, // 2032-05-31
        CertificatePins.CLOUDINARY_ROOT_CA to 1969065600000L 
    )
    
    fun checkPinExpiration() {
        val currentTime = System.currentTimeMillis()
        val warningThreshold = 90 * 24 * 60 * 60 * 1000L // 90 days
        
        pinExpirationDates.forEach { (pin, expirationDate) ->
            val timeUntilExpiration = expirationDate - currentTime
            
            when {
                timeUntilExpiration < 0 -> {
                    val error = "Certificate pin EXPIRED: $pin"
                    Log.e("CertPinMonitor", error)
                    FirebaseAnalytics.getInstance(context).logEvent("cert_pin_expired", Bundle().apply {
                        putString("pin", pin.take(20))
                    })
                }
                timeUntilExpiration < warningThreshold -> {
                    val daysRemaining = timeUntilExpiration / (24 * 60 * 60 * 1000L)
                    Log.w("CertPinMonitor", "Certificate pin expiring in $daysRemaining days: $pin")
                    FirebaseAnalytics.getInstance(context).logEvent("cert_pin_expiring_soon", Bundle().apply {
                        putString("pin", pin.take(20))
                        putLong("days_remaining", daysRemaining)
                    })
                }
            }
        }
    }
}

@Singleton
class CertificatePinningConfig @Inject constructor(
    private val remoteConfig: FirebaseRemoteConfig,
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = run {
        val oldPolicy = android.os.StrictMode.allowThreadDiskReads()
        try {
            context.getSharedPreferences("cert_pinning_prefs", Context.MODE_PRIVATE)
        } finally {
            android.os.StrictMode.setThreadPolicy(oldPolicy)
        }
    }
    private val PREF_PINNING_ENABLED = "cert_pinning_enabled"
    private val PREF_PINNING_FAILURES = "cert_pinning_failures"
    private val MAX_FAILURES_BEFORE_DISABLE = 3
    
    suspend fun isPinningEnabled(): Boolean {
        try {
            remoteConfig.fetchAndActivate().await()
        } catch (e: Exception) {
            // Ignore fetch failure and proceed with local
        }
        val remoteEnabled = remoteConfig.getBoolean("cert_pinning_enabled")
        
        if (!remoteEnabled && remoteConfig.all.containsKey("cert_pinning_enabled")) {
            Log.w("CertPinConfig", "Certificate pinning disabled via remote config")
            return false
        }
        
        val failures = prefs.getInt(PREF_PINNING_FAILURES, 0)
        if (failures >= MAX_FAILURES_BEFORE_DISABLE) {
            Log.w("CertPinConfig", "Certificate pinning disabled due to repeated failures")
            return false
        }
        
        return prefs.getBoolean(PREF_PINNING_ENABLED, true)
    }
    
    fun recordPinningFailure() {
        val failures = prefs.getInt(PREF_PINNING_FAILURES, 0) + 1
        prefs.edit().putInt(PREF_PINNING_FAILURES, failures).apply()
        
        if (failures >= MAX_FAILURES_BEFORE_DISABLE) {
            Log.e("CertPinConfig", "Too many pinning failures, disabling certificate pinning")
            prefs.edit().putBoolean(PREF_PINNING_ENABLED, false).apply()
        }
    }
    
    fun resetFailures() {
        prefs.edit().putInt(PREF_PINNING_FAILURES, 0).apply()
    }
}

@Singleton
class CertificatePinningInterceptor @Inject constructor(
    private val pinningConfig: CertificatePinningConfig,
    @ApplicationContext private val context: Context
) : Interceptor {
    
    companion object {
        private const val TAG = "CertificatePinning"
        private var testMode = false
        
        @VisibleForTesting
        fun enableTestMode() {
            if (!BuildConfig.DEBUG) {
                throw IllegalStateException("Test mode can only be enabled in debug builds")
            }
            testMode = true
            Log.w(TAG, "Certificate pinning TEST MODE enabled - pinning disabled!")
        }
        
        @VisibleForTesting
        fun disableTestMode() {
            testMode = false
        }
        
        @Volatile
        private var cachedPinner: CertificatePinner? = null
        
        fun createCertificatePinner(): CertificatePinner? {
            if (testMode) {
                Log.w(TAG, "Test mode active, returning null pinner")
                return null
            }
            
            return cachedPinner ?: synchronized(this) {
                cachedPinner ?: buildCertificatePinner().also { cachedPinner = it }
            }
        }
        
        private fun buildCertificatePinner(): CertificatePinner {
            return CertificatePinner.Builder()
                .add("*.googleapis.com", CertificatePins.GTS_ROOT_R1, CertificatePins.GTS_ROOT_R2, CertificatePins.GTS_ROOT_R4, CertificatePins.GLOBALSIGN_ROOT_R2)
                .add("*.google.com", CertificatePins.GTS_ROOT_R1, CertificatePins.GTS_ROOT_R2, CertificatePins.GTS_ROOT_R4)
                .add("firestore.googleapis.com", CertificatePins.GTS_ROOT_R1, CertificatePins.GTS_ROOT_R2, CertificatePins.GTS_ROOT_R4)
                .add("*.supabase.co", CertificatePins.SUPABASE_ROOT_CA, CertificatePins.SUPABASE_BACKUP_CA, CertificatePins.SUPABASE_EDGE_CERT, CertificatePins.GTS_ROOT_R1, CertificatePins.GTS_ROOT_R2, CertificatePins.GTS_ROOT_R4, CertificatePins.GTS_WE1, CertificatePins.GLOBALSIGN_ROOT_R2)
                .add("*.cloudinary.com", CertificatePins.CLOUDINARY_ROOT_CA, CertificatePins.CLOUDINARY_BACKUP_CA)
                .add("res.cloudinary.com", CertificatePins.CLOUDINARY_ROOT_CA, CertificatePins.CLOUDINARY_BACKUP_CA)
                .build()
        }
    }
    
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        
        val pinningEnabled = runBlocking { pinningConfig.isPinningEnabled() }
        if (!pinningEnabled || testMode) {
            return chain.proceed(request)
        }
        
        return try {
            val response = chain.proceed(request)
            
            if (BuildConfig.DEBUG && shouldPin(request.url.host)) {
                SecurityLogger.logDebug("Certificate pinning validated for ${request.url.host}")
            }
            
            response
            
        } catch (e: IOException) {
            if (e.message?.contains("Certificate pinning failure") == true) {
                handlePinningFailure(request.url.host, e)
                pinningConfig.recordPinningFailure()
                throw SecurityException("Certificate pinning failed for ${request.url.host}", e)
            }
            throw e
        }
    }
    
    private fun handlePinningFailure(host: String, error: IOException) {
        Timber.e("Certificate pinning FAILED for $host: ${error.message}")
        
        SecurityLogger.logEvent(
            SecurityLogger.EventType.SECURITY_CHECK_FAILED,
            "Certificate pinning failure detected",
            metadata = mapOf("host" to host, "error" to (error.message ?: "unknown"))
        )
        
        try {

            FirebaseAnalytics.getInstance(context).logEvent("cert_pinning_failure", Bundle().apply {
                putString("host", host)
                putString("error", error.message)
            })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to report pinning failure", e)
        }
    }
    
    private fun shouldPin(host: String): Boolean {
        return host.endsWith(".googleapis.com") ||
                host.endsWith(".google.com") ||
                host == "firestore.googleapis.com" ||
                host.endsWith(".supabase.co") ||
                host.endsWith(".cloudinary.com") ||
                host == "res.cloudinary.com"
    }
}

fun OkHttpClient.Builder.withCertificatePinning(
    interceptor: CertificatePinningInterceptor
): OkHttpClient.Builder {
    CertificatePinningInterceptor.createCertificatePinner()?.let { pinner ->
        this.certificatePinner(pinner)
        this.addInterceptor(interceptor)
    }
    return this
}
