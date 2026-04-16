package com.linker.app.data.cache

import android.util.LruCache
import com.linker.app.domain.model.User
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory cache for user data
 * 3-tier caching: Memory (LruCache) -> Room -> Firestore
 */
@Singleton
class UserCache @Inject constructor() {
    private val displayNameCache = LruCache<String, String>(100)
    private val userCache = LruCache<String, User>(50)

    fun getDisplayName(userId: String): String? = displayNameCache.get(userId)
    fun putDisplayName(userId: String, name: String) = displayNameCache.put(userId, name)

    fun getUser(userId: String): User? = userCache.get(userId)
    fun putUser(user: User) = userCache.put(user.userId, user)

    fun clear() {
        displayNameCache.evictAll()
        userCache.evictAll()
    }
}
