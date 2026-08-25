package com.linker.app.data.ble

import com.linker.app.data.local.dao.BleNodeDao
import com.linker.app.data.local.entity.BleNodeEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.linker.app.core.util.SecureLogger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Route information for a BLE mesh node
 */
data class RouteInfo(
    val nodeId: String,
    val deviceAddress: String,
    val rssi: Int,
    val hopCount: Int,
    val routeQuality: Float,
    val lastSeen: Long
)

/**
 * Manages BLE mesh routing table
 * 
 * Maintains information about discovered nodes and calculates optimal routes
 * based on signal strength (RSSI) and hop count.
 * 
 * Handles Requirements 4.9, 4.10: Routing table management and stale node removal
 */
@Singleton
class BLERoutingTable @Inject constructor(
    private val bleNodeDao: BleNodeDao
) {
    
    private val mutex = Mutex()
    private val routeCache = mutableMapOf<String, RouteInfo>()
    
    // Coroutine scope for background cache warming
    private val cacheScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val logger = SecureLogger("BLERoutingTable")
    
    companion object {
        private const val STALE_NODE_THRESHOLD = 60_000L // 60 seconds
        private const val MIN_RSSI = -90 // Minimum acceptable signal strength
        private const val MAX_RSSI = -30 // Maximum signal strength (very close)
        private const val CACHE_WARM_THRESHOLD = 10 // Warm cache when size drops below this
    }
    
    /**
     * Warm the cache by loading recent nodes from database
     * 
     * This reduces database queries by preloading frequently accessed routes.
     * Should be called periodically or when cache size drops below threshold.
     */
    suspend fun warmCache() {
        val now = System.currentTimeMillis()
        val recentNodes = bleNodeDao.getRecentNodes(now - STALE_NODE_THRESHOLD)
        
        mutex.withLock {
            recentNodes.forEach { node ->
                routeCache[node.nodeId] = RouteInfo(
                    nodeId = node.nodeId,
                    deviceAddress = node.deviceAddress,
                    rssi = node.rssi,
                    hopCount = node.hopCount,
                    routeQuality = node.routeQuality,
                    lastSeen = node.lastSeen
                )
            }
        }
        
        logger.d("Cache warmed with ${recentNodes.size} routes")
    }
    
    /**
     * Get routes for multiple recipients in a single batch query
     * 
     * More efficient than calling getRoute() multiple times.
     * 
     * @param recipientIds List of recipient user IDs
     * @return Map of recipient ID to RouteInfo (only includes found routes)
     */
    suspend fun getRoutesBatch(recipientIds: List<String>): Map<String, RouteInfo> {
        if (recipientIds.isEmpty()) return emptyMap()
        
        val result = mutableMapOf<String, RouteInfo>()
        val missingIds = mutableListOf<String>()
        
        // Check cache first
        mutex.withLock {
            recipientIds.forEach { id ->
                val cachedRoute = routeCache[id]
                if (cachedRoute != null && !isStale(cachedRoute.lastSeen)) {
                    result[id] = cachedRoute
                } else {
                    missingIds.add(id)
                }
            }
        }
        
        // Batch query database for missing routes
        if (missingIds.isNotEmpty()) {
            val nodes = bleNodeDao.getNodesByIds(missingIds)
            val now = System.currentTimeMillis()
            
            var shouldWarm = false
            mutex.withLock {
                nodes.forEach { node ->
                    if (!isStale(node.lastSeen)) {
                        val routeInfo = RouteInfo(
                            nodeId = node.nodeId,
                            deviceAddress = node.deviceAddress,
                            rssi = node.rssi,
                            hopCount = node.hopCount,
                            routeQuality = node.routeQuality,
                            lastSeen = node.lastSeen
                        )
                        routeCache[node.nodeId] = routeInfo
                        result[node.nodeId] = routeInfo
                    }
                }
                shouldWarm = routeCache.size < CACHE_WARM_THRESHOLD
            }
            
            if (shouldWarm) {
                cacheScope.launch {
                    warmCache()
                }
            }
        }
        
        return result
    }
    
    /**
     * Add or update a route in the routing table
     * 
     * @param nodeId User ID of the node
     * @param deviceAddress BLE MAC address
     * @param rssi Signal strength
     * @param hopCount Number of hops to reach this node
     * @param timestamp Current timestamp
     */
    suspend fun addRoute(
        nodeId: String,
        deviceAddress: String,
        rssi: Int,
        hopCount: Int = 1,
        timestamp: Long = System.currentTimeMillis()
    ) = mutex.withLock {
        val routeQuality = calculateRouteQuality(rssi, hopCount)
        
        // Check if route already exists
        val existingNode = kotlinx.coroutines.withContext(Dispatchers.IO) {
            bleNodeDao.getNodeById(nodeId)
        }
        
        if (existingNode != null) {
            val isStale = (timestamp - existingNode.lastSeen > STALE_NODE_THRESHOLD)
            val isBetterRoute = routeQuality > existingNode.routeQuality
            val isDirectOverwritingStale = (isStale && hopCount <= existingNode.hopCount) || (timestamp - existingNode.lastSeen > 60_000L)
            
            if (isBetterRoute || isDirectOverwritingStale) {
                // Update with better route or refresh truly stale route
                val updatedNode = existingNode.copy(
                    deviceAddress = deviceAddress,
                    rssi = rssi,
                    hopCount = hopCount,
                    routeQuality = routeQuality,
                    lastSeen = timestamp,
                    updatedAt = timestamp
                )
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    bleNodeDao.updateNode(updatedNode)
                }
                
                // Update cache
                routeCache[nodeId] = RouteInfo(
                    nodeId = nodeId,
                    deviceAddress = deviceAddress,
                    rssi = rssi,
                    hopCount = hopCount,
                    routeQuality = routeQuality,
                    lastSeen = timestamp
                )
            }
        } else {
            // Insert new route
            val newNode = BleNodeEntity(
                nodeId = nodeId,
                deviceAddress = deviceAddress,
                deviceName = null,
                rssi = rssi,
                lastSeen = timestamp,
                isConnected = false,
                hopCount = hopCount,
                routeQuality = routeQuality,
                createdAt = timestamp,
                updatedAt = timestamp
            )
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                bleNodeDao.insertNode(newNode)
            }
            
            // Update cache
            routeCache[nodeId] = RouteInfo(
                nodeId = nodeId,
                deviceAddress = deviceAddress,
                rssi = rssi,
                hopCount = hopCount,
                routeQuality = routeQuality,
                lastSeen = timestamp
            )
        }
    }
    
    /**
     * Get route to a specific node
     * 
     * @param recipientId User ID of the recipient
     * @return RouteInfo if route exists and is not stale, null otherwise
     */
    suspend fun getRoute(recipientId: String): RouteInfo? {
        // Use read lock for cache check (allows concurrent reads)
        val cachedRoute = mutex.withLock {
            routeCache[recipientId]
        }
        
        if (cachedRoute != null && !isStale(cachedRoute.lastSeen)) {
            return cachedRoute
        }
        
        // Query database (outside of lock)
        val node = bleNodeDao.getNodeById(recipientId) ?: return null
        
        // Check if stale
        if (isStale(node.lastSeen)) {
            mutex.withLock {
                routeCache.remove(recipientId)
            }
            return null
        }
        
        // Update cache and return (use write lock)
        val routeInfo = RouteInfo(
            nodeId = node.nodeId,
            deviceAddress = node.deviceAddress,
            rssi = node.rssi,
            hopCount = node.hopCount,
            routeQuality = node.routeQuality,
            lastSeen = node.lastSeen
        )
        
        mutex.withLock {
            routeCache[recipientId] = routeInfo
        }
        
        return routeInfo
    }
    
    /**
     * Get best route to a recipient (lowest hop count, highest RSSI)
     * 
     * @param recipientId User ID of the recipient
     * @return RouteInfo for best route, or null if no route available
     */
    suspend fun getBestRoute(recipientId: String): RouteInfo? {
        return getRoute(recipientId)
    }
    
    /**
     * Get all available routes
     * 
     * @return List of all non-stale routes
     */
    suspend fun getAllRoutes(): List<RouteInfo> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val nodes = bleNodeDao.getRecentNodes(now - STALE_NODE_THRESHOLD)
        
        nodes.map { node ->
            RouteInfo(
                nodeId = node.nodeId,
                deviceAddress = node.deviceAddress,
                rssi = node.rssi,
                hopCount = node.hopCount,
                routeQuality = node.routeQuality,
                lastSeen = node.lastSeen
            )
        }
    }
    
    /**
     * Remove stale routes (not seen in last 60 seconds)
     * 
     * @return Number of routes removed
     */
    suspend fun removeStaleRoutes(): Int {
        val now = System.currentTimeMillis()
        val staleThreshold = now - STALE_NODE_THRESHOLD
        
        // Remove from database on IO dispatcher without holding the mutex
        val removedCount = withContext(Dispatchers.IO) {
            bleNodeDao.deleteStaleNodes(staleThreshold)
        }
        
        // Remove from memory cache with lock
        mutex.withLock {
            val staleNodeIds = routeCache.filter { (_, route) ->
                isStale(route.lastSeen)
            }.keys
            
            staleNodeIds.forEach { nodeId ->
                routeCache.remove(nodeId)
            }
        }
        
        return removedCount
    }
    
    /**
     * Calculate route quality score (0-1) based on RSSI and hop count
     * 
     * Higher score = better route
     * 
     * @param rssi Signal strength (dBm)
     * @param hopCount Number of hops
     * @return Quality score between 0 and 1
     */
    fun calculateRouteQuality(rssi: Int, hopCount: Int): Float {
        // Normalize RSSI to 0-1 range
        // RSSI typically ranges from -90 (weak) to -30 (strong)
        val normalizedRssi = ((rssi - MIN_RSSI).toFloat() / (MAX_RSSI - MIN_RSSI))
            .coerceIn(0f, 1f)
        
        // Penalize higher hop counts
        // 0 or 1 hop = 1.0, 2 hops = 0.5, 3 hops = 0.33, etc.
        val hopPenalty = if (hopCount <= 1) 1f else (1f / hopCount.toFloat())
        
        // Weighted combination: 70% RSSI, 30% hop count
        return (0.7f * normalizedRssi + 0.3f * hopPenalty).coerceIn(0f, 1f)
    }
    
    /**
     * Check if a route is stale
     * 
     * @param lastSeen Last seen timestamp
     * @return true if route is stale (older than 60 seconds)
     */
    private fun isStale(lastSeen: Long): Boolean {
        return System.currentTimeMillis() - lastSeen > STALE_NODE_THRESHOLD
    }
    
    /**
     * Clear all routes
     */
    suspend fun clearAll() = mutex.withLock {
        bleNodeDao.clearAll()
        routeCache.clear()
    }
    
    /**
     * Get route count
     */
    suspend fun getRouteCount(): Int {
        return bleNodeDao.getNodeCount()
    }
}
