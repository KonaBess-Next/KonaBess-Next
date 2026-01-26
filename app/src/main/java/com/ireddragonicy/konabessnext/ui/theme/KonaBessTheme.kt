package com.ireddragonicy.konabessnext.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.os.Build

// --- Default Palette (Purple/Deep Indigo) from colors.xml ---
// Light
private val PurpleLightPrimary = Color(0xFF4F378B)
private val PurpleLightOnPrimary = Color(0xFFFFFFFF)
private val PurpleLightPrimaryContainer = Color(0xFFEADDFF)
private val PurpleLightOnPrimaryContainer = Color(0xFF21005D)
private val PurpleLightSecondary = Color(0xFF5F5C71)
private val PurpleLightOnSecondary = Color(0xFFFFFFFF)
private val PurpleLightSecondaryContainer = Color(0xFFE5DFF9)
private val PurpleLightOnSecondaryContainer = Color(0xFF1D192B)
private val PurpleLightTertiary = Color(0xFF7D5260)
private val PurpleLightOnTertiary = Color(0xFFFFFFFF)
private val PurpleLightTertiaryContainer = Color(0xFFFFD8E4)
private val PurpleLightOnTertiaryContainer = Color(0xFF31111D)
private val PurpleLightError = Color(0xFFB3261E)
private val PurpleLightOnError = Color(0xFFFFFFFF)
private val PurpleLightErrorContainer = Color(0xFFF9DEDC)
private val PurpleLightOnErrorContainer = Color(0xFF410E0B)
private val PurpleLightBackground = Color(0xFFFEF7FF)
private val PurpleLightOnBackground = Color(0xFF1D1B20)
private val PurpleLightSurface = Color(0xFFFEF7FF)
private val PurpleLightOnSurface = Color(0xFF1D1B20)
private val PurpleLightSurfaceVariant = Color(0xFFE7E0EC)
private val PurpleLightOnSurfaceVariant = Color(0xFF49454F)
private val PurpleLightOutline = Color(0xFF79747E)

// Dark
private val PurpleDarkPrimary = Color(0xFFD0BCFF)
private val PurpleDarkOnPrimary = Color(0xFF381E72)
private val PurpleDarkPrimaryContainer = Color(0xFF4F378B)
private val PurpleDarkOnPrimaryContainer = Color(0xFFEADDFF)
private val PurpleDarkSecondary = Color(0xFFCCC2DC)
private val PurpleDarkOnSecondary = Color(0xFF332D41)
private val PurpleDarkSecondaryContainer = Color(0xFF4A4458)
private val PurpleDarkOnSecondaryContainer = Color(0xFFE8DEF8)
private val PurpleDarkTertiary = Color(0xFFEFB8C8)
private val PurpleDarkOnTertiary = Color(0xFF492532)
private val PurpleDarkTertiaryContainer = Color(0xFF633B48)
private val PurpleDarkOnTertiaryContainer = Color(0xFFFFD8E4)
private val PurpleDarkError = Color(0xFFF2B8B5)
private val PurpleDarkOnError = Color(0xFF601410)
private val PurpleDarkErrorContainer = Color(0xFF8C1D18)
private val PurpleDarkOnErrorContainer = Color(0xFFF9DEDC)
private val PurpleDarkBackground = Color(0xFF141218)
private val PurpleDarkOnBackground = Color(0xFFE6E1E5)
private val PurpleDarkSurface = Color(0xFF141218)
private val PurpleDarkOnSurface = Color(0xFFE6E1E5)
private val PurpleDarkSurfaceVariant = Color(0xFF49454F)
private val PurpleDarkOnSurfaceVariant = Color(0xFFCAC4D0)
private val PurpleDarkOutline = Color(0xFF938F99)

// --- Blue Palette ---
private val BlueLightPrimary = Color(0xFF0D84FF)
private val BlueLightPrimaryContainer = Color(0xFFD3E3FD)
private val BlueLightOnPrimaryContainer = Color(0xFF001C3B)
private val BlueLightSecondary = Color(0xFF00639B)
private val BlueLightSecondaryContainer = Color(0xFFCEE5FF)
private val BlueLightOnSecondaryContainer = Color(0xFF001C3B)

