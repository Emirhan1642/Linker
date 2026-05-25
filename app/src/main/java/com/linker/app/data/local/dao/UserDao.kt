package com.linker.app.data.local.dao

import androidx.room.*
import com.linker.app.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {

    @Query("SELECT * FROM users WHERE userId = :userId")
    suspend fun getUserById(userId: String): UserEntity?

    @Query("SELECT * FROM users WHERE userId = :userId")
    fun observeUserById(userId: String): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE username = :username")
    suspend fun getUserByUsername(username: String): UserEntity?

    @Query("SELECT * FROM users WHERE userId IN (:userIds)")
    suspend fun getUsersByIds(userIds: List<String>): List<UserEntity>

    @Query("SELECT * FROM users WHERE userId IN (:userIds)")
    fun observeUsersByIds(userIds: List<String>): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE isFollowing = 1 ORDER BY username ASC")
    fun observeFollowing(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE isFollowedBy = 1 ORDER BY username ASC")
    fun observeFollowers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE username LIKE '%' || :query || '%' ESCAPE '\\' OR displayName LIKE '%' || :query || '%' ESCAPE '\\' LIMIT :limit")
    suspend fun searchUsers(query: String, limit: Int = 20): List<UserEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<UserEntity>)

    @Update
    suspend fun updateUser(user: UserEntity)

    @Transaction
    @Update
    suspend fun updateUsers(users: List<UserEntity>)
    
    @Delete
    suspend fun deleteUser(user: UserEntity)

    @Query("DELETE FROM users WHERE userId = :userId")
    suspend fun deleteUserById(userId: String)

    @Transaction
    @Query("DELETE FROM users WHERE lastSyncedAt < :timestamp")
    suspend fun deleteOldUsers(timestamp: Long): Int

    @Query("UPDATE users SET isFollowing = :isFollowing, updatedAt = :timestamp WHERE userId = :userId")
    suspend fun updateFollowingStatus(userId: String, isFollowing: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE users SET followRequestSent = :sent, updatedAt = :timestamp WHERE userId = :userId")
    suspend fun updateRequestSentStatus(userId: String, sent: Boolean, timestamp: Long = System.currentTimeMillis())

    @Transaction
    @Query("UPDATE users SET followersCount = MAX(0, followersCount + :delta), updatedAt = :timestamp WHERE userId = :userId")
    suspend fun updateFollowersCount(userId: String, delta: Int, timestamp: Long = System.currentTimeMillis())

    @Transaction
    @Query("UPDATE users SET followingCount = MAX(0, followingCount + :delta), updatedAt = :timestamp WHERE userId = :userId")
    suspend fun updateFollowingCount(userId: String, delta: Int, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM users")
    suspend fun getUserCount(): Int
}
