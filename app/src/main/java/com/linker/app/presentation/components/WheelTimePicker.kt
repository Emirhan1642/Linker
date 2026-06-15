package com.linker.app.presentation.components

import android.widget.NumberPicker
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun WheelTimePicker(
    modifier: Modifier = Modifier,
    hours: Int,
    minutes: Int,
    seconds: Int,
    onHoursChange: (Int) -> Unit,
    onMinutesChange: (Int) -> Unit,
    onSecondsChange: (Int) -> Unit
) {
    Row(modifier = modifier) {
        AndroidView(
            factory = { context ->
                NumberPicker(context).apply {
                    minValue = 0
                    maxValue = 99
                    value = hours
                    setOnValueChangedListener { _, _, newVal -> onHoursChange(newVal) }
                }
            },
            update = { view -> view.value = hours },
            modifier = Modifier.weight(1f)
        )
        AndroidView(
            factory = { context ->
                NumberPicker(context).apply {
                    minValue = 0
                    maxValue = 59
                    value = minutes
                    setOnValueChangedListener { _, _, newVal -> onMinutesChange(newVal) }
                }
            },
            update = { view -> view.value = minutes },
            modifier = Modifier.weight(1f)
        )
        AndroidView(
            factory = { context ->
                NumberPicker(context).apply {
                    minValue = 0
                    maxValue = 59
                    value = seconds
                    setOnValueChangedListener { _, _, newVal -> onSecondsChange(newVal) }
                }
            },
            update = { view -> view.value = seconds },
            modifier = Modifier.weight(1f)
        )
    }
}
