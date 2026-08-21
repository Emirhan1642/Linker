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
import androidx.compose.ui.res.stringResource
import com.linker.app.R
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linker.app.presentation.components.BottomNavItem
import com.linker.app.presentation.components.LinkerBottomNavigationBar
import com.linker.app.presentation.theme.Black
import com.linker.app.presentation.theme.LightGray
import com.linker.app.presentation.theme.DarkGray
import com.linker.app.presentation.theme.LightPurple
import com.linker.app.presentation.theme.LinkerAngularGradient
import com.linker.app.presentation.theme.TextPrimary
import com.linker.app.presentation.theme.TextSecondary
import com.linker.app.presentation.theme.TextHint
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.foundation.clickable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateBottomNav: (BottomNavItem) -> Unit,
    onNavigateToStoryGrid: () -> Unit = {},
    viewModel: HomeViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    var topTab by remember { mutableStateOf(0) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { if (uiState.links.isNotEmpty()) uiState.links.size else 1 })
    
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            // Optionally log or handle permission result
        }
    )

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionCheckResult = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            )
            if (permissionCheckResult != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

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
                .padding(top = paddingValues.calculateTopPadding(), bottom = 0.dp) // paddingValues from Scaffold
        ) {
            // Background / Video Player
            if (uiState.links.isNotEmpty()) {
                VerticalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val link = uiState.links.getOrNull(page)
                    if (link != null) {
                        FeedItemView(
                            link = link,
                            onLikeClick = { viewModel.toggleLike(link.linkId) },
                            onSaveClick = { viewModel.toggleSave(link.linkId) },
                            onRelinkClick = { viewModel.toggleRelink(link.linkId) }
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (uiState.isRefreshing) stringResource(R.string.feed_loading) else stringResource(R.string.feed_empty),
                        color = TextSecondary,
                        fontSize = 16.sp
                    )
                }
            }

            // Top Pill Bar
            TopPillTabs(
                selectedTab = topTab,
                onTabSelected = { tabIndex ->
                    topTab = tabIndex
                    if (tabIndex == 2) {
                        onNavigateToStoryGrid()
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp) // Status bar padding approx
            )
        }
    }
}

@Composable
fun FeedItemView(
    link: com.linker.app.domain.model.Link,
    onLikeClick: () -> Unit = {},
    onSaveClick: () -> Unit = {},
    onRelinkClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
    ) {
        val primaryMediaUrl = link.primaryMedia.url
        if (primaryMediaUrl.isNotBlank() && !primaryMediaUrl.startsWith("placeholder://")) {
            coil3.compose.AsyncImage(
                model = primaryMediaUrl,
                contentDescription = link.description,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF2D1B69).copy(alpha = 0.8f),
                                Color(0xFF11111F).copy(alpha = 0.95f)
                            )
                        )
                    )
            )
        }

        // Overlay gradients
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.4f),
                            Color.Transparent, 
                            Black.copy(alpha = 0.85f)
                        )
                    )
                )
        )

        // Post caption & author info
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, end = 80.dp, bottom = 100.dp)
        ) {
            Text(
                text = "@${link.author.username}",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            if (!link.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = link.description,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    maxLines = 3
                )
            }
        }

        // Right side Action Buttons
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 120.dp), // Clear bottom nav
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionButton(
                icon = if (link.engagement.isLiked) R.drawable.ic_heart_bold else R.drawable.ic_heart_outline,
                count = link.engagement.likesCount.toString(),
                tint = if (link.engagement.isLiked) Color.Red else Color.White,
                onClick = onLikeClick
            )
            ActionButton(
                icon = R.drawable.ic_ai_commentary_outline,
                count = link.engagement.commentsCount.toString()
            )
            ActionButton(
                icon = if (link.engagement.isRelinked) R.drawable.ic_toy_6_bold else R.drawable.ic_toy_6_outline,
                count = link.engagement.relinksCount.toString(),
                tint = if (link.engagement.isRelinked) LightPurple else Color.White,
                onClick = onRelinkClick
            )
            ActionButton(
                icon = if (link.engagement.isSaved) R.drawable.ic_bookmark_2_bold else R.drawable.ic_bookmark_2_outline,
                count = link.engagement.savesCount.toString(),
                tint = if (link.engagement.isSaved) Color(0xFFFFD700) else Color.White,
                onClick = onSaveClick
            )
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
            .background(Color(0xFF1E1E2C).copy(alpha = 0.8f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        PillTab(stringResource(R.string.feed_tab_all), R.drawable.ic_hashtag_down_outline, R.drawable.ic_hashtag_down_bold, isSelected = selectedTab == 0) { onTabSelected(0) }
        PillTab(stringResource(R.string.feed_tab_followed), R.drawable.ic_ai_users_outline, R.drawable.ic_ai_users_bold, isSelected = selectedTab == 1) { onTabSelected(1) }
        PillTab(stringResource(R.string.feed_tab_stories), R.drawable.ic_story_outline, R.drawable.ic_story_bold, isSelected = selectedTab == 2) { onTabSelected(2) }
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
                tint = if (isSelected) LightPurple else TextHint,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = title,
                color = if (isSelected) LightPurple else TextHint,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

@Composable
fun ActionButton(
    @DrawableRes icon: Int,
    count: String?,
    tint: Color = Color.White,
    onClick: () -> Unit = {}
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = null,
            tint = tint,
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
