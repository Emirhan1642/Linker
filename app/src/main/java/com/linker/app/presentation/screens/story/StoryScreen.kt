package com.linker.app.presentation.screens.story

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linker.app.R
import com.linker.app.presentation.components.LinkerAvatar
import com.linker.app.presentation.theme.Black
import com.linker.app.presentation.theme.LinkerAngularGradient
import com.linker.app.presentation.theme.TextHint
import com.linker.app.presentation.theme.TextPrimary
import com.linker.app.presentation.theme.TextSecondary

@Composable
fun StoryScreen(
    onNavigateBack: () -> Unit
) {
    // A mock gradient background to simulate an image backdrop for the story
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFCE5D56), // Sunset top
            Color(0xFF755171), // Sunset mid
            Color(0xFF27385E)  // Sunset bottom/dark
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        // Gradient overlay for better text readability at top and bottom
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                        startY = 0f,
                        endY = 2000f // Fallback length to stretch the gradient
                    )
                )
        )

        // Top Overlay Group
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 12.dp, end = 12.dp)
        ) {
            // Story Progress Bars
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Segment 1 (Filled)
                LinearProgressIndicator(
                    progress = { 1.0f },
                    modifier = Modifier.weight(1f),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f),
                )
                // Segment 2 (Partially Filled)
                LinearProgressIndicator(
                    progress = { 0.5f },
                    modifier = Modifier.weight(1f),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f),
                )
                // Segment 3 (Empty)
                LinearProgressIndicator(
                    progress = { 0.0f },
                    modifier = Modifier.weight(1f),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // User Info & Back Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_arrow_left_01_outline),
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
                
                LinkerAvatar(
                    imageUrl = null,
                    size = 40.dp,
                    hasStory = false // We just want the white background per design
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("alex_145", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("15 min ago", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Bottom Overlay (Input)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pill input field with gradient border
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .border(2.dp, LinkerAngularGradient, RoundedCornerShape(28.dp))
                    .background(Color(0x66000000)) // Translucent black / Glassmorphism
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Send a Message",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )

                IconButton(onClick = { /* Like Story */ }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_heart_outline),
                        contentDescription = "Like",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}
