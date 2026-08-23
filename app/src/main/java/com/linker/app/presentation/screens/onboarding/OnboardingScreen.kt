package com.linker.app.presentation.screens.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.linker.app.R
import com.linker.app.presentation.animation.bouncyClick
import com.linker.app.presentation.theme.*
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    onFinishOnboarding: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val pagerState = rememberPagerState(pageCount = { onboardingItems.size })
    val scope = rememberCoroutineScope()

    val currentItem = onboardingItems.getOrElse(pagerState.currentPage) { onboardingItems.first() }
    val currentAccentColor by animateColorAsState(
        targetValue = currentItem.gradientColors.first(),
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "accentColor"
    )

    val onComplete: () -> Unit = {
        viewModel.completeOnboarding()
        onFinishOnboarding()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
    ) {
        // Deep Ambient Background Gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            currentAccentColor.copy(alpha = 0.18f),
                            Color(0xFF0F0F1A),
                            Black
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar: Skip Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (pagerState.currentPage < onboardingItems.size - 1) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(DarkGrayTransparent)
                            .bouncyClick { onComplete() }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.onboarding_skip),
                            color = TextSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(36.dp))
                }
            }

            // Cards Carousel
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                val item = onboardingItems[page]
                OnboardingCardView(item = item)
            }

            // Bottom Section: Worm Indicator & Dynamic Button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Expanding Pill Page Indicator
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(onboardingItems.size) { index ->
                        val isSelected = pagerState.currentPage == index
                        val width by animateDpAsState(
                            targetValue = if (isSelected) 32.dp else 8.dp,
                            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
                            label = "indicatorWidth"
                        )
                        val color by animateColorAsState(
                            targetValue = if (isSelected) currentAccentColor else LightGray.copy(alpha = 0.4f),
                            animationSpec = tween(durationMillis = 350),
                            label = "indicatorColor"
                        )

                        Box(
                            modifier = Modifier
                                .height(8.dp)
                                .width(width)
                                .clip(CircleShape)
                                .background(color)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Next / Get Started Action Button
                val isLastPage = pagerState.currentPage == onboardingItems.size - 1
                val buttonText = if (isLastPage) {
                    stringResource(id = R.string.onboarding_get_started)
                } else {
                    stringResource(id = R.string.onboarding_next)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = currentItem.gradientColors
                            )
                        )
                        .bouncyClick {
                            if (isLastPage) {
                                onComplete()
                            } else {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = buttonText,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
