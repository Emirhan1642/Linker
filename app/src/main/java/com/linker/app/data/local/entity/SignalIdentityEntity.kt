package com.linker.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores Signal Protocol identity keys
 */
@Entity(tableName = "signal_identities")
data class SignalIdentityEntity(
    @PrimaryKey
    val address: String,              // SignalProtocolAddress as string (name:deviceId)
    val identityKey: ByteArray,       // Public identity key
    val trustLevel: Int = 0,          // Trust level (0 = untrusted, 1 = trusted)
    val createdAt: Long,
    val updatedAt: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as SignalIdentityEntity
        
        if (address != other.address) return false
        if (!identityKey.contentEquals(other.identityKey)) return false
        if (trustLevel != other.trustLevel) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = address.hashCode()
        result = 31 * result + identityKey.contentHashCode()
        result = 31 * result + trustLevel
        return result
    }
}
