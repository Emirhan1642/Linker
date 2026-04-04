package com.linker.app.presentation.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush

// Base Dark Colors
val Black = Color(0xFF151515) // Deep dark background
val DarkerGray = Color(0xFF121212) // Background
val DarkGray = Color(0xFF1C1C20) // Surface / Cards
val LightGray = Color(0xFF353434) // Borders, secondary surfaces
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFA0A0A5)
val TextHint = Color(0xFF66666D)

// Accents
val AccentGreen = Color(0xFF8CFF62) // Add button inner color, online status
val ErrorRed = Color(0xFFFF4B4B)
val InfoBlue = Color(0xFF00C2FF)

// Linker Signature Multi-Color Gradient (for borders, rings, active states)
val GradientRed = Color(0xFFFF003C)
val GradientYellow = Color(0xFFFFD600)
val GradientGreen = Color(0xFF00FF85)
val GradientBlue = Color(0xFF00C2FF)
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
