package com.linker.app.presentation.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.linker.app.R
import com.linker.app.presentation.theme.*

// ─── Data models ─────────────────────────────────────────────────────────────

sealed interface SettingsItem {
    data class Navigation(
        val iconRes: Int, val label: String,
        val value: String? = null, val onClick: () -> Unit = {}
    ) : SettingsItem

    data class Toggle(
        val iconRes: Int, val label: String,
        val checked: Boolean, val onToggle: (Boolean) -> Unit
    ) : SettingsItem

    data class Danger(
        val iconRes: Int, val label: String,
        val color: Color = Color(0xFFFF4B4B), val onClick: () -> Unit = {}
    ) : SettingsItem
}

data class SettingsSection(val title: String, val items: List<SettingsItem>)

// ─── Screen ──────────────────────────────────────────────────────────────────

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAccountCenter: () -> Unit = {},
    onNavigateToPendingRequests: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Snackbar
    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissSnackbar()
        }
    }

    // Local-only toggles (notif, theme, media)
    var notificationsEnabled by remember { mutableStateOf(true) }
    var pushStories          by remember { mutableStateOf(true) }
    var pushMessages         by remember { mutableStateOf(true) }
    var activityStatus       by remember { mutableStateOf(true) }
    var readReceipts         by remember { mutableStateOf(true) }
    var dataSaver            by remember { mutableStateOf(false) }
    var autoPlayVideos       by remember { mutableStateOf(true) }

    val sections = listOf(
        SettingsSection("Account", listOf(
            SettingsItem.Navigation(R.drawable.ic_profile_outline, "Edit Profile"),
            SettingsItem.Navigation(
                R.drawable.ic_ai_users_outline, "Account Center",
                value = "Switch accounts", onClick = onNavigateToAccountCenter
            ),
            SettingsItem.Navigation(R.drawable.ic_security_safe_outline, "Password & Security"),
            SettingsItem.Navigation(R.drawable.ic_link_3_outline, "Linked Accounts"),
            SettingsItem.Navigation(R.drawable.ic_smart_lock_ai_outline, "Two-Factor Authentication")
        )),
        SettingsSection("Privacy", listOf(
            // Private Account — Firestore'a yazılır
            SettingsItem.Toggle(
                R.drawable.ic_profile_2user_outline, "Private Account",
                uiState.isPrivateAccount
            ) { viewModel.setPrivateAccount(it) },
            SettingsItem.Toggle(R.drawable.ic_eos__eos__outline, "Activity Status", activityStatus) { activityStatus = it },
            SettingsItem.Toggle(R.drawable.ic_ai_send_message_outline, "Read Receipts", readReceipts) { readReceipts = it },
            SettingsItem.Navigation(R.drawable.ic_close_circle_outline, "Blocked Users"),
            SettingsItem.Navigation(R.drawable.ic_ai_users_outline, "Restricted Accounts"),
            // Gelen follow isteklerini görme
            SettingsItem.Navigation(
                R.drawable.ic_bell_2_outline, "Follow Requests",
                onClick = onNavigateToPendingRequests
            )
        )),
        SettingsSection("Notifications", listOf(
            SettingsItem.Toggle(R.drawable.ic_bell_2_outline, "Push Notifications", notificationsEnabled) { notificationsEnabled = it },
            SettingsItem.Toggle(R.drawable.ic_story_outline, "Story Notifications", pushStories) { pushStories = it },
            SettingsItem.Toggle(R.drawable.ic_ai_commentary_outline, "Message Notifications", pushMessages) { pushMessages = it },
            SettingsItem.Navigation(R.drawable.ic_bell_2_outline, "Notification Preferences")
        )),
        SettingsSection("Appearance & Media", listOf(
            SettingsItem.Navigation(R.drawable.ic_paint_brush_2_outline, "Theme", "Default"),
            SettingsItem.Toggle(R.drawable.ic_gallery_outline, "Auto-Play Videos", autoPlayVideos) { autoPlayVideos = it },
            SettingsItem.Toggle(R.drawable.ic_ai_sand_timer_outline, "Data Saver", dataSaver) { dataSaver = it }
        )),
        SettingsSection("Support & About", listOf(
            SettingsItem.Navigation(R.drawable.ic_search_outline, "Help Center"),
            SettingsItem.Navigation(R.drawable.ic_bookmark_2_outline, "Community Guidelines"),
            SettingsItem.Navigation(R.drawable.ic_more_square_outline, "Privacy Policy"),
            SettingsItem.Navigation(R.drawable.ic_more_square_outline, "Terms of Service"),
            SettingsItem.Navigation(R.drawable.ic_toy_6_outline, "Version", "1.0.0")
        )),
        SettingsSection("Account Actions", listOf(
            SettingsItem.Danger(R.drawable.ic_ai_sand_timer_outline, "Deactivate Account", Color(0xFFFFAA00)),
            SettingsItem.Danger(R.drawable.ic_close_circle_outline, "Delete Account")
        ))
    )

    Scaffold(
        containerColor = Black,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        painterResource(R.drawable.ic_arrow_left_01_outline),
                        contentDescription = "Back",
                        tint = TextPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Settings", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                sections.forEachIndexed { i, section ->
                    Spacer(modifier = Modifier.height(if (i == 0) 4.dp else 24.dp))
                    Text(
                        section.title.uppercase(),
                        color = TextSecondary, fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp)).background(DarkGray)
                    ) {
                        section.items.forEachIndexed { idx, item ->
                            when (item) {
                                is SettingsItem.Navigation -> NavigationRow(item)
                                is SettingsItem.Toggle     -> ToggleRow(item, uiState.isLoading && item.label == "Private Account")
                                is SettingsItem.Danger     -> DangerRow(item)
                            }
                            if (idx < section.items.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 56.dp),
                                    color = LightGray.copy(alpha = 0.4f),
                                    thickness = 0.5.dp
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}

// ─── Row composables ─────────────────────────────────────────────────────────

@Composable
private fun NavigationRow(item: SettingsItem.Navigation) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { item.onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            IconBox(item.iconRes, item.label)
            Spacer(modifier = Modifier.width(14.dp))
            Text(item.label, color = TextPrimary, fontSize = 15.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (item.value != null) {
                Text(item.value, color = TextHint, fontSize = 13.sp)
                Spacer(modifier = Modifier.width(6.dp))
            }
            Icon(painterResource(R.drawable.ic_arrow_left_01_outline), null, tint = TextHint, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ToggleRow(item: SettingsItem.Toggle, isSaving: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            IconBox(item.iconRes, item.label)
            Spacer(modifier = Modifier.width(14.dp))
            Text(item.label, color = TextPrimary, fontSize = 15.sp)
        }
        if (isSaving) {
            CircularProgressIndicator(color = AccentGreen, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            Switch(
                checked = item.checked,
                onCheckedChange = item.onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Black, checkedTrackColor = AccentGreen,
                    uncheckedThumbColor = TextSecondary, uncheckedTrackColor = LightGray
                )
            )
        }
    }
}

@Composable
private fun DangerRow(item: SettingsItem.Danger) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { item.onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                .background(item.color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(item.iconRes), item.label, tint = item.color, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(item.label, color = item.color, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun IconBox(iconRes: Int, label: String) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
            .background(LightGray.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(painterResource(iconRes), label, tint = TextPrimary, modifier = Modifier.size(20.dp))
    }
}
