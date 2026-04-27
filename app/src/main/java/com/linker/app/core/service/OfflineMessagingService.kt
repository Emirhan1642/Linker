package com.linker.app.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.linker.app.R
import com.linker.app.data.ble.BLEMeshManager
import com.linker.app.data.permission.PermissionManager
import com.linker.app.data.queue.MessageQueueProcessor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service for offline messaging.
 * 
 * Implements Requirements 11.1-11.8:
 * - Runs as foreground service with persistent notification
 * - Manages BLE mesh network lifecycle
 * - Handles scanning and advertising
 * - Processes message queue in background
 * - Supports start/stop/toggle actions
 */
@AndroidEntryPoint
class OfflineMessagingService : Service() {
    
    @Inject
    lateinit var bleMeshManager: BLEMeshManager
    
    @Inject
    lateinit var messageQueueProcessor: MessageQueueProcessor
    
    @Inject
    lateinit var permissionManager: PermissionManager
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    private var isScanning = false
    
    companion object {
        const val ACTION_START = "com.linker.app.ACTION_START_OFFLINE_MESSAGING"
        const val ACTION_STOP = "com.linker.app.ACTION_STOP_OFFLINE_MESSAGING"
        const val ACTION_TOGGLE_SCANNING = "com.linker.app.ACTION_TOGGLE_SCANNING"
        
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "offline_messaging_channel"
        const val CHANNEL_NAME = "Offline Messaging"
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Create notification channel
        createNotificationChannel()
        
        // Start foreground service with notification
        startForeground(NOTIFICATION_ID, createNotification())
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startOfflineMessaging()
            }
            ACTION_STOP -> {
                stopOfflineMessaging()
                stopSelf()
            }
            ACTION_TOGGLE_SCANNING -> {
                toggleScanning()
            }
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        stopOfflineMessaging()
        serviceScope.cancel()
    }
    
    /**
     * Start offline messaging (BLE mesh network and message processing).
     */
    private fun startOfflineMessaging() {
        serviceScope.launch {
            try {
                // Check permissions
                if (!permissionManager.hasBluetoothPermissions()) {
                    // Cannot start without permissions
                    updateNotification("Waiting for Bluetooth permissions")
                    return@launch
                }
                
                // Initialize BLE mesh manager
                bleMeshManager.initialize()
                
                // Start mesh network
                bleMeshManager.startMeshNetwork()
                
                // Start scanning
                bleMeshManager.startScanning()
                isScanning = true
                
                // Start advertising
                bleMeshManager.startAdvertising()
                
                // Process message queue periodically
                startQueueProcessing()
                
                updateNotification("Offline messaging active")
                
            } catch (e: Exception) {
                updateNotification("Error: ${e.message}")
            }
        }
    }
    
    /**
     * Stop offline messaging.
     */
    private fun stopOfflineMessaging() {
        serviceScope.launch {
            try {
                // Stop scanning
                bleMeshManager.stopScanning()
                isScanning = false
                
                // Stop advertising
                bleMeshManager.stopAdvertising()
                
                // Stop mesh network
                bleMeshManager.stopMeshNetwork()
                
                updateNotification("Offline messaging stopped")
                
            } catch (e: Exception) {
                // Log error but continue shutdown
            }
        }
    }
    
    /**
     * Toggle BLE scanning on/off.
     */
    private fun toggleScanning() {
        serviceScope.launch {
            try {
                if (isScanning) {
                    bleMeshManager.stopScanning()
                    isScanning = false
                    updateNotification("Scanning paused")
                } else {
                    bleMeshManager.startScanning()
                    isScanning = true
                    updateNotification("Scanning active")
                }
            } catch (e: Exception) {
                updateNotification("Error toggling scan: ${e.message}")
            }
        }
    }
    
    /**
     * Start periodic message queue processing.
     */
    private fun startQueueProcessing() {
        serviceScope.launch {
            // Process queue every 10 seconds
            while (true) {
                try {
                    messageQueueProcessor.processQueue()
                    kotlinx.coroutines.delay(10_000)
                } catch (e: Exception) {
                    // Log error but continue processing
                    kotlinx.coroutines.delay(10_000)
                }
            }
        }
    }
    
    /**
     * Create notification channel for Android O+.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for offline messaging service"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Create foreground service notification.
     */
    private fun createNotification(contentText: String = "Offline messaging is running"): Notification {
        // Create intent to open app
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        // Create stop action
        val stopIntent = Intent(this, OfflineMessagingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Offline Messaging")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_bluetooth_outline) // TODO: Add proper icon
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_close_circle_bold, // TODO: Add proper icon
                "Stop",
                stopPendingIntent
            )
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    /**
     * Update notification with new content.
     */
    private fun updateNotification(contentText: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification(contentText))
    }
}
