package com.ireddragonicy.konabessnext.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Premium Dark Palette based on screenshots
private val DarkPrimary = Color(0xFF8C9EFF) // Soft Indigo
private val DarkOnPrimary = Color(0xFF00105C)
private val DarkPrimaryContainer = Color(0xFF4A4E69) // Muted Dark Purple/Grey
private val DarkOnPrimaryContainer = Color(0xFFE0E0FF)

private val DarkBackground = Color(0xFF121218) // Very Dark Blue/Black
private val DarkSurface = Color(0xFF1E1E26) // Slightly lighter card background
private val DarkOnSurface = Color(0xFFE8E8E8)
private val DarkError = Color(0xFFFFB4AB)

private val PremiumDarkScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    
    background = DarkBackground,
    onBackground = DarkOnSurface,
    
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    
    error = DarkError
)

@Composable
fun KonaBessTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = PremiumDarkScheme,
        typography = androidx.compose.material3.Typography(), // Default for now
        content = content
    )
}
