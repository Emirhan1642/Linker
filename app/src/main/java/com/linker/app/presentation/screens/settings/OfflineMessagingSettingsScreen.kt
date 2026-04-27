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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.linker.app.R
import com.linker.app.presentation.theme.*

/**
 * Offline Messaging Settings Screen
 * 
 * Implements Requirements 15.1-15.9:
 * - Enable/disable offline messaging
 * - BLE mesh networking toggle
 * - Wi-Fi Direct media transfer toggle
 * - Display mesh node count and connection status
 * - Clear offline message queue
 * - Set maximum TTL (1-10 hops)
 * - Display battery usage statistics
 * - Enable/disable foreground service notification
 */
@Composable
fun OfflineMessagingSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: OfflineMessagingSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissSnackbar()
        }
    }
    
    // Permission dialogs
    if (uiState.showPermissionRationale) {
        PermissionRationaleDialog(
            permissionType = uiState.permissionType,
            onDismiss = { viewModel.dismissPermissionRationale() },
            onConfirm = { viewModel.requestPermissions() }
        )
    }
    
    if (uiState.showPermissionSettings) {
        PermissionSettingsDialog(
            onDismiss = { viewModel.dismissPermissionSettings() },
            onOpenSettings = { viewModel.openAppSettings() }
        )
    }
    
    // TTL Picker Dialog
    if (uiState.showTtlPicker) {
        TtlPickerDialog(
            currentTtl = uiState.maxTtl,
            onDismiss = { viewModel.dismissTtlPicker() },
            onConfirm = { viewModel.setMaxTtl(it) }
        )
    }
    
    // Clear Queue Confirmation Dialog
    if (uiState.showClearQueueDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissClearQueueDialog() },
            title = { Text("Clear Message Queue?", color = TextPrimary) },
            text = { 
                Text(
                    "This will delete all pending offline messages. This action cannot be undone.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.clearMessageQueue() }) {
                    Text("Clear", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissClearQueueDialog() }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkGray
        )
    }
    
    Scaffold(
        containerColor = Black,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        painterResource(R.drawable.ic_arrow_left_01_outline),
                        "Back",
                        tint = TextPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Offline Messaging",
                    color = TextPrimary,
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
                // Status Section
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "STATUS",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                )
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkGray)
                        .padding(16.dp)
                ) {
                    StatusRow(
                        label = "Service Status",
                        value = if (uiState.isServiceRunning) "Running" else "Stopped",
                        valueColor = if (uiState.isServiceRunning) AccentGreen else TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    StatusRow(
                        label = "Connected Nodes",
                        value = "${uiState.connectedNodeCount}",
                        valueColor = if (uiState.connectedNodeCount > 0) AccentGreen else TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    StatusRow(
                        label = "Pending Messages",
                        value = "${uiState.pendingMessageCount}",
                        valueColor = if (uiState.pendingMessageCount > 0) InfoBlue else TextSecondary
                    )
                }
                
                // Main Settings
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "SETTINGS",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                )
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkGray)
                ) {
                    ToggleRow(
                        iconRes = R.drawable.ic_bluetooth_outline,
                        label = "Offline Messaging",
                        checked = uiState.isOfflineMessagingEnabled,
                        onToggle = { viewModel.toggleOfflineMessaging(it) },
                        isSaving = uiState.isTogglingService
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = LightGray.copy(alpha = 0.4f),
                        thickness = 0.5.dp
                    )
                    ToggleRow(
                        iconRes = R.drawable.ic_bluetooth_bold,
                        label = "BLE Mesh Network",
                        checked = uiState.isBleEnabled,
                        onToggle = { viewModel.toggleBle(it) },
                        enabled = uiState.isOfflineMessagingEnabled
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = LightGray.copy(alpha = 0.4f),
                        thickness = 0.5.dp
                    )
                    ToggleRow(
                        iconRes = R.drawable.ic_wifi_bold,
                        label = "Wi-Fi Direct Transfer",
                        checked = uiState.isWifiDirectEnabled,
                        onToggle = { viewModel.toggleWifiDirect(it) },
                        enabled = uiState.isOfflineMessagingEnabled
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = LightGray.copy(alpha = 0.4f),
                        thickness = 0.5.dp
                    )
                    ToggleRow(
                        iconRes = R.drawable.ic_bell_2_outline,
                        label = "Show Notification",
                        checked = uiState.showNotification,
                        onToggle = { viewModel.toggleNotification(it) },
                        enabled = uiState.isOfflineMessagingEnabled
                    )
                }
                
                // Advanced Settings
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "ADVANCED",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                )
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkGray)
                ) {
                    NavigationRow(
                        iconRes = R.drawable.ic_driver_outline,
                        label = "Maximum Hops (TTL)",
                        value = "${uiState.maxTtl} hops",
                        onClick = { viewModel.showTtlPicker() }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = LightGray.copy(alpha = 0.4f),
                        thickness = 0.5.dp
                    )
                    NavigationRow(
                        iconRes = R.drawable.ic_battery_charging_outline,
                        label = "Battery Usage",
                        value = "${uiState.batteryUsagePercent}%",
                        onClick = { /* TODO: Show battery details */ }
                    )
                }
                
                // Actions
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "ACTIONS",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                )
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkGray)
                ) {
                    DangerRow(
                        iconRes = R.drawable.ic_trash_outline,
                        label = "Clear Message Queue",
                        color = ErrorRed,
                        onClick = { viewModel.showClearQueueDialog() }
                    )
                }
                
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    value: String,
    valueColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = TextSecondary,
            fontSize = 14.sp
        )
        Text(
            value,
            color = valueColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ToggleRow(
    iconRes: Int,
    label: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    enabled: Boolean = true,
    isSaving: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .alpha(if (enabled) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            IconBox(iconRes, label)
            Spacer(modifier = Modifier.width(14.dp))
            Text(label, color = TextPrimary, fontSize = 15.sp)
        }
        if (isSaving) {
            CircularProgressIndicator(
                color = AccentGreen,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Switch(
                checked = checked,
                onCheckedChange = if (enabled) onToggle else { {} },
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Black,
                    checkedTrackColor = AccentGreen,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = LightGray,
                    disabledCheckedThumbColor = Black.copy(alpha = 0.5f),
                    disabledCheckedTrackColor = AccentGreen.copy(alpha = 0.5f),
                    disabledUncheckedThumbColor = TextSecondary.copy(alpha = 0.5f),
                    disabledUncheckedTrackColor = LightGray.copy(alpha = 0.5f)
                )
            )
        }
    }
}

