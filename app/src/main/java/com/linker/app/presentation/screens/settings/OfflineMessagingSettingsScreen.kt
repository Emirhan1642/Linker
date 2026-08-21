package com.linker.app.presentation.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.draw.alpha
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

@Composable
fun OfflineMessagingSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: OfflineMessagingSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        val permanentlyDenied = permissions.any { (_, granted) -> !granted }
        viewModel.onPermissionResult(allGranted, permanentlyDenied)
    }
    
    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { text ->
            snackbarHostState.showSnackbar(text.asString(context))
            viewModel.dismissSnackbar()
        }
    }
    
    OfflineMessagingDialogs(
        uiState = uiState,
        viewModel = viewModel,
        permissionLauncher = permissionLauncher
    )
    
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        OfflineMessagingSettingsContent(
            modifier = Modifier.fillMaxSize().padding(padding),
            uiState = uiState,
            viewModel = viewModel,
            onNavigateBack = onNavigateBack
        )
    }
}

@Composable
private fun OfflineMessagingDialogs(
    uiState: OfflineMessagingSettingsUiState,
    viewModel: OfflineMessagingSettingsViewModel,
    permissionLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>
) {
    if (uiState.showPermissionRationale) {
        PermissionRationaleDialog(
            permissionType = uiState.permissionType,
            onDismiss = viewModel::dismissPermissionRationale,
            onConfirm = {
                viewModel.dismissPermissionRationale()
                val permissions = when (uiState.permissionType) {
                    "bluetooth" -> arrayOf(
                        android.Manifest.permission.BLUETOOTH_SCAN,
                        android.Manifest.permission.BLUETOOTH_CONNECT,
                        android.Manifest.permission.BLUETOOTH_ADVERTISE,
                        android.Manifest.permission.ACCESS_FINE_LOCATION
                    )
                    "location" -> arrayOf(
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                    "nearby" -> arrayOf(
                        android.Manifest.permission.NEARBY_WIFI_DEVICES
                    )
                    else -> arrayOf(
                        android.Manifest.permission.BLUETOOTH_SCAN,
                        android.Manifest.permission.BLUETOOTH_CONNECT,
                        android.Manifest.permission.BLUETOOTH_ADVERTISE,
                        android.Manifest.permission.ACCESS_FINE_LOCATION
                    )
                }
                permissionLauncher.launch(permissions)
            }
        )
    }
    
    if (uiState.showPermissionSettings) {
        PermissionSettingsDialog(
            onDismiss = viewModel::dismissPermissionSettings,
            onOpenSettings = viewModel::openAppSettings
        )
    }
    
    if (uiState.showBluetoothDialog) {
        BluetoothDialog(
            onDismiss = viewModel::dismissBluetoothDialog,
            onEnable = viewModel::enableBluetooth
        )
    }
    
    if (uiState.showTtlPicker) {
        TtlPickerDialog(
            currentTtl = uiState.maxTtl,
            onDismiss = viewModel::dismissTtlPicker,
            onConfirm = viewModel::setMaxTtl
        )
    }
    
    if (uiState.showClearQueueDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissClearQueueDialog,
            title = { Text(stringResource(R.string.clear_queue_title), color = MaterialTheme.colorScheme.onBackground) },
            text = { Text(stringResource(R.string.clear_queue_desc), color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = viewModel::clearMessageQueue) {
                    Text(stringResource(R.string.action_clear), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissClearQueueDialog) {
                    Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
private fun OfflineMessagingSettingsContent(
    modifier: Modifier = Modifier,
    uiState: OfflineMessagingSettingsUiState,
    viewModel: OfflineMessagingSettingsViewModel,
    onNavigateBack: () -> Unit
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    painterResource(R.drawable.ic_arrow_left_01_outline),
                    contentDescription = stringResource(R.string.action_back),
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                stringResource(R.string.offline_messaging_title),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                stringResource(R.string.settings_status),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
            )
            
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp)
            ) {
                StatusRow(
                    label = "Service Status",
                    value = if (uiState.isServiceRunning) stringResource(R.string.status_running) else stringResource(R.string.status_stopped),
                    valueColor = if (uiState.isServiceRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                StatusRow(
                    label = stringResource(R.string.status_connected_nodes),
                    value = "${uiState.connectedNodeCount}",
                    valueColor = if (uiState.connectedNodeCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                StatusRow(
                    label = stringResource(R.string.status_pending_messages),
                    value = "${uiState.pendingMessageCount}",
                    valueColor = if (uiState.pendingMessageCount > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                stringResource(R.string.settings_header),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
            )
            
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                ToggleRow(
                    iconRes = R.drawable.ic_bluetooth_outline,
                    label = stringResource(R.string.offline_messaging_enable),
                    checked = uiState.isOfflineMessagingEnabled,
                    onToggle = viewModel::toggleOfflineMessaging,
                    isSaving = uiState.isTogglingService
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f), thickness = 0.5.dp)
                ToggleRow(
                    iconRes = R.drawable.ic_bluetooth_bold,
                    label = stringResource(R.string.ble_mesh_network),
                    checked = uiState.isBleEnabled,
                    onToggle = viewModel::toggleBle,
                    enabled = uiState.isOfflineMessagingEnabled
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f), thickness = 0.5.dp)
                ToggleRow(
                    iconRes = R.drawable.ic_wifi_bold,
                    label = stringResource(R.string.wifi_direct_transfer),
                    checked = uiState.isWifiDirectEnabled,
                    onToggle = viewModel::toggleWifiDirect,
                    enabled = uiState.isOfflineMessagingEnabled
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f), thickness = 0.5.dp)
                ToggleRow(
                    iconRes = R.drawable.ic_bell_2_outline,
                    label = stringResource(R.string.show_notification),
                    checked = uiState.showNotification,
                    onToggle = viewModel::toggleNotification,
                    enabled = uiState.isOfflineMessagingEnabled
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                stringResource(R.string.settings_advanced),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
            )
            
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                NavigationRow(
                    iconRes = R.drawable.ic_driver_outline,
                    label = stringResource(R.string.max_hops_ttl),
                    value = stringResource(R.string.hops, uiState.maxTtl),
                    onClick = viewModel::showTtlPicker
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f), thickness = 0.5.dp)
                NavigationRow(
                    iconRes = R.drawable.ic_battery_charging_outline,
                    label = stringResource(R.string.battery_usage),
                    value = "${uiState.batteryUsagePercent}%",
                    onClick = { }
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                stringResource(R.string.settings_actions),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
            )
            
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                DangerRow(
                    iconRes = R.drawable.ic_trash_outline,
                    label = stringResource(R.string.clear_message_queue),
                    color = MaterialTheme.colorScheme.error,
                    onClick = viewModel::showClearQueueDialog
                )
            }
            
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Text(value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ToggleRow(
    iconRes: Int, label: String, checked: Boolean,
    onToggle: (Boolean) -> Unit, enabled: Boolean = true, isSaving: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
            .alpha(if (enabled) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            IconBox(iconRes, label)
            Spacer(modifier = Modifier.width(14.dp))
            Text(label, color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp)
        }
        if (isSaving) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            Switch(
                checked = checked,
                onCheckedChange = if (enabled) onToggle else { {} },
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.background,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surface,
                    disabledCheckedThumbColor = MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                    disabledCheckedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    disabledUncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    disabledUncheckedTrackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                )
            )
        }
    }
}

@Composable
private fun NavigationRow(iconRes: Int, label: String, value: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            IconBox(iconRes, label)
            Spacer(modifier = Modifier.width(14.dp))
            Text(label, color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (value != null) {
                Text(value, color = MaterialTheme.colorScheme.outline, fontSize = 13.sp)
                Spacer(modifier = Modifier.width(6.dp))
            }
            Icon(painterResource(R.drawable.ic_arrow_left_01_outline), null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun DangerRow(iconRes: Int, label: String, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(iconRes), label, tint = color, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(label, color = color, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun IconBox(iconRes: Int, label: String) {
    Box(
        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(painterResource(iconRes), label, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun TtlPickerDialog(currentTtl: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var selectedTtl by remember { mutableIntStateOf(currentTtl) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ttl_picker_title), color = MaterialTheme.colorScheme.onBackground) },
        text = {
            Column {
                Text(stringResource(R.string.ttl_picker_desc), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Column {
                    Text(stringResource(R.string.hops, selectedTtl), color = MaterialTheme.colorScheme.primary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Slider(
                        value = selectedTtl.toFloat(), onValueChange = { selectedTtl = it.toInt() },
                        valueRange = 1f..10f, steps = 8,
                        colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary, inactiveTrackColor = MaterialTheme.colorScheme.surface)
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("1", color = MaterialTheme.colorScheme.outline, fontSize = 12.sp)
                        Text("10", color = MaterialTheme.colorScheme.outline, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selectedTtl) }) { Text(stringResource(R.string.action_save), color = MaterialTheme.colorScheme.primary) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    )
}

@Composable
private fun PermissionRationaleDialog(permissionType: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val (titleRes, messageRes) = when (permissionType) {
        "bluetooth" -> R.string.permission_bluetooth_title to R.string.permission_bluetooth_desc
        "location" -> R.string.permission_location_title to R.string.permission_location_desc_offline
        "nearby" -> R.string.permission_nearby_title to R.string.permission_nearby_desc
        else -> R.string.permission_required_title to R.string.permission_required_desc
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes), color = MaterialTheme.colorScheme.onBackground) },
        text = { Text(stringResource(messageRes), color = MaterialTheme.colorScheme.onSurfaceVariant) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_grant), color = MaterialTheme.colorScheme.primary) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    )
}

@Composable
private fun PermissionSettingsDialog(onDismiss: () -> Unit, onOpenSettings: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.permission_denied_title), color = MaterialTheme.colorScheme.onBackground) },
        text = { Text(stringResource(R.string.permission_denied_desc), color = MaterialTheme.colorScheme.onSurfaceVariant) },
        confirmButton = { TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.action_open_settings), color = MaterialTheme.colorScheme.primary) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    )
}

@Composable
private fun BluetoothDialog(onDismiss: () -> Unit, onEnable: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bluetooth_disabled_title), color = MaterialTheme.colorScheme.onBackground) },
        text = { Text(stringResource(R.string.bluetooth_disabled_desc), color = MaterialTheme.colorScheme.onSurfaceVariant) },
        confirmButton = { TextButton(onClick = onEnable) { Text(stringResource(R.string.action_enable), color = MaterialTheme.colorScheme.primary) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    )
}
