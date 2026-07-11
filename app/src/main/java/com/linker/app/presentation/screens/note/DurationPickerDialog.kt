package com.linker.app.presentation.screens.note

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DurationPickerDialog(
    initialValue: Int,
    minDuration: Int = 5,
    maxDuration: Int = 30,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var selected by remember { mutableIntStateOf(initialValue) }
    
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Klip Süresi",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = maxOf(0, selected - minDuration))
            
            // Auto-select the item in the center
            val centerItemIndex by remember {
                derivedStateOf {
                    val layoutInfo = listState.layoutInfo
                    val visibleItemsInfo = layoutInfo.visibleItemsInfo
                    if (visibleItemsInfo.isEmpty()) return@derivedStateOf -1
                    
                    val center = layoutInfo.viewportStartOffset + (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset) / 2
                    var closestIndex = -1
                    var minDistance = Int.MAX_VALUE
                    
                    for (itemInfo in visibleItemsInfo) {
                        val itemCenter = itemInfo.offset + itemInfo.size / 2
                        val distance = kotlin.math.abs(itemCenter - center)
                        if (distance < minDistance) {
                            minDistance = distance
                            closestIndex = itemInfo.index
                        }
                    }
                    closestIndex
                }
            }

            val actualMax = maxOf(minDuration, maxDuration)
            LaunchedEffect(centerItemIndex) {
                if (centerItemIndex in 0..(actualMax - minDuration)) {
                    selected = minDuration + centerItemIndex
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                contentAlignment = Alignment.Center
            ) {
                LazyColumn(
                    state = listState,
                    flingBehavior = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(lazyListState = listState),
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    contentPadding = PaddingValues(vertical = 55.dp)
                ) {
                    items(actualMax - minDuration + 1) { i ->
                        val value = minDuration + i
                        val isSelected = selected == value
                        Text(
                            text = "${value}sn",
                            fontSize = if (isSelected) 24.sp else 16.sp,
                            color = if (isSelected) Color(0xFF1DB954) else Color.Gray,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier
                                .padding(vertical = 8.dp)
                                .clickable { selected = value }
                        )
                    }
                }
                
                // Overlay lines for selection window (non-blocking)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(44.dp)
                        .border(1.dp, Color(0xFF1DB954).copy(alpha = 0.5f), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { onConfirm(selected) }) {
                Text("Tamam", color = Color(0xFF1DB954), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("İptal", color = Color.Gray)
            }
        },
        containerColor = Color(0xFF1A1A1A),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
    )
}