@Composable
private fun NavigationRow(
    iconRes: Int,
    label: String,
    value: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            IconBox(iconRes, label)
            Spacer(modifier = Modifier.width(14.dp))
            Text(label, color = TextPrimary, fontSize = 15.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (value != null) {
                Text(value, color = TextHint, fontSize = 13.sp)
                Spacer(modifier = Modifier.width(6.dp))
            }
            Icon(
                painterResource(R.drawable.ic_arrow_left_01_outline),
                null,
                tint = TextHint,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun DangerRow(
    iconRes: Int,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(iconRes),
                label,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            label,
            color = color,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun IconBox(iconRes: Int, label: String) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(LightGray.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painterResource(iconRes),
            label,
            tint = TextPrimary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun TtlPickerDialog(
    currentTtl: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var selectedTtl by remember { mutableStateOf(currentTtl) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Maximum Hops (TTL)", color = TextPrimary) },
        text = {
            Column {
                Text(
                    "Select how many hops a message can travel through the mesh network.",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                // TTL Slider
                Column {
                    Text(
                        "${selectedTtl} hops",
                        color = AccentGreen,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Slider(
                        value = selectedTtl.toFloat(),
                        onValueChange = { selectedTtl = it.toInt() },
                        valueRange = 1f..10f,
                        steps = 8,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentGreen,
                            activeTrackColor = AccentGreen,
                            inactiveTrackColor = LightGray
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("1", color = TextHint, fontSize = 12.sp)
                        Text("10", color = TextHint, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedTtl) }) {
                Text("Save", color = AccentGreen)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = DarkGray
    )
}

@Composable
private fun PermissionRationaleDialog(
    permissionType: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val (title, message) = when (permissionType) {
        "bluetooth" -> "Bluetooth Permission" to "Offline messaging requires Bluetooth to connect with nearby devices."
        "location" -> "Location Permission" to "Location permission is required for Bluetooth scanning on Android 12+."
        "nearby" -> "Nearby Devices Permission" to "This permission allows Wi-Fi Direct file transfers with nearby devices."
        else -> "Permission Required" to "This permission is required for offline messaging."
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = TextPrimary) },
        text = { Text(message, color = TextSecondary) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Grant", color = AccentGreen)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = DarkGray
    )
}

@Composable
private fun PermissionSettingsDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Permission Denied", color = TextPrimary) },
        text = {
            Text(
                "This permission has been permanently denied. Please enable it in app settings.",
                color = TextSecondary
            )
        },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text("Open Settings", color = AccentGreen)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = DarkGray
    )
}
