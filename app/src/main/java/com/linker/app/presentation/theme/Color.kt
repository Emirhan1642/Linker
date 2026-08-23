package com.linker.app.presentation.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush

// Base Dark Colors
val Black = Color(0xFF09090E) // Deep obsidian matte background
val DarkerGray = Color(0xFF0F0F16) // Background
val DarkGray = Color(0xFF161622) // Surface / Cards
val LightGray = Color(0xFF262638) // Borders, secondary surfaces
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFF9A9AA8)
val TextHint = Color(0xFF636375)
val DarkGrayTransparent = Color(0xFF161624).copy(alpha = 0.85f)
val GlassCardBackground = Color(0xFF141420).copy(alpha = 0.70f)
val GlassCardBorder = Color(0xFF2C2C40).copy(alpha = 0.40f)

// Linker Signature Brand Accents (Cohesive, refined, easy on the eyes)
val LinkerPrimary = Color(0xFF8B5CF6) // Elegant Violet
val LinkerIndigo = Color(0xFF6366F1) // Refined Indigo
val LinkerAccent = Color(0xFF7C3AED) // Deep Brand Purple
val AccentGreen = Color(0xFF10B981) // Clean Emerald (online status, active toggles)
val ErrorRed = Color(0xFFEF4444)
val InfoBlue = Color(0xFF38BDF8)

// Gradients
val GradientRed = Color(0xFFF43F5E)
val GradientYellow = Color(0xFFFBBF24)
val GradientGreen = Color(0xFF10B981)
val GradientBlue = Color(0xFF38BDF8)
val GradientPurple = Color(0xFF8B5CF6)
val LightPurple = Color(0xFFC4B5FD)
val LightBlue = Color(0xFF93C5FD)

// Linker Signature Gradient (for avatar story rings & highlight borders)
val LinkerGradientColors = listOf(
    LinkerPrimary,
    LinkerIndigo,
    GradientBlue,
    LinkerPrimary
)

val LinkerAngularGradient = Brush.sweepGradient(
    colors = LinkerGradientColors
)

// Clean, sleek, non-distracting background
val ObsidianBackgroundGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF0F0F18),
        Color(0xFF0A0A10),
        Color(0xFF06060A)
    )
)

val LinkerBrandGradient = listOf(LinkerPrimary, LinkerIndigo)
val NeonBlueGreenGradient = listOf(LinkerIndigo, GradientBlue)
val NeonPurpleRedGradient = listOf(LinkerPrimary, LinkerIndigo)
val NeonYellowGreenGradient = listOf(LinkerPrimary, LinkerIndigo)
