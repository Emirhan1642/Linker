package com.linker.app.data.ble

import kotlinx.coroutines.*
import com.linker.app.core.util.SecureLogger
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages incomplete packet fragments with timeout handling
 * 
 * Handles Requirement 16.5: Fragment reassembly with 30-second timeout
 * to prevent memory leaks from incomplete fragments.
 */
class FragmentManager(
    private val fragmentTimeout: Long = com.linker.app.core.config.OfflineMessagingConfig.FRAGMENT_TIMEOUT_MS,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    
    private val fragmentStore = ConcurrentHashMap<String, FragmentSet>()
    private val packetFragmenter = PacketFragmenter()
    private val logger = SecureLogger("FragmentManager")
    
    // Periodic cleanup job
    private var cleanupJob: Job? = null
    
    init {
        // Start periodic cleanup every 60 seconds
        startPeriodicCleanup()
    }
    
    /**
     * Start periodic cleanup of stale fragments
     */
    private fun startPeriodicCleanup() {
        cleanupJob = coroutineScope.launch {
            while (isActive) {
                delay(60_000L) // Run every 60 seconds
                cleanupStaleFragments()
            }
        }
    }
    
    /**
     * Represents a set of fragments for a single message
     */
    private data class FragmentSet(
        val fragments: MutableList<BLEPacket> = mutableListOf(),
        var lastReceivedAt: Long = System.currentTimeMillis(),
        var timeoutJob: Job? = null
    )
    
    /**
     * Add a fragment to the manager
     * 
     * @param fragment Fragment to add
     * @return Reassembled payload if all fragments received, null otherwise
     */
    suspend fun addFragment(fragment: BLEPacket): ByteArray? {
        val messageId = fragment.messageId
        
        // Get or create fragment set
        val fragmentSet = fragmentStore.getOrPut(messageId) {
            FragmentSet()
        }
        
        // Add fragment if not already present
        synchronized(fragmentSet) {
            val existingFragment = fragmentSet.fragments.find { 
                it.fragmentIndex == fragment.fragmentIndex 
            }
            
            if (existingFragment == null) {
                fragmentSet.fragments.add(fragment)
                fragmentSet.lastReceivedAt = System.currentTimeMillis()
                logger.d("Added fragment ${fragment.fragmentIndex} for message $messageId")
            }
            
            // Reschedule sliding window timeout
            fragmentSet.timeoutJob?.cancel()
            fragmentSet.timeoutJob = coroutineScope.launch {
                delay(fragmentTimeout)
                cleanupMessage(messageId)
            }
            
            // Check if complete
            if (packetFragmenter.isComplete(fragmentSet.fragments)) {
                // Cancel timeout
                fragmentSet.timeoutJob?.cancel()
                fragmentSet.timeoutJob = null
                
                // Reassemble and remove from store
                val payload = packetFragmenter.reassemble(fragmentSet.fragments)
                fragmentStore.remove(messageId)
                logger.d("Message $messageId reassembled successfully, fragments removed")
                return payload
            }
        }
        
        return null
    }
    
    /**
     * Clean up stale fragments that have timed out
     * 
     * This is called automatically after fragmentTimeout for each message,
     * but can also be called manually for immediate cleanup.
     */
    fun cleanupStaleFragments() {
        val now = System.currentTimeMillis()
        val staleMessages = mutableListOf<String>()
        
        fragmentStore.forEach { (messageId, fragmentSet) ->
            if (now - fragmentSet.lastReceivedAt > fragmentTimeout) {
                staleMessages.add(messageId)
            }
        }
        
        staleMessages.forEach { messageId ->
            cleanupMessage(messageId)
        }
    }
    
    /**
     * Clean up fragments for a specific message
     */
    private fun cleanupMessage(messageId: String) {
        fragmentStore.remove(messageId)?.let { fragmentSet ->
            fragmentSet.timeoutJob?.cancel()
            fragmentSet.timeoutJob = null
            logger.w("Cleaned up stale fragments for message $messageId")
        }
    }
    
    /**
     * Get current fragment count for a message
     * 
     * @param messageId Message ID to check
     * @return Number of fragments received, or 0 if message not found
     */
    fun getFragmentCount(messageId: String): Int {
        return fragmentStore[messageId]?.fragments?.size ?: 0
    }
    
    /**
     * Check if message has any fragments
     * 
     * @param messageId Message ID to check
     * @return true if message has fragments, false otherwise
     */
    fun hasFragments(messageId: String): Boolean {
        return fragmentStore.containsKey(messageId)
    }
    
    /**
     * Get total number of messages with incomplete fragments
     */
    fun getIncompleteMessageCount(): Int {
        return fragmentStore.size
    }
    
    /**
     * Clear all fragments
     */
    fun clearAll() {
        fragmentStore.values.forEach { fragmentSet ->
            fragmentSet.timeoutJob?.cancel()
            fragmentSet.timeoutJob = null
        }
        fragmentStore.clear()
        logger.d("Cleared all fragments")
    }
    
    /**
     * Clean up resources
     */
    fun shutdown() {
        cleanupJob?.cancel()
        clearAll()
        coroutineScope.cancel()
    }
}
