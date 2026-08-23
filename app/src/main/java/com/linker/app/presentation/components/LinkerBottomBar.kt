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
import androidx.compose.runtime.getValue
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
import com.linker.app.presentation.theme.DarkGrayTransparent
import com.linker.app.presentation.theme.LightBlue
import com.linker.app.presentation.theme.LinkerAngularGradient
import com.linker.app.presentation.theme.TextHint
import com.linker.app.presentation.theme.TextPrimary
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.linker.app.presentation.animation.bouncyClick
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.draw.scale

enum class BottomNavItem(@StringRes val titleRes: Int, val selectedIcon: Int, val unselectedIcon: Int) {
    Explore(R.string.nav_home, R.drawable.ic_ai_homepage_bold, R.drawable.ic_ai_homepage_outline),
    Search(R.string.nav_search, R.drawable.ic_box_search_bold, R.drawable.ic_box_search_outline),
    Add(R.string.nav_create, R.drawable.ic_ai_add_bold, R.drawable.ic_ai_add_outline),
    Chat(R.string.nav_chat, R.drawable.ic_ai_commentary_bold, R.drawable.ic_ai_commentary_outline),
    Profile(R.string.nav_profile, R.drawable.ic_profile_bold, R.drawable.ic_profile_outline)
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
                .background(DarkGrayTransparent)
                .border(2.dp, LinkerAngularGradient, RoundedCornerShape(25.dp))
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem.entries.forEach { item ->
                val title = stringResource(id = item.titleRes)
                val isSelected = currentRoute.equals(item.name, ignoreCase = true)
                val itemScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.08f else 1.0f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "bottomNavItemScale_${item.name}"
                )

                if (item == BottomNavItem.Add) {
                    // Special Add Button
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .scale(itemScale)
                            .bouncyClick { onNavigate(item) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = item.selectedIcon),
                            contentDescription = title,
                            tint = AccentGreen,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                } else {
                    // Normal Nav Items
                    val contentColor = if (isSelected) LightBlue else TextHint
                    
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp)
                            .clip(RoundedCornerShape(35.dp))
                            .scale(itemScale)
                            .bouncyClick { onNavigate(item) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(id = if (isSelected) item.selectedIcon else item.unselectedIcon),
                            contentDescription = title,
                            tint = contentColor,
                            modifier = Modifier.size(35.dp).padding(top = 5.dp)
                        )
                        Text(
                            text = title,
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
