package com.linker.app.data.cache

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.util.LruCache
import com.linker.app.core.util.SecureLogger
import com.linker.app.domain.model.User
import com.linker.app.domain.repository.UserRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Data class to hold cache statistics
 */
data class CacheStats(
    val displayNameHits: Long = 0,
    val displayNameMisses: Long = 0,
    val displayNameEvictions: Long = 0,
    val userHits: Long = 0,
    val userMisses: Long = 0,
    val userEvictions: Long = 0,
    val displayNameSize: Int = 0,
    val userSize: Int = 0
) {
    val displayNameHitRate: Float
        get() = if (displayNameHits + displayNameMisses > 0) {
            displayNameHits.toFloat() / (displayNameHits + displayNameMisses)
        } else 0f
    
    val userHitRate: Float
        get() = if (userHits + userMisses > 0) {
            userHits.toFloat() / (userHits + userMisses)
        } else 0f
}

/**
 * Configuration options for UserCache
 */
data class CacheConfig(
    val displayNameCacheSize: Int = 100,
    val userCacheSize: Int = 50,
    val displayNameTtlMs: Long = 5 * 60 * 1000L,
    val userTtlMs: Long = 10 * 60 * 1000L,
    val enableMetrics: Boolean = true,
    val enableLogging: Boolean = true
)

/**
 * Wrapper for cache entries with timestamp
 */
@Serializable
data class CacheEntry<T>(
    val value: T,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun isExpired(ttlMs: Long): Boolean {
        return System.currentTimeMillis() - timestamp > ttlMs
    }
}

/**
 * Snapshot for serialization
 */
@Serializable
data class CacheSnapshot(
    val displayNames: Map<String, CacheEntry<String>>,
    val users: Map<String, CacheEntry<User>>
)

/**
 * In-memory cache for user data
 * 
 * Thread-safe implementation using read-write locks for optimal performance.
 * 3-tier caching: Memory (LruCache) -> Room -> Firestore
 */
