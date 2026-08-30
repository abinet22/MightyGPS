package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object MC {
    // Surfaces (elevation scale, darkest to lightest)
    val Surface0 = Color(0xFF020617)   // App background canvas
    val Surface1 = Color(0xFF0F172A)   // Cards, primary sheets & drawer panels
    val Surface2 = Color(0xFF1E293B)   // Nested cards, inputs, secondary surfaces
    val Surface3 = Color(0xFF334155)   // Subtle borders, dividers, outlines

    // Text hierarchy
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF94A3B8)
    val TextTertiary = Color(0xFF64748B)

    // Semantic status & Accents
    val StatusOnline = Color(0xFF10B981)
    val StatusIdle = Color(0xFFF59E0B)
    val StatusOffline = Color(0xFFEF4444)
    val AccentPrimary = Color(0xFF3B82F6)
    val AccentSecondary = Color(0xFF8B5CF6)
    val AccentCyan = Color(0xFF06B6D4)

    // Card Surface Top-Highlight Gradient for subtle depth
    val CardTopHighlight = Brush.verticalGradient(
        listOf(Color(0x0DFFFFFF), Color.Transparent)
    )
}
