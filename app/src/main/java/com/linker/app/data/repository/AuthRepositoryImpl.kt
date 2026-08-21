package com.linker.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.linker.app.core.util.Result
import com.linker.app.core.util.safeCall
import com.linker.app.data.local.dao.UserDao
import com.linker.app.data.local.entity.UserEntity
import com.linker.app.data.local.mapper.toDomain
import com.linker.app.domain.model.User
import com.linker.app.domain.repository.AuthRepository
import com.linker.app.domain.repository.AuthError
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

import com.google.firebase.firestore.FirebaseFirestore
import com.linker.app.domain.model.UserMetrics
import com.linker.app.domain.model.UserPrivacy
import com.linker.app.domain.model.UserRelationship

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val userDao: UserDao,
    private val database: com.linker.app.data.local.LinkerDatabase
) : AuthRepository {

    override fun observeCurrentUser(): Flow<User?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            val firebaseUser = auth.currentUser
            if (firebaseUser == null) {
                trySend(null)
            } else {
                // Emit from local cache while we refresh
                // The ViewModel should use this flow in conjunction with userDao.observeUserById
                trySend(firebaseUser.toLocalUser())
            }
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }.shareIn(
        scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + SupervisorJob()),
        started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        replay = 1
    )

    override suspend fun getCurrentUser(): Result<User?> = safeCall {
        firebaseAuth.currentUser?.let {
            userDao.getUserById(it.uid)?.toDomain() ?: it.toLocalUser()
        }
    }

    private val userCacheMutex = kotlinx.coroutines.sync.Mutex()

    override suspend fun signInWithGoogle(idToken: String): Result<User> = safeCall {
        try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = firebaseAuth.signInWithCredential(credential).await()
            val firebaseUser = result.user ?: throw AuthError.SignInFailed("Google")
            
            val user = firebaseUser.toLocalUser()
            
            val entity = user.toEntity()
            userCacheMutex.withLock {
                userDao.insertUser(entity)
            }
            syncUserToFirestore(entity)
            user
        } catch (e: com.google.firebase.auth.FirebaseAuthException) {
            throw when (e.errorCode) {
                "ERROR_NETWORK_REQUEST_FAILED" -> AuthError.NetworkError()
                "ERROR_USER_NOT_FOUND" -> AuthError.UserNotFound()
                else -> AuthError.SignInFailed("Google")
            }
        }
    }

    override suspend fun signInWithEmail(
        email: String,
        password: String
    ): Result<User> = safeCall {
        try {
            val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = result.user ?: throw AuthError.SignInFailed("Email")
            val user = firebaseUser.toLocalUser()
            val entity = user.toEntity()
            userCacheMutex.withLock {
                userDao.insertUser(entity)
            }
            syncUserToFirestore(entity)
            user
        } catch (e: com.google.firebase.auth.FirebaseAuthException) {
            throw when (e.errorCode) {
                "ERROR_NETWORK_REQUEST_FAILED" -> AuthError.NetworkError()
                "ERROR_USER_NOT_FOUND" -> AuthError.UserNotFound()
                "ERROR_WRONG_PASSWORD" -> AuthError.InvalidCredentials()
                else -> AuthError.SignInFailed("Email")
            }
        }
    }

    override suspend fun createAccountWithEmail(
        email: String,
        password: String
    ): Result<User> = safeCall {
        try {
            val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = result.user ?: throw AuthError.AccountCreationFailed()
            val user = firebaseUser.toLocalUser()
            val entity = user.toEntity()
            userCacheMutex.withLock {
                userDao.insertUser(entity)
            }
            syncUserToFirestore(entity)
            user
        } catch (e: com.google.firebase.auth.FirebaseAuthException) {
            throw when (e.errorCode) {
                "ERROR_EMAIL_ALREADY_IN_USE" -> AuthError.AccountCreationFailed("Email already in use")
                "ERROR_NETWORK_REQUEST_FAILED" -> AuthError.NetworkError()
                else -> AuthError.AccountCreationFailed()
            }
        }
    }

    override suspend fun sendPhoneOtp(phoneNumber: String): Result<String> = safeCall {
        if (!phoneNumber.matches(Regex("^\\+[1-9]\\d{1,14}$"))) {
            throw IllegalArgumentException("Invalid phone number format")
        }
        // In clean architecture, Activity context shouldn't be here.
        // We will leave the exception so ViewModel handles it properly.
        throw IllegalStateException("Phone OTP requires Activity context — handled in ViewModel / Presentation Layer")
    }

    override suspend fun signInWithPhoneOtp(
        verificationId: String,
        otp: String
    ): Result<User> = safeCall {
        val credential = PhoneAuthProvider.getCredential(verificationId, otp)
        val result = firebaseAuth.signInWithCredential(credential).await()
        val firebaseUser = result.user ?: throw Exception("Phone sign-in failed")
        val user = firebaseUser.toLocalUser()
        val entity = user.toEntity()
        userDao.insertUser(entity)
        syncUserToFirestore(entity)
        user
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> = safeCall {
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            throw IllegalArgumentException("Invalid email format")
        }
        
        firebaseAuth.sendPasswordResetEmail(email).await()
    }

    override suspend fun completeProfileSetup(
        userId: String,
        username: String,
        displayName: String,
        profileImageLocalPath: String?
    ): Result<User> = safeCall {
        val sanitizedUsername = username.trim().lowercase()
        val sanitizedDisplayName = displayName.trim()

        if (!sanitizedUsername.matches(Regex("^[a-zA-Z0-9_.]{3,20}$"))) {
            throw IllegalArgumentException("Username must be 3-20 alphanumeric characters, dots or underscores")
        }
        
        val existingUser = firestore.collection("users")
            .whereEqualTo("username", sanitizedUsername)
            .get()
            .await()
        
        if (!existingUser.isEmpty && existingUser.documents[0].id != userId) {
            throw IllegalStateException("Username already taken")
        }
        
        if (sanitizedDisplayName.isBlank() || sanitizedDisplayName.length > 50) {
            throw IllegalArgumentException("Display name must be 1-50 characters")
        }

        val request = com.google.firebase.auth.UserProfileChangeRequest.Builder()
            .setDisplayName(sanitizedDisplayName)
            .build()
        firebaseAuth.currentUser?.updateProfile(request)?.await()

        val currentEntity = userDao.getUserById(userId)
        val updated = currentEntity?.copy(
            username = sanitizedUsername,
            displayName = sanitizedDisplayName,
            updatedAt = System.currentTimeMillis(),
            lastSyncedAt = System.currentTimeMillis()
        ) ?: UserEntity(
            userId = userId,
            username = sanitizedUsername,
            displayName = sanitizedDisplayName,
            email = firebaseAuth.currentUser?.email,
            phoneNumber = null,
            bio = null,
            profileImageUrl = null,
            coverImageUrl = null,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        
        // Save to Local DB
        userDao.insertUser(updated)
        
        // Save to Firestore
        val userMap = mapOf(
            "userId" to updated.userId,
            "username" to updated.username,
            "displayName" to updated.displayName,
            "email" to updated.email,
            "bio" to updated.bio,
            "profileImageUrl" to updated.profileImageUrl,
            "coverImageUrl" to updated.coverImageUrl,
            "isVerified" to updated.isVerified,
            "followersCount" to updated.followersCount,
            "followingCount" to updated.followingCount,
            "likesCount" to updated.likesCount,
            "createdAt" to updated.createdAt,
            "updatedAt" to updated.updatedAt
        )
        firestore.collection("users").document(userId).set(userMap).await()

        updated.toDomain()
    }

    override suspend fun signOut(): Result<Unit> = safeCall {
        val uid = firebaseAuth.currentUser?.uid
        firebaseAuth.signOut()
        try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                if (uid != null) {
                    userDao.deleteUserById(uid)
                }
                database.clearAllTables()
            }
        } catch (e: Exception) {
            android.util.Log.e("AuthRepositoryImpl", "Failed to clear user cache on sign out", e)
        }
    }

    override suspend fun deleteAccount(): Result<Unit> = safeCall {
        firebaseAuth.currentUser?.delete()?.await()
    }

    override suspend fun isAuthenticated(): Boolean =
        firebaseAuth.currentUser != null

    // ── Private helpers ────────────────────────────────────────────────────

    private fun com.google.firebase.auth.FirebaseUser.toLocalUser(): User = User(
        userId          = uid,
        username        = email?.substringBefore('@') ?: uid.take(8),
        displayName     = displayName ?: email?.substringBefore('@') ?: "New User",
        _email          = email,
        _phoneNumber    = phoneNumber,
        bio             = null,
        profileImageUrl = photoUrl?.toString(),
        coverImageUrl   = null,
        isVerified      = false,
        relationship    = UserRelationship(),
        privacy         = UserPrivacy(),
        metrics         = UserMetrics(),
        createdAt       = System.currentTimeMillis(),
        updatedAt       = System.currentTimeMillis()
    )

    private fun User.toEntity(): UserEntity = UserEntity(
        userId            = userId,
        username          = username,
        displayName       = displayName,
        email             = getEmail(),
        phoneNumber       = getPhoneNumber(),
        bio               = bio,
        profileImageUrl   = profileImageUrl,
        coverImageUrl     = coverImageUrl,
        isVerified        = isVerified,
        followersCount    = metrics.followersCount,
        followingCount    = metrics.followingCount,
        likesCount        = metrics.likesCount,
        isFollowing       = relationship.isFollowing,
        isFollowedBy      = relationship.isFollowedBy,
        isBlocked         = relationship.isBlocked,
        isMuted           = relationship.isMuted,
        isPrivate         = privacy.isPrivate,
        followRequestSent = relationship.followRequestSent,
        hideFollowLists   = privacy.hideFollowLists,
        createdAt         = createdAt,
        updatedAt         = updatedAt,
        lastSyncedAt      = System.currentTimeMillis()
    )

    private suspend fun syncUserToFirestore(userEntity: UserEntity) {
        try {
            val userMap = mapOf(
                "userId" to userEntity.userId,
                "username" to userEntity.username,
                "displayName" to userEntity.displayName,
                "email" to userEntity.email,
                "phoneNumber" to userEntity.phoneNumber,
                "bio" to userEntity.bio,
                "profileImageUrl" to userEntity.profileImageUrl,
                "coverImageUrl" to userEntity.coverImageUrl,
                "createdAt" to userEntity.createdAt,
                "updatedAt" to System.currentTimeMillis()
            ).filterValues { it != null }

            firestore.collection("users").document(userEntity.userId)
                .set(userMap, com.google.firebase.firestore.SetOptions.merge())
                .await()
        } catch (e: Exception) {
            android.util.Log.e("AuthRepositoryImpl", "Failed to sync user to Firestore", e)
        }
    }
}