private val BlueDarkPrimary = Color(0xFF82CFFF) // Lighter blue for dark mode
private val BlueDarkPrimaryContainer = Color(0xFF004578)
private val BlueDarkOnPrimaryContainer = Color(0xFFD3E3FD)

// --- Green Palette ---
private val GreenLightPrimary = Color(0xFF006D42)
private val GreenLightPrimaryContainer = Color(0xFF9AF6B8)
private val GreenLightOnPrimaryContainer = Color(0xFF002112)
private val GreenLightSecondary = Color(0xFF4D6355)
private val GreenLightSecondaryContainer = Color(0xFFD0E8D7)
private val GreenLightOnSecondaryContainer = Color(0xFF0F2013)

private val GreenDarkPrimary = Color(0xFF7FDA9D)
private val GreenDarkPrimaryContainer = Color(0xFF005231)
private val GreenDarkOnPrimaryContainer = Color(0xFF9AF6B8)

// --- Pink Palette ---
private val PinkLightPrimary = Color(0xFFB90063)
private val PinkLightPrimaryContainer = Color(0xFFFFD9E2)
private val PinkLightOnPrimaryContainer = Color(0xFF3E001D)
private val PinkLightSecondary = Color(0xFF74565F)
private val PinkLightSecondaryContainer = Color(0xFFFFD9E2)
private val PinkLightOnSecondaryContainer = Color(0xFF2B151C)

private val PinkDarkPrimary = Color(0xFFFFB0C8)
private val PinkDarkPrimaryContainer = Color(0xFF8E004A)
private val PinkDarkOnPrimaryContainer = Color(0xFFFFD9E2)

// --- AMOLED Palette ---
private val AMOLEDBackground = Color(0xFF000000)
private val AMOLEDOnBackground = Color(0xFFE6E1E5)
private val AMOLEDSurface = Color(0xFF000000)
private val AMOLEDSurfaceContainer = Color(0xFF000000)
private val AMOLEDSurfaceContainerHigh = Color(0xFF121212)
private val AMOLEDOnSurface = Color(0xFFE6E1E5)


private val PurpleLightScheme = lightColorScheme(
    primary = PurpleLightPrimary,
    onPrimary = PurpleLightOnPrimary,
    primaryContainer = PurpleLightPrimaryContainer,
    onPrimaryContainer = PurpleLightOnPrimaryContainer,
    secondary = PurpleLightSecondary,
    onSecondary = PurpleLightOnSecondary,
    secondaryContainer = PurpleLightSecondaryContainer,
    onSecondaryContainer = PurpleLightOnSecondaryContainer,
    tertiary = PurpleLightTertiary,
    onTertiary = PurpleLightOnTertiary,
    tertiaryContainer = PurpleLightTertiaryContainer,
    onTertiaryContainer = PurpleLightOnTertiaryContainer,
    error = PurpleLightError,
    onError = PurpleLightOnError,
    errorContainer = PurpleLightErrorContainer,
    onErrorContainer = PurpleLightOnErrorContainer,
    background = PurpleLightBackground,
    onBackground = PurpleLightOnBackground,
    surface = PurpleLightSurface,
    onSurface = PurpleLightOnSurface,
    surfaceVariant = PurpleLightSurfaceVariant,
    onSurfaceVariant = PurpleLightOnSurfaceVariant,
    outline = PurpleLightOutline
)

