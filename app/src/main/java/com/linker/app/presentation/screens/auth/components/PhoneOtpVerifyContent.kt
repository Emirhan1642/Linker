package com.linker.app.presentation.screens.auth.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linker.app.R
import com.linker.app.presentation.screens.auth.AuthUiState
import com.linker.app.presentation.theme.*

import androidx.compose.ui.res.stringResource

@Composable
fun PhoneOtpVerifyContent(
    uiState: AuthUiState,
    onOtpChange: (String) -> Unit,
    onVerify: () -> Unit,
    onGoBack: () -> Unit
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
        Spacer(modifier = Modifier.height(40.dp))
        Text(stringResource(R.string.auth_enter_code_title), color = TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(stringResource(R.string.auth_code_sent_to, uiState.phoneNumber), color = TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 20.sp)
        Spacer(modifier = Modifier.height(40.dp))
        OtpInputRow(otp = uiState.otp, onOtpChange = onOtpChange, otpLength = 6, errorMessage = uiState.otpError)
        Spacer(modifier = Modifier.height(32.dp))
        LinkerGradientButton(text = stringResource(R.string.auth_verify_otp), onClick = onVerify)
        Spacer(modifier = Modifier.height(24.dp))
        Text(stringResource(R.string.auth_resend_code), color = LightPurple, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { })
    }
}
