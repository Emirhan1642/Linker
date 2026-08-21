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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linker.app.R
import com.linker.app.presentation.screens.auth.AuthUiState
import com.linker.app.presentation.theme.*

import androidx.compose.ui.res.stringResource

@Composable
fun SignUpContent(
    uiState: AuthUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onSignUp: () -> Unit,
    onGoToSignIn: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(60.dp))
        LinkerLogo()
        Spacer(modifier = Modifier.height(12.dp))
        Text(stringResource(R.string.auth_sign_up_title), color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.auth_sign_up_subtitle), color = TextSecondary, fontSize = 15.sp)
        Spacer(modifier = Modifier.height(40.dp))
        LinkerTextField(value = uiState.email, onValueChange = onEmailChange, placeholder = stringResource(R.string.auth_email_placeholder), keyboardType = KeyboardType.Email, errorMessage = uiState.emailError, leadingIcon = R.drawable.ic_enhance_user_ai_outline)
        Spacer(modifier = Modifier.height(16.dp))
        LinkerTextField(value = uiState.password, onValueChange = onPasswordChange, placeholder = stringResource(R.string.auth_password_min_length), isPassword = true, errorMessage = uiState.passwordError, leadingIcon = R.drawable.ic_smart_lock_ai_outline)
        Spacer(modifier = Modifier.height(16.dp))
        LinkerTextField(value = uiState.confirmPassword, onValueChange = onConfirmPasswordChange, placeholder = stringResource(R.string.auth_confirm_password_placeholder), isPassword = true, errorMessage = if (uiState.confirmPassword.isNotEmpty() && uiState.password != uiState.confirmPassword) stringResource(R.string.auth_error_password_mismatch) else null, leadingIcon = R.drawable.ic_smart_lock_ai_outline)
        Spacer(modifier = Modifier.height(32.dp))
        LinkerGradientButton(text = stringResource(R.string.auth_sign_up_button), onClick = onSignUp)
        Spacer(modifier = Modifier.height(20.dp))
        Text(stringResource(R.string.auth_terms_agreement), color = TextHint, fontSize = 12.sp, textAlign = TextAlign.Center, lineHeight = 18.sp)
        Spacer(modifier = Modifier.height(32.dp))
        Row {
            Text(stringResource(R.string.auth_have_account), color = TextSecondary, fontSize = 14.sp)
            Text(stringResource(R.string.auth_sign_in_button), color = AccentGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onGoToSignIn() })
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}
