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
    
    @Query("SELECT SUM(unreadCount) FROM chats WHERE isArchived = 0 AND isMuted = 0")
    fun observeTotalUnreadCount(): Flow<Int?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: ChatEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChats(chats: List<ChatEntity>)
    
    @Update
    suspend fun updateChat(chat: ChatEntity)
    
    @Delete
    suspend fun deleteChat(chat: ChatEntity)
    
    @Query("DELETE FROM chats WHERE chatId = :chatId")
    suspend fun deleteChatById(chatId: String)
    
    @Query("UPDATE chats SET unreadCount = 0 WHERE chatId = :chatId")
    suspend fun markAsRead(chatId: String)
    
    @Query("UPDATE chats SET isPinned = :isPinned WHERE chatId = :chatId")
    suspend fun updatePinStatus(chatId: String, isPinned: Boolean)
    
    @Query("UPDATE chats SET isMuted = :isMuted WHERE chatId = :chatId")
    suspend fun updateMuteStatus(chatId: String, isMuted: Boolean)
    
    @Query("UPDATE chats SET isArchived = :isArchived WHERE chatId = :chatId")
    suspend fun updateArchiveStatus(chatId: String, isArchived: Boolean)
    
    @Query("UPDATE chats SET lastMessageId = :messageId, lastMessageText = :text, lastMessageAt = :timestamp, unreadCount = unreadCount + 1 WHERE chatId = :chatId")
    suspend fun updateLastMessage(chatId: String, messageId: String, text: String, timestamp: Long)
}
