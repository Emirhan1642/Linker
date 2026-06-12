package com.linker.app.presentation.screens.accountcenter

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.linker.app.R
import com.linker.app.domain.model.AccountSession
import com.linker.app.presentation.theme.*

@Composable
fun AccountCenterScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAuth: () -> Unit,
    onSwitchComplete: () -> Unit,
    viewModel: AccountCenterViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var pendingRemoveUid by remember { mutableStateOf<String?>(null) }

    // Hesap geçişi tamamlandığında Home'a git
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is AccountCenterEffect.SwitchComplete -> onSwitchComplete()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            // Scaffold yok — system bar padding manuel
            .systemBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Top Bar ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        painterResource(R.drawable.ic_arrow_left_01_outline),
                        contentDescription = "Back",
                        tint = TextPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Text(
                    "Account Center",
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = onNavigateToAuth) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(AccentGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_ai_add_outline),
                            contentDescription = "Add account",
                            tint = Black,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // ── Security Note ─────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1A2E1A))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    painterResource(R.drawable.ic_security_safe_outline),
                    null,
                    tint = AccentGreen,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    "Sessions are encrypted with Android Keystore. No passwords are stored.",
                    color = AccentGreen,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Session List ──────────────────────────────────────────────────
            if (uiState.sessions.isEmpty()) {
                EmptyState(onAddAccount = onNavigateToAuth)
            } else {
                val sortedSessions = remember(uiState.sessions) {
                    uiState.sessions.sortedByDescending { it.lastUsedAt }
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    items(
                        items = sortedSessions,
                        key = { it.uid }
                    ) { session ->
                        AccountCard(
                            session = session,
                            isActive = session.uid == uiState.activeUid,
                            isSwitching = uiState.isSwitching && session.uid != uiState.activeUid,
                            onSwitch = {
                                if (session.uid != uiState.activeUid) viewModel.switchAccount(session.uid)
                            },
                            onRemove = { pendingRemoveUid = session.uid }
                        )
                    }
                    item { AddAccountRow(onClick = onNavigateToAuth) }
                }
            }
        }

        // ── Switching overlay ─────────────────────────────────────────────────
        AnimatedVisibility(
            visible = uiState.isSwitching,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = AccentGreen, strokeWidth = 3.dp, modifier = Modifier.size(48.dp))
                    Text("Switching account…", color = TextPrimary, fontSize = 15.sp)
                }
            }
        }

        // ── Error snackbar ────────────────────────────────────────────────────
        uiState.switchError?.let { error ->
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
                action = {
                    TextButton(onClick = { viewModel.dismissError() }) {
                        Text("Dismiss", color = AccentGreen)
                    }
                },
                containerColor = DarkGray
            ) { Text(error, color = TextPrimary) }
        }
    }

    // ── Remove confirmation ───────────────────────────────────────────────────
    pendingRemoveUid?.let { uid ->
        val session = uiState.sessions.firstOrNull { it.uid == uid }
        AlertDialog(
            onDismissRequest = { pendingRemoveUid = null },
            containerColor = DarkGray,
            icon = {
                Icon(painterResource(R.drawable.ic_close_circle_outline), null, tint = ErrorRed)
            },
            title = {
                Text("Remove account?", color = TextPrimary, fontWeight = FontWeight.SemiBold)
            },
            text = {
                Text(
                    "\"${session?.displayName ?: uid}\" will be removed from this device.",
                    color = TextSecondary, fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.removeAccount(uid); pendingRemoveUid = null }) {
                    Text("Remove", color = ErrorRed, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoveUid = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

// ── Account Card ──────────────────────────────────────────────────────────────

@Composable
private fun AccountCard(
    session: AccountSession,
    isActive: Boolean,
    isSwitching: Boolean,
    onSwitch: () -> Unit,
    onRemove: () -> Unit
) {
    val borderBrush = if (isActive) LinkerAngularGradient
    else Brush.linearGradient(listOf(LightGray, LightGray))
    val borderWidth by animateDpAsState(if (isActive) 2.dp else 1.dp, label = "border")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(DarkGray)
            .border(borderWidth, borderBrush, RoundedCornerShape(18.dp))
            .clickable(enabled = !isSwitching && !isActive, onClick = onSwitch)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(modifier = Modifier.size(52.dp)) {
                if (session.avatarUrl != null) {
                    AsyncImage(
                        model = session.avatarUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().clip(CircleShape).background(LightGray),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(painterResource(R.drawable.ic_profile_outline), null, tint = TextSecondary, modifier = Modifier.size(28.dp))
                    }
                }
                if (isActive) {
                    Box(
                        modifier = Modifier
                            .size(14.dp).align(Alignment.BottomEnd)
                            .clip(CircleShape).background(Black)
                            .padding(2.dp).clip(CircleShape).background(AccentGreen)
                    )
                }
            }

            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(session.displayName, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    if (isActive) {
                        Text(
                            "Active", color = AccentGreen, fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(AccentGreen.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text("@${session.username}", color = TextSecondary, fontSize = 13.sp)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (isSwitching) {
                CircularProgressIndicator(color = AccentGreen, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            } else if (!isActive) {
                Icon(painterResource(R.drawable.ic_arrow_left_01_outline), "Switch", tint = TextHint, modifier = Modifier.size(18.dp))
            }
            if (!isActive) {
                IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                    Icon(painterResource(R.drawable.ic_close_circle_outline), "Remove", tint = ErrorRed.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

// ── Add account row ───────────────────────────────────────────────────────────

@Composable
private fun AddAccountRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(DarkGray)
            .border(1.dp, LightGray.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp).clip(CircleShape)
                .background(AccentGreen.copy(alpha = 0.12f))
                .border(1.dp, AccentGreen.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(R.drawable.ic_ai_add_outline), null, tint = AccentGreen, modifier = Modifier.size(26.dp))
        }
        Column {
            Text("Add another account", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text("Log in or create a new Linker account", color = TextSecondary, fontSize = 12.sp)
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(onAddAccount: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(painterResource(R.drawable.ic_ai_users_outline), null, tint = TextHint, modifier = Modifier.size(64.dp))
        Text("No saved accounts", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Add an account to switch between profiles without signing in every time.",
            color = TextSecondary, fontSize = 14.sp, lineHeight = 20.sp
        )
        Button(
            onClick = onAddAccount,
            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Add account", color = Black, fontWeight = FontWeight.SemiBold)
        }
    }
}
