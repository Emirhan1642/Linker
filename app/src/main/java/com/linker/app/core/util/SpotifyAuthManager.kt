package com.linker.app.core.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.linker.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Spotify OAuth 2.0 Authorization Code + PKCE flow.
 *
 * - Token is persisted in SharedPreferences with an expiry timestamp.
 *   App restarts won't require re-login until the 1-hour token expires.
 * - PKCE flow does NOT require a client_secret — safe for mobile.
 */
@Singleton
class SpotifyAuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val REDIRECT_URI = "linker://spotify-callback"
        private const val AUTH_BASE_URL = "https://accounts.spotify.com/authorize"
        private const val TOKEN_URL = "https://accounts.spotify.com/api/token"
        private const val SCOPES = "user-read-private user-read-email app-remote-control streaming"
        private const val TAG = "SpotifyAuthManager"

        private const val PREFS_NAME = "spotify_auth_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_TOKEN_EXPIRY = "token_expiry_ms"
        // Spotify tokens last 1 hour; refresh 5 min early to avoid edge cases
        private const val TOKEN_LIFETIME_MS = 55 * 60 * 1000L
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var refreshJob: kotlinx.coroutines.Job? = null
    private val okHttpClient = OkHttpClient()

    private fun scheduleRefresh(refreshToken: String, delayMs: Long) {
        refreshJob?.cancel()
        refreshJob = coroutineScope.launch {
            kotlinx.coroutines.delay(delayMs)
            refreshAccessToken(refreshToken)
        }
    }

    /** Temporarily stores the PKCE code verifier between auth start and callback. */
    private var pendingCodeVerifier: String? = null

    private val _isPremium = MutableStateFlow<Boolean?>(null)
    val isPremium: StateFlow<Boolean?> = _isPremium.asStateFlow()

    private val _accessToken = MutableStateFlow<String?>(null)
    val accessToken: StateFlow<String?> = _accessToken.asStateFlow()

    init {
        // Restore token from prefs on startup — avoids browser re-auth every launch
        restoreTokenFromPrefs()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Starts the Spotify PKCE login flow by opening the system browser.
     * The result comes back via deep link → MainActivity.onNewIntent() → handleBrowserCallback().
     */
    fun openLoginInBrowser(activity: Activity, clientId: String = BuildConfig.SPOTIFY_CLIENT_ID) {
        val verifier = generateCodeVerifier()
        val challenge = generateCodeChallenge(verifier)
        pendingCodeVerifier = verifier

        val authUri = Uri.parse(AUTH_BASE_URL).buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("scope", SCOPES)
            .appendQueryParameter("show_dialog", "false")
            .build()

        Log.d(TAG, "Opening browser for PKCE auth.")
        val browserIntent = Intent(Intent.ACTION_VIEW, authUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
        }
        try {
            activity.startActivity(browserIntent)
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "No browser found to handle Spotify auth intent", e)
            _isPremium.value = false
        }
    }

    /**
     * Handles the deep link callback from the browser auth flow.
     * Call this from MainActivity.onNewIntent() when intent data scheme is "linker".
     */
    fun handleBrowserCallback(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "linker" || uri.host != "spotify-callback") return

        val error = uri.getQueryParameter("error")
        if (error != null) {
            Log.e(TAG, "Browser auth error: $error")
            _isPremium.value = false
            return
        }

        val code = uri.getQueryParameter("code")
        val verifier = pendingCodeVerifier
        pendingCodeVerifier = null // consume immediately — one-time use

        if (code == null || verifier == null) {
            Log.e(TAG, "Missing code or verifier. code=$code, hasVerifier=${verifier != null}")
            _isPremium.value = false
            return
        }

        Log.d(TAG, "Auth code received. Starting PKCE token exchange...")
        coroutineScope.launch {
            exchangeCodeForToken(code, verifier, BuildConfig.SPOTIFY_CLIENT_ID)
        }
    }

    fun clearToken() {
        _accessToken.value = null
        _isPremium.value = null
        pendingCodeVerifier = null
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_TOKEN_EXPIRY)
            .apply()
        Log.d(TAG, "Token cleared from memory and prefs.")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Token Persistence
    // ─────────────────────────────────────────────────────────────────────────

    private fun restoreTokenFromPrefs() {
        val savedToken = prefs.getString(KEY_ACCESS_TOKEN, null)
        val savedRefreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)
        val expiryMs = prefs.getLong(KEY_TOKEN_EXPIRY, 0L)

        if (savedToken != null && System.currentTimeMillis() < expiryMs) {
            val remainingMs = expiryMs - System.currentTimeMillis()
            Log.d(TAG, "Restored valid token from prefs (expires in ${remainingMs / 1000}s).")
            _accessToken.value = savedToken
            // Fetch premium status from /v1/me
            coroutineScope.launch { fetchUserProfile(savedToken) }
            
            // Schedule a refresh for when this token expires
            if (savedRefreshToken != null) {
                scheduleRefresh(savedRefreshToken, remainingMs)
            }
        } else if (savedRefreshToken != null) {
            Log.d(TAG, "Stored token expired, but refresh token exists. Refreshing now...")
            coroutineScope.launch {
                refreshAccessToken(savedRefreshToken)
            }
        } else {
            Log.d(TAG, "Stored token expired and no refresh token — clearing prefs.")
            clearToken()
        }
    }

    private fun persistToken(token: String, refreshToken: String?) {
        val expiryMs = System.currentTimeMillis() + TOKEN_LIFETIME_MS
        val editor = prefs.edit()
            .putString(KEY_ACCESS_TOKEN, token)
            .putLong(KEY_TOKEN_EXPIRY, expiryMs)
            
        if (refreshToken != null) {
            editor.putString(KEY_REFRESH_TOKEN, refreshToken)
        }
        
        editor.apply()
        Log.d(TAG, "Token persisted to prefs.")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PKCE Token Exchange
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun exchangeCodeForToken(code: String, verifier: String, clientId: String) {
        withContext(Dispatchers.IO) {
            try {
                val body = FormBody.Builder()
                    .add("grant_type", "authorization_code")
                    .add("code", code)
                    .add("redirect_uri", REDIRECT_URI)
                    .add("client_id", clientId)
                    .add("code_verifier", verifier)
                    .build()

                val request = Request.Builder()
                    .url(TOKEN_URL)
                    .post(body)
                    .build()

                val response = okHttpClient.newCall(request).execute()
                val responseBody = response.body?.string()

                if (response.isSuccessful && responseBody != null) {
                    val json = JSONObject(responseBody)
                    val token = json.getString("access_token")
                    val refreshToken = json.optString("refresh_token", null)
                    Log.d(TAG, "PKCE token exchange successful.")
                    _accessToken.value = token
                    persistToken(token, refreshToken)
                    fetchUserProfile(token)
                    
                    // Schedule next refresh
                    if (refreshToken != null) {
                        coroutineScope.launch {
                            kotlinx.coroutines.delay(TOKEN_LIFETIME_MS)
                            refreshAccessToken(refreshToken)
                        }
                    }
                } else {
                    Log.e(TAG, "Token exchange failed: HTTP ${response.code} — $responseBody")
                    _isPremium.value = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Token exchange exception", e)
                _isPremium.value = false
            }
        }
    }

    private suspend fun refreshAccessToken(refreshToken: String, clientId: String = BuildConfig.SPOTIFY_CLIENT_ID) {
        withContext(Dispatchers.IO) {
            try {
                val body = FormBody.Builder()
                    .add("grant_type", "refresh_token")
                    .add("refresh_token", refreshToken)
                    .add("client_id", clientId)
                    .build()

                val request = Request.Builder()
                    .url(TOKEN_URL)
                    .post(body)
                    .build()

                val response = okHttpClient.newCall(request).execute()
                val responseBody = response.body?.string()

                if (response.isSuccessful && responseBody != null) {
                    val json = JSONObject(responseBody)
                    val token = json.getString("access_token")
                    val newRefreshToken = json.optString("refresh_token", refreshToken)
                    Log.d(TAG, "PKCE token refresh successful.")
                    _accessToken.value = token
                    persistToken(token, newRefreshToken)
                    fetchUserProfile(token)
                    
                    // Schedule next refresh
                    scheduleRefresh(newRefreshToken, TOKEN_LIFETIME_MS)
                } else {
                    Log.e(TAG, "Token refresh failed: HTTP ${response.code} — $responseBody")
                    clearToken()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Token refresh exception", e)
                clearToken()
            }
        }
    }

    private suspend fun fetchUserProfile(token: String) {
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("https://api.spotify.com/v1/me")
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()

                val response = okHttpClient.newCall(request).execute()
                val responseBody = response.body?.string()

                if (response.isSuccessful && responseBody != null) {
                    val json = JSONObject(responseBody)
                    val product = json.optString("product", "free")
                    _isPremium.value = (product == "premium")
                    Log.d(TAG, "Fetched user profile. isPremium: ${_isPremium.value}")
                } else {
                    Log.e(TAG, "Failed to fetch user profile: HTTP ${response.code}")
                    // Default to false if we can't verify
                    _isPremium.value = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception fetching user profile", e)
                _isPremium.value = false
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PKCE Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Generates a cryptographically random code verifier (RFC 7636).
     * Length: 128 chars from the unreserved character set.
     */
    private fun generateCodeVerifier(): String {
        val allowed = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
        val rng = SecureRandom()
        return (1..128).map { allowed[rng.nextInt(allowed.length)] }.joinToString("")
    }

    /**
     * Derives the PKCE code challenge from the verifier.
     * Challenge = BASE64URL(SHA-256(ASCII(verifier))), no padding.
     */
    private fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}
