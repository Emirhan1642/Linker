package com.linker.app.presentation.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.res.painterResource
import com.linker.app.R
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linker.app.presentation.components.BottomNavItem
import com.linker.app.presentation.components.LinkerBottomNavigationBar
import com.linker.app.presentation.theme.Black
import com.linker.app.presentation.theme.LightGray
import com.linker.app.presentation.theme.LinkerAngularGradient
import com.linker.app.presentation.theme.TextPrimary
import com.linker.app.presentation.theme.TextSecondary
import com.linker.app.presentation.theme.TextHint

@Composable
fun HomeScreen(
    onNavigateBottomNav: (BottomNavItem) -> Unit
) {
    var topTab by remember { mutableStateOf(0) }
    val pagerState = rememberPagerState(pageCount = { 10 }) // 10 fake items

    Scaffold(
        containerColor = Black, // Full black for edge to edge videos
        bottomBar = {
            LinkerBottomNavigationBar(
                currentRoute = "Explore",
                onNavigate = onNavigateBottomNav,
                modifier = Modifier.background(Color.Transparent)
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Background / Video Player
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                FeedItemView(page = page)
            }

            // Top Pill Bar
            TopPillTabs(
                selectedTab = topTab,
                onTabSelected = { topTab = it },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp) // Status bar padding approx
            )
            
            // We ignore bottom padding strictly because bottom nav is translucent and floating!
        }
    }
}

@Composable
fun FeedItemView(page: Int) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Black) // Placeholder for video/image
    ) {
        // Mock image gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFE9B584).copy(alpha = 0.5f), 
                            Color.Transparent, 
                            Black.copy(alpha = 0.8f)
                        )
                    )
                )
        )

        // Right side Action Buttons
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 120.dp), // Clear bottom nav
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActionButton(icon = R.drawable.ic_heart_outline, count = "1253") // Like
            ActionButton(icon = R.drawable.ic_ai_commentary_outline, count = "15") // Comment
            ActionButton(icon = R.drawable.ic_toy_6_outline, count = "278") // Relink
            ActionButton(icon = R.drawable.ic_bookmark_2_outline, count = "152") // Save
            ActionButton(icon = R.drawable.ic_ai_send_message_outline, count = "41") // Send
            ActionButton(icon = R.drawable.ic_more_square_outline, count = null) // More
        }
    }
}

@Composable
fun TopPillTabs(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(32.dp))
            .background(Color(0xFF242424))
            .border(2.dp, LinkerAngularGradient, RoundedCornerShape(32.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        PillTab("Feed", R.drawable.ic_hashtag_down_outline, R.drawable.ic_hashtag_down_bold, isSelected = selectedTab == 0) { onTabSelected(0) }
        PillTab("Followed", R.drawable.ic_ai_users_outline, R.drawable.ic_ai_users_bold, isSelected = selectedTab == 1) { onTabSelected(1) }
        PillTab("Stories", R.drawable.ic_story_outline, R.drawable.ic_story_bold, isSelected = selectedTab == 2) { onTabSelected(2) }
    }
}

@Composable
fun PillTab(
    title: String,
    @DrawableRes icon: Int,
    @DrawableRes selectedIcon: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, Modifier.padding(horizontal = 10.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(id = if (isSelected) selectedIcon else icon),
                contentDescription = title,
                tint = if (isSelected) Color(0xFF7C79CA) else TextHint,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = title,
                color = if (isSelected) Color(0xFF7C79CA) else TextHint,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

@Composable
fun ActionButton(@DrawableRes icon: Int, count: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(38.dp)
        )
        if (count != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = count,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
