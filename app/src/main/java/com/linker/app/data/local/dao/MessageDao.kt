package com.linker.app.data.local.dao

import androidx.room.*
import com.linker.app.data.local.entity.MessageEntity
import com.linker.app.data.local.entity.MessageStatus
import kotlinx.coroutines.flow.Flow

/**
 * Message DAO - Data Access Object for messages
 */
@Dao
interface MessageDao {
    
    @Query("SELECT * FROM messages WHERE messageId = :messageId")
    suspend fun getMessageById(messageId: String): MessageEntity?
    
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAt ASC")
    fun observeMessagesByChat(chatId: String): Flow<List<MessageEntity>>
    
    @Query("SELECT * FROM messages WHERE chatId = :chatId AND isDeleted = 0 ORDER BY createdAt ASC LIMIT :limit OFFSET :offset")
    suspend fun getMessagesByChat(chatId: String, limit: Int = 50, offset: Int = 0): List<MessageEntity>
    
    @Query("SELECT * FROM messages WHERE chatId = :chatId AND messageStatus = :status")
    suspend fun getMessagesByStatus(chatId: String, status: MessageStatus): List<MessageEntity>
    
    @Query("SELECT * FROM messages WHERE messageStatus = :status")
    fun observeMessagesByStatus(status: MessageStatus): Flow<List<MessageEntity>>
    
    @Query("SELECT * FROM messages WHERE replyToMessageId = :messageId")
    suspend fun getReplies(messageId: String): List<MessageEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)
    
    @Update
    suspend fun updateMessage(message: MessageEntity)
    
    @Delete
    suspend fun deleteMessage(message: MessageEntity)
    
    @Query("DELETE FROM messages WHERE messageId = :messageId")
    suspend fun deleteMessageById(messageId: String)
    
    @Query("UPDATE messages SET isDeleted = 1, deletedForEveryone = :forEveryone WHERE messageId = :messageId")
    suspend fun markAsDeleted(messageId: String, forEveryone: Boolean)
    
    @Query("UPDATE messages SET messageStatus = :status WHERE messageId = :messageId")
    suspend fun updateMessageStatus(messageId: String, status: MessageStatus)
    
    @Query("UPDATE messages SET content = :content, isEdited = 1, updatedAt = :timestamp WHERE messageId = :messageId")
    suspend fun editMessage(messageId: String, content: String, timestamp: Long)
    
    @Query("UPDATE messages SET messageStatus = :status WHERE chatId = :chatId AND senderId != :currentUserId AND messageStatus != 'READ'")
    suspend fun markChatMessagesAsRead(chatId: String, currentUserId: String, status: MessageStatus)
    
    @Query("SELECT COUNT(*) FROM messages WHERE chatId = :chatId AND isDeleted = 0")
    suspend fun getMessageCount(chatId: String): Int
    
    @Query("SELECT * FROM messages WHERE chatId = :chatId AND (content LIKE '%' || :query || '%') AND isDeleted = 0 ORDER BY createdAt DESC")
    suspend fun searchMessagesInChat(chatId: String, query: String): List<MessageEntity>

    // ✅ PAGINATION: Load messages before a specific timestamp
    @Query("""
        SELECT * FROM messages 
        WHERE chatId = :chatId 
        AND createdAt < :beforeTimestamp 
        AND isDeleted = 0 
        ORDER BY createdAt DESC 
        LIMIT :limit
    """)
    suspend fun getMessagesBeforeTimestamp(
        chatId: String,
        beforeTimestamp: Long,
        limit: Int = 50
    ): List<MessageEntity>

    // ✅ PAGINATION: Get oldest message timestamp for loading more
    @Query("SELECT MIN(createdAt) FROM messages WHERE chatId = :chatId AND isDeleted = 0")
    suspend fun getOldestMessageTimestamp(chatId: String): Long?
}
