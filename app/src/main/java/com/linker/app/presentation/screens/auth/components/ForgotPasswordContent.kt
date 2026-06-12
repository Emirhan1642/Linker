package com.linker.app.presentation.screens.auth.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linker.app.R
import com.linker.app.presentation.screens.auth.AuthUiState
import com.linker.app.presentation.theme.*

@Composable
fun ForgotPasswordContent(
    uiState: AuthUiState,
    onEmailChange: (String) -> Unit,
    onSendReset: () -> Unit,
    onGoBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onGoBack) {
                Icon(painter = painterResource(id = R.drawable.ic_arrow_left_01_outline), contentDescription = "Back", tint = TextPrimary, modifier = Modifier.size(30.dp))
            }
        }
        Spacer(modifier = Modifier.height(40.dp))
        Box(modifier = Modifier.size(100.dp).clip(CircleShape).background(LightGray), contentAlignment = Alignment.Center) {
            Icon(painter = painterResource(id = R.drawable.ic_smart_lock_ai_outline), contentDescription = null, tint = AccentGreen, modifier = Modifier.size(48.dp))
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("Reset Password", color = TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Enter the email associated with your\naccount and we'll send a reset link", color = TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 20.sp)
        Spacer(modifier = Modifier.height(32.dp))
        LinkerTextField(value = uiState.email, onValueChange = onEmailChange, placeholder = "Email address", keyboardType = KeyboardType.Email, errorMessage = uiState.emailError, leadingIcon = R.drawable.ic_enhance_user_ai_outline)
        Spacer(modifier = Modifier.height(32.dp))
        LinkerGradientButton(text = "Send Reset Link", onClick = onSendReset)
        Spacer(modifier = Modifier.height(24.dp))
        Text("Back to Sign In", color = LightPurple, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { onGoBack() })
    }
}
