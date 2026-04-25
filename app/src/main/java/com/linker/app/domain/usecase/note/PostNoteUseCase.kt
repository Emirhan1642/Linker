package com.linker.app.domain.usecase.note

import com.linker.app.core.util.Result
import com.linker.app.domain.model.Note
import com.linker.app.domain.repository.NoteRepository
import javax.inject.Inject

class PostNoteUseCase @Inject constructor(
    private val noteRepository: NoteRepository
) {
    suspend operator fun invoke(content: String): Result<Note> {
        return noteRepository.postNote(content)
    }
}
