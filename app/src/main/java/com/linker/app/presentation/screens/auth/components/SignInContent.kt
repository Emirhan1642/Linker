package com.linker.app.presentation.screens.auth.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linker.app.R
import com.linker.app.presentation.screens.auth.AuthUiState
import com.linker.app.presentation.theme.*

import androidx.compose.ui.res.stringResource

@Composable
fun SignInContent(
    uiState: AuthUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSignIn: () -> Unit,
    onGoToSignUp: () -> Unit,
    onGoToForgotPassword: () -> Unit,
    onGoToPhoneAuth: () -> Unit,
    onGoogleSignIn: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(60.dp))
        LinkerLogo()
        Spacer(modifier = Modifier.height(12.dp))
        Text(stringResource(R.string.auth_sign_in_title), color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.auth_sign_in_subtitle), color = TextSecondary, fontSize = 15.sp)
        Spacer(modifier = Modifier.height(40.dp))
        LinkerTextField(value = uiState.email, onValueChange = onEmailChange, placeholder = stringResource(R.string.auth_email_placeholder), keyboardType = KeyboardType.Email, errorMessage = uiState.emailError, leadingIcon = R.drawable.ic_enhance_user_ai_outline)
        Spacer(modifier = Modifier.height(16.dp))
        LinkerTextField(value = uiState.password, onValueChange = onPasswordChange, placeholder = stringResource(R.string.auth_password_placeholder), isPassword = true, errorMessage = uiState.passwordError, leadingIcon = R.drawable.ic_smart_lock_ai_outline)
        Spacer(modifier = Modifier.height(8.dp))
        Text(stringResource(R.string.auth_forgot_password_prompt), color = LightPurple, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.End).clickable { onGoToForgotPassword() }.padding(vertical = 4.dp))
        Spacer(modifier = Modifier.height(24.dp))
        LinkerGradientButton(text = stringResource(R.string.auth_sign_in_button), onClick = onSignIn)
        Spacer(modifier = Modifier.height(24.dp))
        OrDivider()
        Spacer(modifier = Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            SocialButton(iconRes = R.drawable.ic_link_3_outline, label = stringResource(R.string.auth_social_google), onClick = onGoogleSignIn)
            SocialButton(iconRes = R.drawable.ic_ai_send_message_outline, label = stringResource(R.string.auth_social_phone), onClick = onGoToPhoneAuth)
        }
        Spacer(modifier = Modifier.height(32.dp))
        Row {
            Text(stringResource(R.string.auth_have_account), color = TextSecondary, fontSize = 14.sp)
            Text(stringResource(R.string.auth_sign_up_button), color = AccentGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onGoToSignUp() })
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}
