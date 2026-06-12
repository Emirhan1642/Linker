package com.linker.app.domain.usecase.note

import com.linker.app.core.util.Result
import com.linker.app.domain.model.Note
import com.linker.app.domain.repository.NoteRepository
import javax.inject.Inject

class PostNoteUseCase @Inject constructor(
    private val noteRepository: NoteRepository
) {
    suspend operator fun invoke(content: String): Result<Note> {
        if (content.isBlank()) return Result.Error("Note content cannot be empty")
        if (content.length > 500) return Result.Error("Note content too long (max 500)")
        val sanitized = content.replace(Regex("<[^>]*>"), "").trim()
        return noteRepository.postNote(sanitized)
    }
}
