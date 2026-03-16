package com.linker.app.data.local.dao

import androidx.room.*
import com.linker.app.data.local.entity.NotificationEntity
import com.linker.app.data.local.entity.NotificationType
import kotlinx.coroutines.flow.Flow

/**
 * Notification DAO - Data Access Object for notifications
 */
@Dao
interface NotificationDao {
    
    @Query("SELECT * FROM notifications WHERE notificationId = :notificationId")
    suspend fun getNotificationById(notificationId: String): NotificationEntity?
    
    @Query("SELECT * FROM notifications ORDER BY createdAt DESC")
    fun observeAllNotifications(): Flow<List<NotificationEntity>>
    
    @Query("SELECT * FROM notifications WHERE isRead = 0 ORDER BY createdAt DESC")
    fun observeUnreadNotifications(): Flow<List<NotificationEntity>>
    
    @Query("SELECT * FROM notifications WHERE notificationType = :type ORDER BY createdAt DESC")
    suspend fun getNotificationsByType(type: NotificationType): List<NotificationEntity>
    
    @Query("SELECT COUNT(*) FROM notifications WHERE isRead = 0")
    fun observeUnreadCount(): Flow<Int>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotifications(notifications: List<NotificationEntity>)
    
    @Update
    suspend fun updateNotification(notification: NotificationEntity)
    
    @Delete
    suspend fun deleteNotification(notification: NotificationEntity)
    
    @Query("DELETE FROM notifications WHERE notificationId = :notificationId")
    suspend fun deleteNotificationById(notificationId: String)
    
    @Query("UPDATE notifications SET isRead = 1 WHERE notificationId = :notificationId")
    suspend fun markAsRead(notificationId: String)
    
    @Query("UPDATE notifications SET isRead = 1")
    suspend fun markAllAsRead()
    
    @Query("DELETE FROM notifications")
    suspend fun deleteAllNotifications()
    
    @Query("DELETE FROM notifications WHERE createdAt < :timestamp")
    suspend fun deleteOldNotifications(timestamp: Long)
}
