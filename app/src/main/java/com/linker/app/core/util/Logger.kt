package com.linker.app.core.util

import android.util.Log
import com.linker.app.BuildConfig

/**
 * Logging utility that automatically checks BuildConfig.DEBUG
 * to prevent logging in production builds.
 * 
 * Usage:
 * ```
 * Logger.d("MyTag", "Debug message")
 * Logger.e("MyTag", "Error message", exception)
 * ```
 * 
 * Debug and verbose logs are only printed in debug builds.
 * Error and warning logs are always printed.
 */
object Logger {
    
    /**
     * Send a DEBUG log message.
     * Only logs in debug builds.
     */
    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message)
        }
    }
    
    /**
     * Send a VERBOSE log message.
     * Only logs in debug builds.
     */
    fun v(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.v(tag, message)
        }
    }
    
    /**
     * Send an INFO log message.
     * Only logs in debug builds.
     */
    fun i(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.i(tag, message)
        }
    }
    
    /**
     * Send a WARN log message.
     * Always logs (even in production) as warnings are important.
     */
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.w(tag, message, throwable)
        } else {
            Log.w(tag, message)
        }
    }
    
    /**
     * Send an ERROR log message.
     * Always logs (even in production) as errors are critical.
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }
    
    /**
     * Send a "What a Terrible Failure" log message.
     * Always logs (even in production) as WTF indicates critical issues.
     */
    fun wtf(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.wtf(tag, message, throwable)
        } else {
            Log.wtf(tag, message)
        }
    }
}
