package com.linker.app.presentation.screens.onboarding

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linker.app.R
import com.linker.app.presentation.theme.*

data class OnboardingItem(
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int,
    @StringRes val badgeRes: Int,
    @DrawableRes val iconRes: Int,
    val gradientColors: List<Color>
)

val onboardingItems = listOf(
    OnboardingItem(
        titleRes = R.string.onboarding_card1_title,
        descRes = R.string.onboarding_card1_desc,
        badgeRes = R.string.onboarding_card1_badge,
        iconRes = R.drawable.ic_link_3_outline,
        gradientColors = listOf(GradientBlue, GradientGreen)
    ),
    OnboardingItem(
        titleRes = R.string.onboarding_card2_title,
        descRes = R.string.onboarding_card2_desc,
        badgeRes = R.string.onboarding_card2_badge,
        iconRes = R.drawable.ic_play_add_outline,
        gradientColors = listOf(GradientGreen, GradientYellow)
    ),
    OnboardingItem(
        titleRes = R.string.onboarding_card3_title,
        descRes = R.string.onboarding_card3_desc,
        badgeRes = R.string.onboarding_card3_badge,
        iconRes = R.drawable.ic_profile_outline,
        gradientColors = listOf(GradientPurple, GradientRed)
    ),
    OnboardingItem(
        titleRes = R.string.onboarding_card4_title,
        descRes = R.string.onboarding_card4_desc,
        badgeRes = R.string.onboarding_card4_badge,
        iconRes = R.drawable.ic_security_safe_outline,
        gradientColors = listOf(GradientRed, GradientPurple)
    )
)

@Composable
fun OnboardingCardView(
    item: OnboardingItem,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "onboardingCardGlow")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "borderRotation"
    )
    val floatScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatingScale"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Glowing Floating Illustration Box
        Box(
            modifier = Modifier
                .size(240.dp)
                .scale(floatScale),
            contentAlignment = Alignment.Center
        ) {
            // Neon Blurred Background Ambient Glow
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                item.gradientColors.first().copy(alpha = 0.35f),
                                item.gradientColors.last().copy(alpha = 0.15f),
                                Color.Transparent
                            )
                        )
                    )
                    .blur(28.dp)
            )

            // Animated Rotating Gradient Ring
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .rotate(rotation)
                    .border(
                        width = 3.dp,
                        brush = Brush.sweepGradient(
                            colors = item.gradientColors + item.gradientColors.first()
                        ),
                        shape = CircleShape
                    )
            )

            // Inner Glass Icon Container
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape)
                    .background(DarkGrayTransparent)
                    .border(1.dp, LightGray.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = item.iconRes),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(56.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Badge Pill
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(item.gradientColors.first().copy(alpha = 0.2f))
                .border(
                    width = 1.dp,
                    color = item.gradientColors.first().copy(alpha = 0.6f),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text = stringResource(id = item.badgeRes),
                color = item.gradientColors.first(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Title
        Text(
            text = stringResource(id = item.titleRes),
            color = TextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            lineHeight = 32.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Description
        Text(
            text = stringResource(id = item.descRes),
            color = TextSecondary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}
