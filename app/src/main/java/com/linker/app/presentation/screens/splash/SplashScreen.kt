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
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.linker.app.R
import com.linker.app.presentation.theme.*
import kotlinx.coroutines.async
import kotlinx.coroutines.delay

/**
 * Splash Screen
 *
 * Routing logic:
 *   - Not signed in                      → Auth
 *   - Signed in, no Firestore profile    → Auth (ProfileSetup adımına düşer)
 *   - Signed in, profile tam             → session güncelle → Home
 */
@Composable
fun SplashScreen(
    onNavigateToOnboarding: () -> Unit,
    onNavigateToAuth: () -> Unit,
    onNavigateToHome: () -> Unit,
    viewModel: SplashViewModel = hiltViewModel()
) {
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
                text = stringResource(id = R.string.app_name),
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
                text = stringResource(id = R.string.app_slogan),
                color = TextSecondary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.alpha(alpha)
            )
        }
    }

    LaunchedEffect(Unit) {
        val minDelay = async { delay(1500) } // Minimum 1.5 seconds delay for animation
        val destinationAsync = async { viewModel.resolveStartDestination() }
        
        minDelay.await()
        when (destinationAsync.await()) {
            SplashDestination.HOME -> onNavigateToHome()
            SplashDestination.ONBOARDING -> onNavigateToOnboarding()
            SplashDestination.AUTH -> onNavigateToAuth()
        }
    }
}

enum class SplashDestination { HOME, AUTH, ONBOARDING }
