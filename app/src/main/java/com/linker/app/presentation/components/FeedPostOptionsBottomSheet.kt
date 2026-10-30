package com.linker.app.presentation.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linker.app.R
import com.linker.app.domain.model.Link
import com.linker.app.presentation.animation.bouncyClick
import com.linker.app.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedPostOptionsBottomSheet(
    link: Link,
    onDismiss: () -> Unit,
    onNotInterested: () -> Unit = {},
    onSaveToggle: () -> Unit = {},
    onReport: () -> Unit = {},
    onHideUser: () -> Unit = {}
) {
    val context = LocalContext.current
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
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.feed_options_title),
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Not Interested
            OptionRow(
                icon = painterResource(R.drawable.ic_forbidden_outline),
                title = stringResource(R.string.feed_options_not_interested),
                subtitle = stringResource(R.string.feed_options_not_interested_desc),
                tint = Color.White,
                onClick = {
                    onNotInterested()
                    Toast.makeText(context, context.getString(R.string.feed_options_hidden_success), Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            )

            // Save / Bookmark
            OptionRow(
                icon = painterResource(if (link.engagement.isSaved) R.drawable.ic_bookmark_2_bold else R.drawable.ic_bookmark_2_outline),
                title = stringResource(if (link.engagement.isSaved) R.string.feed_options_save_remove else R.string.feed_options_save_add),
                subtitle = stringResource(R.string.feed_options_save_desc),
                tint = if (link.engagement.isSaved) GradientYellow else Color.White,
                onClick = {
                    onSaveToggle()
                    onDismiss()
                }
            )

            // Copy Link
            OptionRow(
                icon = painterResource(R.drawable.ic_hashtag_down_outline),
                title = stringResource(R.string.feed_options_copy_link),
                subtitle = stringResource(R.string.feed_options_copy_link_desc),
                tint = Color.White,
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Linker Post", "https://linker.app/p/${link.linkId}")
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, context.getString(R.string.feed_options_link_copied), Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            )

            // System Share
            OptionRow(
                icon = painterResource(R.drawable.ic_toy_6_outline),
                title = stringResource(R.string.feed_options_share),
                subtitle = stringResource(R.string.feed_options_share_desc),
                tint = Color.White,
                onClick = {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, "Linker: https://linker.app/p/${link.linkId}\n\n${link.description ?: ""}")
                        type = "text/plain"
                    }
                    val shareIntent = Intent.createChooser(sendIntent, null)
                    context.startActivity(shareIntent)
                    onDismiss()
                }
            )

            HorizontalDivider(
                color = Color.White.copy(alpha = 0.08f),
                modifier = Modifier.padding(vertical = 6.dp)
            )

            // Report
            OptionRow(
                icon = painterResource(R.drawable.ic_heart_outline),
                title = stringResource(R.string.feed_options_report),
                subtitle = stringResource(R.string.feed_options_report_desc),
                tint = ErrorRed,
                onClick = {
                    onReport()
                    onDismiss()
                }
            )

            // Hide user
            OptionRow(
                icon = painterResource(R.drawable.ic_profile_outline),
                title = stringResource(R.string.feed_options_hide_user, link.author.username),
                subtitle = stringResource(R.string.feed_options_hide_user_desc),
                tint = ErrorRed.copy(alpha = 0.85f),
                onClick = {
                    onHideUser()
                    Toast.makeText(context, context.getString(R.string.feed_options_user_hidden_success, link.author.username), Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            )
        }
    }
}

@Composable
private fun OptionRow(
    icon: androidx.compose.ui.graphics.painter.Painter,
    title: String,
    subtitle: String? = null,
    tint: Color = TextPrimary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClick(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(DarkGrayTransparent, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = tint,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}
