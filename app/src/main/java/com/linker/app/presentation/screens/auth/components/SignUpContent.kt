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
        Text("Create Account", color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Join the Linker community", color = TextSecondary, fontSize = 15.sp)
        Spacer(modifier = Modifier.height(40.dp))
        LinkerTextField(value = uiState.email, onValueChange = onEmailChange, placeholder = "Email address", keyboardType = KeyboardType.Email, errorMessage = uiState.emailError, leadingIcon = R.drawable.ic_enhance_user_ai_outline)
        Spacer(modifier = Modifier.height(16.dp))
        LinkerTextField(value = uiState.password, onValueChange = onPasswordChange, placeholder = "Password (min 8 characters)", isPassword = true, errorMessage = uiState.passwordError, leadingIcon = R.drawable.ic_smart_lock_ai_outline)
        Spacer(modifier = Modifier.height(16.dp))
        LinkerTextField(value = uiState.confirmPassword, onValueChange = onConfirmPasswordChange, placeholder = "Confirm password", isPassword = true, errorMessage = if (uiState.confirmPassword.isNotEmpty() && uiState.password != uiState.confirmPassword) "Passwords do not match" else null, leadingIcon = R.drawable.ic_smart_lock_ai_outline)
        Spacer(modifier = Modifier.height(32.dp))
        LinkerGradientButton(text = "Sign Up", onClick = onSignUp)
        Spacer(modifier = Modifier.height(20.dp))
        Text("By signing up, you agree to our Terms of Service\nand Privacy Policy", color = TextHint, fontSize = 12.sp, textAlign = TextAlign.Center, lineHeight = 18.sp)
        Spacer(modifier = Modifier.height(32.dp))
        Row {
            Text("Already have an account? ", color = TextSecondary, fontSize = 14.sp)
            Text("Sign In", color = AccentGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onGoToSignIn() })
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}
