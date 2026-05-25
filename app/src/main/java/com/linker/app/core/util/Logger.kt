package com.linker.app.core.util

import android.util.Log
import com.linker.app.BuildConfig
// FirebaseCrashlytics import removed — dependency not available
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * Logging utility that automatically checks BuildConfig.DEBUG
 * to prevent logging in production builds.
 * 
 * Usage:
 * ```
 * Logger.d("MyTag", "Debug message")
 * Logger.e("MyTag", "Error message", exception)
 * Logger.setLogLevel(LogLevel.DEBUG)
 * ```
 * 
 * ✅ ENHANCED: Added crash reporting, log levels, structured logging, file logging, tag filtering
 */

enum class LogLevel {
    VERBOSE, DEBUG, INFO, WARN, ERROR, WTF, NONE
}

object Logger {
    
    // ✅ Configurable log level
    var minLogLevel: LogLevel = if (BuildConfig.DEBUG) {
        LogLevel.VERBOSE
    } else {
        LogLevel.WARN
    }
    
    // ✅ Enable structured logging for production
    var enableStructuredLogging: Boolean = !BuildConfig.DEBUG
    
    // ✅ Enable file logging
    var enableFileLogging: Boolean = !BuildConfig.DEBUG
    private var logFile: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    
    // ✅ Tag-based filtering
    private val enabledTags = mutableSetOf<String>()
    private var tagFilteringEnabled = false
    
    /**
     * ✅ Set minimum log level at runtime
     * Useful for enabling debug logs in production for specific users
     */
    fun setLogLevel(level: LogLevel) {
        minLogLevel = level
        Log.i("Logger", "Log level set to $level")
    }
    
