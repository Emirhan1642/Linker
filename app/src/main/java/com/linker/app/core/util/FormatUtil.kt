package com.linker.app.core.util

object FormatUtil {
    fun formatStat(value: Int): String = when {
        value >= 1_000_000 -> String.format("%.1fM", value / 1_000_000.0)
        value >= 1_000     -> String.format("%.1fK", value / 1_000.0)
        else               -> value.toString()
    }
}
