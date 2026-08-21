package com.linker.app.presentation.screens.link

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linker.app.domain.model.DescriptionVersion
import com.linker.app.presentation.theme.DarkGray
import com.linker.app.presentation.theme.TextPrimary
import com.linker.app.presentation.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val descriptionHistoryDateFormat = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DescriptionHistorySheet(
    history: List<DescriptionVersion>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkGray,
        dragHandle = { BottomSheetDefaults.DragHandle(color = TextSecondary) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Düzenleme Geçmişi",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (history.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "Geçmiş bulunamadı.", color = TextSecondary)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    items(history) { version ->
                        Column(modifier = Modifier.padding(bottom = 16.dp)) {
                            Text(
                                text = "Versiyon ${version.version}",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                            Text(
                                text = version.content,
                                color = TextPrimary,
                                fontSize = 15.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            Text(
                                text = descriptionHistoryDateFormat.format(Date(version.editedAt)),
                                color = TextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
