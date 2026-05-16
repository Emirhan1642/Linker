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

/**
 * Stores Signal Protocol Kyber pre-keys (for PQXDH)
 */
@Entity(tableName = "signal_kyber_prekeys")
data class SignalKyberPreKeyEntity(
    @PrimaryKey
    val kyberPreKeyId: Int,
    val kyberPreKeyRecord: ByteArray,  // Serialized Kyber pre-key record
    val createdAt: Long,
    val isUsed: Boolean = false        // Track if key has been used
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as SignalKyberPreKeyEntity
        
        if (kyberPreKeyId != other.kyberPreKeyId) return false
        if (!kyberPreKeyRecord.contentEquals(other.kyberPreKeyRecord)) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = kyberPreKeyId
        result = 31 * result + kyberPreKeyRecord.contentHashCode()
        return result
    }
}

/**
 * Stores Signal Protocol sender keys (for group messaging)
 */
@Entity(
    tableName = "signal_sender_keys",
    primaryKeys = ["senderAddress", "distributionId"]
)
data class SignalSenderKeyEntity(
    val senderAddress: String,         // Format: "userId:deviceId"
    val distributionId: String,        // UUID as string
    val senderKeyRecord: ByteArray,    // Serialized sender key record
    val createdAt: Long,
    val updatedAt: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as SignalSenderKeyEntity
        
        if (senderAddress != other.senderAddress) return false
        if (distributionId != other.distributionId) return false
        if (!senderKeyRecord.contentEquals(other.senderKeyRecord)) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = senderAddress.hashCode()
        result = 31 * result + distributionId.hashCode()
        result = 31 * result + senderKeyRecord.contentHashCode()
        return result
    }
}
