package com.linker.app.presentation.screens.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linker.app.presentation.theme.TextPrimary
import com.linker.app.presentation.theme.TextSecondary
import kotlin.math.roundToInt

/**
 * Emoji Picker Panel
 * Shows categorized emojis in a floating panel above or below the emoji bar
 */
@Composable
fun EmojiPickerPanel(
    emojiBarX: Float,
    emojiBarY: Float,
    emojiBarWidth: Float,
    emojiBarHeight: Float,
    screenHeight: Float,
    messageBounds: androidx.compose.ui.geometry.Rect,
    onEmojiSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf(EmojiCategory.SMILEYS) }
    
    val panelHeight = 320.dp
    val density = androidx.compose.ui.platform.LocalDensity.current
    val panelY = remember(emojiBarY, emojiBarHeight, screenHeight, messageBounds, density) {
        val panelHeightPx = with(density) { panelHeight.toPx() }
        val paddingPx = with(density) { 8.dp.toPx() }
        
        // Calculate position: above or below emoji bar, avoiding message bubble
        val spaceAbove = emojiBarY - paddingPx
        val spaceBelow = screenHeight - emojiBarY - emojiBarHeight - paddingPx
        
        // Check if placing above would overlap with message
        val panelAboveY = emojiBarY - panelHeightPx - paddingPx
        val wouldOverlapMessage = panelAboveY < messageBounds.bottom + paddingPx
        
        if (spaceAbove >= panelHeightPx && !wouldOverlapMessage) {
            // Enough space above and won't overlap message
            panelAboveY
        } else if (spaceBelow >= panelHeightPx) {
            // Enough space below, place below emoji bar
            emojiBarY + emojiBarHeight + paddingPx
        } else {
            // Not enough space either way, prefer below if message is above emoji bar
            if (messageBounds.bottom < emojiBarY) {
                // Message is above, place picker below
                (emojiBarY + emojiBarHeight + paddingPx).coerceAtMost(screenHeight - panelHeightPx - paddingPx)
            } else {
                // Message is below or overlapping, place picker above and clamp
                panelAboveY.coerceAtLeast(paddingPx)
            }
        }
    }
    
    Box(
        modifier = modifier
            .offset { IntOffset(emojiBarX.roundToInt(), panelY.roundToInt()) }
            .width(with(androidx.compose.ui.platform.LocalDensity.current) { emojiBarWidth.toDp() })
            .height(panelHeight)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Category tabs
            ScrollableTabRow(
                selectedTabIndex = EmojiCategory.entries.indexOf(selectedCategory),
                containerColor = Color.Transparent,
                contentColor = TextPrimary,
                edgePadding = 4.dp,
                indicator = {},
                divider = {},
                modifier = Modifier.fillMaxWidth()
            ) {
                EmojiCategory.entries.forEach { category ->
                    Tab(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        modifier = Modifier.padding(horizontal = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(vertical = 6.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (selectedCategory == category) MaterialTheme.colorScheme.surfaceVariant
                                    else Color.Transparent
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = category.icon,
                                fontSize = 18.sp,
                                color = if (selectedCategory == category) TextPrimary else TextSecondary
                            )
                        }
                    }
                }
            }
            
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
            
            // Emoji grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                contentPadding = PaddingValues(4.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(selectedCategory.emojis) { emoji ->
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onEmojiSelected(emoji) }
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = emoji,
                            fontSize = 24.sp
                        )
                    }
                }
            }
        }
    }
}

