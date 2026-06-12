package com.linker.app.presentation.screens.auth.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linker.app.R
import com.linker.app.presentation.theme.*

@Composable
fun LinkerLogo() {
    Text(text = "Linker", fontSize = 42.sp, fontWeight = FontWeight.ExtraBold,
        style = TextStyle(brush = Brush.linearGradient(colors = listOf(GradientRed, GradientYellow, GradientGreen, GradientBlue, GradientPurple))))
}

@Composable
fun LinkerTextField(
    value: String, onValueChange: (String) -> Unit, placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text, isPassword: Boolean = false,
    errorMessage: String? = null, leadingIcon: Int? = null
) {
    var passwordVisible by remember { mutableStateOf(false) }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp)).background(DarkGray)
                .border(1.5.dp, if (errorMessage != null) ErrorRed else LightGray, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                Icon(painter = painterResource(id = leadingIcon), contentDescription = null, tint = TextHint, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(12.dp))
            }
            BasicTextField(
                value = value, onValueChange = onValueChange, modifier = Modifier.weight(1f),
                textStyle = TextStyle(color = TextPrimary, fontSize = 16.sp), singleLine = true,
                cursorBrush = SolidColor(AccentGreen),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
                visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
                decorationBox = { inner -> if (value.isEmpty()) Text(placeholder, color = TextHint, fontSize = 16.sp); inner() }
            )
            if (isPassword) {
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = { passwordVisible = !passwordVisible }, modifier = Modifier.size(24.dp)) {
                    Icon(painter = painterResource(id = if (passwordVisible) R.drawable.ic_security_safe_outline else R.drawable.ic_smart_lock_ai_outline),
                        contentDescription = null, tint = TextHint, modifier = Modifier.size(20.dp))
                }
            }
        }
        if (errorMessage != null) Text(errorMessage, color = ErrorRed, fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp, top = 4.dp))
    }
}

@Composable
fun LinkerGradientButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp))
            .background(Brush.horizontalGradient(colors = listOf(GradientPurple, GradientBlue, GradientGreen))).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun OrDivider() {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.weight(1f).height(1.dp).background(LightGray))
        Text("  or  ", color = TextHint, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Box(modifier = Modifier.weight(1f).height(1.dp).background(LightGray))
    }
}

@Composable
fun SocialButton(iconRes: Int, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(DarkGray).border(1.5.dp, LightGray, CircleShape), contentAlignment = Alignment.Center) {
            Icon(painter = painterResource(id = iconRes), contentDescription = label, tint = TextPrimary, modifier = Modifier.size(26.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(label, color = TextSecondary, fontSize = 12.sp)
    }
}

@Composable
fun OtpInputRow(otp: String, onOtpChange: (String) -> Unit, otpLength: Int = 6, errorMessage: String? = null) {
    val focusRequester = remember { FocusRequester() }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BasicTextField(
            value = otp,
            onValueChange = { if (it.length <= otpLength && it.all { c -> c.isDigit() }) onOtpChange(it) },
            modifier = Modifier.size(1.dp).focusRequester(focusRequester),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.clickable { focusRequester.requestFocus() }) {
            repeat(otpLength) { index ->
                val char = otp.getOrNull(index)?.toString() ?: ""
                val isFocused = otp.length == index
                Box(
                    modifier = Modifier.size(50.dp).clip(RoundedCornerShape(12.dp)).background(DarkGray)
                        .border(2.dp, if (isFocused) LinkerAngularGradient else SolidColor(LightGray), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = char, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (errorMessage != null) { Spacer(modifier = Modifier.height(8.dp)); Text(errorMessage, color = ErrorRed, fontSize = 12.sp) }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}
