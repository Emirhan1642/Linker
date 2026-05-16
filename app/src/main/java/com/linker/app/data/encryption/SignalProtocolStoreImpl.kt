package com.linker.app.data.encryption

import com.linker.app.data.local.dao.*
import com.linker.app.data.local.entity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.signal.libsignal.protocol.*
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.groups.state.SenderKeyRecord
import org.signal.libsignal.protocol.groups.state.SenderKeyStore
import org.signal.libsignal.protocol.state.*
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of Signal Protocol stores backed by Room database
 * 
 * Provides persistent storage for:
 * - Identity keys
 * - Session state
 * - Pre-keys
 * - Signed pre-keys
 * - Kyber pre-keys (PQXDH)
 * - Sender keys (group messaging)
 * 
 * NOTE: SignalProtocolStore is a Java interface that doesn't support suspend functions.
 * We use runBlocking(Dispatchers.IO) to bridge the gap between coroutines and blocking calls.
 * This ensures database operations run on IO thread pool, preventing ANR on main thread.
 */
@Singleton
class SignalProtocolStoreImpl @Inject constructor(
    private val identityDao: SignalIdentityDao,
    private val sessionDao: SignalSessionDao,
    private val preKeyDao: SignalPreKeyDao,
    private val signedPreKeyDao: SignalSignedPreKeyDao,
    private val kyberPreKeyDao: SignalKyberPreKeyDao,
    private val senderKeyDao: SignalSenderKeyDao,
    private val androidKeystoreWrapper: AndroidKeystoreWrapper
) : SignalProtocolStore {
    
    private var identityKeyPair: IdentityKeyPair? = null
    private var localRegistrationId: Int = 0
    
    companion object {
        private const val IDENTITY_KEY_ALIAS = "signal_identity_key"
        private const val LOCAL_REGISTRATION_ID_KEY = "local_registration_id"
    }
    
    /**
     * Initialize the store with identity key pair and registration ID
     */
    fun initialize(identityKeyPair: IdentityKeyPair, registrationId: Int) {
        this.identityKeyPair = identityKeyPair
        this.localRegistrationId = registrationId
        
        // Store in Android Keystore for security
        androidKeystoreWrapper.storeIdentityKeyPair(IDENTITY_KEY_ALIAS, identityKeyPair)
        androidKeystoreWrapper.storeRegistrationId(LOCAL_REGISTRATION_ID_KEY, registrationId)
    }
    
    // IdentityKeyStore implementation
    
    override fun getIdentityKeyPair(): IdentityKeyPair {
        if (identityKeyPair == null) {
            identityKeyPair = androidKeystoreWrapper.getIdentityKeyPair(IDENTITY_KEY_ALIAS)
        }
        return identityKeyPair ?: throw IllegalStateException("Identity key pair not initialized")
    }
    
    override fun getLocalRegistrationId(): Int {
        if (localRegistrationId == 0) {
            localRegistrationId = androidKeystoreWrapper.getRegistrationId(LOCAL_REGISTRATION_ID_KEY)
        }
        return localRegistrationId
    }
    
    override fun saveIdentity(address: SignalProtocolAddress, identityKey: IdentityKey): IdentityKeyStore.IdentityChange = runBlocking(Dispatchers.IO) {
        val addressString = "${address.name}:${address.deviceId}"
        val now = System.currentTimeMillis()
        
        val entity = SignalIdentityEntity(
            address = addressString,
            identityKey = identityKey.serialize(),
            trustLevel = 1, // Auto-trust for now
            createdAt = now,
            updatedAt = now
        )
        
        identityDao.insertIdentity(entity)
        IdentityKeyStore.IdentityChange.NEW_OR_UNCHANGED
    }
    
    override fun isTrustedIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey,
        direction: IdentityKeyStore.Direction
    ): Boolean = runBlocking(Dispatchers.IO) {
        val addressString = "${address.name}:${address.deviceId}"
        val stored = identityDao.getIdentity(addressString)
        
        if (stored == null) {
            // TOFU: Trust On First Use — ilk karşılaşmada kimliği güvenilir say.
            // Kimlik değişikliği tespiti (isTrustedIdentity false döndüğünde) UI'da
            // "Güvenlik kodu değişti" uyarısı göstermelidir.
            return@runBlocking true
        }
        
        // Check if identity key matches
        stored.identityKey.contentEquals(identityKey.serialize())
    }
    
    override fun getIdentity(address: SignalProtocolAddress): IdentityKey? = runBlocking(Dispatchers.IO) {
        val addressString = "${address.name}:${address.deviceId}"
        val entity = identityDao.getIdentity(addressString)
        entity?.let { IdentityKey(it.identityKey, 0) }
    }
    
    // SessionStore implementation
    
    override fun loadSession(address: SignalProtocolAddress): SessionRecord = runBlocking(Dispatchers.IO) {
        val addressString = "${address.name}:${address.deviceId}"
        val entity = sessionDao.getSession(addressString)
        
        if (entity != null) {
            SessionRecord(entity.sessionRecord)
        } else {
            SessionRecord()
        }
    }
    
    override fun loadExistingSessions(addresses: MutableList<SignalProtocolAddress>): MutableList<SessionRecord> = runBlocking(Dispatchers.IO) {
        val sessions = mutableListOf<SessionRecord>()
        
        for (address in addresses) {
            val session = loadSession(address)
            if (session.hasSenderChain()) {
                sessions.add(session)
            }
        }
        
        sessions
    }
    
    override fun getSubDeviceSessions(name: String): MutableList<Int> = runBlocking(Dispatchers.IO) {
        val allSessions = sessionDao.getAllSessions()
        val deviceIds = mutableListOf<Int>()
        
        for (session in allSessions) {
            val parts = session.address.split(":")
            if (parts.size == 2 && parts[0] == name) {
                deviceIds.add(parts[1].toInt())
            }
        }
        
        deviceIds
    }
    
    override fun storeSession(address: SignalProtocolAddress, record: SessionRecord) = runBlocking(Dispatchers.IO) {
        val addressString = "${address.name}:${address.deviceId}"
        val now = System.currentTimeMillis()
        
        val entity = SignalSessionEntity(
            address = addressString,
            sessionRecord = record.serialize(),
            createdAt = now,
            updatedAt = now
        )
        
        sessionDao.insertSession(entity)
    }
    
    override fun containsSession(address: SignalProtocolAddress): Boolean = runBlocking(Dispatchers.IO) {
        val addressString = "${address.name}:${address.deviceId}"
        val session = sessionDao.getSession(addressString)
        session != null && SessionRecord(session.sessionRecord).hasSenderChain()
    }
    
    override fun deleteSession(address: SignalProtocolAddress) = runBlocking(Dispatchers.IO) {
        val addressString = "${address.name}:${address.deviceId}"
        sessionDao.deleteSession(addressString)
    }
    
    override fun deleteAllSessions(name: String) = runBlocking(Dispatchers.IO) {
        val allSessions = sessionDao.getAllSessions()
        
        for (session in allSessions) {
            val parts = session.address.split(":")
            if (parts.size == 2 && parts[0] == name) {
                sessionDao.deleteSession(session.address)
            }
        }
    }
    
    // PreKeyStore implementation
    
    override fun loadPreKey(preKeyId: Int): PreKeyRecord = runBlocking(Dispatchers.IO) {
        val entity = preKeyDao.getPreKey(preKeyId)
        
        if (entity != null) {
            PreKeyRecord(entity.preKeyRecord)
        } else {
            throw InvalidKeyIdException("No pre-key with ID $preKeyId")
        }
    }
    
    override fun storePreKey(preKeyId: Int, record: PreKeyRecord) = runBlocking(Dispatchers.IO) {
        val entity = SignalPreKeyEntity(
            preKeyId = preKeyId,
            preKeyRecord = record.serialize(),
            createdAt = System.currentTimeMillis()
        )
        
        preKeyDao.insertPreKey(entity)
    }
    
    override fun containsPreKey(preKeyId: Int): Boolean = runBlocking(Dispatchers.IO) {
        preKeyDao.getPreKey(preKeyId) != null
    }
    
    override fun removePreKey(preKeyId: Int) = runBlocking(Dispatchers.IO) {
        preKeyDao.deletePreKey(preKeyId)
    }

    /**
     * Load all stored pre-key records.
     * Used by EncryptionManagerImpl to determine the next available pre-key ID.
     */
    fun loadAllPreKeys(): List<PreKeyRecord> = runBlocking(Dispatchers.IO) {
        preKeyDao.getAllPreKeys().map { PreKeyRecord(it.preKeyRecord) }
    }
    
    // SignedPreKeyStore implementation
    
    override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord = runBlocking(Dispatchers.IO) {
        val entity = signedPreKeyDao.getSignedPreKey(signedPreKeyId)
        
        if (entity != null) {
            SignedPreKeyRecord(entity.signedPreKeyRecord)
        } else {
            throw InvalidKeyIdException("No signed pre-key with ID $signedPreKeyId")
        }
    }
    
    override fun loadSignedPreKeys(): MutableList<SignedPreKeyRecord> = runBlocking(Dispatchers.IO) {
        val entities = signedPreKeyDao.getAllSignedPreKeys()
        entities.map { SignedPreKeyRecord(it.signedPreKeyRecord) }.toMutableList()
    }
    
    override fun storeSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord) = runBlocking(Dispatchers.IO) {
        val entity = SignalSignedPreKeyEntity(
            signedPreKeyId = signedPreKeyId,
            signedPreKeyRecord = record.serialize(),
            createdAt = System.currentTimeMillis()
        )
        
        signedPreKeyDao.insertSignedPreKey(entity)
    }
    
    override fun containsSignedPreKey(signedPreKeyId: Int): Boolean = runBlocking(Dispatchers.IO) {
        signedPreKeyDao.getSignedPreKey(signedPreKeyId) != null
    }
    
    override fun removeSignedPreKey(signedPreKeyId: Int) = runBlocking(Dispatchers.IO) {
        signedPreKeyDao.deleteSignedPreKey(signedPreKeyId)
    }

    // KyberPreKeyStore implementation
    
    override fun loadKyberPreKey(kyberPreKeyId: Int): KyberPreKeyRecord = runBlocking(Dispatchers.IO) {
        val entity = kyberPreKeyDao.getKyberPreKey(kyberPreKeyId)
        
        if (entity != null) {
            KyberPreKeyRecord(entity.kyberPreKeyRecord)
        } else {
            throw InvalidKeyIdException("No Kyber pre-key with ID $kyberPreKeyId")
        }
    }

    override fun loadKyberPreKeys(): MutableList<KyberPreKeyRecord> = runBlocking(Dispatchers.IO) {
        val entities = kyberPreKeyDao.getAllKyberPreKeys()
        entities.map { KyberPreKeyRecord(it.kyberPreKeyRecord) }.toMutableList()
    }

    override fun storeKyberPreKey(kyberPreKeyId: Int, record: KyberPreKeyRecord) = runBlocking(Dispatchers.IO) {
        val entity = SignalKyberPreKeyEntity(
            kyberPreKeyId = kyberPreKeyId,
            kyberPreKeyRecord = record.serialize(),
            createdAt = System.currentTimeMillis(),
            isUsed = false
        )
        
        kyberPreKeyDao.insertKyberPreKey(entity)
    }

    override fun containsKyberPreKey(kyberPreKeyId: Int): Boolean = runBlocking(Dispatchers.IO) {
        kyberPreKeyDao.getKyberPreKey(kyberPreKeyId) != null
    }

    override fun markKyberPreKeyUsed(kyberPreKeyId: Int, signedPreKeyId: Int, baseKey: ECPublicKey) = runBlocking(Dispatchers.IO) {
        kyberPreKeyDao.markKyberPreKeyUsed(kyberPreKeyId)
    }
    
    /**
     * Load all stored Kyber pre-key records.
     * Used by EncryptionManagerImpl to determine the next available Kyber pre-key ID.
     */
    fun loadAllKyberPreKeys(): List<KyberPreKeyRecord> = runBlocking(Dispatchers.IO) {
        kyberPreKeyDao.getAllKyberPreKeys().map { KyberPreKeyRecord(it.kyberPreKeyRecord) }
    }

    // SenderKeyStore implementation
    
    override fun storeSenderKey(sender: SignalProtocolAddress, distributionId: UUID, record: SenderKeyRecord) = runBlocking(Dispatchers.IO) {
        val senderAddress = "${sender.name}:${sender.deviceId}"
        val now = System.currentTimeMillis()
        
        val entity = SignalSenderKeyEntity(
            senderAddress = senderAddress,
            distributionId = distributionId.toString(),
            senderKeyRecord = record.serialize(),
            createdAt = now,
            updatedAt = now
        )
        
        senderKeyDao.insertSenderKey(entity)
    }

    override fun loadSenderKey(sender: SignalProtocolAddress, distributionId: UUID): SenderKeyRecord? = runBlocking(Dispatchers.IO) {
        val senderAddress = "${sender.name}:${sender.deviceId}"
        val entity = senderKeyDao.getSenderKey(senderAddress, distributionId.toString())
        
        entity?.let { SenderKeyRecord(it.senderKeyRecord) }
    }
}
