package com.linker.app.presentation.screens.auth.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linker.app.R
import com.linker.app.presentation.screens.auth.AuthUiState
import com.linker.app.presentation.theme.*

import androidx.compose.ui.res.stringResource

@Composable
fun ProfileSetupContent(
    uiState: AuthUiState,
    onUsernameChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onComplete: () -> Unit,
    onGoBack: () -> Unit,
    onImageClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onGoBack) {
                Icon(painter = painterResource(id = R.drawable.ic_arrow_left_01_outline), contentDescription = stringResource(R.string.action_back), tint = TextPrimary, modifier = Modifier.size(30.dp))
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Box(
            modifier = Modifier.size(120.dp).clip(CircleShape).border(3.dp, LinkerAngularGradient, CircleShape).padding(4.dp).clip(CircleShape).background(LightGray).clickable { onImageClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(painter = painterResource(id = R.drawable.ic_profile_outline), contentDescription = stringResource(R.string.auth_tap_to_add_photo), tint = TextSecondary, modifier = Modifier.size(48.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(stringResource(R.string.auth_tap_to_add_photo), color = TextHint, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(32.dp))
        Text(stringResource(R.string.auth_complete_profile_title), color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Text(stringResource(R.string.auth_choose_username_subtitle), color = TextSecondary, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(32.dp))
        LinkerTextField(value = uiState.username, onValueChange = onUsernameChange, placeholder = stringResource(R.string.auth_username_hint), errorMessage = uiState.usernameError, leadingIcon = R.drawable.ic_profile_outline)
        Spacer(modifier = Modifier.height(16.dp))
        LinkerTextField(value = uiState.displayName, onValueChange = onDisplayNameChange, placeholder = stringResource(R.string.auth_display_name_placeholder), leadingIcon = R.drawable.ic_user_edit_outline)
        Spacer(modifier = Modifier.height(40.dp))
        LinkerGradientButton(text = stringResource(R.string.auth_lets_go), onClick = onComplete)
        Spacer(modifier = Modifier.height(32.dp))
    }
}
