package com.linker.app.presentation.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.linker.app.presentation.components.BottomNavItem
import com.linker.app.presentation.components.LinkerAvatar
import com.linker.app.presentation.components.LinkerBottomNavigationBar
import com.linker.app.presentation.components.LinkerSearchBar
import com.linker.app.presentation.components.StoryState
import com.linker.app.presentation.theme.*

@Composable
fun SearchScreen(
    onNavigateBack: () -> Unit,
    onNavigateBottomNav: (BottomNavItem) -> Unit,
    onNavigateToUserProfile: (String) -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()



    Scaffold(
        containerColor = Black,
        bottomBar = {
            LinkerBottomNavigationBar(
                currentRoute = "Search",
                onNavigate = onNavigateBottomNav,
                modifier = Modifier.background(Color.Transparent)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // ── Top Bar ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(painterResource(R.drawable.ic_arrow_left_01_outline),
                        contentDescription = stringResource(R.string.action_back), tint = TextPrimary)
                }
                LinkerSearchBar(
                    query = uiState.query,
                    onQueryChange = viewModel::onQueryChange,
                    placeholder = stringResource(R.string.search_placeholder),
                    modifier = Modifier.weight(1f),
                    onSearch = { viewModel.onSearchSubmit() }
                )
                IconButton(onClick = {}) {
                    Icon(painterResource(R.drawable.ic_box_search_outline),
                        contentDescription = "Scan", tint = TextPrimary)
                }
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        SearchTabItem(
                            title = stringResource(R.string.search_tab_links),
                            isSelected = uiState.selectedTab == SearchTab.LINKS,
                            modifier = Modifier.padding(end = 24.dp)
                        ) { viewModel.onTabSelected(SearchTab.LINKS) }
                        SearchTabItem(
                            title = stringResource(R.string.search_tab_users),
                            isSelected = uiState.selectedTab == SearchTab.USERS,
                            modifier = Modifier.padding(start = 24.dp)
                        ) { viewModel.onTabSelected(SearchTab.USERS) }
                    }

                    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp)
                        .background(LightGray.copy(alpha = 0.5f)))

                    if (uiState.selectedTab == SearchTab.USERS) {
                        when {
                            uiState.isSearching -> Box(
                                modifier = Modifier.fillMaxWidth().padding(top = 64.dp),
                                contentAlignment = Alignment.Center
                            ) { CircularProgressIndicator(color = AccentGreen, strokeWidth = 2.dp) }

                            uiState.searchResults.isEmpty() -> Box(
                                modifier = Modifier.fillMaxWidth().padding(top = 64.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(stringResource(R.string.search_no_users_found, uiState.query),
                                    color = TextSecondary, fontSize = 14.sp)
                            }

                            else -> LazyColumn(contentPadding = PaddingValues(bottom = 100.dp, top = 8.dp)) {
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
                        Box(modifier = Modifier.fillMaxSize().padding(top = 100.dp),
                            contentAlignment = Alignment.TopCenter) {
                            Text(stringResource(R.string.search_links_coming_soon), color = TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

// ── Recent Searches ───────────────────────────────────────────────────────────

@Composable
private fun RecentSearchesSection(
    recents: List<String>,
    onRecentClick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearAll: () -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 100.dp)) {
        if (recents.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.search_recent_searches), color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.search_clear_all), color = TextSecondary, fontSize = 13.sp,
                        modifier = Modifier.clickable { onClearAll() })
                }
            }
            items(recents, key = { it }) { recent ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onRecentClick(recent) }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(painterResource(R.drawable.ic_clock_outline), null,
                        tint = TextHint, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(recent, color = TextPrimary, fontSize = 15.sp,
                        fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onRemove(recent) }, modifier = Modifier.size(32.dp)) {
                        Icon(painterResource(R.drawable.ic_close_circle_outline), "Remove",
                            tint = TextHint, modifier = Modifier.size(20.dp))
                    }
                }
            }
        } else {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 80.dp),
                    contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.search_no_recent), color = TextHint, fontSize = 14.sp)
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
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LinkerAvatar(imageUrl = user.profileImageUrl, size = 56.dp, storyState = StoryState.NONE)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(user.displayName, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                if (user.isVerified) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(painterResource(R.drawable.ic_security_safe_outline), "Verified",
                        tint = AccentGreen, modifier = Modifier.size(14.dp))
                }
            }
            Text("@${user.username}", color = TextSecondary, fontSize = 13.sp)
            Text("${formatStat(user.metrics.followersCount)} followers · ${formatStat(user.metrics.likesCount)} likes",
                color = TextHint, fontSize = 12.sp)
        }
        val isFollowing = user.relationship.isFollowing
        val isRequested = user.relationship.followRequestSent
        val buttonText = when {
            isFollowing -> stringResource(R.string.follow_status_following)
            isRequested -> stringResource(R.string.follow_status_requested)
            else -> stringResource(R.string.follow_status_follow)
        }
        val buttonColor = if (isFollowing || isRequested) LightGray else AccentGreen
        val textColor = if (isFollowing || isRequested) TextPrimary else Black
        Button(
            onClick = onFollowClick,
            colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.height(32.dp).padding(start = 8.dp)
        ) {
            Text(
                text = buttonText,
                color = textColor,
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ── Tab Item ──────────────────────────────────────────────────────────────────

@Composable
fun SearchTabItem(
    title: String, isSelected: Boolean,
    modifier: Modifier = Modifier, onClick: () -> Unit
) {
    Column(modifier = modifier.clickable { onClick() }, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title,
            color = if (isSelected) TextPrimary else TextSecondary,
            fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp))
        Box(modifier = Modifier.height(2.dp).width(48.dp)
            .background(if (isSelected) TextPrimary else Color.Transparent))
    }
}
