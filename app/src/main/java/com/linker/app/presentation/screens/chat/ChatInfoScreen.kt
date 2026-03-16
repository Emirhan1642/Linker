package com.linker.app.presentation.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linker.app.R
import com.linker.app.presentation.components.LinkerAvatar
import com.linker.app.presentation.theme.Black
import com.linker.app.presentation.theme.TextPrimary
import com.linker.app.presentation.theme.TextSecondary
import com.linker.app.presentation.theme.LightGray

@Composable
fun ChatInfoScreen(
    onNavigateBack: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: Gallery, 1: Reel, 2: Link, 3: User

    Scaffold(
        containerColor = Black
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(painterResource(id = R.drawable.ic_arrow_left_01_outline), contentDescription = "Back", tint = TextPrimary, modifier = Modifier.size(30.dp))
                    }
                }
            }

            // Avatar & Name
            item {
                LinkerAvatar(
                    imageUrl = null,
                    size = 150.dp,
                    hasStory = true
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Bad Bunny", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("@badbunny", color = TextSecondary, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Options List
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    ChatInfoOption(icon = R.drawable.ic_enhance_user_ai_outline, title = "Profile", subtitle = "@badbunny")
                    Spacer(modifier = Modifier.height(20.dp))
                    ChatInfoOption(icon = R.drawable.ic_bell_2_outline, title = "Silent Mode", subtitle = "Off")
                    Spacer(modifier = Modifier.height(20.dp))
                    ChatInfoOption(icon = R.drawable.ic_search_status_1_outline, title = "Search", subtitle = null)
                    Spacer(modifier = Modifier.height(20.dp))
                    ChatInfoOption(icon = R.drawable.ic_paint_brush_2_outline, title = "Theme", subtitle = "Default")
                    Spacer(modifier = Modifier.height(20.dp))
                    ChatInfoOption(icon = R.drawable.ic_ai_sand_timer_outline, title = "Disappearing messages", subtitle = "Off")
                    Spacer(modifier = Modifier.height(20.dp))
                    ChatInfoOption(icon = R.drawable.ic_security_safe_outline, title = "Security", subtitle = null, subtitleStyle = false)
                    Spacer(modifier = Modifier.height(20.dp))
                    ChatInfoOption(icon = R.drawable.ic_user_edit_outline, title = "Nicknames", subtitle = null)
                    Spacer(modifier = Modifier.height(20.dp))
                    ChatInfoOption(icon = R.drawable.ic_ai_users_outline, title = "Create a group", subtitle = null)
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // Tabs
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TabRowIconItem(icon = R.drawable.ic_gallery_outline, isSelected = selectedTab == 0) { selectedTab = 0 }
                    TabRowIconItem(icon = R.drawable.ic_play_add_outline, isSelected = selectedTab == 1) { selectedTab = 1 }
                    TabRowIconItem(icon = R.drawable.ic_toy_6_outline, isSelected = selectedTab == 2) { selectedTab = 2 } // Fallback to toy_6 (relink) or similar link icon if available
                    TabRowIconItem(icon = R.drawable.ic_profile_outline, isSelected = selectedTab == 3) { selectedTab = 3 }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(LightGray.copy(alpha=0.5f)))
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Grid content for Gallery
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Demo images blocks matching the staggered profile feed
                    Box(modifier = Modifier.weight(1f).aspectRatio(0.8f).clip(RoundedCornerShape(16.dp)).background(LightGray))
                    Box(modifier = Modifier.weight(1f).aspectRatio(0.8f).clip(RoundedCornerShape(16.dp)).background(LightGray))
                }
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }
}

@Composable
fun ChatInfoOption(icon: Int, title: String, subtitle: String?, subtitleStyle: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = title,
            tint = TextPrimary,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = title, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(
                    text = subtitle, 
                    color = if(subtitleStyle) TextPrimary else TextSecondary, 
                    fontSize = 11.sp, 
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun TabRowIconItem(icon: Int, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clickable { onClick() }.padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = null,
            tint = if (isSelected) Color.White else TextSecondary,
            modifier = Modifier.size(32.dp).padding(bottom = 8.dp)
        )
        Box(
            modifier = Modifier
                .height(2.dp)
                .width(48.dp)
                .background(if (isSelected) Color.White else Color.Transparent)
        )
    }
}
