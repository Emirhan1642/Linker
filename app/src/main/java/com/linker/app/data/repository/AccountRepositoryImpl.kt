package com.linker.app.data.repository

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.annotation.Keep
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.firebase.auth.FirebaseAuth
import com.linker.app.core.security.CredentialEncoder
import com.linker.app.core.util.Result
import com.linker.app.core.util.safeCall
import com.linker.app.domain.model.AccountSession
import com.linker.app.domain.repository.AccountRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firebaseAuth: FirebaseAuth,
    private val pushTokenRegistrar: com.linker.app.core.notification.PushTokenRegistrar
) : AccountRepository {

    private companion object {
        const val TAG               = "AccountRepository"
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS         = "linker_account_key_v2"
        const val AES_GCM_TRANSFORM = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH    = 128
        const val IV_LENGTH         = 12
        const val PREFS_FILE        = "linker_accounts_v2"
        const val KEY_JSON          = "sessions_json"
        const val MAX_PASSIVE_SESSIONS = 5  // Maximum number of accounts per device
        // ✅ REMOVED: const val SEP = "::" - No longer using delimiter-based format
    }

    // ─── ÖNEMLI: Kotlin property'leri yukarıdan aşağıya initialize edilir.
    // jsonSerializer, loadSessionsFromDisk() içinde kullanılıyor.
    // loadSessionsFromDisk() ise hem encryptedPrefs init'inde hem de
    // init{} bloğunda çağrılıyor.
    // Bu yüzden jsonSerializer MUTLAKA encryptedPrefs'ten önce tanımlanmalıdır.

    private val jsonSerializer = Json {
        ignoreUnknownKeys = true
        encodeDefaults    = true
    }

    // ── EncryptedSharedPreferences ────────────────────────────────────────

    private val encryptedPrefs = try {
        buildEncryptedPrefs()
    } catch (e: Exception) {
        Log.e(TAG, "EncryptedSharedPreferences init failed, resetting: ${e.message}")
        context.deleteSharedPreferences(PREFS_FILE)
        buildEncryptedPrefs()
    }

    private fun buildEncryptedPrefs(): android.content.SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // ── Reaktif session listesi ───────────────────────────────────────────

    private val _sessionsFlow = MutableStateFlow<List<AccountSession>>(emptyList())

    init {
        val loaded = loadSessionsFromDisk()
        Log.d(TAG, "init: disk'ten ${loaded.size} session yüklendi")
        _sessionsFlow.value = loaded.map { it.toSafeSession() }
    }

    override fun observeSessions(): Flow<List<AccountSession>> = _sessionsFlow.asStateFlow()

    override suspend fun getActiveUid(): String? = firebaseAuth.currentUser?.uid

    // ── Ekle / Güncelle ───────────────────────────────────────────────────

    override suspend fun addSession(session: AccountSession): Result<Unit> = safeCall {
        withContext(Dispatchers.Default) {
            Log.d(TAG, "addSession: uid=${session.uid}, username=${session.username}")

            val current = loadSessionsFromDisk().toMutableList()
            
            // Check if we're adding a new account (not updating existing)
            val isNewAccount = current.none { it.uid == session.uid }
            if (isNewAccount && current.size >= MAX_PASSIVE_SESSIONS) {
                Log.w(TAG, "addSession: Maximum account limit ($MAX_PASSIVE_SESSIONS) reached")
                throw IllegalStateException("Maximum account limit reached. You can add up to $MAX_PASSIVE_SESSIONS accounts.")
            }

            val plainBytes = session.encryptedToken.toByteArray(Charsets.UTF_8)
            val ciphertext: String
            try {
                ciphertext = encryptWithKeystore(plainBytes)
            } finally {
                plainBytes.fill(0)
            }

            val dto = SessionDto(
                uid                  = session.uid,
                displayName          = session.displayName,
                username             = session.username,
                avatarUrl            = session.avatarUrl,
                encryptedToken       = ciphertext,
                addedAt              = session.addedAt,
                lastUsedAt           = session.lastUsedAt,
                requiresAuthOnSwitch = session.requiresAuthOnSwitch
            )

            current.removeAll { it.uid == dto.uid }
            current.add(dto)
            persistSessions(current)

            Log.d(TAG, "addSession: toplam ${current.size} session kaydedildi")
            _sessionsFlow.value = current.map { it.toSafeSession() }
        }
    }

    // ── Kaldır ────────────────────────────────────────────────────────────

    override suspend fun removeSession(uid: String): Result<Unit> = safeCall {
        withContext(Dispatchers.Default) {
            Log.d(TAG, "removeSession: uid=$uid")
            if (firebaseAuth.currentUser?.uid == uid) firebaseAuth.signOut()
            val current = loadSessionsFromDisk().toMutableList()
            current.removeAll { it.uid == uid }
            persistSessions(current)
            _sessionsFlow.value = current.map { it.toSafeSession() }
        }
    }

    // ── Geçiş ─────────────────────────────────────────────────────────────

    override suspend fun switchToAccount(uid: String): Result<Unit> = safeCall {
        withContext(Dispatchers.IO) {
            Log.d(TAG, "switchToAccount: uid=$uid")
            val sessions = loadSessionsFromDisk()
            val dto = sessions.firstOrNull { it.uid == uid }
                ?: throw IllegalStateException("Session bulunamadı: uid=$uid")

            val plainBytes = decryptWithKeystore(dto.encryptedToken)
            try {
                // ✅ SECURITY: Use CredentialEncoder for delimiter-free decoding
                val credential = String(plainBytes, Charsets.UTF_8)
                val (email, password) = CredentialEncoder.decode(credential)

                firebaseAuth.signInWithEmailAndPassword(email, password).await()
                Log.d(TAG, "switchToAccount: giriş başarılı uid=$uid")
                
                // ✅ Register FCM token for the new account
                pushTokenRegistrar.registerCurrentToken()
            } finally {
                plainBytes.fill(0)
            }

            val updated = sessions.toMutableList()
            val idx = updated.indexOfFirst { it.uid == uid }
            if (idx != -1) {
                updated[idx] = updated[idx].copy(lastUsedAt = System.currentTimeMillis())
                persistSessions(updated)
                _sessionsFlow.value = updated.map { it.toSafeSession() }
            }
        }
    }

    override suspend fun updateSessionMetadata(
        uid: String, displayName: String, username: String, avatarUrl: String?
    ): Result<Unit> = safeCall {
        withContext(Dispatchers.Default) {
            val sessions = loadSessionsFromDisk().toMutableList()
            val idx = sessions.indexOfFirst { it.uid == uid }
            if (idx != -1) {
                sessions[idx] = sessions[idx].copy(
                    displayName = displayName,
                    username    = username,
                    avatarUrl   = avatarUrl
                )
                persistSessions(sessions)
                _sessionsFlow.value = sessions.map { it.toSafeSession() }
            }
        }
    }

    override suspend fun getSessions(): List<AccountSession> =
        loadSessionsFromDisk().map { it.toSafeSession() }

    /**
     * Get decrypted credentials for a specific user without switching accounts
     * Used by HybridAccountManager to create passive sessions
     * 
     * @return Pair of (email, password) or null if session not found
     */
    override suspend fun getDecryptedCredentials(uid: String): Pair<String, String>? = withContext(Dispatchers.Default) {
        try {
            val sessions = loadSessionsFromDisk()
            val dto = sessions.firstOrNull { it.uid == uid }
                ?: return@withContext null
            
            val plainBytes = decryptWithKeystore(dto.encryptedToken)
            try {
                val credential = String(plainBytes, Charsets.UTF_8)
                val (email, password) = CredentialEncoder.decode(credential)
                return@withContext Pair(email, password)
            } finally {
                plainBytes.fill(0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "getDecryptedCredentials failed for uid=$uid: ${e.message}", e)
            null
        }
    }

    // ── Keystore ──────────────────────────────────────────────────────────

    private fun encryptWithKeystore(plainBytes: ByteArray): String {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val combined = cipher.iv + cipher.doFinal(plainBytes)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decryptWithKeystore(encoded: String): ByteArray {
        val combined   = Base64.decode(encoded, Base64.NO_WRAP)
        val iv         = combined.sliceArray(0 until IV_LENGTH)
        val ciphertext = combined.sliceArray(IV_LENGTH until combined.size)
        val cipher     = Cipher.getInstance(AES_GCM_TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).also { it.load(null) }
        if (ks.containsAlias(KEY_ALIAS)) {
            return (ks.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
        }
        Log.d(TAG, "Keystore: yeni anahtar oluşturuluyor — alias=$KEY_ALIAS")
        val kg = KeyGenerator.getInstance(
            android.security.keystore.KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE_PROVIDER
        )
        kg.init(
            android.security.keystore.KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                        android.security.keystore.KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return kg.generateKey()
    }

    // ── Disk ──────────────────────────────────────────────────────────────

    private fun loadSessionsFromDisk(): List<SessionDto> {
        return try {
            val raw = encryptedPrefs.getString(KEY_JSON, null)
            if (raw.isNullOrBlank()) {
                Log.d(TAG, "loadSessionsFromDisk: disk boş")
                return emptyList()
            }
            val list = jsonSerializer.decodeFromString<List<SessionDto>>(raw)
            Log.d(TAG, "loadSessionsFromDisk: ${list.size} session okundu")
            list
        } catch (e: Exception) {
            Log.e(TAG, "loadSessionsFromDisk hatası: ${e.message}", e)
            emptyList()
        }
    }

    private fun persistSessions(sessions: List<SessionDto>) {
        try {
            val json = jsonSerializer.encodeToString(sessions)
            encryptedPrefs.edit()
                .putString(KEY_JSON, json)
                .commit()   // commit() → synchronous, uygulama kapanmadan önce diske yazar
            Log.d(TAG, "persistSessions: ${sessions.size} session yazıldı")
        } catch (e: Exception) {
            Log.e(TAG, "persistSessions hatası: ${e.message}", e)
        }
    }

    // ── DTO ───────────────────────────────────────────────────────────────

    @Keep
    @Serializable
    private data class SessionDto(
        val uid: String,
        val displayName: String,
        val username: String,
        val avatarUrl: String?,
        val encryptedToken: String,
        val addedAt: Long,
        val lastUsedAt: Long,
        val requiresAuthOnSwitch: Boolean = false
    )

    private fun SessionDto.toSafeSession() = AccountSession(
        uid                  = uid,
        displayName          = displayName,
        username             = username,
        avatarUrl            = avatarUrl,
        encryptedToken       = "",
        addedAt              = addedAt,
        lastUsedAt           = lastUsedAt,
        requiresAuthOnSwitch = requiresAuthOnSwitch
    )
}
