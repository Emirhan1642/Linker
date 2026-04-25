package com.linker.app.domain.usecase.note

import com.linker.app.domain.model.Note
import com.linker.app.domain.repository.NoteRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveActiveNotesUseCase @Inject constructor(
    private val noteRepository: NoteRepository
) {
    operator fun invoke(): Flow<List<Note>> {
        return noteRepository.observeActiveNotes()
    }
}
