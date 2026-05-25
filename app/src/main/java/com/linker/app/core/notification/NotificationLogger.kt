package com.linker.app.core.notification

import android.util.Log

import com.linker.app.BuildConfig

object NotificationLogger {
    private const val TAG = "LinkerNotification"

    fun d(message: String, vararg args: Any?) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, String.format(message, *args))
        }
    }

    fun w(message: String, throwable: Throwable? = null) {
        Log.w(TAG, message, throwable)
    }

    fun e(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
        try {
            android.util.Log.e("NotificationLogger", "ERROR: $message", throwable)
        } catch (e: Exception) {
            android.util.Log.e("NotificationLogger", "Failed to log error", e)
        }
    }
}
