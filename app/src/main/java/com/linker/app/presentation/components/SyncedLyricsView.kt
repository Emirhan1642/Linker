package com.linker.app.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linker.app.domain.repository.SyncedLyricLine
import kotlinx.coroutines.launch

@Composable
fun SyncedLyricsView(
    lyrics: List<SyncedLyricLine>,
    currentPositionMs: Long,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Find the current active line index based on time
    val activeIndex = remember(currentPositionMs, lyrics) {
        lyrics.indexOfLast { it.timeMs <= currentPositionMs }.coerceAtLeast(0)
    }

    // Check if the user is dragging
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    
    // Track exact height of the container to prevent layoutInfo mid-measurement bugs
    var viewportHeight by remember { mutableIntStateOf(0) }
    
    // Intent-based auto-scroll pausing
    var userScrolledAway by remember { mutableStateOf(false) }
    var lastPositionMs by remember { mutableLongStateOf(currentPositionMs) }

    // If user interacts with the list, pause auto-scroll
    LaunchedEffect(isDragged) {
        if (isDragged) {
            userScrolledAway = true
        }
    }

    // If the audio jumps by more than 1 second (user scrubbed the waveform), resume auto-scroll immediately
    LaunchedEffect(currentPositionMs) {
        if (kotlin.math.abs(currentPositionMs - lastPositionMs) > 1000) {
            userScrolledAway = false
        }
        lastPositionMs = currentPositionMs
    }

    // Auto-scroll to the active line
    LaunchedEffect(activeIndex, userScrolledAway) {
        if (activeIndex >= 0 && lyrics.isNotEmpty()) {
            if (!userScrolledAway && !isDragged) {
                coroutineScope.launch {
                    // Dynamically calculate the exact center offset based on the item's true height.
                    val itemInfo = listState.layoutInfo.visibleItemsInfo.find { it.index == activeIndex }
                    val itemHeight = itemInfo?.size ?: 150
                    
                    // In Compose, animateScrollToItem places the item's top edge at exactly 'offset' pixels from the top of the viewport.
                    // To place the item's center at the screen's center, we must push its top edge down by half the viewport height,
                    // and then pull it back up by half the item's height.
                    val centerOffset = if (viewportHeight > 0) {
                        (viewportHeight / 2) - (itemHeight / 2)
                    } else {
                        400
                    }
                    
                    listState.animateScrollToItem(activeIndex, centerOffset)
                }
            }
        }
    }

    val density = LocalDensity.current
    val halfViewportDp = remember(viewportHeight) {
        with(density) { (if (viewportHeight > 0) viewportHeight / 2 else 400).toDp() }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { viewportHeight = it.size.height },
        contentPadding = PaddingValues(top = halfViewportDp, bottom = halfViewportDp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        itemsIndexed(lyrics) { index, line ->
            val isActive = index == activeIndex
            val alpha by animateFloatAsState(
                targetValue = if (isActive) 1f else 0.4f,
                animationSpec = tween(300),
                label = "lyric_alpha"
            )
            val scale by animateFloatAsState(
                targetValue = if (isActive) 1.1f else 1f,
                animationSpec = tween(300),
                label = "lyric_scale"
            )

            Text(
                text = line.text,
                color = if (isActive) Color.White else Color.Gray,
                fontSize = (18 * scale).sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .alpha(alpha)
            )
        }
    }
}
