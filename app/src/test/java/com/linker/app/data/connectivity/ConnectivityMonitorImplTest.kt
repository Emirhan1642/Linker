package com.linker.app.data.connectivity

import android.content.Context
import android.net.ConnectivityManager
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class ConnectivityMonitorImplTest {

    private lateinit var context: Context
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var monitor: ConnectivityMonitorImpl

    @Before
    fun setup() {
        context = mock(Context::class.java)
        connectivityManager = mock(ConnectivityManager::class.java)
        
        `when`(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(connectivityManager)
        
        monitor = ConnectivityMonitorImpl(context)
    }

    @Test
    fun `monitor initialization is successful`() {
        assertNotNull(monitor)
    }
}
