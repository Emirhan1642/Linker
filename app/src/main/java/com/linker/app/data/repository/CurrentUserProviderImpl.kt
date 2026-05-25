package com.linker.app.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.linker.app.domain.usecase.user.CurrentUserProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of CurrentUserProvider using FirebaseAuth
 * Features memory caching and thread safety.
 */
@Singleton
class CurrentUserProviderImpl @Inject constructor(
    private val auth: FirebaseAuth
) : CurrentUserProvider {

    @Volatile
    private var cachedUserId: String? = null
    
    @Volatile
    private var cachedDisplayName: String? = null
    
    @Volatile
    private var lastCacheTime: Long = 0
    
    private val cacheLock = Any()
    private val cacheDuration = 5000L // 5 seconds
    
    init {
        auth.addAuthStateListener { firebaseAuth ->
            synchronized(cacheLock) {
                val user = firebaseAuth.currentUser
                cachedUserId = user?.uid
                cachedDisplayName = user?.displayName ?: user?.email?.substringBefore("@")
                lastCacheTime = System.currentTimeMillis()
            }
        }
    }

    override fun getCurrentUserId(): String? {
        return try {
            synchronized(cacheLock) {
                if (System.currentTimeMillis() - lastCacheTime < cacheDuration) {
                    return cachedUserId
                }
                
                val user = auth.currentUser
                cachedUserId = user?.uid
                lastCacheTime = System.currentTimeMillis()
                cachedUserId
            }
        } catch (e: Exception) {
            Log.e("CurrentUserProvider", "Error getting current user ID: ${e.message}", e)
            null
        }
    }

    override fun getCurrentUserDisplayName(): String? {
        return try {
            synchronized(cacheLock) {
                if (System.currentTimeMillis() - lastCacheTime < cacheDuration) {
                    return cachedDisplayName
                }
                
                val user = auth.currentUser
                cachedDisplayName = user?.displayName ?: user?.email?.substringBefore("@")
                lastCacheTime = System.currentTimeMillis()
                cachedDisplayName
            }
        } catch (e: Exception) {
            Log.e("CurrentUserProvider", "Error getting current user display name: ${e.message}", e)
            null
        }
    }
}
