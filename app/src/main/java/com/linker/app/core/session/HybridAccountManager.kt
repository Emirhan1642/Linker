package com.linker.app.core.session

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.linker.app.core.di.SupabaseNotificationApi
import com.linker.app.core.util.Result
import com.linker.app.domain.repository.AccountRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hybrid Account Manager
 * 
 * Manages multiple account sessions:
 * - Active account: Full-featured session (current system)
 * - Passive accounts: Minimal sessions for notification actions only
 * 
 * This allows users to reply to notifications from any account without switching.
 */
@Singleton
class HybridAccountManager @Inject constructor(
    private val accountRepository: AccountRepository,
    private val supabaseNotificationApi: SupabaseNotificationApi,
    @ApplicationContext private val context: Context
) {
    private val passiveSessions = ConcurrentHashMap<String, MinimalAccountSession>()
    private val TAG = "HybridAccountManager"
    
    companion object {
        private const val MAX_PASSIVE_SESSIONS = 5
    }
    
    /**
     * Get or create a passive session for the given user
     * 
     * @param userId The user ID to create session for
     * @return MinimalAccountSession or null if failed
     */
    suspend fun getOrCreatePassiveSession(userId: String): MinimalAccountSession? {
        // Check if session already exists and not expired
        passiveSessions[userId]?.let { session ->
            if (!session.isExpired()) {
                Log.d(TAG, "Reusing existing passive session for $userId (${passiveSessions.size} total sessions)")
                return session
            } else {
                Log.d(TAG, "Session expired for $userId, removing and recreating")
                removePassiveSession(userId)
            }
        }
        
        // Check session limit
        if (passiveSessions.size >= MAX_PASSIVE_SESSIONS) {
            Log.w(TAG, "Max passive sessions ($MAX_PASSIVE_SESSIONS) reached, cleaning up oldest")
            cleanupOldestSession()
        }
        
        // Create new session
        return try {
            Log.d(TAG, "Creating new passive session for $userId (current sessions: ${passiveSessions.size})")
            val session = createPassiveSession(userId)
            passiveSessions[userId] = session
            Log.d(TAG, "Successfully created passive session for $userId (total sessions: ${passiveSessions.size})")
            session
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create passive session for $userId: ${e.message}", e)
            null
        }
    }
    
    /**
     * Create a new passive session by signing in with stored credentials
     * 
     * This method creates a separate Firebase instance for the passive account
     * without affecting the active account session.
     */
    private suspend fun createPassiveSession(userId: String): MinimalAccountSession {
        // Get Firebase app instance for this user
        val firebaseApp = getOrCreateFirebaseApp(userId)
        val auth = FirebaseAuth.getInstance(firebaseApp)
        val firestore = FirebaseFirestore.getInstance(firebaseApp)
        
        // Get decrypted credentials without switching accounts
        val credentials = accountRepository.getDecryptedCredentials(userId)
            ?: throw IllegalStateException("Failed to get credentials for $userId")
        
        val (email, password) = credentials
        
        try {
            // Sign in to the passive Firebase instance
            auth.signInWithEmailAndPassword(email, password).await()
            Log.d(TAG, "Successfully signed in to passive session for $userId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sign in to passive session for $userId: ${e.message}", e)
            // Clean up Firebase app on failure
            try {
                firebaseApp.delete()
            } catch (deleteError: Exception) {
                Log.w(TAG, "Failed to delete Firebase app after sign-in failure: ${deleteError.message}")
            }
            throw Exception("Failed to authenticate passive session: ${e.message}")
        }
        
        return MinimalAccountSession(
            userId = userId,
            firebaseAuth = auth,
            firestore = firestore
        )
    }
    
    /**
     * Get or create Firebase App instance for the given user
     */
    private fun getOrCreateFirebaseApp(userId: String): FirebaseApp {
        val appName = "passive_account_$userId"
        
        return try {
            FirebaseApp.getInstance(appName)
        } catch (e: IllegalStateException) {
            // Parse google-services.json
            val options = parseFirebaseOptions()
            FirebaseApp.initializeApp(context, options, appName)
        }
    }
    
    /**
     * Parse Firebase options from google-services.json
     */
    private fun parseFirebaseOptions(): FirebaseOptions {
        // Get default Firebase app options
        val defaultApp = FirebaseApp.getInstance()
        val defaultOptions = defaultApp.options
        
        return FirebaseOptions.Builder()
            .setApplicationId(defaultOptions.applicationId)
            .setApiKey(defaultOptions.apiKey)
            .setDatabaseUrl(defaultOptions.databaseUrl)
            .setGcmSenderId(defaultOptions.gcmSenderId)
            .setProjectId(defaultOptions.projectId)
            .setStorageBucket(defaultOptions.storageBucket)
            .build()
    }
    
    /**
     * Remove a passive session and cleanup resources
     */
    fun removePassiveSession(userId: String) {
        passiveSessions.remove(userId)
        Log.d(TAG, "Removed passive session for $userId (remaining sessions: ${passiveSessions.size})")
        
        // Delete Firebase app instance
        val appName = "passive_account_$userId"
        try {
            FirebaseApp.getInstance(appName).delete()
            Log.d(TAG, "Deleted Firebase app for $userId")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete Firebase app for $userId: ${e.message}")
        }
    }
    
    /**
     * Cleanup oldest session to make room for new one
     */
    private fun cleanupOldestSession() {
        val oldest = passiveSessions.entries.minByOrNull { it.value.createdAt }
        oldest?.let {
            Log.d(TAG, "Removing oldest session: ${it.key} (created at ${it.value.createdAt})")
            removePassiveSession(it.key)
        }
    }
    
    /**
     * Cleanup all expired sessions
     */
    fun cleanupExpiredSessions() {
        val expiredSessions = passiveSessions.filter { it.value.isExpired() }
        Log.d(TAG, "Cleaning up ${expiredSessions.size} expired sessions")
        expiredSessions.forEach { (userId, _) ->
            Log.d(TAG, "Cleaning up expired session for $userId")
            removePassiveSession(userId)
        }
    }
    
    /**
     * Cleanup all passive sessions (call on app exit)
     */
    fun cleanupAllSessions() {
        Log.d(TAG, "Cleaning up all ${passiveSessions.size} passive sessions")
        passiveSessions.keys.toList().forEach { userId ->
            removePassiveSession(userId)
        }
    }
    
    /**
     * Get active session count for monitoring
     */
    fun getActiveSessionCount(): Int = passiveSessions.size
    
    /**
     * Send message from passive account
     */
    suspend fun sendMessageFromPassiveAccount(
        userId: String,
        chatId: String,
        content: String
    ): Result<Unit> {
        return try {
            val session = getOrCreatePassiveSession(userId)
            if (session == null) {
                Log.e(TAG, "Failed to create session for $userId")
                return Result.Error("Authentication failed. Please try again.")
            }
            
            val repository = MinimalMessageRepository(
                firestore = session.firestore,
                currentUserId = userId,
                supabaseNotificationApi = supabaseNotificationApi
            )
            
            repository.sendMessage(chatId, content)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message from passive account: ${e.message}", e)
            
            // Distinguish between network and auth errors
            val errorMessage = when {
                e.message?.contains("Network error", ignoreCase = true) == true -> 
                    e.message ?: "Network error"
                e.message?.contains("Authentication", ignoreCase = true) == true ||
                e.message?.contains("credentials", ignoreCase = true) == true -> 
                    "Authentication failed. Please try again."
                else -> "Failed to send message: ${e.message}"
            }
            
            Result.Error(errorMessage)
        }
    }
    
    /**
     * React to message from passive account
     */
    suspend fun reactToMessageFromPassiveAccount(
        userId: String,
        chatId: String,
        messageId: String,
        emoji: String?
    ): Result<Unit> {
        return try {
            val session = getOrCreatePassiveSession(userId)
            if (session == null) {
                Log.e(TAG, "Failed to create session for $userId")
                return Result.Error("Authentication failed. Please try again.")
            }
            
            val repository = MinimalMessageRepository(
                firestore = session.firestore,
                currentUserId = userId,
                supabaseNotificationApi = supabaseNotificationApi
            )
            
            repository.reactToMessage(chatId, messageId, emoji)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to react from passive account: ${e.message}", e)
            
            val errorMessage = when {
                e.message?.contains("Network error", ignoreCase = true) == true -> 
                    e.message ?: "Network error"
                e.message?.contains("Authentication", ignoreCase = true) == true ||
                e.message?.contains("credentials", ignoreCase = true) == true -> 
                    "Authentication failed. Please try again."
                else -> "Failed to react: ${e.message}"
            }
            
            Result.Error(errorMessage)
        }
    }
    
    /**
     * Mark chat as read from passive account
     */
    suspend fun markChatAsReadFromPassiveAccount(
        userId: String,
        chatId: String
    ): Result<Unit> {
        return try {
            val session = getOrCreatePassiveSession(userId)
            if (session == null) {
                Log.e(TAG, "Failed to create session for $userId")
                return Result.Error("Authentication failed. Please try again.")
            }
            
            val repository = MinimalMessageRepository(
                firestore = session.firestore,
                currentUserId = userId,
                supabaseNotificationApi = supabaseNotificationApi
            )
            
            repository.markChatAsRead(chatId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mark as read from passive account: ${e.message}", e)
            
            val errorMessage = when {
                e.message?.contains("Network error", ignoreCase = true) == true -> 
                    e.message ?: "Network error"
                e.message?.contains("Authentication", ignoreCase = true) == true ||
                e.message?.contains("credentials", ignoreCase = true) == true -> 
                    "Authentication failed. Please try again."
                else -> "Failed to mark as read: ${e.message}"
            }
            
            Result.Error(errorMessage)
        }
    }
}
