package com.linker.app.core.security

import android.util.Log
import okhttp3.CertificatePinner
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Certificate pinning interceptor for OkHttp to prevent MITM attacks.
 * 
 * Addresses Issue #40 (P3): Add certificate pinning
 * 
 * Pins certificates for critical services:
 * - Firebase/Firestore (*.googleapis.com)
 * - Google APIs (*.google.com)
 * 
 * Certificate pinning ensures that the app only trusts specific certificates,
 * preventing man-in-the-middle attacks even if a CA is compromised.
 * 
 * Usage:
 * ```
 * val okHttpClient = OkHttpClient.Builder()
 *     .certificatePinner(CertificatePinningInterceptor.createCertificatePinner())
 *     .build()
 * ```
 * 
 * Note: Certificate pins must be updated when certificates are rotated.
 * Monitor certificate expiration and update pins before expiry.
 */
@Singleton
class CertificatePinningInterceptor @Inject constructor() : Interceptor {
    
    companion object {
        private const val TAG = "CertificatePinning"
        
        /**
         * Create a CertificatePinner with pins for Firebase/Google services.
         * 
         * Pins are SHA-256 hashes of the public key (SPKI) of the certificate.
         * 
         * To get certificate pins:
         * ```
         * openssl s_client -connect firestore.googleapis.com:443 | \
         *   openssl x509 -pubkey -noout | \
         *   openssl pkey -pubin -outform der | \
         *   openssl dgst -sha256 -binary | \
         *   openssl enc -base64
         * ```
         * 
         * IMPORTANT: These are example pins. You MUST update them with actual pins
         * for your production environment. Use multiple pins (backup pins) to handle
         * certificate rotation.
         */
        fun createCertificatePinner(): CertificatePinner {
            return CertificatePinner.Builder()
                // Firebase/Firestore (*.googleapis.com)
                // Pin both current and backup certificates
                .add(
                    "*.googleapis.com",
                    // Google Trust Services LLC (GTS) Root CA
                    "sha256/hxqRlPTu1bMS/0DITB1SSu0vd4u/8l8TjPgfaAp63Gc=",
                    // Google Internet Authority G3 (backup)
                    "sha256/f8NnEFh3BqcHPcJqIKvnT8K8YWVnKvWWXvRvBJvqCCk=",
                    // GlobalSign Root CA (backup)
                    "sha256/r/mIkG3eEpVdm+u/ko/cwxzOMo1bk4TyHIlByibiA5E="
                )
                // Google APIs (*.google.com)
                .add(
                    "*.google.com",
                    // Google Trust Services LLC (GTS) Root CA
                    "sha256/hxqRlPTu1bMS/0DITB1SSu0vd4u/8l8TjPgfaAp63Gc=",
                    // Google Internet Authority G3 (backup)
                    "sha256/f8NnEFh3BqcHPcJqIKvnT8K8YWVnKvWWXvRvBJvqCCk="
                )
                // Firestore specific
                .add(
                    "firestore.googleapis.com",
                    // Google Trust Services LLC (GTS) Root CA
                    "sha256/hxqRlPTu1bMS/0DITB1SSu0vd4u/8l8TjPgfaAp63Gc=",
                    // Google Internet Authority G3 (backup)
                    "sha256/f8NnEFh3BqcHPcJqIKvnT8K8YWVnKvWWXvRvBJvqCCk="
                )
                .build()
        }
        
        /**
         * Create an OkHttpClient with certificate pinning enabled.
         * 
         * Use this for all network requests to Firebase/Google services.
         * 
         * @return OkHttpClient with certificate pinning
         */
        fun createSecureOkHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .certificatePinner(createCertificatePinner())
                .addInterceptor(CertificatePinningInterceptor())
                .build()
        }
    }
    
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        
        return try {
            val response = chain.proceed(request)
            
            // Log successful pinning validation
            if (shouldPin(request.url.host)) {
                Log.d(TAG, "Certificate pinning validated for ${request.url.host}")
            }
            
            response
            
        } catch (e: IOException) {
            // Certificate pinning failure
            if (e.message?.contains("Certificate pinning failure") == true) {
                Log.e(TAG, "Certificate pinning FAILED for ${request.url.host}: ${e.message}")
                
                // In production, you might want to:
                // 1. Report to analytics/crash reporting
                // 2. Show user a security warning
                // 3. Prevent the request from proceeding
                
                throw SecurityException("Certificate pinning failed for ${request.url.host}", e)
            }
            
            throw e
        }
    }
    
    /**
     * Check if the host should be pinned.
     * 
     * @param host Hostname
     * @return true if host should be pinned
     */
    private fun shouldPin(host: String): Boolean {
        return host.endsWith(".googleapis.com") ||
                host.endsWith(".google.com") ||
                host == "firestore.googleapis.com"
    }
}

/**
 * Extension function to add certificate pinning to OkHttpClient.Builder.
 * 
 * Usage:
 * ```
 * val client = OkHttpClient.Builder()
 *     .withCertificatePinning()
 *     .build()
 * ```
 */
fun OkHttpClient.Builder.withCertificatePinning(): OkHttpClient.Builder {
    return this
        .certificatePinner(CertificatePinningInterceptor.createCertificatePinner())
        .addInterceptor(CertificatePinningInterceptor())
}
