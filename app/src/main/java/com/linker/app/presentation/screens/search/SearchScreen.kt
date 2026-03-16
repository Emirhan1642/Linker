package com.linker.app.presentation.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.res.painterResource
import com.linker.app.R
import androidx.compose.material3.Divider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linker.app.presentation.components.BottomNavItem
import com.linker.app.presentation.components.LinkerBottomNavigationBar
import com.linker.app.presentation.components.LinkerSearchBar
import com.linker.app.presentation.theme.Black
import com.linker.app.presentation.theme.LightGray
import com.linker.app.presentation.theme.TextHint
import com.linker.app.presentation.theme.TextPrimary
import com.linker.app.presentation.theme.TextSecondary
import com.linker.app.presentation.theme.GradientPurple
import com.linker.app.presentation.theme.LightPurple
import com.linker.app.presentation.theme.AccentGreen
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.draw.clip
import com.linker.app.presentation.components.LinkerAvatar

@Composable
fun SearchScreen(
    onNavigateBack: () -> Unit,
    onNavigateBottomNav: (BottomNavItem) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val recents = listOf(
        "Why does Linker have many active users?",
        "Who is Elon Musk?",
        "Popular links",
        "67",
        "Turkish lessons",
        "Istanbul view",
        "Who is the president of US?",
        "Bad Bunny Super Bowl",
        "Gemini 3.1 Pro"
    )

    val suggestions = listOf(
        "Bad Bunny",
        "Karagül",
        "Iran vs Isreal",
        "MrBeast",
        "GPT 5.4",
        "Spain Turkey",
        "Claude",
        "Donald Trump",
        "What does 67 mean?"
    )

    var selectedSearchTab by remember { mutableStateOf(1) } // 0: Links, 1: Users

    val mockUsers = listOf(
        MockUser("badbunny", "Bad Bunny", "41.5M followers • 438.6M likes", "Following"),
        MockUser("badbunny", "Bad Bunny", "41.5M followers • 438.6M likes", "Follow"),
        MockUser("badbunny", "Bad Bunny", "41.5M followers • 438.6M likes", "Follow"),
        MockUser("badbunny", "Bad Bunny", "41.5M followers • 438.6M likes", "Pending"),
        MockUser("badbunny", "Bad Bunny", "41.5M followers • 438.6M likes", "Follow"),
        MockUser("badbunny", "Bad Bunny", "41.5M followers • 438.6M likes", "Follow"),
        MockUser("badbunny", "Bad Bunny", "41.5M followers • 438.6M likes", "Follow"),
        MockUser("badbunny", "Bad Bunny", "41.5M followers • 438.6M likes", "Follow"),
        MockUser("badbunny", "Bad Bunny", "41.5M followers • 438.6M likes", "Follow")
    )

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
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        painterResource(id = R.drawable.ic_arrow_left_01_outline),
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
                LinkerSearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = "Why is Linker popular?",
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { /* Scan QR */ }) {
                    Icon(
                        painterResource(id = R.drawable.ic_box_search_outline),
                        contentDescription = "Scan",
                        tint = TextPrimary
                    )
                }
            }

            if (query.isEmpty()) {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    // Recent Searches
                    items(recents) { recent ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { query = recent }
                                .padding(horizontal = 16.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painterResource(id = R.drawable.ic_clock_outline),
                                contentDescription = null,
                                tint = TextHint,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = recent,
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                painterResource(id = R.drawable.ic_close_circle_outline),
                                contentDescription = "Remove",
                                tint = TextHint,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    item {
                        Text(
                            text = "Show more",
                            color = TextSecondary,
                            fontSize = 15.sp,
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                                .clickable { /* expand list */ }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(LightGray))
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Suggestions Header
                    item {
                        Text(
                            text = "You might like these",
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    // Suggestions List
                    items(suggestions) { suggestion ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { query = suggestion }
                                .padding(horizontal = 16.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painterResource(id = R.drawable.ic_eos__eos__bold),
                                contentDescription = null,
                                tint = LightPurple, // the icon in design is a purple node/crystal, fire is closest base MDC icon
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = suggestion,
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            } else {
                // Showing Search Results
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Tabs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        TabItem(
                            title = "Links",
                            isSelected = selectedSearchTab == 0,
                            modifier = Modifier.padding(end = 24.dp)
                        ) { selectedSearchTab = 0 }
                        TabItem(
                            title = "Users",
                            isSelected = selectedSearchTab == 1,
                            modifier = Modifier.padding(start = 24.dp)
                        ) { selectedSearchTab = 1 }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth().height(1.dp)
                            .background(LightGray.copy(alpha = 0.5f))
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (selectedSearchTab == 1) {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 100.dp, top = 8.dp)
                        ) {
                            items(mockUsers.size) { index ->
                                val user = mockUsers[index]
                                UserSearchResultItem(user = user, index = index)
                            }
                        }
                    } else {
                        // Links Search Results Placeholder
                        Box(
                            modifier = Modifier.fillMaxSize().padding(top = 100.dp),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Text("Links search results...", color = TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

data class MockUser(
    val username: String,
    val name: String,
    val stats: String,
    val status: String // "Following", "Follow", "Pending"
)

@Composable
fun TabItem(
    title: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier.clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            color = if (isSelected) Color.White else TextSecondary,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Box(
            modifier = Modifier
                .height(2.dp)
                .width(48.dp)
                .background(if (isSelected) Color.White else Color.Transparent)
        )
    }
}

@Composable
fun UserSearchResultItem(user: MockUser, index: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* open profile */ }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LinkerAvatar(
            imageUrl = null,
            size = 56.dp,
            hasStory = index < 4 // just some mock rule to show gradient on first few
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = user.username, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(text = user.name, color = TextSecondary, fontSize = 12.sp)
            Text(text = user.stats, color = TextSecondary, fontSize = 12.sp)
        }
        
        val (bgColor, textColor) = when(user.status) {
            "Following" -> Pair(LightGray, TextPrimary)
            "Follow" -> Pair(AccentGreen, Black)
            "Pending" -> Pair(GradientPurple, Color.White)
            else -> Pair(LightGray, TextPrimary)
        }
        
        Button(
            onClick = { /* toggle follow */ },
            colors = ButtonDefaults.buttonColors(containerColor = bgColor),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.height(32.dp).padding(start = 8.dp)
        ) {
            Text(text = user.status, color = textColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
