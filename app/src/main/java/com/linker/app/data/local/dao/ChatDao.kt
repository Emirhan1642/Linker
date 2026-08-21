package com.linker.app.data.local.dao

import androidx.room.*
import com.linker.app.data.local.entity.ChatEntity
import kotlinx.coroutines.flow.Flow

/**
 * Chat DAO - Data Access Object for chats
 */
@Dao
interface ChatDao {
    
    @Query("SELECT * FROM chats WHERE chatId = :chatId")
    suspend fun getChatById(chatId: String): ChatEntity?
    
    @Query("SELECT * FROM chats WHERE chatId = :chatId")
    fun observeChatById(chatId: String): Flow<ChatEntity?>
    
    @Query("SELECT * FROM chats WHERE isArchived = 0 ORDER BY isPinned DESC, lastMessageAt DESC")
    fun observeActiveChats(): Flow<List<ChatEntity>>
    

    @Query("SELECT * FROM chats WHERE isArchived = 1 ORDER BY lastMessageAt DESC")
    fun observeArchivedChats(): Flow<List<ChatEntity>>
    
    @Query("SELECT * FROM chats WHERE isPinned = 1 ORDER BY lastMessageAt DESC")
    fun observePinnedChats(): Flow<List<ChatEntity>>
    
    @Query("SELECT COALESCE(SUM(unreadCount), 0) FROM chats WHERE isArchived = 0 AND isMuted = 0")
    fun observeTotalUnreadCount(): Flow<Int>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: ChatEntity)
    
    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChats(chats: List<ChatEntity>)
    
    @Update
    suspend fun updateChat(chat: ChatEntity)
    
    @Delete
    suspend fun deleteChat(chat: ChatEntity)
    
    @Query("DELETE FROM chats WHERE chatId = :chatId")
    suspend fun deleteChatById(chatId: String)
    
    @Query("UPDATE chats SET unreadCount = 0, updatedAt = :timestamp WHERE chatId = :chatId")
    suspend fun markAsRead(chatId: String, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE chats SET isPinned = :isPinned, updatedAt = :timestamp WHERE chatId = :chatId")
    suspend fun updatePinStatus(chatId: String, isPinned: Boolean, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE chats SET isMuted = :isMuted, updatedAt = :timestamp WHERE chatId = :chatId")
    suspend fun updateMuteStatus(chatId: String, isMuted: Boolean, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE chats SET isArchived = :isArchived, updatedAt = :timestamp WHERE chatId = :chatId")
    suspend fun updateArchiveStatus(chatId: String, isArchived: Boolean, timestamp: Long = System.currentTimeMillis())
    
    @Transaction
    @Query("UPDATE chats SET lastMessageId = :messageId, lastMessageText = SUBSTR(:text, 1, 200), lastMessageAt = :timestamp, updatedAt = :timestamp, unreadCount = CASE WHEN :incrementUnread THEN unreadCount + 1 ELSE unreadCount END WHERE chatId = :chatId")
    suspend fun updateLastMessage(chatId: String, messageId: String, text: String, timestamp: Long, incrementUnread: Boolean = false)

    @Query("UPDATE chats SET lastMessageId = NULL, lastMessageText = NULL, lastMessageAt = NULL, updatedAt = :timestamp WHERE chatId = :chatId")
    suspend fun clearLastMessage(chatId: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE chats SET isFavorited = :isFavorited, updatedAt = :timestamp WHERE chatId = :chatId")
    suspend fun updateFavoriteStatus(chatId: String, isFavorited: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE chats SET isBlocked = :isBlocked, updatedAt = :timestamp WHERE chatId = :chatId")
    suspend fun updateBlockedStatus(chatId: String, isBlocked: Boolean, timestamp: Long = System.currentTimeMillis())

    @Transaction
    @Query("UPDATE chats SET isArchived = :isArchived, updatedAt = :timestamp WHERE chatId IN (:chatIds)")
    suspend fun batchUpdateArchiveStatus(chatIds: List<String>, isArchived: Boolean, timestamp: Long = System.currentTimeMillis())

    @Transaction
    @Query("UPDATE chats SET unreadCount = 0, updatedAt = :timestamp WHERE chatId IN (:chatIds)")
    suspend fun markChatsAsRead(chatIds: List<String>, timestamp: Long = System.currentTimeMillis())
}
