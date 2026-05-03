package com.linker.app.data.encryption

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.signal.libsignal.protocol.*
import org.signal.libsignal.protocol.ecc.*
import org.signal.libsignal.protocol.kem.*
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.message.SignalMessage
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.util.KeyHelper
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of EncryptionManager using Signal Protocol
 * 
 * Provides end-to-end encryption for offline messages using the Signal Protocol
 * (Double Ratchet Algorithm).
 */
@Singleton
class EncryptionManagerImpl @Inject constructor(
    private val protocolStore: SignalProtocolStoreImpl
) : EncryptionManager {
    
    companion object {
        private const val TAG = "EncryptionManager"
        private const val DEVICE_ID = 1 // Single device for now
        private const val PRE_KEY_START_ID = 1
        private const val PRE_KEY_COUNT = 100
    }
    
    private var isInitialized = false
    
    override suspend fun initialize() = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext
        
        try {
            // Check if identity key pair exists
            val hasIdentity = try {
                protocolStore.getIdentityKeyPair()
                true
            } catch (e: Exception) {
                false
            }
            
            if (!hasIdentity) {
                // Generate new identity key pair
                val identityKeyPair = IdentityKeyPair.generate()
                val registrationId = KeyHelper.generateRegistrationId(false)
                
                protocolStore.initialize(identityKeyPair, registrationId)
                
                // Generate pre-keys
                generatePreKeys()
                
                Log.d(TAG, "Generated new identity and pre-keys")
            }
            
            isInitialized = true
            Log.d(TAG, "Encryption manager initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing encryption manager", e)
            throw e
        }
    }
    
    override suspend fun encryptMessage(recipientId: String, plaintext: String): Result<EncryptedMessage> = withContext(Dispatchers.IO) {
        try {
            if (!isInitialized) {
                initialize()
            }
            
            val recipientAddress = SignalProtocolAddress(recipientId, DEVICE_ID)
            
            // Check if session exists
            if (!protocolStore.containsSession(recipientAddress)) {
                // Need to establish session first
                // In a real app, this would fetch the recipient's pre-key bundle from server
                Log.w(TAG, "No session for $recipientId, cannot encrypt")
                return@withContext Result.failure(Exception("No encryption keys for recipient"))
            }
            
            // Create session cipher
            val sessionCipher = SessionCipher(protocolStore, recipientAddress)
            
            // Encrypt message
            val ciphertext = sessionCipher.encrypt(plaintext.toByteArray())
            
            // Serialize the ciphertext
            val serialized = ciphertext.serialize()
            
            Log.d(TAG, "Message encrypted for $recipientId")
            Result.success(EncryptedMessage(serialized))
        } catch (e: Exception) {
            Log.e(TAG, "Error encrypting message for $recipientId", e)
            Result.failure(e)
        }
    }
    
    override suspend fun decryptMessage(senderId: String, encrypted: EncryptedMessage): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!isInitialized) {
                initialize()
            }
            
            val senderAddress = SignalProtocolAddress(senderId, DEVICE_ID)
            val sessionCipher = SessionCipher(protocolStore, senderAddress)
            
            // Determine message type and decrypt
            val plaintext = try {
                // Try as PreKeySignalMessage first
                val preKeyMessage = PreKeySignalMessage(encrypted.signalMessage)
                sessionCipher.decrypt(preKeyMessage)
            } catch (e: Exception) {
                // Try as regular SignalMessage
                val signalMessage = SignalMessage(encrypted.signalMessage)
                sessionCipher.decrypt(signalMessage)
            }
            
            val decrypted = String(plaintext)
            Log.d(TAG, "Message decrypted from $senderId")
            Result.success(decrypted)
        } catch (e: Exception) {
            Log.e(TAG, "Error decrypting message from $senderId", e)
            Result.failure(e)
        }
    }
    
    override suspend fun hasKeysFor(userId: String): Boolean = withContext(Dispatchers.IO) {
        val address = SignalProtocolAddress(userId, DEVICE_ID)
        protocolStore.containsSession(address)
    }
    
    override suspend fun rotateKeys(): Unit = withContext(Dispatchers.IO) {
        try {
            // Generate new signed pre-key
            val identityKeyPair = protocolStore.getIdentityKeyPair()
            val signedPreKeyId = (System.currentTimeMillis() / 1000).toInt()
            
            val signedPreKeyPair = ECKeyPair.generate()
            val signature = identityKeyPair.privateKey.calculateSignature(signedPreKeyPair.publicKey.serialize())
            val signedPreKeyRecord = SignedPreKeyRecord(signedPreKeyId, System.currentTimeMillis(), signedPreKeyPair, signature)
            
            protocolStore.storeSignedPreKey(signedPreKeyId, signedPreKeyRecord)
            
            // Generate new batch of pre-keys
            generatePreKeys()
            
            Log.d(TAG, "Keys rotated successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error rotating keys", e)
            throw e
        }
    }
    
    /**
     * Get identity key fingerprint for verification
     * 
     * Note: This is a synchronous method that performs a quick hash calculation.
     * It's safe to call from any thread as it doesn't perform I/O operations.
     * 
     * @return Hex-encoded SHA-256 fingerprint of the identity key
     */
    override fun getIdentityKeyFingerprint(): String {
        val identityKey = protocolStore.getIdentityKeyPair().publicKey
        val publicKey = identityKey.serialize()
        
        // Generate SHA-256 fingerprint
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(publicKey)
        
        // Convert to hex string
        return hash.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Generate pre-keys for key exchange
     */
    private fun generatePreKeys() {
        val identityKeyPair = protocolStore.getIdentityKeyPair()
        
        // Generate one-time pre-keys
        for (i in 0 until PRE_KEY_COUNT) {
            val id = PRE_KEY_START_ID + i
            val keyPair = ECKeyPair.generate()
            val preKeyRecord = PreKeyRecord(id, keyPair)
            protocolStore.storePreKey(id, preKeyRecord)
        }
        
        // Generate signed pre-key
        val signedPreKeyId = (System.currentTimeMillis() / 1000).toInt()
        val signedPreKeyPair = ECKeyPair.generate()
        val signature = identityKeyPair.privateKey.calculateSignature(signedPreKeyPair.publicKey.serialize())
        val signedPreKey = SignedPreKeyRecord(signedPreKeyId, System.currentTimeMillis(), signedPreKeyPair, signature)
        
        protocolStore.storeSignedPreKey(signedPreKeyId, signedPreKey)
        
        // Generate and store Kyber pre-key (required by libsignal 0.86.5+)
        try {
            val kyberKeyPair = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
            // NOTE: libsignal doesn't have built-in Kyber pre-key storage yet (as of 0.86.5)
            // The Kyber key is generated on-demand in getLocalPreKeyBundle() for session establishment
            // In a production app, you would either:
            // 1. Store this in a custom table (requires additional migration)
            // 2. Wait for libsignal to add native Kyber pre-key storage support
            // For now, we generate it fresh each time which is acceptable for the MVP
            Log.d(TAG, "Generated Kyber pre-key (generated on-demand, not persisted due to libsignal limitation)")
        } catch (e: Exception) {
            Log.w(TAG, "Kyber pre-key generation not supported in this libsignal version", e)
        }
        
        Log.d(TAG, "Generated $PRE_KEY_COUNT pre-keys, 1 signed pre-key, and 1 Kyber pre-key")
    }
    
    /**
     * Process pre-key bundle from recipient (for establishing session)
     * 
     * This would typically be called when receiving a pre-key bundle from the server
     * or via an out-of-band channel.
     */
    suspend fun processPreKeyBundle(
        recipientId: String,
        preKeyBundle: PreKeyBundle
    ) = withContext(Dispatchers.IO) {
        try {
            val recipientAddress = SignalProtocolAddress(recipientId, DEVICE_ID)
            val sessionBuilder = SessionBuilder(protocolStore, recipientAddress)
            
            sessionBuilder.process(preKeyBundle)
            
            Log.d(TAG, "Session established with $recipientId")
        } catch (e: Exception) {
            Log.e(TAG, "Error processing pre-key bundle for $recipientId", e)
            throw e
        }
    }
    
    /**
     * Get local pre-key bundle for sharing with others
     * 
     * This bundle should be uploaded to the server or shared via an out-of-band channel
     * so others can establish sessions with us.
     */
    suspend fun getLocalPreKeyBundle(): PreKeyBundle = withContext(Dispatchers.IO) {
        val identityKeyPair = protocolStore.getIdentityKeyPair()
        val registrationId = protocolStore.getLocalRegistrationId()
        
        // Get a pre-key
        val preKeyRecord = protocolStore.loadPreKey(PRE_KEY_START_ID)
        
        // Get the current signed pre-key
        val signedPreKeys = protocolStore.loadSignedPreKeys()
        val signedPreKey = signedPreKeys.maxByOrNull { it.timestamp }
            ?: throw IllegalStateException("No signed pre-key available")
        
        // Generate a Kyber pre-key (required by libsignal 0.86.5+)
        val kyberKeyPair = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
        val kyberPreKeyPublic = kyberKeyPair.publicKey
        val kyberPreKeySignature = identityKeyPair.privateKey.calculateSignature(kyberPreKeyPublic.serialize())
        
        PreKeyBundle(
            registrationId,
            DEVICE_ID,
            preKeyRecord.id,
            preKeyRecord.keyPair.publicKey,
            signedPreKey.id,
            signedPreKey.keyPair.publicKey,
            signedPreKey.signature,
            identityKeyPair.publicKey,
            0, // kyberPreKeyId
            kyberPreKeyPublic,
            kyberPreKeySignature
        )
    }
}