private val PurpleDarkScheme = darkColorScheme(
    primary = PurpleDarkPrimary,
    onPrimary = PurpleDarkOnPrimary,
    primaryContainer = PurpleDarkPrimaryContainer,
    onPrimaryContainer = PurpleDarkOnPrimaryContainer,
    secondary = PurpleDarkSecondary,
    onSecondary = PurpleDarkOnSecondary,
    secondaryContainer = PurpleDarkSecondaryContainer,
    onSecondaryContainer = PurpleDarkOnSecondaryContainer,
    tertiary = PurpleDarkTertiary,
    onTertiary = PurpleDarkOnTertiary,
    tertiaryContainer = PurpleDarkTertiaryContainer,
    onTertiaryContainer = PurpleDarkOnTertiaryContainer,
    error = PurpleDarkError,
    onError = PurpleDarkOnError,
    errorContainer = PurpleDarkErrorContainer,
    onErrorContainer = PurpleDarkOnErrorContainer,
    background = PurpleDarkBackground,
    onBackground = PurpleDarkOnBackground,
    surface = PurpleDarkSurface,
    onSurface = PurpleDarkOnSurface,
    surfaceVariant = PurpleDarkSurfaceVariant,
    onSurfaceVariant = PurpleDarkOnSurfaceVariant,
    outline = PurpleDarkOutline
)

// Helper to create derived schemes
private fun createScheme(
    base: ColorScheme,
    primary: Color, primaryContainer: Color, onPrimaryContainer: Color,
    secondary: Color? = null, secondaryContainer: Color? = null, onSecondaryContainer: Color? = null,
    background: Color? = null, surface: Color? = null
): ColorScheme {
    return base.copy(
        primary = primary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary ?: base.secondary,
        secondaryContainer = secondaryContainer ?: base.secondaryContainer,
        onSecondaryContainer = onSecondaryContainer ?: base.onSecondaryContainer,
        background = background ?: base.background,
        surface = surface ?: base.surface
    )
}

@Composable
fun KonaBessTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    colorPalette: Int = 0, // 0 = PURPLE/DYNAMIC, 1=PURPLE, 2=BLUE, 3=GREEN, 4=PINK, 5=AMOLED
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            // Dynamic Color inherently ignores 'colorPalette' unless we manually seed it, which Material3 API doesn't easily support.
            // So if Dynamic is on, we use system colors.
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> {
            when (colorPalette) {
                5 -> PurpleDarkScheme.copy( // AMOLED
                    background = AMOLEDBackground, onBackground = AMOLEDOnBackground,
                    surface = AMOLEDSurface, onSurface = AMOLEDOnSurface
                )
                2 -> createScheme(PurpleDarkScheme, BlueDarkPrimary, BlueDarkPrimaryContainer, BlueDarkOnPrimaryContainer) // Blue
                3 -> createScheme(PurpleDarkScheme, GreenDarkPrimary, GreenDarkPrimaryContainer, GreenDarkOnPrimaryContainer) // Green
                4 -> createScheme(PurpleDarkScheme, PinkDarkPrimary, PinkDarkPrimaryContainer, PinkDarkOnPrimaryContainer) // Pink
                else -> PurpleDarkScheme // Purple (Default)
            }
        }
        else -> { // Light Theme
            when (colorPalette) {
                // AMOLED concept doesn't apply to Light mode usually, but if selected we just use PurpleLight or maybe a high contrast? 
                // Let's stick to PurpleLight for AMOLED selection in Light Mode or maybe just normal behavior.
                5 -> PurpleLightScheme
                2 -> createScheme(PurpleLightScheme, BlueLightPrimary, BlueLightPrimaryContainer, BlueLightOnPrimaryContainer, BlueLightSecondary, BlueLightSecondaryContainer, BlueLightOnSecondaryContainer)
                3 -> createScheme(PurpleLightScheme, GreenLightPrimary, GreenLightPrimaryContainer, GreenLightOnPrimaryContainer, GreenLightSecondary, GreenLightSecondaryContainer, GreenLightOnSecondaryContainer)
                4 -> createScheme(PurpleLightScheme, PinkLightPrimary, PinkLightPrimaryContainer, PinkLightOnPrimaryContainer, PinkLightSecondary, PinkLightSecondaryContainer, PinkLightOnSecondaryContainer)
                else -> PurpleLightScheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