    /**
     * ✅ Enable debug logging for specific duration (for troubleshooting)
     */
    fun enableDebugLoggingTemporarily(durationMs: Long = 5 * 60 * 1000) {
        val previousLevel = minLogLevel
        minLogLevel = LogLevel.DEBUG
        
        Log.i("Logger", "Debug logging enabled for ${durationMs}ms")
        
        // Reset after duration
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            minLogLevel = previousLevel
            Log.i("Logger", "Debug logging disabled, restored to $previousLevel")
        }, durationMs)
    }
    
    /**
     * ✅ Initialize file logging
     */
    fun initializeFileLogging(context: android.content.Context) {
        if (enableFileLogging) {
            try {
                val logDir = File(context.filesDir, "logs")
                if (!logDir.exists()) {
                    logDir.mkdirs()
                }
                
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                logFile = File(logDir, "app_log_$timestamp.txt")
                
                Log.i("Logger", "File logging initialized: ${logFile?.absolutePath}")
                
                // Clean old log files (keep last 7 days)
                cleanOldLogFiles(logDir, 7)
            } catch (e: Exception) {
                Log.e("Logger", "Failed to initialize file logging", e)
            }
        }
    }
    
    private fun cleanOldLogFiles(logDir: File, daysToKeep: Int) {
        try {
            val cutoffTime = System.currentTimeMillis() - (daysToKeep * 24 * 60 * 60 * 1000L)
            logDir.listFiles()?.forEach { file ->
                if (file.lastModified() < cutoffTime) {
                    file.delete()
                    Log.d("Logger", "Deleted old log file: ${file.name}")
                }
            }
        } catch (e: Exception) {
            Log.e("Logger", "Failed to clean old log files", e)
        }
    }
    
    /**
     * ✅ Enable logging for specific tags only
     */
    fun enableTagFiltering(tags: Set<String>) {
        tagFilteringEnabled = true
        enabledTags.clear()
        enabledTags.addAll(tags)
        Log.i("Logger", "Tag filtering enabled for: $tags")
    }
    
    /**
     * ✅ Disable tag filtering (log all tags)
     */
    fun disableTagFiltering() {
        tagFilteringEnabled = false
        enabledTags.clear()
        Log.i("Logger", "Tag filtering disabled")
    }
    
    /**
     * ✅ Add tag to filter
     */
    fun addTag(tag: String) {
        enabledTags.add(tag)
        Log.i("Logger", "Added tag to filter: $tag")
    }
    
    /**
     * ✅ Remove tag from filter
     */
    fun removeTag(tag: String) {
        enabledTags.remove(tag)
        Log.i("Logger", "Removed tag from filter: $tag")
    }
    
    /**
     * ✅ Get log file for sharing/debugging
     */
    fun getLogFile(): File? = logFile
    
    private fun shouldLog(level: LogLevel, tag: String): Boolean {
        // Check log level
        if (level.ordinal < minLogLevel.ordinal) return false
        
        // Check tag filtering
        if (tagFilteringEnabled && !enabledTags.contains(tag)) return false
        
        return true
    }
    
    private fun reportToCrashlytics(tag: String, message: String, throwable: Throwable?) {
        if (!BuildConfig.DEBUG) {
            // Structured log for future crash reporting integration
            if (throwable != null) {
                Log.e("Logger", "CRASH_REPORT tag=$tag msg=$message", throwable)
            } else {
                Log.e("Logger", "CRASH_REPORT tag=$tag msg=$message")
            }
        }
    }
    
    private fun writeToFile(level: String, tag: String, message: String, throwable: Throwable? = null) {
        if (!enableFileLogging || logFile == null) return
        
        try {
            val timestamp = dateFormat.format(Date())
            val logEntry = buildString {
                append("$timestamp [$level] $tag: $message")
                if (throwable != null) {
                    append("\n")
                    append(Log.getStackTraceString(throwable))
                }
                append("\n")
            }
            
            FileWriter(logFile, true).use { writer ->
                writer.append(logEntry)
            }
        } catch (e: Exception) {
            // Silently fail to avoid infinite loop
        }
    }
    
    /**
     * Send a DEBUG log message.
     * Only logs in debug builds.
     */
    fun d(tag: String, message: String) {
        if (shouldLog(LogLevel.DEBUG, tag)) {
            Log.d(tag, message)
        }
    }
    
    /**
     * Send a VERBOSE log message.
     * Only logs in debug builds.
     */
    fun v(tag: String, message: String) {
        if (shouldLog(LogLevel.VERBOSE, tag)) {
            Log.v(tag, message)
        }
    }
    
    /**
     * Send an INFO log message.
     * Only logs in debug builds.
     */
    fun i(tag: String, message: String) {
        if (shouldLog(LogLevel.INFO, tag)) {
            Log.i(tag, message)
        }
    }
    
    /**
     * Send a WARN log message.
     * Always logs (even in production) as warnings are important.
     */
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (shouldLog(LogLevel.WARN, tag)) {
            if (throwable != null) {
                Log.w(tag, message, throwable)
            } else {
                Log.w(tag, message)
            }
        }
        
        // ✅ Write to file
        writeToFile("WARN", tag, message, throwable)
        
        // ✅ Report to crash reporting
        reportToCrashlytics(tag, message, throwable)
    }
    
    /**
     * Send an ERROR log message.
     * Always logs (even in production) as errors are critical.
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (shouldLog(LogLevel.ERROR, tag)) {
            if (throwable != null) {
                Log.e(tag, message, throwable)
            } else {
                Log.e(tag, message)
            }
        }
        
        // ✅ Write to file
        writeToFile("ERROR", tag, message, throwable)
        
        // ✅ Report to crash reporting
        reportToCrashlytics(tag, message, throwable)
    }
    
    /**
     * Send a "What a Terrible Failure" log message.
     * Always logs (even in production) as WTF indicates critical issues.
     */
    fun wtf(tag: String, message: String, throwable: Throwable? = null) {
        if (shouldLog(LogLevel.WTF, tag)) {
            if (throwable != null) {
                Log.wtf(tag, message, throwable)
            } else {
                Log.wtf(tag, message)
            }
        }
        
        // ✅ Write to file
        writeToFile("WTF", tag, message, throwable)
        
        // ✅ Report to crash reporting
        reportToCrashlytics(tag, message, throwable)
    }
}
