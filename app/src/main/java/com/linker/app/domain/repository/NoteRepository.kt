package com.linker.app.domain.repository

import com.linker.app.domain.model.Note
import com.linker.app.core.util.Result
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Note operations
 *
 * Notes are temporary content that expires after 24 hours
 */
interface NoteRepository {

    /**
     * Observe all active (non-expired) notes in real-time
     *
     * @return Flow of note list, ordered by expiration time
     */
    fun observeActiveNotes(): Flow<List<Note>>

    /**
     * Post a new text note that expires in 24 hours
     *
     * @param content The note text content
     * @return Result with created Note or Error
     */
    suspend fun postNote(content: String): Result<Note>

    /**
     * Delete a note by ID
     *
     * @param noteId The note identifier
     * @return Result with Unit on success or Error
     */
    suspend fun deleteNote(noteId: String): Result<Unit>
}
