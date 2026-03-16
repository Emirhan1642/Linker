package com.linker.app.data.local.dao

import androidx.room.*
import com.linker.app.data.local.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

/**
 * Note DAO - Data Access Object for notes
 */
@Dao
interface NoteDao {
    
    @Query("SELECT * FROM notes WHERE noteId = :noteId")
    suspend fun getNoteById(noteId: String): NoteEntity?
    
    @Query("SELECT * FROM notes WHERE authorId = :authorId AND expiresAt > :currentTime ORDER BY createdAt DESC")
    suspend fun getNotesByAuthor(authorId: String, currentTime: Long = System.currentTimeMillis()): List<NoteEntity>
    
    @Query("SELECT * FROM notes WHERE expiresAt > :currentTime ORDER BY createdAt DESC")
    fun observeActiveNotes(currentTime: Long = System.currentTimeMillis()): Flow<List<NoteEntity>>
    
    @Query("SELECT * FROM notes WHERE authorId IN (:authorIds) AND expiresAt > :currentTime ORDER BY createdAt DESC")
    fun observeNotesByAuthors(authorIds: List<String>, currentTime: Long = System.currentTimeMillis()): Flow<List<NoteEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: List<NoteEntity>)
    
    @Update
    suspend fun updateNote(note: NoteEntity)
    
    @Delete
    suspend fun deleteNote(note: NoteEntity)
    
    @Query("DELETE FROM notes WHERE noteId = :noteId")
    suspend fun deleteNoteById(noteId: String)
    
    @Query("DELETE FROM notes WHERE expiresAt < :currentTime")
    suspend fun deleteExpiredNotes(currentTime: Long = System.currentTimeMillis())
    
    @Query("SELECT COUNT(*) FROM notes WHERE authorId = :authorId AND expiresAt > :currentTime")
    suspend fun getActiveNoteCount(authorId: String, currentTime: Long = System.currentTimeMillis()): Int
}
