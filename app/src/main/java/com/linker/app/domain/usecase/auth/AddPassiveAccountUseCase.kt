package com.linker.app.domain.usecase.auth

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.linker.app.core.security.CredentialEncoder
import com.linker.app.core.security.SecurityLogger
import com.linker.app.core.util.Result
import com.linker.app.core.util.safeCall
import com.linker.app.domain.model.AccountSession
import com.linker.app.domain.repository.AccountRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Adds a new passive account to the Account Center WITHOUT changing the active Firebase Auth user.
 * 
 * Uses a temporary FirebaseApp instance to verify the email and password,
 * then saves the credentials securely to the AccountRepository.
 */
class AddPassiveAccountUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accountRepository: AccountRepository,
    private val credentialEncoder: CredentialEncoder
) {
    suspend operator fun invoke(email: String, password: String): Result<Unit> = safeCall {
        withContext(Dispatchers.IO) {
            val appName = "temp_auth_app_${System.currentTimeMillis()}"
            var tempApp: FirebaseApp? = null
            
            try {
                // Get main app options to initialize the temporary app
                val options = FirebaseApp.getInstance().options
                tempApp = FirebaseApp.initializeApp(context, options, appName)
                val tempAuth = FirebaseAuth.getInstance(tempApp)
                
                // Sign in with the temporary Auth instance
                val result = tempAuth.signInWithEmailAndPassword(email, password).await()
                val firebaseUser = result.user ?: throw IllegalStateException("Auth failed: User is null")
                
                // Map to basic info
                val uid = firebaseUser.uid
                val displayName = firebaseUser.displayName ?: email.substringBefore('@')
                val username = email.substringBefore('@')
                val avatarUrl = firebaseUser.photoUrl?.toString()
                
                // Encode credential
                val credential = credentialEncoder.encode(email, password)
                
                // Save to AccountRepository
                val session = AccountSession(
                    uid = uid,
                    displayName = displayName,
                    username = username,
                    avatarUrl = avatarUrl,
                    encryptedToken = credential,
                    lastUsedAt = System.currentTimeMillis()
                )
                
                accountRepository.addSession(session)
                SecurityLogger.logSessionCreated(uid)
                
                // We do NOT update the main UserDao here, because this account is passive.
                // When they switch to it, HybridAccountManager or the normal login flow will handle caching.
                
            } catch (e: FirebaseAuthException) {
                throw when (e.errorCode) {
                    "ERROR_NETWORK_REQUEST_FAILED" -> Exception("Network Error")
                    "ERROR_USER_NOT_FOUND" -> Exception("User not found")
                    "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL" -> Exception("Invalid credentials")
                    else -> Exception(e.message ?: "Sign-in failed")
                }
            } catch (e: com.google.firebase.FirebaseException) {
                throw Exception(e.message ?: "Firebase authentication error")
            } finally {
                // Clean up the temporary Firebase app
                tempApp?.delete()
            }
        }
    }
}