enum class EmojiCategory(val icon: String, val emojis: List<String>) {
    SMILEYS(
        icon = "😀",
        emojis = listOf(
            "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂",
            "🙂", "🙃", "😉", "😊", "😇", "🥰", "😍", "🤩",
            "😘", "😗", "😚", "😙", "🥲", "😋", "😛", "😜",
            "🤪", "😝", "🤑", "🤗", "🤭", "🤫", "🤔", "🤐",
            "🤨", "😐", "😑", "😶", "😏", "😒", "🙄", "😬",
            "🤥", "😌", "😔", "😪", "🤤", "😴", "😷", "🤒",
            "🤕", "🤢", "🤮", "🤧", "🥵", "🥶", "🥴", "😵",
            "🤯", "🤠", "🥳", "🥸", "😎", "🤓", "🧐", "😕",
            "😟", "🙁", "☹️", "😮", "😯", "😲", "😳", "🥺",
            "😦", "😧", "😨", "😰", "😥", "😢", "😭", "😱",
            "😖", "😣", "😞", "😓", "😩", "😫", "🥱", "😤",
            "😡", "😠", "🤬", "😈", "👿", "💀", "☠️", "💩"
        )
    ),
    GESTURES(
        icon = "👋",
        emojis = listOf(
            "👋", "🤚", "🖐️", "✋", "🖖", "👌", "🤌", "🤏",
            "✌️", "🤞", "🤟", "🤘", "🤙", "👈", "👉", "👆",
            "🖕", "👇", "☝️", "👍", "👎", "✊", "👊", "🤛",
            "🤜", "👏", "🙌", "👐", "🤲", "🤝", "🙏", "✍️",
            "💅", "🤳", "💪", "🦾", "🦿", "🦵", "🦶", "👂",
            "🦻", "👃", "🧠", "🫀", "🫁", "🦷", "🦴", "👀",
            "👁️", "👅", "👄", "💋", "🩸"
        )
    ),
    PEOPLE(
        icon = "👤",
        emojis = listOf(
            "👶", "👧", "🧒", "👦", "👩", "🧑", "👨", "👩‍🦱",
            "🧑‍🦱", "👨‍🦱", "👩‍🦰", "🧑‍🦰", "👨‍🦰", "👱‍♀️", "👱", "👱‍♂️",
            "👩‍🦳", "🧑‍🦳", "👨‍🦳", "👩‍🦲", "🧑‍🦲", "👨‍🦲", "🧔‍♀️", "🧔",
            "🧔‍♂️", "👵", "🧓", "👴", "👲", "👳‍♀️", "👳", "👳‍♂️",
            "🧕", "👮‍♀️", "👮", "👮‍♂️", "👷‍♀️", "👷", "👷‍♂️", "💂‍♀️",
            "💂", "💂‍♂️", "🕵️‍♀️", "🕵️", "🕵️‍♂️", "👩‍⚕️", "🧑‍⚕️", "👨‍⚕️",
            "👩‍🌾", "🧑‍🌾", "👨‍🌾", "👩‍🍳", "🧑‍🍳", "👨‍🍳", "👩‍🎓", "🧑‍🎓",
            "👨‍🎓", "👩‍🎤", "🧑‍🎤", "👨‍🎤", "👩‍🏫", "🧑‍🏫", "👨‍🏫", "👩‍🏭"
        )
    ),
    ANIMALS(
        icon = "🐶",
        emojis = listOf(
            "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼",
            "🐻‍❄️", "🐨", "🐯", "🦁", "🐮", "🐷", "🐽", "🐸",
            "🐵", "🙈", "🙉", "🙊", "🐒", "🐔", "🐧", "🐦",
            "🐤", "🐣", "🐥", "🦆", "🦅", "🦉", "🦇", "🐺",
            "🐗", "🐴", "🦄", "🐝", "🪱", "🐛", "🦋", "🐌",
            "🐞", "🐜", "🪰", "🪲", "🪳", "🦟", "🦗", "🕷️",
            "🕸️", "🦂", "🐢", "🐍", "🦎", "🦖", "🦕", "🐙",
            "🦑", "🦐", "🦞", "🦀", "🐡", "🐠", "🐟", "🐬",
            "🐳", "🐋", "🦈", "🐊", "🐅", "🐆", "🦓", "🦍",
            "🦧", "🦣", "🐘", "🦛", "🦏", "🐪", "🐫", "🦒"
        )
    ),
    FOOD(
        icon = "🍕",
        emojis = listOf(
            "🍇", "🍈", "🍉", "🍊", "🍋", "🍌", "🍍", "🥭",
            "🍎", "🍏", "🍐", "🍑", "🍒", "🍓", "🫐", "🥝",
            "🍅", "🫒", "🥥", "🥑", "🍆", "🥔", "🥕", "🌽",
            "🌶️", "🫑", "🥒", "🥬", "🥦", "🧄", "🧅", "🍄",
            "🥜", "🌰", "🍞", "🥐", "🥖", "🫓", "🥨", "🥯",
            "🥞", "🧇", "🧀", "🍖", "🍗", "🥩", "🥓", "🍔",
            "🍟", "🍕", "🌭", "🥪", "🌮", "🌯", "🫔", "🥙",
            "🧆", "🥚", "🍳", "🥘", "🍲", "🫕", "🥣", "🥗",
            "🍿", "🧈", "🧂", "🥫", "🍱", "🍘", "🍙", "🍚",
            "🍛", "🍜", "🍝", "🍠", "🍢", "🍣", "🍤", "🍥"
        )
    ),
    TRAVEL(
        icon = "✈️",
        emojis = listOf(
            "🚗", "🚕", "🚙", "🚌", "🚎", "🏎️", "🚓", "🚑",
            "🚒", "🚐", "🛻", "🚚", "🚛", "🚜", "🦯", "🦽",
            "🦼", "🛴", "🚲", "🛵", "🏍️", "🛺", "🚨", "🚔",
            "🚍", "🚘", "🚖", "🚡", "🚠", "🚟", "🚃", "🚋",
            "🚞", "🚝", "🚄", "🚅", "🚈", "🚂", "🚆", "🚇",
            "🚊", "🚉", "✈️", "🛫", "🛬", "🛩️", "💺", "🛰️",
            "🚀", "🛸", "🚁", "🛶", "⛵", "🚤", "🛥️", "🛳️",
            "⛴️", "🚢", "⚓", "⛽", "🚧", "🚦", "🚥", "🚏",
            "🗺️", "🗿", "🗽", "🗼", "🏰", "🏯", "🏟️", "🎡",
            "🎢", "🎠", "⛲", "⛱️", "🏖️", "🏝️", "🏜️", "🌋"
        )
    ),
    OBJECTS(
        icon = "⚽",
        emojis = listOf(
            "⚽", "🏀", "🏈", "⚾", "🥎", "🎾", "🏐", "🏉",
            "🥏", "🎱", "🪀", "🏓", "🏸", "🏒", "🏑", "🥍",
            "🏏", "🪃", "🥅", "⛳", "🪁", "🏹", "🎣", "🤿",
            "🥊", "🥋", "🎽", "🛹", "🛼", "🛷", "⛸️", "🥌",
            "🎿", "⛷️", "🏂", "🪂", "🏋️", "🤼", "🤸", "🤺",
            "⛹️", "🤾", "🏌️", "🏇", "🧘", "🏊", "🏄", "🚣",
            "🧗", "🚵", "🚴", "🏆", "🥇", "🥈", "🥉", "🏅",
            "🎖️", "🏵️", "🎗️", "🎫", "🎟️", "🎪", "🤹", "🎭",
            "🩰", "🎨", "🎬", "🎤", "🎧", "🎼", "🎹", "🥁",
            "🪘", "🎷", "🎺", "🪗", "🎸", "🪕", "🎻", "🎲"
        )
    ),
    SYMBOLS(
        icon = "❤️",
        emojis = listOf(
            "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍",
            "🤎", "💔", "❤️‍🔥", "❤️‍🩹", "💕", "💞", "💓", "💗",
            "💖", "💘", "💝", "💟", "☮️", "✝️", "☪️", "🕉️",
            "☸️", "✡️", "🔯", "🕎", "☯️", "☦️", "🛐", "⛎",
            "♈", "♉", "♊", "♋", "♌", "♍", "♎", "♏",
            "♐", "♑", "♒", "♓", "🆔", "⚛️", "🉑", "☢️",
            "☣️", "📴", "📳", "🈶", "🈚", "🈸", "🈺", "🈷️",
            "✴️", "🆚", "💮", "🉐", "㊙️", "㊗️", "🈴", "🈵",
            "🈹", "🈲", "🅰️", "🅱️", "🆎", "🆑", "🅾️", "🆘",
            "❌", "⭕", "🛑", "⛔", "📛", "🚫", "💯", "💢"
        )
    ),
    FLAGS(
        icon = "🏁",
        emojis = listOf(
            "🏁", "🚩", "🎌", "🏴", "🏳️", "🏳️‍🌈", "🏳️‍⚧️", "🏴‍☠️",
            "🇦🇨", "🇦🇩", "🇦🇪", "🇦🇫", "🇦🇬", "🇦🇮", "🇦🇱", "🇦🇲",
            "🇦🇴", "🇦🇶", "🇦🇷", "🇦🇸", "🇦🇹", "🇦🇺", "🇦🇼", "🇦🇽",
            "🇦🇿", "🇧🇦", "🇧🇧", "🇧🇩", "🇧🇪", "🇧🇫", "🇧🇬", "🇧🇭",
            "🇧🇮", "🇧🇯", "🇧🇱", "🇧🇲", "🇧🇳", "🇧🇴", "🇧🇶", "🇧🇷",
            "🇧🇸", "🇧🇹", "🇧🇻", "🇧🇼", "🇧🇾", "🇧🇿", "🇨🇦", "🇨🇨",
            "🇨🇩", "🇨🇫", "🇨🇬", "🇨🇭", "🇨🇮", "🇨🇰", "🇨🇱", "🇨🇲",
            "🇨🇳", "🇨🇴", "🇨🇵", "🇨🇷", "🇨🇺", "🇨🇻", "🇨🇼", "🇨🇽",
            "🇨🇾", "🇨🇿", "🇩🇪", "🇩🇬", "🇩🇯", "🇩🇰", "🇩🇲", "🇩🇴",
            "🇩🇿", "🇪🇦", "🇪🇨", "🇪🇪", "🇪🇬", "🇪🇭", "🇪🇷", "🇪🇸"
        )
    )
}
