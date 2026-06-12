package com.linker.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "users",
    indices = [
        Index(value = ["username"], unique = true),
        Index(value = ["displayName"]),
        Index(value = ["isVerified", "followersCount"], name = "idx_verified_popular")
    ]
)
data class UserEntity(
    @PrimaryKey val userId: String,
    val username: String,
    val displayName: String,
    val email: String?,
    val phoneNumber: String?,
    val bio: String?,
    val profileImageUrl: String?,
    val coverImageUrl: String?,
    val isVerified: Boolean = false,
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val likesCount: Int = 0,
    val isFollowing: Boolean = false,
    val isFollowedBy: Boolean = false,
    val isBlocked: Boolean = false,
    val isMuted: Boolean = false,
    val isPrivate: Boolean = false,
    val followRequestSent: Boolean = false,
    val hideFollowLists: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val lastSyncedAt: Long = System.currentTimeMillis()
) {
    init {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(username.isNotBlank()) { "Username cannot be blank" }
        require(username.matches(Regex("^[a-zA-Z0-9_.]{3,20}$"))) {
            "Username must be 3-20 characters, alphanumeric, dot, and underscore only"
        }
        require(displayName.isNotBlank()) { "Display name cannot be blank" }
        require(displayName.length <= MAX_DISPLAY_NAME_LENGTH) { "Display name too long" }
        
        email?.let {
            require(it.matches(Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"))) {
                "Invalid email format"
            }
        }
        
        phoneNumber?.let {
            require(it.matches(Regex("^\\+?[1-9]\\d{1,14}$"))) {
                "Invalid phone number format"
            }
        }
        
        bio?.let {
            require(it.length <= MAX_BIO_LENGTH) { "Bio too long (max $MAX_BIO_LENGTH characters)" }
        }
        
        // Timestamp validations
        require(updatedAt >= createdAt) { "Updated cannot be before created" }
        require(lastSyncedAt >= createdAt) { "Last synced cannot be before created" }
        
        // Logic validations
        if (isBlocked) {
            require(!isFollowing) { "Cannot follow blocked user" }
            require(!isFollowedBy) { "Blocked user cannot follow" }
        }
    }

    fun isMutualFollower(): Boolean {
        return isFollowing && isFollowedBy
    }

    fun canSendMessage(): Boolean {
        return !isBlocked && (!isPrivate || isFollowing)
    }

    fun getDisplayUsername(): String {
        return if (isVerified) "$username ✓" else username
    }

    fun hasPublicProfile(): Boolean {
        return !isPrivate || isFollowing
    }
    
    companion object {
        const val MIN_USERNAME_LENGTH = 3
        const val MAX_USERNAME_LENGTH = 20
        const val MAX_DISPLAY_NAME_LENGTH = 50
        const val MAX_BIO_LENGTH = 150
    }
}
