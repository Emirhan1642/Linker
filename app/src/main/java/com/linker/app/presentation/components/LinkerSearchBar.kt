package com.linker.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linker.app.R
import com.linker.app.presentation.animation.bouncyClick
import com.linker.app.presentation.theme.*

@Composable
fun LinkerSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search...",
    onClear: () -> Unit = { onQueryChange("") },
    onMicClick: () -> Unit = {},
    onSearch: () -> Unit = {}
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val searchBarBorderBrush = Brush.horizontalGradient(
        colors = listOf(
            GradientBlue.copy(alpha = 0.45f),
            GradientPurple.copy(alpha = 0.35f),
            GradientGreen.copy(alpha = 0.25f)
        )
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(DarkGrayTransparent)
            .border(1.2.dp, searchBarBorderBrush, RoundedCornerShape(26.dp))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_search_outline),
            contentDescription = "Search",
            tint = GradientBlue,
            modifier = Modifier.size(22.dp)
        )
        
        Spacer(modifier = Modifier.width(12.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentAlignment = Alignment.CenterStart
        ) {
            if (query.isEmpty()) {
                Text(
                    text = placeholder,
                    color = TextHint,
                    fontSize = 15.sp
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                cursorBrush = SolidColor(GradientBlue),
                textStyle = Typography.bodyLarge.copy(color = TextPrimary, fontSize = 15.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        onSearch()
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (query.isNotEmpty()) {
            IconButton(
                onClick = onClear,
                modifier = Modifier.size(32.dp).bouncyClick { onClear() }
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_close_circle_bold),
                    contentDescription = "Clear",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
