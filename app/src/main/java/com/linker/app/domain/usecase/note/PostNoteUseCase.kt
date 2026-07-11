package com.linker.app.domain.usecase.note

import com.linker.app.core.util.Result
import com.linker.app.domain.model.Note
import com.linker.app.domain.repository.NoteRepository
import javax.inject.Inject

class PostNoteUseCase @Inject constructor(
    private val noteRepository: NoteRepository
) {
    suspend operator fun invoke(content: String, backgroundColor: String? = null, textColor: String? = null): Result<Note> {
        if (content.isBlank()) return Result.Error("Note content cannot be empty")
        if (content.codePointCount(0, content.length) > Note.Text.MAX_TEXT_CONTENT_LENGTH) {
            return Result.Error("Note content too long (max ${Note.Text.MAX_TEXT_CONTENT_LENGTH})")
        }
        val sanitized = content.replace("<", "&lt;").replace(">", "&gt;").trim()
        return noteRepository.postNote(sanitized, backgroundColor, textColor)
    }
}
