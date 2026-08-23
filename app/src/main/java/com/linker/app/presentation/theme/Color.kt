package com.linker.app.presentation.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush

// Base Dark Colors
val Black = Color(0xFF0D0D14) // Deep obsidian dark background
val DarkerGray = Color(0xFF101018) // Background
val DarkGray = Color(0xFF181824) // Surface / Cards
val LightGray = Color(0xFF2C2C3D) // Borders, secondary surfaces
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFA0A0B2)
val TextHint = Color(0xFF6B6B7F)
val DarkGrayTransparent = Color(0xFF181826).copy(alpha = 0.85f)
val GlassCardBackground = Color(0xFF1C1C2C).copy(alpha = 0.70f)
val GlassCardBorder = Color(0xFF3F3F5A).copy(alpha = 0.45f)

// Accents
val AccentGreen = Color(0xFF00FF85) // Add button inner color, online status
val ErrorRed = Color(0xFFFF3B5C)
val InfoBlue = Color(0xFF00D2FF)

// Linker Signature Multi-Color Gradient (for borders, rings, active states)
val GradientRed = Color(0xFFFF0055)
val GradientYellow = Color(0xFFFFD600)
val GradientGreen = Color(0xFF00FF85)
val GradientBlue = Color(0xFF00D2FF)
val GradientPurple = Color(0xFF9E00FF)
val LightPurple = Color(0xFFF0B7FF)
val LightBlue = Color(0xFF7994CA)

val LinkerGradientColors = listOf(
    GradientRed,
    GradientYellow,
    GradientGreen,
    GradientBlue,
    GradientPurple,
    GradientRed // loop back for smooth sweep gradient
)

val LinkerAngularGradient = Brush.sweepGradient(
    colors = LinkerGradientColors
)

val ObsidianBackgroundGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF16162D),
        Color(0xFF0F0F1A),
        Color(0xFF0A0A10)
    )
)

val NeonBlueGreenGradient = listOf(GradientBlue, GradientGreen)
val NeonPurpleRedGradient = listOf(GradientPurple, GradientRed)
val NeonYellowGreenGradient = listOf(GradientYellow, GradientGreen)
