package com.linker.app.core.util

import java.util.concurrent.TimeUnit

/**
 * Centralized time constants for consistent time calculations across the app
 */
object TimeConstants {
    // Millisecond constants
    const val MINUTE_MS = 60_000L
    const val HOUR_MS = 3_600_000L
    const val DAY_MS = 86_400_000L
    const val WEEK_MS = 604_800_000L
    
    // Alternative using TimeUnit (more readable)
    val ONE_MINUTE_MS = TimeUnit.MINUTES.toMillis(1)
    val ONE_HOUR_MS = TimeUnit.HOURS.toMillis(1)
    val ONE_DAY_MS = TimeUnit.DAYS.toMillis(1)
    val ONE_WEEK_MS = TimeUnit.DAYS.toMillis(7)
    val TWENTY_FOUR_HOURS_MS = TimeUnit.DAYS.toMillis(1)
}

/**
 * Format timestamp to relative time string (e.g., "5m ago", "2h ago")
 */
fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < TimeConstants.MINUTE_MS -> "just now"
        diff < TimeConstants.HOUR_MS -> "${diff / TimeConstants.MINUTE_MS}m ago"
        diff < TimeConstants.DAY_MS -> "${diff / TimeConstants.HOUR_MS}h ago"
        diff < TimeConstants.WEEK_MS -> "${diff / TimeConstants.DAY_MS}d ago"
        else -> "${diff / TimeConstants.WEEK_MS}w ago"
    }
}
