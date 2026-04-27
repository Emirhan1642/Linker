package com.linker.app.data.ble

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Message batcher for efficient BLE transmission.
 * 
 * Implements Requirement 9.6:
 * - Batch size: 5 messages
 * - Batch timeout: 5000ms (5 seconds)
 * - Automatic flush when size or timeout reached
 */
@Singleton
class MessageBatcher @Inject constructor() {
    
    private val messageQueue = ConcurrentLinkedQueue<BLEPacket>()
    private var flushJob: Job? = null
    
    private var onBatchReady: ((List<BLEPacket>) -> Unit)? = null
    
    companion object {
        private const val TAG = "MessageBatcher"
        private const val BATCH_SIZE = 5
        private const val BATCH_TIMEOUT_MS = 5000L
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
     * 
     * @param packet BLE packet to add
     */
    fun addMessage(packet: BLEPacket) {
        messageQueue.add(packet)
        
        Log.d(TAG, "Added message to batch (${messageQueue.size}/$BATCH_SIZE)")
        
        // Check if batch size reached
        if (messageQueue.size >= BATCH_SIZE) {
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
            onBatchReady?.invoke(batch)
        }
    }
    
    /**
     * Schedule a timeout flush.
     * 
     * Flushes the batch after BATCH_TIMEOUT_MS if not already flushed.
     */
    private fun scheduleTimeoutFlush() {
        // Don't schedule if already scheduled
        if (flushJob?.isActive == true) {
            return
        }
        
        flushJob = CoroutineScope(Dispatchers.Default).launch {
            delay(BATCH_TIMEOUT_MS)
            
            if (messageQueue.isNotEmpty()) {
                Log.d(TAG, "Timeout reached, flushing batch")
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
     * Clear all pending messages.
     */
    fun clear() {
        flushJob?.cancel()
        flushJob = null
        messageQueue.clear()
        Log.d(TAG, "Cleared message batch")
    }
}
