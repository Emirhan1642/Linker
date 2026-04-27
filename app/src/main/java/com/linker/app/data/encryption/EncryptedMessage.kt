package com.linker.app.data.encryption

/**
 * Encrypted message using Signal Protocol
 * 
 * Contains serialized SignalMessage or PreKeySignalMessage from libsignal-client.
 * The signalMessage field contains the complete encrypted message that can be
 * transmitted and decrypted by the recipient.
 */
data class EncryptedMessage(
    val signalMessage: ByteArray  // Serialized SignalMessage from libsignal-client
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as EncryptedMessage
        
        return signalMessage.contentEquals(other.signalMessage)
    }
    
    override fun hashCode(): Int {
        return signalMessage.contentHashCode()
    }
}
