package com.linker.app.data.ble

import com.linker.app.core.util.SecureLogger as Log
import com.linker.app.core.config.OfflineMessagingConfig
import com.linker.app.data.connectivity.ConnectivityMonitor
import com.linker.app.data.connectivity.ConnectivityState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Message batcher for efficient BLE transmission with adaptive batching.
 * 
 * Implements Requirement 9.6:
 * - Base batch size: 5 messages
 * - Base batch timeout: 5000ms (5 seconds)
 * - Automatic flush when size or timeout reached
 * 
 * Addresses Issue #20 (P3): Implement adaptive batching
 * - Adjusts batch parameters based on network state
 * - Smaller batches when online (faster delivery)
 * - Larger batches when offline (better efficiency)
 */
@Singleton
class MessageBatcher @Inject constructor(
    private val connectivityMonitor: ConnectivityMonitor
) {
    
    private val messageQueue = ConcurrentLinkedQueue<BLEPacket>()
    private val coroutineScope = CoroutineScope(Dispatchers.Default + Job())
    private var flushJob: Job? = null

    @Volatile
    private var onBatchReady: ((List<BLEPacket>) -> Unit)? = null
    
    // Adaptive batch parameters
    @Volatile
    private var currentBatchSize = OfflineMessagingConfig.MESSAGE_BATCH_SIZE
    @Volatile
    private var currentBatchTimeout = OfflineMessagingConfig.MESSAGE_BATCH_TIMEOUT_MS
    
    companion object {
        private const val TAG = "MessageBatcher"
        
        // Adaptive batch sizes based on network state
        private const val BATCH_SIZE_ONLINE = 3 // Smaller batches when online (faster delivery)
        private const val BATCH_SIZE_OFFLINE = 5 // Standard batch size when offline
        private const val BATCH_SIZE_LIMITED = 7 // Larger batches when connection is limited
        
        // Adaptive timeouts based on network state
        private const val BATCH_TIMEOUT_ONLINE_MS = 2000L // 2 seconds when online
        private const val BATCH_TIMEOUT_OFFLINE_MS = 5000L // 5 seconds when offline
        private const val BATCH_TIMEOUT_LIMITED_MS = 8000L // 8 seconds when limited
    }
    
    init {
        // Observe connectivity state and adjust batch parameters
        connectivityMonitor.observeConnectivityState()
            .onEach { state ->
                adjustBatchParameters(state)
            }
            .launchIn(coroutineScope)
    }
    
    /**
     * Adjust batch parameters based on connectivity state.
     * 
     * Addresses Issue #20 (P3): Adaptive batching based on network conditions
     * 
     * - Online: Smaller batches, shorter timeout (prioritize speed)
     * - Offline: Standard batches, standard timeout (balance efficiency and latency)
     * - Limited: Larger batches, longer timeout (maximize efficiency)
     */
    private fun adjustBatchParameters(state: ConnectivityState) {
        val (newSize, newTimeout) = when (state) {
            is ConnectivityState.Online -> {
                Log.d(TAG, "Network online - using smaller batches for faster delivery")
                Pair(BATCH_SIZE_ONLINE, BATCH_TIMEOUT_ONLINE_MS)
            }
            is ConnectivityState.Offline -> {
                Log.d(TAG, "Network offline - using standard batches")
                Pair(BATCH_SIZE_OFFLINE, BATCH_TIMEOUT_OFFLINE_MS)
            }
            is ConnectivityState.Limited -> {
                Log.d(TAG, "Network limited - using larger batches for efficiency")
                Pair(BATCH_SIZE_LIMITED, BATCH_TIMEOUT_LIMITED_MS)
            }
        }
        
        // Update parameters if changed
        if (newSize != currentBatchSize || newTimeout != currentBatchTimeout) {
            currentBatchSize = newSize
            currentBatchTimeout = newTimeout
            
            Log.d(TAG, "Batch parameters adjusted: size=$currentBatchSize, timeout=${currentBatchTimeout}ms")
            
            // Reschedule timeout flush with new timeout
            if (messageQueue.isNotEmpty() && flushJob?.isActive == true) {
                flushJob?.cancel()
                scheduleTimeoutFlush()
            }
        }
    }
    
    /**
     * Set callback for when a batch is ready to send.
     * 
     * @param callback Function to call with batched messages
     */
    fun setOnBatchReady(callback: (List<BLEPacket>) -> Unit) {
        onBatchReady = callback
    }
    
    /**
     * Add a message to the batch.
     * 
     * Automatically flushes when batch size is reached.
     * Uses adaptive batch size based on network state.
     * 
     * @param packet BLE packet to add
     */
    fun addMessage(packet: BLEPacket) {
        messageQueue.add(packet)
        
        Log.d(TAG, "Added message to batch (${messageQueue.size}/$currentBatchSize)")
        
        // Check if batch size reached (using adaptive size)
        if (messageQueue.size >= currentBatchSize) {
            flushBatch()
        } else {
            // Schedule timeout flush if not already scheduled
            scheduleTimeoutFlush()
        }
    }
    
    /**
     * Flush the current batch immediately.
     * 
     * Sends all queued messages to the callback.
     */
    fun flushBatch() {
        // Cancel any pending timeout flush
        flushJob?.cancel()
        flushJob = null

        if (messageQueue.isEmpty()) {
            return
        }

        // Collect all messages from queue
        val batch = mutableListOf<BLEPacket>()
        while (messageQueue.isNotEmpty()) {
            messageQueue.poll()?.let { batch.add(it) }
        }

        if (batch.isNotEmpty()) {
            Log.d(TAG, "Flushing batch of ${batch.size} messages")
            val callback = onBatchReady
            if (callback == null) {
                Log.w(TAG, "onBatchReady callback is null – restoring ${batch.size} messages to queue!")
                batch.forEach { messageQueue.offer(it) }
                return
            }
            callback.invoke(batch)
        }
    }
    
    /**
     * Schedule a timeout flush.
     * 
     * Flushes the batch after adaptive timeout if not already flushed.
     * Uses adaptive timeout based on network state.
     */
    private fun scheduleTimeoutFlush() {
        // Don't schedule if already scheduled
        if (flushJob?.isActive == true) {
            return
        }
        
        flushJob = coroutineScope.launch {
            delay(currentBatchTimeout) // Use adaptive timeout
            
            if (messageQueue.isNotEmpty()) {
                Log.d(TAG, "Timeout reached (${currentBatchTimeout}ms), flushing batch")
                flushBatch()
            }
        }
    }
    
    /**
     * Get current batch size.
     */
    fun getBatchSize(): Int {
        return messageQueue.size
    }
    
    /**
     * Clean up resources and cancel the internal coroutine scope.
     * 
     * Call this when the batcher is no longer needed to prevent coroutine leaks.
     */
    fun shutdown() {
        flushJob?.cancel()
        flushJob = null
        messageQueue.clear()
        coroutineScope.cancel()
        Log.d(TAG, "Shutdown message batcher and cancelled coroutine scope")
    }
}
