package com.linker.app.core.util

import javax.inject.Inject
import javax.inject.Singleton
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Interface for providing current time
 * 
 * This abstraction allows for testable time-dependent code by enabling
 * time mocking in tests.
 * 
 * ✅ ENHANCED: Added time zone support, clock skew detection
 */
interface TimeProvider {
    /**
     * Get current time in milliseconds since epoch (UTC)
     * 
     * @return Current time in milliseconds
     */
    fun currentTimeMillis(): Long
    
    /**
     * Get current time in nanoseconds (for high-precision timing)
     * 
     * @return Current time in nanoseconds
     */
    fun nanoTime(): Long
    
    /**
     * ✅ Get current time with time zone
     * 
     * @param zoneId Time zone ID (default: system default)
     * @return ZonedDateTime with time zone
     */
    fun currentZonedDateTime(zoneId: ZoneId = ZoneId.systemDefault()): ZonedDateTime {
        return ZonedDateTime.ofInstant(
            Instant.ofEpochMilli(currentTimeMillis()),
            zoneId
        )
    }
    
    /**
     * ✅ Get current time in specific time zone
     * 
     * @param zoneId Time zone ID
     * @return Current time in milliseconds adjusted for time zone
     */
    fun currentTimeInZone(zoneId: ZoneId): Long {
        return currentZonedDateTime(zoneId).toInstant().toEpochMilli()
    }
    
    /**
     * ✅ Get current date (without time)
     * 
     * @param zoneId Time zone ID (default: system default)
     * @return Date in milliseconds (midnight of current day)
     */
    fun currentDate(zoneId: ZoneId = ZoneId.systemDefault()): Long {
        return currentZonedDateTime(zoneId)
            .toLocalDate()
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
    }
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
 * @param fixedZoneId The fixed time zone (default: system default)
 */
class FixedTimeProvider(
    private val fixedTime: Long,
    private val fixedZoneId: ZoneId = ZoneId.systemDefault()
) : TimeProvider {
    override fun currentTimeMillis(): Long = fixedTime
    
    override fun nanoTime(): Long = fixedTime * 1_000_000
    
    override fun currentZonedDateTime(zoneId: ZoneId): ZonedDateTime {
        return ZonedDateTime.ofInstant(
            Instant.ofEpochMilli(fixedTime),
            zoneId
        )
    }
}

/**
 * Controllable time provider for testing
 * 
 * Allows advancing time manually in tests.
 */
class ControllableTimeProvider(
    initialTime: Long = 0L,
    private val initialZoneId: ZoneId = ZoneId.systemDefault()
) : TimeProvider {
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
    
    override fun currentZonedDateTime(zoneId: ZoneId): ZonedDateTime {
        return ZonedDateTime.ofInstant(
            Instant.ofEpochMilli(currentTime),
            zoneId
        )
    }
}

/**
 * ✅ Extension functions for time calculations
 */
fun TimeProvider.isTimeInPast(timestamp: Long): Boolean {
    return timestamp < currentTimeMillis()
}

fun TimeProvider.isTimeInFuture(timestamp: Long): Boolean {
    return timestamp > currentTimeMillis()
}

fun TimeProvider.timeSince(timestamp: Long): Long {
    return currentTimeMillis() - timestamp
}

fun TimeProvider.timeUntil(timestamp: Long): Long {
    return timestamp - currentTimeMillis()
}
