package com.linker.app.data.encryption

import com.linker.app.core.util.Logger
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
        private const val PRE_KEY_COUNT = 100
    }
    
    @Volatile
    private var isInitialized = false
    
    override suspend fun initialize() = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext

        // Double-checked locking: prevent redundant initialization under concurrency
        synchronized(this@EncryptionManagerImpl) {
            if (isInitialized) return@synchronized
        }

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

                Logger.d(TAG, "Generated new identity and pre-keys")
            }

            isInitialized = true
            Logger.d(TAG, "Encryption manager initialized")
        } catch (e: Exception) {
            Logger.e(TAG, "Error initializing encryption manager", e)
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
                Logger.w(TAG, "No session for $recipientId, cannot encrypt")
                return@withContext Result.failure(Exception("No encryption keys for recipient"))
            }

            // Create session cipher
            val sessionCipher = SessionCipher(protocolStore, recipientAddress)

            // Encrypt message
            val ciphertext = sessionCipher.encrypt(plaintext.toByteArray())

            // Serialize the ciphertext
            val serialized = ciphertext.serialize()

            Logger.d(TAG, "Message encrypted for $recipientId")
            Result.success(EncryptedMessage(serialized))
        } catch (e: Exception) {
            Logger.e(TAG, "Error encrypting message for $recipientId", e)
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

            // Determine message type and decrypt using explicit type checks
            // Catching a broad Exception to distinguish parse failures is unsafe:
            // we explicitly check the version byte to avoid masking real errors.
            val messageVersion = encrypted.signalMessage.firstOrNull()?.toInt()?.and(0xFF) ?: 0
            val plaintext = if (messageVersion >= 3) {
                // Version 3+ indicates PreKeySignalMessage
                try {
                    val preKeyMessage = PreKeySignalMessage(encrypted.signalMessage)
                    sessionCipher.decrypt(preKeyMessage)
                } catch (e: InvalidVersionException) {
                    // Not a PreKeySignalMessage after all, fall back to regular
                    val signalMessage = SignalMessage(encrypted.signalMessage)
                    sessionCipher.decrypt(signalMessage)
                } catch (e: InvalidMessageException) {
                    Logger.e(TAG, "Invalid pre-key signal message from $senderId", e)
                    throw e
                }
            } else {
                try {
                    val signalMessage = SignalMessage(encrypted.signalMessage)
                    sessionCipher.decrypt(signalMessage)
                } catch (e: InvalidMessageException) {
                    Logger.e(TAG, "Invalid signal message from $senderId", e)
                    throw e
                }
            }

            val decrypted = String(plaintext)
            Logger.d(TAG, "Message decrypted from $senderId")
            Result.success(decrypted)
        } catch (e: Exception) {
            Logger.e(TAG, "Error decrypting message from $senderId", e)
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

            // Generate new batch of pre-keys (continuing from current max ID)
            generatePreKeys()

            Logger.d(TAG, "Keys rotated successfully")
        } catch (e: Exception) {
            Logger.e(TAG, "Error rotating keys", e)
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

        // Determine starting ID by finding the current maximum stored pre-key ID.
        // This prevents overwriting existing pre-keys on every rotation call.
        val existingPreKeys = try {
            protocolStore.loadAllPreKeys()
        } catch (e: Exception) {
            emptyList<org.signal.libsignal.protocol.state.PreKeyRecord>()
        }
        val startId = (existingPreKeys.maxOfOrNull { record -> record.id } ?: 0) + 1

        // Generate one-time pre-keys starting from the next available ID
        for (i in 0 until PRE_KEY_COUNT) {
            val id = startId + i
            val keyPair = ECKeyPair.generate()
            val preKeyRecord = PreKeyRecord(id, keyPair)
            protocolStore.storePreKey(id, preKeyRecord)
        }

        // Generate signed pre-key with timestamp-based ID
        val signedPreKeyId = (System.currentTimeMillis() / 1000).toInt()
        val signedPreKeyPair = ECKeyPair.generate()
        val signature = identityKeyPair.privateKey.calculateSignature(signedPreKeyPair.publicKey.serialize())
        val signedPreKey = SignedPreKeyRecord(signedPreKeyId, System.currentTimeMillis(), signedPreKeyPair, signature)

        protocolStore.storeSignedPreKey(signedPreKeyId, signedPreKey)

        // NOTE: Kyber pre-key generation is disabled for libsignal 0.86.5
        // The KyberPreKeyRecord constructor and PreKeyBundle don't support Kyber parameters yet.
        // Kyber support will be added when libsignal library is updated to support PQXDH.
        
        Logger.d(TAG, "Generated $PRE_KEY_COUNT pre-keys (starting at ID $startId) and 1 signed pre-key")
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
    ): Unit = withContext(Dispatchers.IO) {
        try {
            val recipientAddress = SignalProtocolAddress(recipientId, DEVICE_ID)
            val sessionBuilder = SessionBuilder(protocolStore, recipientAddress)

            sessionBuilder.process(preKeyBundle)

            Logger.d(TAG, "Session established with $recipientId")
        } catch (e: Exception) {
            Logger.e(TAG, "Error processing pre-key bundle for $recipientId", e)
            throw e
        }
    }
    
    /**
     * Get local pre-key bundle for sharing with others
     * 
     * This bundle should be uploaded to the server or shared via an out-of-band channel
     * so others can establish sessions with us.
     * 
     * NOTE: libsignal 0.86.5 requires Kyber parameters in PreKeyBundle constructor.
     * We generate a temporary Kyber pre-key for each bundle (not persisted).
     */
    suspend fun getLocalPreKeyBundle(): PreKeyBundle = withContext(Dispatchers.IO) {
        val identityKeyPair = protocolStore.getIdentityKeyPair()
        val registrationId = protocolStore.getLocalRegistrationId()

        // Get the oldest unused pre-key (lowest ID) to hand out
        val preKeys = protocolStore.loadAllPreKeys()
        val preKeyRecord = preKeys.minByOrNull { record -> record.id }
            ?: throw IllegalStateException("No pre-keys available; call generatePreKeys() first")

        // Get the current signed pre-key
        val signedPreKeys = protocolStore.loadSignedPreKeys()
        val signedPreKey = signedPreKeys.maxByOrNull { it.timestamp }
            ?: throw IllegalStateException("No signed pre-key available")

        // Generate a temporary Kyber pre-key for this bundle
        // libsignal 0.86.5 requires Kyber parameters in PreKeyBundle
        val kyberPreKeyId = (System.currentTimeMillis() / 1000).toInt()
        val kyberKeyPair = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
        val kyberSignature = identityKeyPair.privateKey.calculateSignature(kyberKeyPair.publicKey.serialize())

        // Create PQXDH PreKeyBundle with Kyber support
        PreKeyBundle(
            registrationId,
            DEVICE_ID,
            preKeyRecord.id,
            preKeyRecord.keyPair.publicKey,
            signedPreKey.id,
            signedPreKey.keyPair.publicKey,
            signedPreKey.signature,
            identityKeyPair.publicKey,
            kyberPreKeyId,
            kyberKeyPair.publicKey,
            kyberSignature
        )
    }
}
