package com.ireddragonicy.konabessnext.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.ireddragonicy.konabessnext.ui.SettingsActivity

// Color definitions (approximated from XML/screenshots)
private val PurplePrimary = Color(0xFF4F378B)
private val PurplePrimaryDark = Color(0xFFD0BCFF)
private val PurpleContainer = Color(0xFFEADDFF)
private val PurpleContainerDark = Color(0xFF4F378B)

private val BluePrimary = Color(0xFF0D84FF)
private val BlueContainer = Color(0xFFD3E3FD)

private val GreenPrimary = Color(0xFF006D42)
private val GreenContainer = Color(0xFF9AF6B8)

private val PinkPrimary = Color(0xFFB90063)
private val PinkContainer = Color(0xFFFFD9E2)

@Composable
fun KonaBessTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE) }
    
    // Read preferences
    val dynamicColor = prefs.getBoolean(SettingsActivity.KEY_DYNAMIC_COLOR, true)
    // Default to Dynamic(0) if not set, but handle the case where it might be 0
    val colorPalette = prefs.getInt(SettingsActivity.KEY_COLOR_PALETTE, SettingsActivity.PALETTE_DYNAMIC)
    val amoledMode = prefs.getBoolean(SettingsActivity.KEY_AMOLED_MODE, false)
    val savedTheme = prefs.getInt(SettingsActivity.KEY_THEME, SettingsActivity.THEME_SYSTEM)

    // Determine effective dark mode
    val effectiveDarkTheme = when (savedTheme) {
        SettingsActivity.THEME_LIGHT -> false
        SettingsActivity.THEME_DARK -> true
        else -> darkTheme
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val scheme = if (effectiveDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
             if (effectiveDarkTheme && amoledMode) scheme.copy(background = Color.Black, surface = Color.Black) else scheme
        }
        else -> {
            // Manual Palettes
            val baseScheme = if (effectiveDarkTheme) {
                when (colorPalette) {
                    SettingsActivity.PALETTE_BLUE -> darkColorScheme(
                        primary = Color(0xFF69C4FF),
                        onPrimary = Color(0xFF003258),
                        primaryContainer = Color(0xFF00497D),
                        onPrimaryContainer = Color(0xFFD1E4FF),
                        secondary = Color(0xFF69C4FF),
                        onSecondary = Color(0xFF003258),
                        secondaryContainer = Color(0xFF00497D),
                        onSecondaryContainer = Color(0xFFD1E4FF),
                        tertiary = Color(0xFF82CFFF)
                    )
                    SettingsActivity.PALETTE_GREEN -> darkColorScheme(
                        primary = Color(0xFF43E086),
                        onPrimary = Color(0xFF00381E),
                        primaryContainer = Color(0xFF00522F),
                        onPrimaryContainer = Color(0xFF5FFDA6),
                        secondary = Color(0xFFFFB4AB), // Reddish for secondary
                        onSecondary = Color(0xFF690038),
                        secondaryContainer = Color(0xFF00522F), // Use Green container for navbar selection to match theme
                        onSecondaryContainer = Color(0xFF5FFDA6),
                        tertiary = Color(0xFF43E086)
                    )
                    SettingsActivity.PALETTE_PINK -> darkColorScheme(
                        primary = Color(0xFFFFB0C9),
                        onPrimary = Color(0xFF650033),
                        primaryContainer = Color(0xFF8D0049),
                        onPrimaryContainer = Color(0xFFFFD9E2),
                        secondary = Color(0xFFFFB0C9),
                        onSecondary = Color(0xFF650033),
                        secondaryContainer = Color(0xFF8D0049),
                        onSecondaryContainer = Color(0xFFFFD9E2),
                        tertiary = Color(0xFFFFB0C9)
                    )
                    else -> darkColorScheme(
                        primary = PurplePrimaryDark,
                        onPrimary = Color(0xFF381E72),
                        primaryContainer = PurpleContainerDark,
                        onPrimaryContainer = Color(0xFFEADDFF),
                        secondary = PurplePrimaryDark,
                        onSecondary = Color(0xFF381E72),
                        secondaryContainer = PurpleContainerDark,
                        onSecondaryContainer = Color(0xFFEADDFF),
                        tertiary = PurplePrimaryDark
                    )
                }
            } else {
                 when (colorPalette) {
                    SettingsActivity.PALETTE_BLUE -> lightColorScheme(
                        primary = BluePrimary,
                        onPrimary = Color.White,
                        primaryContainer = BlueContainer,
                        onPrimaryContainer = Color(0xFF001D36),
                        secondary = BluePrimary,
                        onSecondary = Color.White,
                        secondaryContainer = BlueContainer,
                        onSecondaryContainer = Color(0xFF001D36),
                        tertiary = Color(0xFF57A9FF)
                    )
                    SettingsActivity.PALETTE_GREEN -> lightColorScheme(
                        primary = GreenPrimary,
                        onPrimary = Color.White,
                        primaryContainer = GreenContainer,
                        onPrimaryContainer = Color(0xFF002110),
                        secondary = Color(0xFFB90063), // Red/Pink secondary
                        onSecondary = Color.White,
                        secondaryContainer = GreenContainer, // Use Green container for navbar selection
                        onSecondaryContainer = Color(0xFF002110),
                        tertiary = GreenPrimary
                    )
                    SettingsActivity.PALETTE_PINK -> lightColorScheme(
                        primary = PinkPrimary,
                        onPrimary = Color.White,
                        primaryContainer = PinkContainer,
                        onPrimaryContainer = Color(0xFF3E001D),
                        secondary = PinkPrimary,
                        onSecondary = Color.White,
                        secondaryContainer = PinkContainer,
                        onSecondaryContainer = Color(0xFF3E001D),
                        tertiary = PinkPrimary
                    )
                    else -> lightColorScheme(
                        primary = PurplePrimary,
                        onPrimary = Color.White,
                        primaryContainer = PurpleContainer,
                        onPrimaryContainer = Color(0xFF21005D),
                        secondary = PurplePrimary,
                        onSecondary = Color.White,
                        secondaryContainer = PurpleContainer,
                        onSecondaryContainer = Color(0xFF21005D),
                        tertiary = PurplePrimary
                    )
                }
            }
            
            if (effectiveDarkTheme && amoledMode) {
                baseScheme.copy(background = Color.Black, surface = Color.Black)
            } else {
                baseScheme
            }
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context.findActivity()
            if (activity != null) {
                @Suppress("DEPRECATION")
                val window = activity.window
                @Suppress("DEPRECATION")
                window.statusBarColor = Color.Transparent.toArgb() 
                
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !effectiveDarkTheme
                
                if (effectiveDarkTheme && amoledMode) {
                     @Suppress("DEPRECATION")
                     window.navigationBarColor = Color.Black.toArgb()
                     WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
                } else {
                     @Suppress("DEPRECATION")
                     window.navigationBarColor = colorScheme.surface.toArgb() 
                     WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !effectiveDarkTheme
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
