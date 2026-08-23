package com.linker.app.presentation.screens.search

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linker.app.R
import com.linker.app.core.util.FormatUtil.formatStat
import com.linker.app.domain.model.User
import com.linker.app.presentation.animation.bouncyClick
import com.linker.app.presentation.components.*
import com.linker.app.presentation.theme.*

@Composable
fun SearchScreen(
    onNavigateBack: () -> Unit,
    onNavigateBottomNav: (BottomNavItem) -> Unit,
    onNavigateToUserProfile: (String) -> Unit = {},
    onNavigateToLinkDetail: (String) -> Unit = {},
    showBottomBar: Boolean = true,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Black,
        bottomBar = {
            if (showBottomBar) {
                LinkerBottomNavigationBar(
                    currentRoute = "Search",
                    onNavigate = onNavigateBottomNav,
                    modifier = Modifier.background(Color.Transparent)
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBackgroundGradient)
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // ── Top Bar ──────────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassIconButton(
                        iconRes = R.drawable.ic_arrow_left_01_outline,
                        onClick = onNavigateBack,
                        size = 44.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    LinkerSearchBar(
                        query = uiState.query,
                        onQueryChange = viewModel::onQueryChange,
                        placeholder = stringResource(R.string.search_placeholder),
                        modifier = Modifier.weight(1f),
                        onSearch = { viewModel.onSearchSubmit() }
                    )
                }

                // ── Content ──────────────────────────────────────────────────────
                if (uiState.query.isBlank()) {
                    RecentSearchesSection(
                        recents = uiState.recentSearches,
                        onRecentClick = { viewModel.onRecentSearchClick(it) },
                        onRemove = { viewModel.removeRecentSearch(it) },
                        onClearAll = { viewModel.clearAllRecentSearches() }
                    )
                } else {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Capsule Tab Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            GlassBox(
                                shape = RoundedCornerShape(25.dp),
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                Row(modifier = Modifier.padding(4.dp)) {
                                    SearchPillTab(
                                        title = stringResource(R.string.search_tab_links),
                                        isSelected = uiState.selectedTab == SearchTab.LINKS,
                                        onClick = { viewModel.onTabSelected(SearchTab.LINKS) }
                                    )
                                    SearchPillTab(
                                        title = stringResource(R.string.search_tab_users),
                                        isSelected = uiState.selectedTab == SearchTab.USERS,
                                        onClick = { viewModel.onTabSelected(SearchTab.USERS) }
                                    )
                                }
                            }
                        }

                        if (uiState.selectedTab == SearchTab.USERS) {
                            when {
                                uiState.isSearching -> Box(
                                    modifier = Modifier.fillMaxWidth().padding(top = 64.dp),
                                    contentAlignment = Alignment.Center
                                ) { CircularProgressIndicator(color = LinkerPrimary, strokeWidth = 2.dp) }

                                uiState.searchResults.isEmpty() -> {
                                    SearchEmptyState(
                                        query = uiState.query,
                                        onSuggestionClick = { viewModel.onSearchSubmit(it) }
                                    )
                                }

                                else -> LazyColumn(
                                    contentPadding = PaddingValues(bottom = 100.dp, top = 8.dp, start = 12.dp, end = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(uiState.searchResults, key = { it.userId }) { user ->
                                        UserSearchResultItem(
                                            user = user,
                                            onClick = { onNavigateToUserProfile(user.userId) },
                                            onFollowClick = { viewModel.onFollowClick(user) }
                                        )
                                    }
                                }
                            }
                        } else {
                            when {
                                uiState.isSearching -> Box(
                                    modifier = Modifier.fillMaxWidth().padding(top = 64.dp),
                                    contentAlignment = Alignment.Center
                                ) { CircularProgressIndicator(color = LinkerPrimary, strokeWidth = 2.dp) }

                                uiState.linkResults.isEmpty() -> {
                                    SearchEmptyState(
                                        query = uiState.query,
                                        onSuggestionClick = { viewModel.onSearchSubmit(it) }
                                    )
                                }

                                else -> LazyColumn(
                                    contentPadding = PaddingValues(bottom = 100.dp, top = 8.dp, start = 12.dp, end = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(uiState.linkResults, key = { it.linkId }) { link ->
                                        GlassBox(
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .bouncyClick { onNavigateToLinkDetail(link.linkId) }
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(42.dp)
                                                        .clip(CircleShape)
                                                        .background(LinkerPrimary.copy(alpha = 0.15f))
                                                        .border(1.dp, LinkerPrimary.copy(alpha = 0.4f), CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        painter = painterResource(R.drawable.ic_link_3_outline),
                                                        contentDescription = null,
                                                        tint = LinkerPrimary,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(14.dp))
                                                val defaultTitle = stringResource(R.string.feed_default_post_title)
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = link.description?.ifBlank { defaultTitle } ?: defaultTitle,
                                                        color = TextPrimary,
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = "@${link.author.username}",
                                                        color = TextSecondary,
                                                        fontSize = 13.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Search Empty State with Suggestions ───────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchEmptyState(
    query: String,
    onSuggestionClick: (String) -> Unit
) {
    val suggestions = listOf("Yazılım", "Tasarım", "Müzik", "Yapay Zeka", "Seyahat", "Sanat", "Fotoğraf", "Teknoloji")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp, start = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(LinkerPrimary.copy(alpha = 0.15f))
                .border(1.dp, LinkerPrimary.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_search_status_1_outline),
                contentDescription = null,
                tint = LinkerPrimary,
                modifier = Modifier.size(26.dp)
            )
        }
        Text(
            text = if (query.isNotBlank()) "\"$query\" ile eşleşen sonuç bulunamadı" else "Arama sonucu bulunamadı",
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Text(
            text = "Bunu mu demek istedin? Popüler konuları keşfet:",
            color = TextSecondary,
            fontSize = 13.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            suggestions.forEach { suggestion ->
                GlassBox(
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.bouncyClick { onSuggestionClick(suggestion) }
                ) {
                    Text(
                        text = "#$suggestion",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

// ── Recent Searches ───────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecentSearchesSection(
    recents: List<String>,
    onRecentClick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearAll: () -> Unit
) {
    val popularTopics = listOf("Yazılım", "Tasarım", "Müzik", "Yapay Zeka", "Seyahat", "Sanat", "Fotoğraf", "Teknoloji", "Sinema", "Oyun")
    LazyColumn(contentPadding = PaddingValues(bottom = 100.dp, start = 12.dp, end = 12.dp)) {
        if (recents.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.search_recent_searches),
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.search_clear_all),
                        color = LinkerPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { onClearAll() }
                    )
                }
            }
            items(recents, key = { it }) { recent ->
                GlassBox(
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .bouncyClick { onRecentClick(recent) }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_clock_outline),
                            null,
                            tint = LinkerPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            recent,
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { onRemove(recent) },
                            modifier = Modifier.size(28.dp).bouncyClick { onRemove(recent) }
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_close_circle_outline),
                                "Remove",
                                tint = TextHint,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Popüler Konular",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    popularTopics.forEach { topic ->
                        GlassBox(
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.bouncyClick { onRecentClick(topic) }
                        ) {
                            Text(
                                text = "#$topic",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }
        } else {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp, start = 4.dp, end = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(LinkerPrimary.copy(alpha = 0.15f))
                            .border(1.dp, LinkerPrimary.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_search_status_1_outline),
                            null,
                            tint = LinkerPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Keşfetmeye Başla",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Kullanıcıları veya paylaşılan linkleri arayabilirsin.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Popüler Konular",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        popularTopics.forEach { topic ->
                            GlassBox(
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.bouncyClick { onRecentClick(topic) }
                            ) {
                                Text(
                                    text = "#$topic",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── User Search Result Item ───────────────────────────────────────────────────

@Composable
fun UserSearchResultItem(
    user: User,
    onClick: () -> Unit,
    onFollowClick: () -> Unit
) {
    GlassBox(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClick(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            LinkerAvatar(imageUrl = user.profileImageUrl, size = 48.dp, storyState = StoryState.NONE)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = user.displayName,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (user.isVerified) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            painterResource(R.drawable.ic_security_safe_outline),
                            "Verified",
                            tint = AccentGreen,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "@${user.username}",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            val isFollowing = user.relationship.isFollowing
            val isRequested = user.relationship.followRequestSent
            val buttonText = when {
                isFollowing -> stringResource(R.string.follow_status_following)
                isRequested -> stringResource(R.string.follow_status_requested)
                else -> stringResource(R.string.follow_status_follow)
            }
            
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .then(
                        if (isFollowing || isRequested) Modifier.background(DarkGrayTransparent)
                        else Modifier.background(Brush.horizontalGradient(LinkerBrandGradient))
                    )
                    .border(
                        1.dp,
                        if (isFollowing || isRequested) GlassCardBorder else Color.Transparent,
                        RoundedCornerShape(20.dp)
                    )
                    .bouncyClick(onClick = onFollowClick)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = buttonText,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ── Tab Item ──────────────────────────────────────────────────────────────────

@Composable
fun SearchPillTab(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .then(
                if (isSelected) Modifier.background(Brush.horizontalGradient(LinkerBrandGradient))
                else Modifier.background(Color.Transparent)
            )
            .bouncyClick(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = if (isSelected) Color.White else TextSecondary,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
