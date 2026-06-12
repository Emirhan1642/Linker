package com.linker.app

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.material3.CircularProgressIndicator
import com.linker.app.presentation.navigation.LinkerNavHost
import com.linker.app.presentation.theme.LinkerTheme
import dagger.hilt.android.AndroidEntryPoint
import com.linker.app.core.notification.PushTokenRegistrar
import com.linker.app.core.notification.ChatNotificationHelper
import com.linker.app.core.session.HybridAccountManager
import com.linker.app.domain.repository.AccountRepository
import com.linker.app.core.util.Result as LinkerResult
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Main Activity - Single Activity Architecture
 *
 * Hosts the entire app using Jetpack Compose and Navigation.
 * 
 * DEPENDENCY INJECTION:
 * Uses Hilt field injection (@Inject lateinit var) which is the standard
 * pattern for Android Activities. Constructor injection is not possible
 * for Activities as they are instantiated by the Android framework.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var pushTokenRegistrar: PushTokenRegistrar
    @Inject lateinit var accountRepository: AccountRepository
    @Inject lateinit var hybridAccountManager: HybridAccountManager
    @Inject lateinit var connectivityMonitor: com.linker.app.data.connectivity.ConnectivityMonitor

    companion object {
        private const val TAG = "MainActivity"
        const val EXTRA_CHAT_ID = "chat_id"
    }

    private var pendingChatId by mutableStateOf<String?>(null)
    private var accountSwitchInProgress by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        // MIUI and some other OEM frameworks might perform disk reads for font settings inside super.onCreate
        val oldPolicy = android.os.StrictMode.allowThreadDiskReads()
        try {
            super.onCreate(savedInstanceState)
        } finally {
            android.os.StrictMode.setThreadPolicy(oldPolicy)
        }

        enableEdgeToEdge()

        // Start monitoring connectivity - lifecycle aware
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                connectivityMonitor.startMonitoring()
            }
            override fun onStop(owner: LifecycleOwner) {
                connectivityMonitor.stopMonitoring()
            }
        })

        setContent {
            LinkerTheme {
                if (accountSwitchInProgress) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        LinkerNavHost(
                            currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid,
                            initialChatId = pendingChatId,
                            onChatDeepLinkHandled = { pendingChatId = null }
                        )
                    }
                }
            }
        }

        lifecycleScope.launch {
            try {
                applyLaunchIntent(intent)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to apply launch intent", e)
                pendingChatId = null
            }

            try {
                pushTokenRegistrar.registerCurrentToken()
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to register push token", e)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        lifecycleScope.launch {
            try {
                applyLaunchIntent(intent)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to apply new intent", e)
                pendingChatId = null
            }
        }
    }

    /**
     * Önce hedef hesaba geç (bildirim), sonra sohbet deep link [pendingChatId] atanır;
     * böylece yanlış hesapta chat açılmaz.
     */
    private suspend fun applyLaunchIntent(intent: Intent?) {
        try {
            val target = intent?.getStringExtra(com.linker.app.core.notification.NotificationConstants.EXTRA_TARGET_ACCOUNT_UID)
            if (!target.isNullOrBlank()) {
                val activeResult = accountRepository.getActiveUid()
                val active = if (activeResult is LinkerResult.Success) activeResult.data else null
                if (active != target) {
                    accountSwitchInProgress = true
                    when (val r = accountRepository.switchToAccount(target)) {
                        is LinkerResult.Success -> {
                            pendingChatId = intent.getStringExtra(EXTRA_CHAT_ID)
                            accountSwitchInProgress = false
                        }
                        is LinkerResult.Error -> {
                            android.util.Log.w(TAG, "switchToAccount failed: ${r.message}")
                            pendingChatId = null
                            accountSwitchInProgress = false
                            showErrorToast("Hesap değiştirilemedi: ${r.message}")
                        }
                        else -> {
                            accountSwitchInProgress = false
                        }
                    }
                    return
                }
            }
            pendingChatId = intent?.getStringExtra(EXTRA_CHAT_ID)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error in applyLaunchIntent", e)
            pendingChatId = null
            throw e
        }
    }

    private fun showErrorToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Cleanup all passive sessions when app is destroyed
        if (isFinishing) {
            lifecycleScope.launch {
                hybridAccountManager.cleanupAllSessions()
            }
        }
    }
}
