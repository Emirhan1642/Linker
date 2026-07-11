package com.linker.app.presentation.components

import android.widget.NumberPicker
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun WheelTimePicker(
    modifier: Modifier = Modifier,
    days: Int,
    hours: Int,
    minutes: Int,
    onDaysChange: (Int) -> Unit,
    onHoursChange: (Int) -> Unit,
    onMinutesChange: (Int) -> Unit
) {
    Row(modifier = modifier) {
        AndroidView(
            factory = { context ->
                NumberPicker(context).apply {
                    minValue = 0
                    maxValue = 7
                    value = days
                    setOnValueChangedListener { _, _, newVal -> onDaysChange(newVal) }
                }
            },
            update = { view -> view.value = days },
            modifier = Modifier.weight(1f)
        )
        AndroidView(
            factory = { context ->
                NumberPicker(context).apply {
                    minValue = 0
                    maxValue = 23
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
    }
}
