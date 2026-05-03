package com.linker.app.core.util

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interface for providing current time
 * 
 * This abstraction allows for testable time-dependent code by enabling
 * time mocking in tests.
 */
interface TimeProvider {
    /**
     * Get current time in milliseconds since epoch
     * 
     * @return Current time in milliseconds
     */
    fun currentTimeMillis(): Long
    
    /**
     * Get current time in nanoseconds
     * 
     * @return Current time in nanoseconds (for high-precision timing)
     */
    fun nanoTime(): Long
}

/**
 * Default implementation using System time functions
 */
@Singleton
class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
    
    override fun nanoTime(): Long = System.nanoTime()
}

/**
 * Fixed time provider for testing
 * 
 * @param fixedTime The fixed time to return (in milliseconds)
 */
class FixedTimeProvider(private val fixedTime: Long) : TimeProvider {
    override fun currentTimeMillis(): Long = fixedTime
    
    override fun nanoTime(): Long = fixedTime * 1_000_000 // Convert to nanoseconds
}

/**
 * Controllable time provider for testing
 * 
 * Allows advancing time manually in tests.
 */
class ControllableTimeProvider(initialTime: Long = 0L) : TimeProvider {
    private var currentTime = initialTime
    
    override fun currentTimeMillis(): Long = currentTime
    
    override fun nanoTime(): Long = currentTime * 1_000_000
    
    /**
     * Advance time by the specified amount
     * 
     * @param millis Milliseconds to advance
     */
    fun advanceTime(millis: Long) {
        currentTime += millis
    }
    
    /**
     * Set time to a specific value
     * 
     * @param millis Time in milliseconds
     */
    fun setTime(millis: Long) {
        currentTime = millis
    }
}
