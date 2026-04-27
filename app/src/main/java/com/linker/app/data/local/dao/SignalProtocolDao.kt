package com.linker.app.data.local.dao

import androidx.room.*
import com.linker.app.data.local.entity.*

/**
 * DAO for Signal Protocol identity keys
 */
@Dao
interface SignalIdentityDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIdentity(identity: SignalIdentityEntity)
    
    @Query("SELECT * FROM signal_identities WHERE address = :address")
    suspend fun getIdentity(address: String): SignalIdentityEntity?
    
    @Query("SELECT * FROM signal_identities")
    suspend fun getAllIdentities(): List<SignalIdentityEntity>
    
    @Query("DELETE FROM signal_identities WHERE address = :address")
    suspend fun deleteIdentity(address: String)
    
    @Query("DELETE FROM signal_identities")
    suspend fun clearAll()
}

/**
 * DAO for Signal Protocol sessions
 */
@Dao
interface SignalSessionDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SignalSessionEntity)
    
    @Query("SELECT * FROM signal_sessions WHERE address = :address")
    suspend fun getSession(address: String): SignalSessionEntity?
    
    @Query("SELECT * FROM signal_sessions")
    suspend fun getAllSessions(): List<SignalSessionEntity>
    
    @Query("DELETE FROM signal_sessions WHERE address = :address")
    suspend fun deleteSession(address: String)
    
    @Query("DELETE FROM signal_sessions")
    suspend fun clearAll()
}

/**
 * DAO for Signal Protocol pre-keys
 */
@Dao
interface SignalPreKeyDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreKey(preKey: SignalPreKeyEntity)
    
    @Query("SELECT * FROM signal_prekeys WHERE preKeyId = :preKeyId")
    suspend fun getPreKey(preKeyId: Int): SignalPreKeyEntity?
    
    @Query("SELECT * FROM signal_prekeys")
    suspend fun getAllPreKeys(): List<SignalPreKeyEntity>
    
    @Query("DELETE FROM signal_prekeys WHERE preKeyId = :preKeyId")
    suspend fun deletePreKey(preKeyId: Int)
    
    @Query("DELETE FROM signal_prekeys")
    suspend fun clearAll()
}

/**
 * DAO for Signal Protocol signed pre-keys
 */
@Dao
interface SignalSignedPreKeyDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSignedPreKey(signedPreKey: SignalSignedPreKeyEntity)
    
    @Query("SELECT * FROM signal_signed_prekeys WHERE signedPreKeyId = :signedPreKeyId")
    suspend fun getSignedPreKey(signedPreKeyId: Int): SignalSignedPreKeyEntity?
    
    @Query("SELECT * FROM signal_signed_prekeys")
    suspend fun getAllSignedPreKeys(): List<SignalSignedPreKeyEntity>
    
    @Query("DELETE FROM signal_signed_prekeys WHERE signedPreKeyId = :signedPreKeyId")
    suspend fun deleteSignedPreKey(signedPreKeyId: Int)
    
    @Query("DELETE FROM signal_signed_prekeys")
    suspend fun clearAll()
}
