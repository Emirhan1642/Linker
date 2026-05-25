package com.linker.app.core.util

import java.util.concurrent.TimeUnit

/**
 * Centralized time constants for consistent time calculations across the app
 * 
 * ✅ ENHANCED: Organized constants, added extension functions, removed duplicates
 */
object TimeConstants {
    // ✅ Base time units
    val MILLISECOND_MS = 1L
    val SECOND_MS = TimeUnit.SECONDS.toMillis(1)
    val MINUTE_MS = TimeUnit.MINUTES.toMillis(1)
    val HOUR_MS = TimeUnit.HOURS.toMillis(1)
    val DAY_MS = TimeUnit.DAYS.toMillis(1)
    val WEEK_MS = TimeUnit.DAYS.toMillis(7)
    val MONTH_MS = TimeUnit.DAYS.toMillis(30)  // Approximate
    val YEAR_MS = TimeUnit.DAYS.toMillis(365)  // Approximate
    
    // ✅ Common durations
    object Duration {
        val FIVE_SECONDS = TimeUnit.SECONDS.toMillis(5)
        val TEN_SECONDS = TimeUnit.SECONDS.toMillis(10)
        val THIRTY_SECONDS = TimeUnit.SECONDS.toMillis(30)
        val ONE_MINUTE = MINUTE_MS
        val FIVE_MINUTES = TimeUnit.MINUTES.toMillis(5)
        val TEN_MINUTES = TimeUnit.MINUTES.toMillis(10)
        val FIFTEEN_MINUTES = TimeUnit.MINUTES.toMillis(15)
        val THIRTY_MINUTES = TimeUnit.MINUTES.toMillis(30)
        val FORTY_FIVE_MINUTES = TimeUnit.MINUTES.toMillis(45)
        val ONE_HOUR = HOUR_MS
        val TWO_HOURS = TimeUnit.HOURS.toMillis(2)
        val THREE_HOURS = TimeUnit.HOURS.toMillis(3)
        val SIX_HOURS = TimeUnit.HOURS.toMillis(6)
        val TWELVE_HOURS = TimeUnit.HOURS.toMillis(12)
        val ONE_DAY = DAY_MS
        val TWO_DAYS = TimeUnit.DAYS.toMillis(2)
        val THREE_DAYS = TimeUnit.DAYS.toMillis(3)
        val ONE_WEEK = WEEK_MS
        val TWO_WEEKS = TimeUnit.DAYS.toMillis(14)
        val ONE_MONTH = MONTH_MS
        val THREE_MONTHS = TimeUnit.DAYS.toMillis(90)
        val SIX_MONTHS = TimeUnit.DAYS.toMillis(180)
        val ONE_YEAR = YEAR_MS
    }
    
    // ✅ Timeout constants
    object Timeout {
        val INSTANT = TimeUnit.SECONDS.toMillis(1)
        val SHORT = TimeUnit.SECONDS.toMillis(5)
        val MEDIUM = TimeUnit.SECONDS.toMillis(15)
        val LONG = TimeUnit.SECONDS.toMillis(30)
        val VERY_LONG = TimeUnit.MINUTES.toMillis(1)
        val EXTENDED = TimeUnit.MINUTES.toMillis(5)
    }
    
    // ✅ Cache expiration constants
    object CacheExpiration {
        val SHORT = Duration.FIVE_MINUTES
        val MEDIUM = Duration.THIRTY_MINUTES
        val LONG = Duration.ONE_HOUR
        val VERY_LONG = Duration.ONE_DAY
    }
    
    // ✅ Session timeout constants
    object SessionTimeout {
        val SHORT = Duration.FIVE_MINUTES
        val MEDIUM = Duration.THIRTY_MINUTES
        val LONG = Duration.ONE_HOUR
        val EXTENDED = Duration.ONE_DAY
    }
}

/**
 * ✅ Extension functions for time calculations
 */
fun Long.toSeconds(): Long = this / TimeConstants.SECOND_MS
fun Long.toMinutes(): Long = this / TimeConstants.MINUTE_MS
fun Long.toHours(): Long = this / TimeConstants.HOUR_MS
fun Long.toDays(): Long = this / TimeConstants.DAY_MS

fun Int.seconds(): Long = this * TimeConstants.SECOND_MS
fun Int.minutes(): Long = this * TimeConstants.MINUTE_MS
fun Int.hours(): Long = this * TimeConstants.HOUR_MS
fun Int.days(): Long = this * TimeConstants.DAY_MS

/**
 * ✅ Format relative time with localization support
 */
fun formatRelativeTime(
    timestamp: Long,
    context: android.content.Context? = null,
    useAndroidFormatter: Boolean = true
): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    // ✅ Use Android's built-in formatter for localization
    if (useAndroidFormatter && context != null) {
        return android.text.format.DateUtils.getRelativeTimeSpanString(
            timestamp,
            now,
            android.text.format.DateUtils.MINUTE_IN_MILLIS,
            android.text.format.DateUtils.FORMAT_ABBREV_RELATIVE
        ).toString()
    }
    
    // Fallback to simple format
    return when {
        diff < TimeConstants.MINUTE_MS -> "just now"
        diff < TimeConstants.HOUR_MS -> "${diff / TimeConstants.MINUTE_MS}m ago"
        diff < TimeConstants.DAY_MS -> "${diff / TimeConstants.HOUR_MS}h ago"
        diff < TimeConstants.WEEK_MS -> "${diff / TimeConstants.DAY_MS}d ago"
        else -> "${diff / TimeConstants.WEEK_MS}w ago"
    }
}

/**
 * ✅ Format absolute time with localization
 */
fun formatAbsoluteTime(
    timestamp: Long,
    pattern: String = "MMM dd, yyyy HH:mm",
    locale: java.util.Locale = java.util.Locale.getDefault()
): String {
    val dateFormat = java.text.SimpleDateFormat(pattern, locale)
    return dateFormat.format(java.util.Date(timestamp))
}

/**
 * ✅ Format time with smart selection (relative for recent, absolute for old)
 */
fun formatSmartTime(
    timestamp: Long,
    context: android.content.Context? = null,
    relativeThreshold: Long = TimeConstants.WEEK_MS
): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return if (diff < relativeThreshold) {
        formatRelativeTime(timestamp, context)
    } else {
        formatAbsoluteTime(timestamp)
    }
}
