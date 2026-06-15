package com.linker.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linker.app.R
import com.linker.app.domain.model.ReportableContentType
import com.linker.app.presentation.theme.DarkGray
import com.linker.app.presentation.theme.ErrorRed
import com.linker.app.presentation.theme.LightGray
import com.linker.app.presentation.theme.TextPrimary
import com.linker.app.presentation.theme.TextSecondary

/**
 * 3-dot context menu sheet for content actions.
 *
 * Shows contextual options for a Story, Link, or Comment.
 * Actions differ based on whether the current user is the content author.
 *
 * @param isOwnContent Whether the current user is the author.
 * @param contentType Content type for routing to the correct report flow.
 * @param canEdit Whether the edit option should be shown (e.g. < MAX_EDITS).
 * @param onShare Called when user taps "Paylaş".
 * @param onEdit Called when user taps "Düzenle" (null = hidden).
 * @param onDelete Called when user taps "Sil" (null = hidden).
 * @param onMuteUser Called when user taps "Kullanıcıyı Sustur".
 * @param onBlockUser Called when user taps "Kullanıcıyı Engelle".
 * @param onReport Called with selected ReportReason after confirmation.
 * @param onDismiss Called when sheet is dismissed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserActionSheet(
    isOwnContent: Boolean,
    contentType: ReportableContentType,
    canEdit: Boolean = false,
    onShare: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onMuteUser: (() -> Unit)? = null,
    onBlockUser: (() -> Unit)? = null,
    onReport: ((com.linker.app.domain.model.ReportReason) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showReportSheet by remember { mutableStateOf(false) }

    if (showReportSheet) {
        ContentReportSheet(
            contentType = contentType,
            onSubmit = { reason ->
                showReportSheet = false
                onReport?.invoke(reason)
                onDismiss()
            },
            onDismiss = { showReportSheet = false }
        )
        return
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkGray,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(LightGray)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Share
            if (onShare != null) {
                ActionItem(
                    icon = Icons.Default.Share,
                    label = "Paylaş",
                    tint = TextPrimary,
                    onClick = { onShare(); onDismiss() }
                )
            }

            if (isOwnContent) {
                // Edit (only for authors within edit limit)
                if (canEdit && onEdit != null) {
                    ActionItem(
                        icon = Icons.Default.Edit,
                        label = "Düzenle",
                        tint = TextPrimary,
                        onClick = { onEdit(); onDismiss() }
                    )
                }
                // Delete
                if (onDelete != null) {
                    HorizontalDivider(color = LightGray, modifier = Modifier.padding(vertical = 4.dp))
                    ActionItem(
                        icon = Icons.Default.Delete,
                        label = "Sil",
                        tint = ErrorRed,
                        onClick = { onDelete(); onDismiss() }
                    )
                }
            } else {
                // Mute user
                if (onMuteUser != null) {
                    ActionItem(
                        icon = Icons.Default.VolumeOff,
                        label = "Kullanıcıyı Sustur",
                        tint = TextPrimary,
                        onClick = { onMuteUser(); onDismiss() }
                    )
                }
                // Block user
                if (onBlockUser != null) {
                    ActionItem(
                        icon = Icons.Default.Block,
                        label = "Kullanıcıyı Engelle",
                        tint = ErrorRed,
                        onClick = { onBlockUser(); onDismiss() }
                    )
                }
                // Report
                if (onReport != null) {
                    HorizontalDivider(color = LightGray, modifier = Modifier.padding(vertical = 4.dp))
                    ActionItem(
                        icon = Icons.Default.Warning,
                        label = "Şikayet Et",
                        tint = ErrorRed,
                        onClick = { showReportSheet = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ActionItem(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            color = tint,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
