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

    private var pendingChatId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Start monitoring connectivity
        connectivityMonitor.startMonitoring()

        setContent {
            LinkerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LinkerNavHost(
                        initialChatId = pendingChatId,
                        onChatDeepLinkHandled = { pendingChatId = null }
                    )
                }
            }
        }

        lifecycleScope.launch {
            applyLaunchIntent(intent)
            pushTokenRegistrar.registerCurrentToken()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        lifecycleScope.launch {
            applyLaunchIntent(intent)
        }
    }

    /**
     * Önce hedef hesaba geç (bildirim), sonra sohbet deep link [pendingChatId] atanır;
     * böylece yanlış hesapta chat açılmaz.
     */
    private suspend fun applyLaunchIntent(intent: Intent?) {
        val target = intent?.getStringExtra(ChatNotificationHelper.EXTRA_TARGET_ACCOUNT_UID)
        if (!target.isNullOrBlank()) {
            val active = accountRepository.getActiveUid()
            if (active != target) {
                when (val r = accountRepository.switchToAccount(target)) {
                    is LinkerResult.Success -> {
                        // Restart activity to reload UI with new account
                        val chatId = intent.getStringExtra("chat_id")
                        val restartIntent = Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            putExtra("chat_id", chatId)
                        }
                        startActivity(restartIntent)
                        finish()
                    }
                    is LinkerResult.Error -> {
                        android.util.Log.w("MainActivity", "switchToAccount failed: ${r.message}")
                        pendingChatId = null
                    }
                    else -> {}
                }
                return
            }
        }
        pendingChatId = intent?.getStringExtra("chat_id")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Stop monitoring connectivity
        connectivityMonitor.stopMonitoring()
        // Cleanup all passive sessions when app is destroyed
        if (isFinishing) {
            hybridAccountManager.cleanupAllSessions()
        }
    }
}
