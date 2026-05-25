package com.linker.app.core.di

import com.linker.app.data.encryption.EncryptionManager
import com.linker.app.data.encryption.EncryptionManagerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for encryption components
 * 
 * Provides Signal Protocol encryption manager for end-to-end encryption.
 * 
 * **Encryption:**
 * - Signal Protocol (Double Ratchet Algorithm)
 * - End-to-end encryption for messages
 * - Forward secrecy
 * - Post-compromise security
 * 
 * **Key Management:**
 * - Identity keys (long-term)
 * - Signed pre-keys (medium-term)
 * - One-time pre-keys (single-use)
 * - Session keys (ephemeral)
 * 
 * **Security:**
 * - Keys stored in encrypted database (SQLCipher)
 * - Automatic key rotation
 * - Secure key generation (libsignal-client)
 * 
 * **Validation:**
 * EncryptionManager validates itself on initialization:
 * - Checks libsignal-client library availability
 * - Verifies key storage access
 * - Tests encryption/decryption functionality
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class EncryptionModule {
    
    /**
     * Binds EncryptionManager implementation
     * 
     * CRITICAL: EncryptionManager must be initialized before sending/receiving messages.
     * The implementation validates itself on first use:
     * - Verifies Signal Protocol library is loaded
     * - Checks database access for key storage
     * - Generates identity keys if not present
     * 
     * @throws IllegalStateException if encryption initialization fails
     */
    @Binds
    @Singleton
    abstract fun bindEncryptionManager(
        impl: EncryptionManagerImpl
    ): EncryptionManager
}
