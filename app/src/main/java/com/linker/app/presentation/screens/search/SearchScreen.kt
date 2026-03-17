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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.firebase.auth.FirebaseAuth
import com.linker.app.R
import com.linker.app.domain.model.User
import com.linker.app.presentation.components.BottomNavItem
import com.linker.app.presentation.components.LinkerAvatar
import com.linker.app.presentation.components.LinkerBottomNavigationBar
import com.linker.app.presentation.components.LinkerSearchBar
import com.linker.app.presentation.components.StoryState
import com.linker.app.presentation.screens.profile.formatStat
import com.linker.app.presentation.theme.*

@Composable
fun SearchScreen(
    onNavigateBack: () -> Unit,
    onNavigateBottomNav: (BottomNavItem) -> Unit,
    onNavigateToUserProfile: (String) -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Ekran her göründüğünde aktif UID'yi oku.
    // Eğer değiştiyse (hesap geçişi) state'i sıfırla ve yeni hesabın
    // arama geçmişini yükle.
    val currentUid = remember { mutableStateOf(FirebaseAuth.getInstance().currentUser?.uid) }
    LaunchedEffect(Unit) {
        val freshUid = FirebaseAuth.getInstance().currentUser?.uid
        if (freshUid != currentUid.value) {
            currentUid.value = freshUid
            viewModel.onAccountChanged()
        } else {
            viewModel.startListeningRecentSearches()
        }
    }

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
                        contentDescription = "Back", tint = TextPrimary)
                }
                LinkerSearchBar(
                    query = uiState.query,
                    onQueryChange = viewModel::onQueryChange,
                    placeholder = "Search users, links…",
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
                            title = "Links",
                            isSelected = uiState.selectedTab == SearchTab.LINKS,
                            modifier = Modifier.padding(end = 24.dp)
                        ) { viewModel.onTabSelected(SearchTab.LINKS) }
                        SearchTabItem(
                            title = "Users",
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
                                Text("No users found for \"${uiState.query}\"",
                                    color = TextSecondary, fontSize = 14.sp)
                            }

                            else -> LazyColumn(contentPadding = PaddingValues(bottom = 100.dp, top = 8.dp)) {
                                items(uiState.searchResults, key = { it.userId }) { user ->
                                    UserSearchResultItem(
                                        user = user,
                                        onClick = { onNavigateToUserProfile(user.userId) }
                                    )
                                }
                            }
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize().padding(top = 100.dp),
                            contentAlignment = Alignment.TopCenter) {
                            Text("Links search coming soon…", color = TextSecondary)
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
                    Text("Recent Searches", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text("Clear all", color = TextSecondary, fontSize = 13.sp,
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
                    Text("No recent searches", color = TextHint, fontSize = 14.sp)
                }
            }
        }
    }
}

// ── User Search Result Item ───────────────────────────────────────────────────

@Composable
fun UserSearchResultItem(user: User, onClick: () -> Unit) {
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
            Text("${formatStat(user.followersCount)} followers · ${formatStat(user.likesCount)} likes",
                color = TextHint, fontSize = 12.sp)
        }
        val isFollowing = user.isFollowing
        Button(
            onClick = {},
            colors = ButtonDefaults.buttonColors(containerColor = if (isFollowing) LightGray else AccentGreen),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.height(32.dp).padding(start = 8.dp)
        ) {
            Text(
                text = if (isFollowing) "Following" else "Follow",
                color = if (isFollowing) TextPrimary else Black,
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
