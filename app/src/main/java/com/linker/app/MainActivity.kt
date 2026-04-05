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
import com.linker.app.domain.repository.AccountRepository
import com.linker.app.core.util.Result as LinkerResult
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Main Activity - Single Activity Architecture
 *
 * Hosts the entire app using Jetpack Compose and Navigation.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var pushTokenRegistrar: PushTokenRegistrar
    @Inject lateinit var accountRepository: AccountRepository

    private var pendingChatId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
            when (val r = accountRepository.switchToAccount(target)) {
                is LinkerResult.Error -> android.util.Log.w(
                    "MainActivity",
                    "switchToAccount failed: ${r.message}"
                )
                else -> { }
            }
        }
        pendingChatId = intent?.getStringExtra("chat_id")
    }
}
