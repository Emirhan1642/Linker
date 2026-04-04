package com.linker.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import com.linker.app.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linker.app.presentation.theme.AccentGreen
import com.linker.app.presentation.theme.DarkGray
import com.linker.app.presentation.theme.LightBlue
import com.linker.app.presentation.theme.LinkerAngularGradient
import com.linker.app.presentation.theme.TextHint
import com.linker.app.presentation.theme.TextPrimary
enum class BottomNavItem(val title: String, val selectedIcon: Int, val unselectedIcon: Int) {
    Explore("Explore", R.drawable.ic_ai_homepage_bold, R.drawable.ic_ai_homepage_outline),
    Search("Search", R.drawable.ic_box_search_bold, R.drawable.ic_box_search_outline),
    Add("Add", R.drawable.ic_ai_add_bold, R.drawable.ic_ai_add_outline),
    Chat("Chat", R.drawable.ic_ai_commentary_bold, R.drawable.ic_ai_commentary_outline),
    Profile("Profile", R.drawable.ic_profile_bold, R.drawable.ic_profile_outline)
}

@Composable
fun LinkerBottomNavigationBar(
    currentRoute: String,
    onNavigate: (BottomNavItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(25.dp))
                .background(Color(0xE61C1C20)) // Dark Gray transparent
                .border(2.dp, LinkerAngularGradient, RoundedCornerShape(25.dp))
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem.values().forEach { item ->
                if (item == BottomNavItem.Add) {
                    // Special Add Button
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable { onNavigate(item) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = item.selectedIcon),
                            contentDescription = item.title,
                            tint = AccentGreen,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                } else {
                    // Normal Nav Items
                    val isSelected = currentRoute.equals(item.name, ignoreCase = true)
                    val contentColor = if (isSelected) LightBlue else TextHint
                    
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp)
                            .clip(RoundedCornerShape(35.dp))
                            .clickable { onNavigate(item) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(id = if (isSelected) item.selectedIcon else item.unselectedIcon),
                            contentDescription = item.title,
                            tint = contentColor,
                            modifier = Modifier.size(35.dp).padding(top = 5.dp)
                        )
                        Text(
                            text = item.title,
                            color = contentColor,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}
