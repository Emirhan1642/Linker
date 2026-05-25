package com.linker.app.data.encryption

import android.util.Base64
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Encrypted message using Signal Protocol
 * 
 * Contains serialized SignalMessage or PreKeySignalMessage from libsignal-client.
 * The signalMessage field contains the complete encrypted message that can be
 * transmitted and decrypted by the recipient.
 * 
 * @throws IllegalArgumentException if signalMessage is empty
 */
@Serializable
data class EncryptedMessage(
    @Serializable(with = ByteArraySerializer::class)
    val signalMessage: ByteArray  // Serialized SignalMessage from libsignal-client
) {
    init {
        require(signalMessage.isNotEmpty()) {
            "Signal message cannot be empty"
        }
    }

    /**
     * Check equality based on content of signalMessage
     * 
     * Uses contentEquals for ByteArray comparison instead of reference equality.
     * 
     * @param other Object to compare with
     * @return true if signalMessage contents are equal, false otherwise
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as EncryptedMessage
        
        return signalMessage.contentEquals(other.signalMessage)
    }
    
    /**
     * Generate hash code based on content of signalMessage
     * 
     * Uses contentHashCode for ByteArray to ensure consistent hashing
     * with equals implementation.
     * 
     * @return Hash code based on signalMessage content
     */
    override fun hashCode(): Int {
        return signalMessage.contentHashCode()
    }
    
    /**
     * Get message size in bytes
     */
    fun size(): Int = signalMessage.size
    
    /**
     * Check if message is valid (non-empty)
     */
    fun isValid(): Boolean = signalMessage.isNotEmpty()
    
    /**
     * Get Base64 encoded representation
     */
    fun toBase64(): String {
        return Base64.encodeToString(signalMessage, Base64.NO_WRAP)
    }
    
    /**
     * Get hex encoded representation (for debugging)
     */
    fun toHex(): String {
        return signalMessage.joinToString("") { "%02x".format(it) }
    }
    
    companion object {
        /**
         * Create from Base64 encoded string
         */
        fun fromBase64(encoded: String): EncryptedMessage {
            val decoded = Base64.decode(encoded, Base64.NO_WRAP)
            return EncryptedMessage(decoded)
        }
        
        /**
         * Create from hex encoded string
         */
        fun fromHex(hex: String): EncryptedMessage {
            val bytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            return EncryptedMessage(bytes)
        }
    }
}

// Custom serializer for ByteArray
object ByteArraySerializer : KSerializer<ByteArray> {
    override val descriptor = PrimitiveSerialDescriptor("ByteArray", PrimitiveKind.STRING)
    
    override fun serialize(encoder: Encoder, value: ByteArray) {
        encoder.encodeString(Base64.encodeToString(value, Base64.NO_WRAP))
    }
    
    override fun deserialize(decoder: Decoder): ByteArray {
        return Base64.decode(decoder.decodeString(), Base64.NO_WRAP)
    }
}
