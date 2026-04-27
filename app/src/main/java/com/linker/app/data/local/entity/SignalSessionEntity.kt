package com.linker.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores Signal Protocol session state
 */
@Entity(tableName = "signal_sessions")
data class SignalSessionEntity(
    @PrimaryKey
    val address: String,              // SignalProtocolAddress as string (name:deviceId)
    val sessionRecord: ByteArray,     // Serialized session record
    val createdAt: Long,
    val updatedAt: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as SignalSessionEntity
        
        if (address != other.address) return false
        if (!sessionRecord.contentEquals(other.sessionRecord)) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = address.hashCode()
        result = 31 * result + sessionRecord.contentHashCode()
        return result
    }
}
