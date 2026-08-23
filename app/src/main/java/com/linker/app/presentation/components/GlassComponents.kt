package com.linker.app.presentation.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linker.app.presentation.animation.bouncyClick
import com.linker.app.presentation.theme.*

/**
 * Modern Glassmorphic Container with translucent background and subtle glow border.
 */
@Composable
fun GlassBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    backgroundColor: Color = GlassCardBackground,
    borderColor: Color = GlassCardBorder,
    borderWidth: Dp = 1.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .border(borderWidth, borderColor, shape),
        content = content
    )
}

/**
 * Onboarding-style Pill Badge with translucent backdrop and neon border.
 */
@Composable
fun PillBadge(
    text: String,
    accentColor: Color = AccentGreen,
    modifier: Modifier = Modifier,
    fontSize: Int = 12
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(accentColor.copy(alpha = 0.15f))
            .border(
                width = 1.dp,
                color = accentColor.copy(alpha = 0.5f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 12.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = accentColor,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

/**
 * Onboarding-style Neon Horizontal Gradient Action Button with spring click feedback.
 */
@Composable
fun NeonGradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    gradientColors: List<Color> = NeonBlueGreenGradient,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(27.dp))
            .background(
                if (enabled) Brush.horizontalGradient(gradientColors)
                else Brush.horizontalGradient(listOf(DarkGray, LightGray))
            )
            .bouncyClick(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) Color.White else TextHint,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Onboarding-style Glass Rounded Icon Button with spring click feedback.
 */
@Composable
fun GlassIconButton(
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    iconSize: Dp = 24.dp,
    tint: Color = TextPrimary
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(DarkGrayTransparent)
            .border(1.dp, GlassCardBorder, CircleShape)
            .bouncyClick(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * Ambient Glowing Light Orb for page top/background highlights.
 */
@Composable
fun AmbientGlow(
    glowColor: Color = GradientBlue,
    size: Dp = 220.dp,
    alpha: Float = 0.25f,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        glowColor.copy(alpha = alpha),
                        glowColor.copy(alpha = alpha * 0.4f),
                        Color.Transparent
                    )
                )
            )
            .blur(36.dp)
    )
}
