package com.linker.app.presentation.screens.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.linker.app.presentation.theme.*
import kotlinx.coroutines.delay

/**
 * Splash Screen
 *
 * Shows animated Linker logo and checks Firebase auth status.
 */
@Composable
fun SplashScreen(
    onNavigateToAuth: () -> Unit,
    onNavigateToHome: () -> Unit
) {
    // Fade-in animation
    val infiniteTransition = rememberInfiniteTransition(label = "splash")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logoAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Linker",
                fontSize = 52.sp,
                fontWeight = FontWeight.ExtraBold,
                style = TextStyle(
                    brush = Brush.linearGradient(
                        colors = listOf(GradientRed, GradientYellow, GradientGreen, GradientBlue, GradientPurple)
                    )
                ),
                modifier = Modifier.alpha(alpha)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Connect without limits",
                color = TextSecondary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.alpha(alpha)
            )
        }
    }

    LaunchedEffect(Unit) {
        delay(2200) // Splash display time
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null && !currentUser.displayName.isNullOrBlank()) {
            onNavigateToHome()
        } else {
            onNavigateToAuth()
        }
    }
}
