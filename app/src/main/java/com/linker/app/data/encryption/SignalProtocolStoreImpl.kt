package com.linker.app.data.encryption

import com.linker.app.data.local.dao.*
import com.linker.app.data.local.entity.*
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
 */
@Singleton
class SignalProtocolStoreImpl @Inject constructor(
    private val identityDao: SignalIdentityDao,
    private val sessionDao: SignalSessionDao,
    private val preKeyDao: SignalPreKeyDao,
    private val signedPreKeyDao: SignalSignedPreKeyDao,
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
    
    override fun saveIdentity(address: SignalProtocolAddress, identityKey: IdentityKey): IdentityKeyStore.IdentityChange = runBlocking {
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
    ): Boolean = runBlocking {
        val addressString = "${address.name}:${address.deviceId}"
        val stored = identityDao.getIdentity(addressString)
        
        if (stored == null) {
            // First time seeing this identity, trust it
            return@runBlocking true
        }
        
        // Check if identity key matches
        stored.identityKey.contentEquals(identityKey.serialize())
    }
    
    override fun getIdentity(address: SignalProtocolAddress): IdentityKey? = runBlocking {
        val addressString = "${address.name}:${address.deviceId}"
        val entity = identityDao.getIdentity(addressString)
        entity?.let { IdentityKey(it.identityKey, 0) }
    }
    
    // SessionStore implementation
    
    override fun loadSession(address: SignalProtocolAddress): SessionRecord = runBlocking {
        val addressString = "${address.name}:${address.deviceId}"
        val entity = sessionDao.getSession(addressString)
        
        if (entity != null) {
            SessionRecord(entity.sessionRecord)
        } else {
            SessionRecord()
        }
    }
    
    override fun loadExistingSessions(addresses: MutableList<SignalProtocolAddress>): MutableList<SessionRecord> = runBlocking {
        val sessions = mutableListOf<SessionRecord>()
        
        for (address in addresses) {
            val session = loadSession(address)
            if (session.hasSenderChain()) {
                sessions.add(session)
            }
        }
        
        sessions
    }
    
    override fun getSubDeviceSessions(name: String): MutableList<Int> = runBlocking {
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
    
    override fun storeSession(address: SignalProtocolAddress, record: SessionRecord) = runBlocking {
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
    
    override fun containsSession(address: SignalProtocolAddress): Boolean = runBlocking {
        val addressString = "${address.name}:${address.deviceId}"
        val session = sessionDao.getSession(addressString)
        session != null && SessionRecord(session.sessionRecord).hasSenderChain()
    }
    
    override fun deleteSession(address: SignalProtocolAddress) = runBlocking {
        val addressString = "${address.name}:${address.deviceId}"
        sessionDao.deleteSession(addressString)
    }
    
    override fun deleteAllSessions(name: String) = runBlocking {
        val allSessions = sessionDao.getAllSessions()
        
        for (session in allSessions) {
            val parts = session.address.split(":")
            if (parts.size == 2 && parts[0] == name) {
                sessionDao.deleteSession(session.address)
            }
        }
    }
    
    // PreKeyStore implementation
    
    override fun loadPreKey(preKeyId: Int): PreKeyRecord = runBlocking {
        val entity = preKeyDao.getPreKey(preKeyId)
        
        if (entity != null) {
            PreKeyRecord(entity.preKeyRecord)
        } else {
            throw InvalidKeyIdException("No pre-key with ID $preKeyId")
        }
    }
    
    override fun storePreKey(preKeyId: Int, record: PreKeyRecord) = runBlocking {
        val entity = SignalPreKeyEntity(
            preKeyId = preKeyId,
            preKeyRecord = record.serialize(),
            createdAt = System.currentTimeMillis()
        )
        
        preKeyDao.insertPreKey(entity)
    }
    
    override fun containsPreKey(preKeyId: Int): Boolean = runBlocking {
        preKeyDao.getPreKey(preKeyId) != null
    }
    
    override fun removePreKey(preKeyId: Int) = runBlocking {
        preKeyDao.deletePreKey(preKeyId)
    }
    
    // SignedPreKeyStore implementation
    
    override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord = runBlocking {
        val entity = signedPreKeyDao.getSignedPreKey(signedPreKeyId)
        
        if (entity != null) {
            SignedPreKeyRecord(entity.signedPreKeyRecord)
        } else {
            throw InvalidKeyIdException("No signed pre-key with ID $signedPreKeyId")
        }
    }
    
    override fun loadSignedPreKeys(): MutableList<SignedPreKeyRecord> = runBlocking {
        val entities = signedPreKeyDao.getAllSignedPreKeys()
        entities.map { SignedPreKeyRecord(it.signedPreKeyRecord) }.toMutableList()
    }
    
    override fun storeSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord) = runBlocking {
        val entity = SignalSignedPreKeyEntity(
            signedPreKeyId = signedPreKeyId,
            signedPreKeyRecord = record.serialize(),
            createdAt = System.currentTimeMillis()
        )
        
        signedPreKeyDao.insertSignedPreKey(entity)
    }
    
    override fun containsSignedPreKey(signedPreKeyId: Int): Boolean = runBlocking {
        signedPreKeyDao.getSignedPreKey(signedPreKeyId) != null
    }
    
    override fun removeSignedPreKey(signedPreKeyId: Int) = runBlocking {
        signedPreKeyDao.deleteSignedPreKey(signedPreKeyId)
    }

    // KyberPreKeyStore implementation (Stubs - Implement properly if using PQXDH)
    override fun loadKyberPreKey(kyberPreKeyId: Int): KyberPreKeyRecord {
        throw InvalidKeyIdException("Kyber pre-keys not implemented")
    }

    override fun loadKyberPreKeys(): MutableList<KyberPreKeyRecord> = mutableListOf()

    override fun storeKyberPreKey(kyberPreKeyId: Int, record: KyberPreKeyRecord) {
        // No-op for now
    }

    override fun containsKyberPreKey(kyberPreKeyId: Int): Boolean = false

    override fun markKyberPreKeyUsed(kyberPreKeyId: Int, signedPreKeyId: Int, baseKey: ECPublicKey) {
        // No-op for now
    }

    // SenderKeyStore implementation (Stubs - Implement properly for group messaging)
    override fun storeSenderKey(sender: SignalProtocolAddress, distributionId: UUID, record: SenderKeyRecord) {
        // No-op for now
    }

    override fun loadSenderKey(sender: SignalProtocolAddress, distributionId: UUID): SenderKeyRecord? {
        return null
    }
}
