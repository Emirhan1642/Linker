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
import java.util.concurrent.Executors
import java.util.concurrent.Callable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import com.linker.app.core.util.Logger
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

    // In-memory thread-safe caches to prevent IO thread starvation and avoid blocking in hot paths
    private val sessionCache = java.util.concurrent.ConcurrentHashMap<String, SessionRecord>()
    private val identityCache = java.util.concurrent.ConcurrentHashMap<String, IdentityKey>()
    private val preKeyCache = java.util.concurrent.ConcurrentHashMap<Int, PreKeyRecord>()
    private val signedPreKeyCache = java.util.concurrent.ConcurrentHashMap<Int, SignedPreKeyRecord>()
    private val kyberPreKeyCache = java.util.concurrent.ConcurrentHashMap<Int, KyberPreKeyRecord>()

    private val _identityChanges = kotlinx.coroutines.flow.MutableSharedFlow<IdentityChangeEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val identityChanges: kotlinx.coroutines.flow.SharedFlow<IdentityChangeEvent> = _identityChanges.asSharedFlow()

    private fun <T> runOnDbThread(block: suspend () -> T): T {
        return runBlocking(Dispatchers.IO) { block() }
    }

    
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
        val stored = androidKeystoreWrapper.storeIdentityKeyPair(IDENTITY_KEY_ALIAS, identityKeyPair)
        if (!stored) {
            android.util.Log.e("SignalProtocolStore", "Failed to store identity key pair to Keystore")
        }
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
    
    override fun saveIdentity(address: SignalProtocolAddress, identityKey: IdentityKey): IdentityKeyStore.IdentityChange = runOnDbThread {
        val addressString = "${address.name}:${address.deviceId}"
        identityCache[addressString] = identityKey
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
    ): Boolean {
        val addressString = "${address.name}:${address.deviceId}"
        val cached = identityCache[addressString]
        if (cached != null) {
            val matches = cached.serialize().contentEquals(identityKey.serialize())
            if (!matches) {
                _identityChanges.tryEmit(
                    IdentityChangeEvent.IdentityChanged(
                        address = address,
                        oldKey = cached,
                        newKey = identityKey
                    )
                )
            }
            return matches
        }

        return runOnDbThread {
            val stored = identityDao.getIdentity(addressString)
            
            if (stored == null) {
                // TOFU: Trust On First Use
                identityCache[addressString] = identityKey
                _identityChanges.tryEmit(
                    IdentityChangeEvent.NewIdentity(address, identityKey)
                )
                return@runOnDbThread true
            }
            
            val loadedKey = IdentityKey(stored.identityKey, 0)
            identityCache[addressString] = loadedKey
            val matches = stored.identityKey.contentEquals(identityKey.serialize())
            
            if (!matches) {
                // Identity changed
                _identityChanges.tryEmit(
                    IdentityChangeEvent.IdentityChanged(
                        address = address,
                        oldKey = loadedKey,
                        newKey = identityKey
                    )
                )
            }
            
            matches
        }
    }
    
    override fun getIdentity(address: SignalProtocolAddress): IdentityKey? {
        val addressString = "${address.name}:${address.deviceId}"
        identityCache[addressString]?.let { return it }
        return runOnDbThread {
            val entity = identityDao.getIdentity(addressString)
            entity?.let {
                val key = IdentityKey(it.identityKey, 0)
                identityCache[addressString] = key
                key
            }
        }
    }
    
    // SessionStore implementation
    
    override fun loadSession(address: SignalProtocolAddress): SessionRecord {
        val addressString = "${address.name}:${address.deviceId}"
        sessionCache[addressString]?.let { return it }
        return runOnDbThread {
            try {
                val entity = sessionDao.getSession(addressString)
                if (entity != null) {
                    val session = SessionRecord(entity.sessionRecord)
                    sessionCache[addressString] = session
                    session
                } else {
                    val emptySession = SessionRecord()
                    emptySession
                }
            } catch (e: Exception) {
                Logger.e("SignalProtocolStore", "Error loading session", e)
                SessionRecord()
            }
        }
    }
    
    override fun loadExistingSessions(addresses: MutableList<SignalProtocolAddress>): MutableList<SessionRecord> {
        val sessions = mutableListOf<SessionRecord>()
        for (address in addresses) {
            val session = loadSession(address)
            if (session.hasSenderChain()) {
                sessions.add(session)
            }
        }
        return sessions
    }
    
    override fun getSubDeviceSessions(name: String): MutableList<Int> = runOnDbThread {
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
    
    override fun storeSession(address: SignalProtocolAddress, record: SessionRecord) {
        val addressString = "${address.name}:${address.deviceId}"
        sessionCache[addressString] = record
        runOnDbThread {
            val now = System.currentTimeMillis()
            val entity = SignalSessionEntity(
                address = addressString,
                sessionRecord = record.serialize(),
                createdAt = now,
                updatedAt = now
            )
            sessionDao.insertSession(entity)
        }
    }
    
    override fun containsSession(address: SignalProtocolAddress): Boolean {
        val addressString = "${address.name}:${address.deviceId}"
        sessionCache[addressString]?.let { return it.hasSenderChain() }
        return runOnDbThread {
            val session = sessionDao.getSession(addressString)
            if (session != null) {
                val record = SessionRecord(session.sessionRecord)
                sessionCache[addressString] = record
                record.hasSenderChain()
            } else false
        }
    }
    
    override fun deleteSession(address: SignalProtocolAddress) {
        val addressString = "${address.name}:${address.deviceId}"
        sessionCache.remove(addressString)
        runOnDbThread {
            sessionDao.deleteSession(addressString)
        }
    }
    
    override fun deleteAllSessions(name: String) {
        val prefix = "$name:"
        sessionCache.keys.filter { it.startsWith(prefix) }.forEach { sessionCache.remove(it) }
        runOnDbThread {
            val allSessions = sessionDao.getAllSessions()
            for (session in allSessions) {
                val parts = session.address.split(":")
                if (parts.size == 2 && parts[0] == name) {
                    sessionDao.deleteSession(session.address)
                }
            }
        }
    }
    
    // PreKeyStore implementation
    
    override fun loadPreKey(preKeyId: Int): PreKeyRecord {
        preKeyCache[preKeyId]?.let { return it }
        return runOnDbThread {
            val entity = preKeyDao.getPreKey(preKeyId)
            if (entity != null) {
                val record = PreKeyRecord(entity.preKeyRecord)
                preKeyCache[preKeyId] = record
                record
            } else {
                throw InvalidKeyIdException("No pre-key with ID $preKeyId")
            }
        }
    }
    
    override fun storePreKey(preKeyId: Int, record: PreKeyRecord) {
        preKeyCache[preKeyId] = record
        runOnDbThread {
            val entity = SignalPreKeyEntity(
                preKeyId = preKeyId,
                preKeyRecord = record.serialize(),
                createdAt = System.currentTimeMillis()
            )
            preKeyDao.insertPreKey(entity)
        }
    }
    
    override fun containsPreKey(preKeyId: Int): Boolean {
        if (preKeyCache.containsKey(preKeyId)) return true
        return runOnDbThread {
            preKeyDao.getPreKey(preKeyId) != null
        }
    }
    
    override fun removePreKey(preKeyId: Int) {
        preKeyCache.remove(preKeyId)
        runOnDbThread {
            preKeyDao.deletePreKey(preKeyId)
        }
    }

    /**
     * Load all stored pre-key records.
     * Used by EncryptionManagerImpl to determine the next available pre-key ID.
     */
    fun loadAllPreKeys(): List<PreKeyRecord> = runOnDbThread {
        preKeyDao.getAllPreKeys().map { 
            val record = PreKeyRecord(it.preKeyRecord)
            preKeyCache[it.preKeyId] = record
            record
        }
    }
    
    // SignedPreKeyStore implementation
    
    override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord {
        signedPreKeyCache[signedPreKeyId]?.let { return it }
        return runOnDbThread {
            val entity = signedPreKeyDao.getSignedPreKey(signedPreKeyId)
            if (entity != null) {
                val record = SignedPreKeyRecord(entity.signedPreKeyRecord)
                signedPreKeyCache[signedPreKeyId] = record
                record
            } else {
                throw InvalidKeyIdException("No signed pre-key with ID $signedPreKeyId")
            }
        }
    }
    
    override fun loadSignedPreKeys(): MutableList<SignedPreKeyRecord> = runOnDbThread {
        val entities = signedPreKeyDao.getAllSignedPreKeys()
        entities.map { 
            val record = SignedPreKeyRecord(it.signedPreKeyRecord)
            signedPreKeyCache[it.signedPreKeyId] = record
            record
        }.toMutableList()
    }
    
    override fun storeSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord) {
        signedPreKeyCache[signedPreKeyId] = record
        runOnDbThread {
            val entity = SignalSignedPreKeyEntity(
                signedPreKeyId = signedPreKeyId,
                signedPreKeyRecord = record.serialize(),
                createdAt = System.currentTimeMillis()
            )
            signedPreKeyDao.insertSignedPreKey(entity)
        }
    }
    
    override fun containsSignedPreKey(signedPreKeyId: Int): Boolean {
        if (signedPreKeyCache.containsKey(signedPreKeyId)) return true
        return runOnDbThread {
            signedPreKeyDao.getSignedPreKey(signedPreKeyId) != null
        }
    }
    
    override fun removeSignedPreKey(signedPreKeyId: Int) {
        signedPreKeyCache.remove(signedPreKeyId)
        runOnDbThread {
            signedPreKeyDao.deleteSignedPreKey(signedPreKeyId)
        }
    }

    // KyberPreKeyStore implementation
    
    override fun loadKyberPreKey(kyberPreKeyId: Int): KyberPreKeyRecord {
        kyberPreKeyCache[kyberPreKeyId]?.let { return it }
        return runOnDbThread {
            val entity = kyberPreKeyDao.getKyberPreKey(kyberPreKeyId)
            if (entity != null) {
                val record = KyberPreKeyRecord(entity.kyberPreKeyRecord)
                kyberPreKeyCache[kyberPreKeyId] = record
                record
            } else {
                throw InvalidKeyIdException("No Kyber pre-key with ID $kyberPreKeyId")
            }
        }
    }

    override fun loadKyberPreKeys(): MutableList<KyberPreKeyRecord> = runOnDbThread {
        val entities = kyberPreKeyDao.getAllKyberPreKeys()
        entities.map { 
            val record = KyberPreKeyRecord(it.kyberPreKeyRecord)
            kyberPreKeyCache[it.kyberPreKeyId] = record
            record
        }.toMutableList()
    }

    override fun storeKyberPreKey(kyberPreKeyId: Int, record: KyberPreKeyRecord) {
        kyberPreKeyCache[kyberPreKeyId] = record
        runOnDbThread {
            val entity = SignalKyberPreKeyEntity(
                kyberPreKeyId = kyberPreKeyId,
                kyberPreKeyRecord = record.serialize(),
                createdAt = System.currentTimeMillis(),
                isUsed = false
            )
            kyberPreKeyDao.insertKyberPreKey(entity)
        }
    }

    override fun containsKyberPreKey(kyberPreKeyId: Int): Boolean {
        if (kyberPreKeyCache.containsKey(kyberPreKeyId)) return true
        return runOnDbThread {
            kyberPreKeyDao.getKyberPreKey(kyberPreKeyId) != null
        }
    }

    override fun markKyberPreKeyUsed(kyberPreKeyId: Int, signedPreKeyId: Int, baseKey: ECPublicKey) = runOnDbThread {
        kyberPreKeyDao.markKyberPreKeyUsed(kyberPreKeyId)
    }
    
    /**
     * Load all stored Kyber pre-key records.
     * Used by EncryptionManagerImpl to determine the next available Kyber pre-key ID.
     */
    fun loadAllKyberPreKeys(): List<KyberPreKeyRecord> = runOnDbThread {
        kyberPreKeyDao.getAllKyberPreKeys().map { 
            val record = KyberPreKeyRecord(it.kyberPreKeyRecord)
            kyberPreKeyCache[it.kyberPreKeyId] = record
            record
        }
    }

    // SenderKeyStore implementation
    
    override fun storeSenderKey(sender: SignalProtocolAddress, distributionId: UUID, record: SenderKeyRecord) = runOnDbThread {
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

    override fun loadSenderKey(sender: SignalProtocolAddress, distributionId: UUID): SenderKeyRecord? = runOnDbThread {
        val senderAddress = "${sender.name}:${sender.deviceId}"
        val entity = senderKeyDao.getSenderKey(senderAddress, distributionId.toString())
        
        entity?.let { SenderKeyRecord(it.senderKeyRecord) }
    }
}

sealed class IdentityChangeEvent {
    data class NewIdentity(val address: SignalProtocolAddress, val identityKey: IdentityKey) : IdentityChangeEvent()
    data class IdentityChanged(val address: SignalProtocolAddress, val oldKey: IdentityKey, val newKey: IdentityKey) : IdentityChangeEvent()
}
