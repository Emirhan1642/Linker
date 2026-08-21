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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linker.app.R

sealed interface SettingsItem {
    data class Navigation(
        val iconRes: Int, val labelRes: Int,
        val valueRes: Int? = null, val valueString: String? = null, val onClick: () -> Unit = {}
    ) : SettingsItem

    data class Toggle(
        val iconRes: Int, val labelRes: Int,
        val fieldKey: SettingField,
        val checked: Boolean, val onToggle: (Boolean) -> Unit
    ) : SettingsItem

    data class Danger(
        val iconRes: Int, val labelRes: Int,
        val isWarning: Boolean = false, val onClick: () -> Unit = {}
    ) : SettingsItem
}

data class SettingsSection(val titleRes: Int, val items: List<SettingsItem>)

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAccountCenter: () -> Unit = {},
    onNavigateToPendingRequests: () -> Unit = {},
    onNavigateToOfflineMessaging: () -> Unit = {},
    onNavigateToEditProfile: () -> Unit = {},
    onNavigateToBlockedUsers: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showLanguageDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it.asString(context))
            viewModel.dismissSnackbar()
        }
    }

    val currentLangLabel = when (uiState.currentLanguage) {
        "tr" -> stringResource(R.string.settings_language_tr)
        "en" -> stringResource(R.string.settings_language_en)
        else -> stringResource(R.string.settings_language_system)
    }

    val sections = listOf(
        SettingsSection(R.string.settings_account, listOf(
            SettingsItem.Navigation(R.drawable.ic_profile_outline, R.string.settings_edit_profile, onClick = onNavigateToEditProfile),
            SettingsItem.Navigation(
                R.drawable.ic_ai_users_outline, R.string.settings_account_center,
                valueRes = R.string.settings_switch_accounts, onClick = onNavigateToAccountCenter
            ),
            SettingsItem.Navigation(R.drawable.ic_security_safe_outline, R.string.settings_password_security, onClick = {
                viewModel.showSnackbar(com.linker.app.core.util.UiText.StringResource(R.string.settings_toast_security_soon))
            }),
            SettingsItem.Navigation(R.drawable.ic_link_3_outline, R.string.settings_linked_accounts, onClick = onNavigateToAccountCenter),
            SettingsItem.Navigation(R.drawable.ic_smart_lock_ai_outline, R.string.settings_2fa, onClick = {
                viewModel.showSnackbar(com.linker.app.core.util.UiText.StringResource(R.string.settings_toast_2fa_soon))
            })
        )),
        SettingsSection(R.string.settings_privacy, listOf(
            SettingsItem.Toggle(
                R.drawable.ic_profile_2user_outline, R.string.settings_private_account, SettingField.PRIVATE_ACCOUNT,
                uiState.isPrivateAccount
            ) { viewModel.setPrivateAccount(it) },
            SettingsItem.Toggle(
                R.drawable.ic_ai_users_outline, R.string.settings_hide_follow_lists, SettingField.HIDE_FOLLOW_LISTS,
                uiState.hideFollowLists
            ) { viewModel.setHideFollowLists(it) },
            SettingsItem.Toggle(
                R.drawable.ic_eos__eos__outline, R.string.settings_activity_status, SettingField.ACTIVITY_STATUS,
                uiState.activityStatus
            ) { viewModel.setActivityStatus(it) },
            SettingsItem.Toggle(
                R.drawable.ic_ai_send_message_outline, R.string.settings_read_receipts, SettingField.READ_RECEIPTS,
                uiState.readReceipts
            ) { viewModel.setReadReceipts(it) },
            SettingsItem.Navigation(R.drawable.ic_close_circle_outline, R.string.settings_blocked_users, onClick = onNavigateToBlockedUsers),
            SettingsItem.Navigation(R.drawable.ic_ai_users_outline, R.string.settings_restricted_accounts, onClick = onNavigateToBlockedUsers),
            SettingsItem.Navigation(
                R.drawable.ic_bell_2_outline, R.string.settings_follow_requests,
                onClick = onNavigateToPendingRequests
            )
        )),
        SettingsSection(R.string.settings_notifications, listOf(
            SettingsItem.Toggle(R.drawable.ic_bell_2_outline, R.string.settings_push_notifications, SettingField.NOTIFICATIONS_ENABLED, uiState.notificationsEnabled) { viewModel.setNotificationsEnabled(it) },
            SettingsItem.Toggle(R.drawable.ic_story_outline, R.string.settings_story_notifications, SettingField.PUSH_STORIES, uiState.pushStories) { viewModel.setPushStories(it) },
            SettingsItem.Toggle(R.drawable.ic_ai_commentary_outline, R.string.settings_message_notifications, SettingField.PUSH_MESSAGES, uiState.pushMessages) { viewModel.setPushMessages(it) },
            SettingsItem.Navigation(R.drawable.ic_bell_2_outline, R.string.settings_notification_preferences, onClick = {
                viewModel.showSnackbar(com.linker.app.core.util.UiText.StringResource(R.string.settings_toast_notifications_configured))
            })
        )),
        SettingsSection(R.string.settings_appearance_media, listOf(
            SettingsItem.Navigation(
                R.drawable.ic_search_outline,
                R.string.settings_language,
                valueString = currentLangLabel,
                onClick = { showLanguageDialog = true }
            ),
            SettingsItem.Navigation(R.drawable.ic_paint_brush_2_outline, R.string.settings_theme, valueRes = R.string.settings_default_theme),
            SettingsItem.Toggle(R.drawable.ic_gallery_outline, R.string.settings_autoplay_videos, SettingField.AUTO_PLAY_VIDEOS, uiState.autoPlayVideos) { viewModel.setAutoPlayVideos(it) },
            SettingsItem.Toggle(R.drawable.ic_ai_sand_timer_outline, R.string.settings_data_saver, SettingField.DATA_SAVER, uiState.dataSaver) { viewModel.setDataSaver(it) }
        )),
        SettingsSection(R.string.settings_connectivity, listOf(
            SettingsItem.Navigation(R.drawable.ic_bluetooth_outline, R.string.settings_offline_messaging, onClick = onNavigateToOfflineMessaging)
        )),
        SettingsSection(R.string.settings_support_about, listOf(
            SettingsItem.Navigation(R.drawable.ic_search_outline, R.string.settings_help_center, onClick = {
                viewModel.showSnackbar(com.linker.app.core.util.UiText.StringResource(R.string.settings_toast_support_email))
            }),
            SettingsItem.Navigation(R.drawable.ic_bookmark_2_outline, R.string.settings_community_guidelines),
            SettingsItem.Navigation(R.drawable.ic_more_square_outline, R.string.settings_privacy_policy),
            SettingsItem.Navigation(R.drawable.ic_more_square_outline, R.string.settings_terms_of_service),
            SettingsItem.Navigation(R.drawable.ic_toy_6_outline, R.string.settings_version, valueString = "1.0.0")
        )),
        SettingsSection(R.string.settings_account_actions, listOf(
            SettingsItem.Danger(R.drawable.ic_ai_sand_timer_outline, R.string.settings_deactivate_account, isWarning = true, onClick = {
                viewModel.showSnackbar(com.linker.app.core.util.UiText.StringResource(R.string.settings_toast_deactivate_request))
            }),
            SettingsItem.Danger(R.drawable.ic_close_circle_outline, R.string.settings_delete_account, onClick = {
                viewModel.showSnackbar(com.linker.app.core.util.UiText.StringResource(R.string.settings_toast_delete_request))
            })
        ))
    )

    if (showLanguageDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.settings_language_dialog_title),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val languages = listOf(
                        "system" to stringResource(R.string.settings_language_system),
                        "tr" to stringResource(R.string.settings_language_tr),
                        "en" to stringResource(R.string.settings_language_en)
                    )
                    languages.forEach { (code, name) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.setLanguage(code)
                                    showLanguageDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = uiState.currentLanguage == code,
                                onClick = {
                                    viewModel.setLanguage(code)
                                    showLanguageDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = name,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(painterResource(R.drawable.ic_arrow_left_01_outline), stringResource(R.string.action_back),
                        tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(28.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.settings_title), color = MaterialTheme.colorScheme.onBackground, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
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
                        stringResource(section.titleRes).uppercase(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        section.items.forEachIndexed { idx, item ->
                            when (item) {
                                is SettingsItem.Navigation -> NavigationRow(item)
                                is SettingsItem.Toggle     -> ToggleRow(
                                    item = item,
                                    isSaving = uiState.savingField == item.fieldKey
                                )
                                is SettingsItem.Danger -> DangerRow(item)
                            }
                            if (idx < section.items.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 56.dp),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
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

@Composable
private fun NavigationRow(item: SettingsItem.Navigation) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { item.onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            IconBox(item.iconRes, stringResource(item.labelRes))
            Spacer(modifier = Modifier.width(14.dp))
            Text(stringResource(item.labelRes), color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val value = when {
                item.valueRes != null -> stringResource(item.valueRes)
                item.valueString != null -> item.valueString
                else -> null
            }
            if (value != null) {
                Text(value, color = MaterialTheme.colorScheme.outline, fontSize = 13.sp)
                Spacer(modifier = Modifier.width(6.dp))
            }
            Icon(painterResource(R.drawable.ic_arrow_left_01_outline), null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
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
            IconBox(item.iconRes, stringResource(item.labelRes))
            Spacer(modifier = Modifier.width(14.dp))
            Text(stringResource(item.labelRes), color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp)
        }
        if (isSaving) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            Switch(
                checked = item.checked,
                onCheckedChange = item.onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.background,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    }
}

@Composable
private fun DangerRow(item: SettingsItem.Danger) {
    val color = if (item.isWarning) Color(0xFFFFAA00) else MaterialTheme.colorScheme.error
    Row(
        modifier = Modifier.fillMaxWidth().clickable { item.onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(item.iconRes), stringResource(item.labelRes), tint = color, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(stringResource(item.labelRes), color = color, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun IconBox(iconRes: Int, label: String) {
    Box(
        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(painterResource(iconRes), label, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(20.dp))
    }
}
