package com.linker.app.data.local.converter

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.linker.app.domain.model.NoteReference

class NoteReferenceConverter {
    private val gson = Gson()

    @TypeConverter
    fun fromNoteReference(noteRef: NoteReference?): String? {
        return noteRef?.let { gson.toJson(it) }
    }

    @TypeConverter
    fun toNoteReference(json: String?): NoteReference? {
        return json?.takeIf { it.isNotBlank() }?.let { 
            try {
                gson.fromJson(it, NoteReference::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }
}
