package com.linker.app.data.local.dao

import androidx.room.*
import com.linker.app.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

/**
 * User DAO - Data Access Object for users
 */
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
    
    @Query("SELECT * FROM users WHERE username LIKE '%' || :query || '%' OR displayName LIKE '%' || :query || '%' LIMIT :limit")
    suspend fun searchUsers(query: String, limit: Int = 20): List<UserEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<UserEntity>)
    
    @Update
    suspend fun updateUser(user: UserEntity)
    
    @Delete
    suspend fun deleteUser(user: UserEntity)
    
    @Query("DELETE FROM users WHERE userId = :userId")
    suspend fun deleteUserById(userId: String)
    
    @Query("DELETE FROM users WHERE lastSyncedAt < :timestamp")
    suspend fun deleteOldUsers(timestamp: Long)
    
    @Query("UPDATE users SET isFollowing = :isFollowing WHERE userId = :userId")
    suspend fun updateFollowingStatus(userId: String, isFollowing: Boolean)
    
    @Query("SELECT COUNT(*) FROM users")
    suspend fun getUserCount(): Int
}
