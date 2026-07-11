package com.linker.app.data.cache

import android.content.Context
import com.linker.app.domain.model.User
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class UserCacheTest {

    private lateinit var context: Context
    private lateinit var userCache: UserCache

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        userCache = UserCache(context)
    }

    @Test
    fun `put and get display name works`() {
        userCache.putDisplayName("user1", "John Doe")
        assertEquals("John Doe", userCache.getDisplayName("user1"))
    }

    @Test
    fun `put and get user works`() {
        val user = User(userId = "user1", username = "johndoe", displayName = "John Doe")
        userCache.putUser(user)
        
        val cachedUser = userCache.getUser("user1")
        assertEquals("user1", cachedUser?.userId)
        assertEquals("John Doe", cachedUser?.displayName)
    }

    @Test
    fun `clear cache works`() {
        userCache.putDisplayName("user1", "John Doe")
        userCache.clear()
        
        assertNull(userCache.getDisplayName("user1"))
    }

    @Test
    fun `blank inputs are ignored`() {
        userCache.putDisplayName("", "Test")
        assertNull(userCache.getDisplayName(""))
        
        userCache.putUser(User(userId = "", username = "test"))
        assertNull(userCache.getUser(""))
    }
}
