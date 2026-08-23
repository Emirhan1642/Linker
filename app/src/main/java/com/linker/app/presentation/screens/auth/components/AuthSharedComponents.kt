package com.linker.app.presentation.screens.auth.components

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.focus.onFocusChanged
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
import com.linker.app.presentation.animation.bouncyClick
import com.linker.app.presentation.components.NeonGradientButton
import com.linker.app.presentation.theme.*

@Composable
fun LinkerLogo() {
    Text(
        text = "Linker",
        fontSize = 46.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.sp,
        style = TextStyle(
            brush = Brush.linearGradient(
                colors = LinkerBrandGradient
            )
        )
    )
}

@Composable
fun LinkerTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    errorMessage: String? = null,
    leadingIcon: Int? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }

    val borderColor = when {
        errorMessage != null -> ErrorRed
        isFocused -> LinkerPrimary
        else -> GlassCardBorder
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(DarkGrayTransparent)
                .border(1.dp, borderColor, RoundedCornerShape(20.dp))
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                Icon(
                    painter = painterResource(id = leadingIcon),
                    contentDescription = null,
                    tint = if (isFocused) LinkerPrimary else TextHint,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { isFocused = it.isFocused },
                textStyle = TextStyle(color = TextPrimary, fontSize = 16.sp),
                singleLine = true,
                cursorBrush = SolidColor(LinkerPrimary),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
                visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
                decorationBox = { inner ->
                    if (value.isEmpty()) Text(placeholder, color = TextHint, fontSize = 15.sp)
                    inner()
                }
            )
            if (isPassword) {
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { passwordVisible = !passwordVisible },
                    modifier = Modifier.size(28.dp).bouncyClick { passwordVisible = !passwordVisible }
                ) {
                    Icon(
                        painter = painterResource(
                            id = if (passwordVisible) R.drawable.ic_security_safe_outline else R.drawable.ic_smart_lock_ai_outline
                        ),
                        contentDescription = null,
                        tint = if (passwordVisible) GradientBlue else TextHint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = ErrorRed,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }
    }
}

@Composable
fun LinkerGradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    NeonGradientButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        gradientColors = NeonPurpleRedGradient
    )
}

@Composable
fun OrDivider() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(GlassCardBorder)
        )
        Text(
            text = "  ${androidx.compose.ui.res.stringResource(R.string.auth_or_divider)}  ",
            color = TextHint,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(GlassCardBorder)
        )
    }
}

@Composable
fun SocialButton(
    iconRes: Int,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.bouncyClick(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(DarkGrayTransparent)
                .border(1.2.dp, GlassCardBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = label,
                tint = TextPrimary,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun OtpInputRow(
    otp: String,
    onOtpChange: (String) -> Unit,
    otpLength: Int = 6,
    errorMessage: String? = null
) {
    val focusRequester = remember { FocusRequester() }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BasicTextField(
            value = otp,
            onValueChange = { if (it.length <= otpLength && it.all { c -> c.isDigit() }) onOtpChange(it) },
            modifier = Modifier
                .size(1.dp)
                .focusRequester(focusRequester),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.bouncyClick { focusRequester.requestFocus() }
        ) {
            repeat(otpLength) { index ->
                val char = otp.getOrNull(index)?.toString() ?: ""
                val isFocused = otp.length == index
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkGrayTransparent)
                        .border(
                            1.5.dp,
                            if (isFocused) LinkerAngularGradient else SolidColor(GlassCardBorder),
                            RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = char,
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = errorMessage, color = ErrorRed, fontSize = 12.sp)
        }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}
