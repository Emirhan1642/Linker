package com.linker.app.presentation.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.linker.app.presentation.theme.GradientBlue
import com.linker.app.presentation.theme.LinkerPrimary

@Composable
fun LinkerFormattedText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.White.copy(alpha = 0.95f),
    fontSize: TextUnit = 14.sp,
    fontWeight: FontWeight = FontWeight.Normal,
    lineHeight: TextUnit = 19.sp,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    onHashtagClick: ((String) -> Unit)? = null,
    onMentionClick: ((String) -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val annotatedString = buildAnnotatedText(text)
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        text = annotatedString,
        modifier = modifier.pointerInput(annotatedString, onHashtagClick, onMentionClick, onClick) {
            detectTapGestures { pos ->
                val layout = layoutResult
                if (layout != null) {
                    val offset = layout.getOffsetForPosition(pos)
                    val hashtagAnnotation = annotatedString.getStringAnnotations(tag = "HASHTAG", start = 0, end = annotatedString.length)
                        .find { it.start <= offset && offset <= it.end }
                    if (hashtagAnnotation != null) {
                        onHashtagClick?.invoke(hashtagAnnotation.item)
                        return@detectTapGestures
                    }

                    val mentionAnnotation = annotatedString.getStringAnnotations(tag = "MENTION", start = 0, end = annotatedString.length)
                        .find { it.start <= offset && offset <= it.end }
                    if (mentionAnnotation != null) {
                        onMentionClick?.invoke(mentionAnnotation.item)
                        return@detectTapGestures
                    }
                }
                onClick?.invoke()
            }
        },
        style = TextStyle(
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            lineHeight = lineHeight
        ),
        maxLines = maxLines,
        overflow = overflow,
        onTextLayout = { layoutResult = it }
    )
}

fun buildAnnotatedText(text: String): AnnotatedString {
    val regex = Regex("([#@][\\w_.]+)")
    val annotatedString = buildAnnotatedString {
        var lastIndex = 0
        regex.findAll(text).forEach { matchResult ->
            val matchRange = matchResult.range
            // Append non-matching normal text
            if (matchRange.first > lastIndex) {
                append(text.substring(lastIndex, matchRange.first))
            }
            val matchValue = matchResult.value
            if (matchValue.startsWith("#")) {
                // Topic Hashtag: Primary pink/purple bold
                pushStringAnnotation(tag = "HASHTAG", annotation = matchValue.removePrefix("#"))
                withStyle(
                    style = SpanStyle(
                        color = LinkerPrimary,
                        fontWeight = FontWeight.Bold
                    )
                ) {
                    append(matchValue)
                }
                pop()
            } else if (matchValue.startsWith("@")) {
                // User Mention: Blue/Cyan bold
                pushStringAnnotation(tag = "MENTION", annotation = matchValue.removePrefix("@"))
                withStyle(
                    style = SpanStyle(
                        color = GradientBlue,
                        fontWeight = FontWeight.Bold
                    )
                ) {
                    append(matchValue)
                }
                pop()
            }
            lastIndex = matchRange.last + 1
        }
        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }
    return annotatedString
}