@Singleton
class UserCache @Inject constructor(
    @ApplicationContext private val context: Context
) : ComponentCallbacks2 {

    private val config: CacheConfig = CacheConfig()

    companion object {
        private const val TAG = "UserCache"
    }

    private val displayNameCache = object : LruCache<String, CacheEntry<String>>(config.displayNameCacheSize) {
        override fun entryRemoved(evicted: Boolean, key: String?, oldValue: CacheEntry<String>?, newValue: CacheEntry<String>?) {
            if (evicted) displayNameEvictions.incrementAndGet()
        }
    }
    
    private val userCache = object : LruCache<String, CacheEntry<User>>(config.userCacheSize) {
        override fun entryRemoved(evicted: Boolean, key: String?, oldValue: CacheEntry<User>?, newValue: CacheEntry<User>?) {
            if (evicted) userEvictions.incrementAndGet()
        }
    }

    private val avatarCache = object : LruCache<String, CacheEntry<String>>(config.displayNameCacheSize) {
        override fun entryRemoved(evicted: Boolean, key: String?, oldValue: CacheEntry<String>?, newValue: CacheEntry<String>?) {}
    }

    // Read-write locks for thread-safe access
    private val displayNameLock = ReentrantReadWriteLock()
    private val userLock = ReentrantReadWriteLock()
    private val avatarLock = ReentrantReadWriteLock()

    // Metrics
    private val displayNameHits = AtomicLong(0)
    private val displayNameMisses = AtomicLong(0)
    private val displayNameEvictions = AtomicLong(0)
    private val userHits = AtomicLong(0)
    private val userMisses = AtomicLong(0)
    private val userEvictions = AtomicLong(0)

    init {
        context.registerComponentCallbacks(this)
    }

    /**
     * Get display name from cache
     * 
     * @param userId User ID to lookup
     * @return Display name if found and not expired, null otherwise
     */
    fun getDisplayName(userId: String): String? {
        if (userId.isBlank()) {
            if (config.enableLogging) SecureLogger.w(TAG, "Attempted to get display name with blank userId")
            return null
        }
        
        return displayNameLock.read {
            val entry = displayNameCache.get(userId)
            if (entry != null) {
                if (entry.isExpired(config.displayNameTtlMs)) {
                    null
                } else {
                    if (config.enableMetrics) displayNameHits.incrementAndGet()
                    entry.value
                }
            } else {
                if (config.enableMetrics) displayNameMisses.incrementAndGet()
                null
            }
        }
    }

    /**
     * Put display name into cache
     * 
     * @param userId User ID
     * @param name Display name to cache
     */
    fun putDisplayName(userId: String, name: String) {
        if (userId.isBlank() || name.isBlank()) {
            if (config.enableLogging) SecureLogger.w(TAG, "Invalid input for putDisplayName")
            return
        }
        
        displayNameLock.write {
            displayNameCache.put(userId, CacheEntry(name))
            if (config.enableLogging) SecureLogger.d(TAG, "Display name cached for user")
        }
    }

    /**
     * Get user from cache
     * 
     * @param userId User ID to lookup
     * @return User object if found and not expired, null otherwise
     */
    fun getUser(userId: String): User? {
        if (userId.isBlank()) {
            if (config.enableLogging) SecureLogger.w(TAG, "Attempted to get user with blank userId")
            return null
        }
        
        return userLock.read {
            val entry = userCache.get(userId)
            if (entry != null) {
                if (entry.isExpired(config.userTtlMs)) {
                    null
                } else {
                    if (config.enableMetrics) userHits.incrementAndGet()
                    entry.value
                }
            } else {
                if (config.enableMetrics) userMisses.incrementAndGet()
                null
            }
        }
    }

    /**
     * Put user into cache
     * 
     * @param user User object to cache
     */
    fun putUser(user: User?) {
        if (user == null || user.userId.isBlank()) {
            if (config.enableLogging) SecureLogger.w(TAG, "Invalid user input for putUser")
            return
        }
        
        userLock.write {
            userCache.put(user.userId, CacheEntry(user))
            if (config.enableLogging) SecureLogger.d(TAG, "User cached")
        }
    }

    /**
     * Get avatar URL from cache
     */
    fun getAvatarUrl(userId: String): String? {
        if (userId.isBlank()) return null
        return avatarLock.read {
            val entry = avatarCache.get(userId)
            if (entry != null && !entry.isExpired(config.displayNameTtlMs)) {
                entry.value
            } else null
        }
    }

    /**
     * Put avatar URL into cache
     */
    fun putAvatarUrl(userId: String, url: String) {
        if (userId.isBlank() || url.isBlank()) return
        avatarLock.write {
            avatarCache.put(userId, CacheEntry(url))
        }
    }

    /**
     * Get multiple display names at once
     */
    fun getDisplayNames(userIds: List<String>): Map<String, String> {
        if (userIds.isEmpty()) return emptyMap()
        return displayNameLock.read {
            userIds.mapNotNull { userId ->
                if (userId.isBlank()) return@mapNotNull null
                val entry = displayNameCache.get(userId)
                if (entry != null && !entry.isExpired(config.displayNameTtlMs)) {
                    userId to entry.value
                } else null
            }.toMap()
        }
    }

    /**
     * Put multiple display names at once
     */
    fun putDisplayNames(displayNames: Map<String, String>) {
        if (displayNames.isEmpty()) return
        displayNameLock.write {
            displayNames.forEach { (userId, name) ->
                if (userId.isNotBlank() && name.isNotBlank()) {
                    displayNameCache.put(userId, CacheEntry(name))
                }
            }
        }
        if (config.enableLogging) SecureLogger.d(TAG, "Batch cached ${displayNames.size} display names")
    }

    /**
     * Get multiple users at once
     */
    fun getUsers(userIds: List<String>): Map<String, User> {
        if (userIds.isEmpty()) return emptyMap()
        return userLock.read {
            userIds.mapNotNull { userId ->
                if (userId.isBlank()) return@mapNotNull null
                val entry = userCache.get(userId)
                if (entry != null && !entry.isExpired(config.userTtlMs)) {
                    userId to entry.value
                } else null
            }.toMap()
        }
    }

    /**
     * Put multiple users at once
     */
    fun putUsers(users: List<User>) {
        if (users.isEmpty()) return
        userLock.write {
            users.forEach { user ->
                if (user.userId.isNotBlank()) {
                    userCache.put(user.userId, CacheEntry(user))
                }
            }
        }
        if (config.enableLogging) SecureLogger.d(TAG, "Batch cached ${users.size} users")
    }

    /**
     * Remove specific display name from cache
     */
    fun invalidateDisplayName(userId: String) {
        if (userId.isBlank()) return
        displayNameLock.write { displayNameCache.remove(userId) }
        if (config.enableLogging) SecureLogger.d(TAG, "Invalidated display name for user")
    }

    /**
     * Remove specific user from cache
     */
    fun invalidateUser(userId: String) {
        if (userId.isBlank()) return
        userLock.write { userCache.remove(userId) }
        if (config.enableLogging) SecureLogger.d(TAG, "Invalidated user")
    }

    /**
     * Invalidate both display name and user for a userId
     */
    fun invalidateAll(userId: String) {
        if (userId.isBlank()) return
        invalidateDisplayName(userId)
        invalidateUser(userId)
        if (config.enableLogging) SecureLogger.d(TAG, "Invalidated all cache entries for user")
    }

    /**
     * Invalidate multiple users at once
     */
    fun invalidateUsers(userIds: List<String>) {
        if (userIds.isEmpty()) return
        userIds.forEach { invalidateAll(it) }
        if (config.enableLogging) SecureLogger.d(TAG, "Invalidated ${userIds.size} users")
    }

    /**
     * Remove expired entries from cache
     * Should be called periodically
     */
    fun cleanupExpired() {
        var removedCount = 0
        displayNameLock.write {
            val snapshot = displayNameCache.snapshot()
            snapshot.forEach { (key, entry) ->
                if (entry.isExpired(config.displayNameTtlMs)) {
                    displayNameCache.remove(key)
                    removedCount++
                }
            }
        }
        userLock.write {
            val snapshot = userCache.snapshot()
            snapshot.forEach { (key, entry) ->
                if (entry.isExpired(config.userTtlMs)) {
                    userCache.remove(key)
                    removedCount++
                }
            }
        }
        avatarLock.write {
            val snapshot = avatarCache.snapshot()
            snapshot.forEach { (key, entry) ->
                if (entry.isExpired(config.displayNameTtlMs)) {
                    avatarCache.remove(key)
                    removedCount++
                }
            }
        }
        if (removedCount > 0 && config.enableLogging) {
            SecureLogger.d(TAG, "Cleaned up $removedCount expired cache entries")
        }
    }

    /**
     * Clear all caches
     * 
     * Removes all display names, avatars and user objects from memory.
     * This operation is thread-safe.
     */
    fun clear() {
        val displayNameSize = displayNameLock.read { displayNameCache.size() }
        val userSize = userLock.read { userCache.size() }
        
        displayNameLock.write { displayNameCache.evictAll() }
        userLock.write { userCache.evictAll() }
        avatarLock.write { avatarCache.evictAll() }
        
        if (config.enableLogging) SecureLogger.d(TAG, "Cache cleared: $displayNameSize display names, $userSize users")
    }

    /**
     * Get cache statistics
     */
    fun getStats(): CacheStats {
        return CacheStats(
            displayNameHits = displayNameHits.get(),
            displayNameMisses = displayNameMisses.get(),
            displayNameEvictions = displayNameEvictions.get(),
            userHits = userHits.get(),
            userMisses = userMisses.get(),
            userEvictions = userEvictions.get(),
            displayNameSize = displayNameLock.read { displayNameCache.size() },
            userSize = userLock.read { userCache.size() }
        )
    }

    /**
     * Reset cache statistics
     */
    fun resetStats() {
        displayNameHits.set(0)
        displayNameMisses.set(0)
        displayNameEvictions.set(0)
        userHits.set(0)
        userMisses.set(0)
        userEvictions.set(0)
    }

    /**
     * Get current cache sizes
     */
    fun getCacheSizes(): Pair<Int, Int> {
        val displayNameSize = displayNameLock.read { displayNameCache.size() }
        val userSize = userLock.read { userCache.size() }
        return Pair(displayNameSize, userSize)
    }

    /**
     * Get cache capacity utilization
     */
    fun getCacheUtilization(): Pair<Float, Float> {
        val (displayNameSize, userSize) = getCacheSizes()
        return Pair(
            displayNameSize.toFloat() / config.displayNameCacheSize,
            userSize.toFloat() / config.userCacheSize
        )
    }

    /**
     * Check if cache is near capacity
     */
    fun isNearCapacity(threshold: Float = 0.9f): Boolean {
        val (displayNameUtil, userUtil) = getCacheUtilization()
        return displayNameUtil > threshold || userUtil > threshold
    }

    /**
     * Warm up cache with frequently accessed users
     */
    suspend fun warmUp(userIds: List<String>, userRepository: UserRepository) {
        if (userIds.isEmpty()) return
        if (config.enableLogging) SecureLogger.d(TAG, "Warming up cache with ${userIds.size} users")
        try {
            withContext(Dispatchers.IO) {
                userIds.map { id ->
                    async {
                        val result = userRepository.getUserById(id)
                        result.getOrNull()?.let { user ->
                            putUser(user)
                            putDisplayName(user.userId, user.displayName)
                        }
                    }
                }.awaitAll()
            }
            if (config.enableLogging) SecureLogger.d(TAG, "Cache warmed up")
        } catch (e: Exception) {
            if (config.enableLogging) SecureLogger.e(TAG, "Error warming up cache", e)
        }
    }

    /**
     * Preload users based on access patterns
     */
    suspend fun preloadFrequentUsers(
        userRepository: UserRepository,
        accessLog: List<String>
    ) {
        val frequentUsers = accessLog
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(20)
            .map { it.key }
        warmUp(frequentUsers, userRepository)
    }

    /**
     * Save cache to disk - No-op as Room DB handles persistent disk caching securely
     */
    suspend fun saveToDisk() {
        // Persistent caching is securely handled by Room LinkerDatabase
    }

    /**
     * Load cache from disk - Cleans up legacy plaintext file if present
     */
    suspend fun loadFromDisk() {
        withContext(Dispatchers.IO) {
            try {
                val legacyFile = File(context.cacheDir, "user_cache.json")
                if (legacyFile.exists()) {
                    legacyFile.delete()
                }
            } catch (_: Exception) {}
        }
    }

    override fun onTrimMemory(level: Int) {
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                clear()
                if (config.enableLogging) SecureLogger.w(TAG, "Cache cleared due to critical memory pressure")
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE -> {
                trimCache(0.5f)
                if (config.enableLogging) SecureLogger.w(TAG, "Cache trimmed due to moderate memory pressure")
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> {
                trimCache(0.75f)
                if (config.enableLogging) SecureLogger.d(TAG, "Cache trimmed due to light memory pressure")
            }
        }
    }
    
    override fun onConfigurationChanged(newConfig: Configuration) {}
    
    override fun onLowMemory() {
        clear()
        if (config.enableLogging) SecureLogger.w(TAG, "Cache cleared due to low memory")
    }
    
    private fun trimCache(percentage: Float) {
        require(percentage in 0f..1f) { "Percentage must be between 0 and 1" }
        displayNameLock.write {
            val targetSize = (config.displayNameCacheSize * percentage).toInt()
            displayNameCache.trimToSize(targetSize)
        }
        userLock.write {
            val targetSize = (config.userCacheSize * percentage).toInt()
            userCache.trimToSize(targetSize)
        }
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        context.unregisterComponentCallbacks(this)
        clear()
    }
}

