package com.linker.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores Signal Protocol pre-keys
 */
@Entity(tableName = "signal_prekeys")
data class SignalPreKeyEntity(
    @PrimaryKey
    val preKeyId: Int,
    val preKeyRecord: ByteArray,      // Serialized pre-key record
    val createdAt: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as SignalPreKeyEntity
        
        if (preKeyId != other.preKeyId) return false
        if (!preKeyRecord.contentEquals(other.preKeyRecord)) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = preKeyId
        result = 31 * result + preKeyRecord.contentHashCode()
        return result
    }
}

/**
 * Stores Signal Protocol signed pre-keys
 */
@Entity(tableName = "signal_signed_prekeys")
data class SignalSignedPreKeyEntity(
    @PrimaryKey
    val signedPreKeyId: Int,
    val signedPreKeyRecord: ByteArray,  // Serialized signed pre-key record
    val createdAt: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as SignalSignedPreKeyEntity
        
        if (signedPreKeyId != other.signedPreKeyId) return false
        if (!signedPreKeyRecord.contentEquals(other.signedPreKeyRecord)) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = signedPreKeyId
        result = 31 * result + signedPreKeyRecord.contentHashCode()
        return result
    }
}
