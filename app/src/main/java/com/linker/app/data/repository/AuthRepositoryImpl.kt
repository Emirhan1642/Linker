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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

import com.google.firebase.firestore.FirebaseFirestore

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val userDao: UserDao
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
    }

    override suspend fun getCurrentUser(): User? =
        firebaseAuth.currentUser?.let {
            userDao.getUserById(it.uid)?.toDomain() ?: it.toLocalUser()
        }

    override suspend fun signInWithGoogle(idToken: String): Result<User> = safeCall {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = firebaseAuth.signInWithCredential(credential).await()
        val firebaseUser = result.user ?: throw Exception("Sign-in failed")
        val user = firebaseUser.toLocalUser()
        // Cache the user
        userDao.insertUser(user.toEntity())
        user
    }

    override suspend fun signInWithEmail(
        email: String,
        password: String
    ): Result<User> = safeCall {
        val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
        val firebaseUser = result.user ?: throw Exception("Sign-in failed")
        val user = firebaseUser.toLocalUser()
        userDao.insertUser(user.toEntity())
        user
    }

    override suspend fun createAccountWithEmail(
        email: String,
        password: String
    ): Result<User> = safeCall {
        val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
        val firebaseUser = result.user ?: throw Exception("Account creation failed")
        val user = firebaseUser.toLocalUser()
        userDao.insertUser(user.toEntity())
        user
    }

    override suspend fun sendPhoneOtp(phoneNumber: String): Result<String> {
        // Firebase Phone Auth requires an Activity for verification callbacks.
        // In production this would use PhoneAuthOptions. Here we return a placeholder
        // that the AuthViewModel fills in via the Credential Manager flow.
        return Result.Error("Phone OTP requires Activity context — handled in ViewModel")
    }

    override suspend fun signInWithPhoneOtp(
        verificationId: String,
        otp: String
    ): Result<User> = safeCall {
        val credential = PhoneAuthProvider.getCredential(verificationId, otp)
        val result = firebaseAuth.signInWithCredential(credential).await()
        val firebaseUser = result.user ?: throw Exception("Phone sign-in failed")
        val user = firebaseUser.toLocalUser()
        userDao.insertUser(user.toEntity())
        user
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> = safeCall {
        firebaseAuth.sendPasswordResetEmail(email).await()
    }

    override suspend fun completeProfileSetup(
        userId: String,
        username: String,
        displayName: String,
        profileImageLocalPath: String?
    ): Result<User> = safeCall {
        // Update Firebase Auth display name so we know onboarding is complete
        val request = com.google.firebase.auth.UserProfileChangeRequest.Builder()
            .setDisplayName(displayName)
            .build()
        firebaseAuth.currentUser?.updateProfile(request)?.await()

        // TODO: Upload profile image to Cloudinary, update Supabase profile record
        val currentEntity = userDao.getUserById(userId)
        val updated = currentEntity?.copy(
            username = username,
            displayName = displayName,
            updatedAt = System.currentTimeMillis(),
            lastSyncedAt = System.currentTimeMillis()
        ) ?: UserEntity(
            userId = userId,
            username = username,
            displayName = displayName,
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
        firebaseAuth.signOut()
    }

    override suspend fun deleteAccount(): Result<Unit> = safeCall {
        firebaseAuth.currentUser?.delete()?.await()
    }

    override suspend fun isAuthenticated(): Boolean =
        firebaseAuth.currentUser != null

    // ── Private helpers ────────────────────────────────────────────────────

    private fun com.google.firebase.auth.FirebaseUser.toLocalUser(): User = User(
        userId         = uid,
        username       = email?.substringBefore('@') ?: uid.take(8),
        displayName    = displayName ?: email?.substringBefore('@') ?: "New User",
        email          = email,
        phoneNumber    = phoneNumber,
        bio            = null,
        profileImageUrl = photoUrl?.toString(),
        coverImageUrl  = null,
        isVerified     = false,
        followersCount = 0,
        followingCount = 0,
        likesCount     = 0,
        isFollowing    = false,
        isFollowedBy   = false,
        isBlocked      = false,
        isMuted        = false,
        createdAt      = System.currentTimeMillis(),
        updatedAt      = System.currentTimeMillis()
    )

    private fun User.toEntity(): UserEntity = UserEntity(
        userId         = userId,
        username       = username,
        displayName    = displayName,
        email          = email,
        phoneNumber    = phoneNumber,
        bio            = bio,
        profileImageUrl = profileImageUrl,
        coverImageUrl  = coverImageUrl,
        isVerified     = isVerified,
        followersCount = followersCount,
        followingCount = followingCount,
        likesCount     = likesCount,
        isFollowing    = isFollowing,
        isFollowedBy   = isFollowedBy,
        isBlocked      = isBlocked,
        isMuted        = isMuted,
        createdAt      = createdAt,
        updatedAt      = updatedAt,
        lastSyncedAt   = System.currentTimeMillis()
    )
}
