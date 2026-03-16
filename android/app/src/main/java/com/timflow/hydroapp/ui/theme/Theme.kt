package com.timflow.hydroapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Brand colours ────────────────────────────────────────────────────────────
// Aquifer-blue primary palette; earth-brown secondary.

private val AquiferBlue = Color(0xFF006494)
private val AquiferBlueDark = Color(0xFF4DA6D4)
private val EarthBrown = Color(0xFF8D6E63)
private val EarthBrownDark = Color(0xFFBCAAA4)

private val LightColorScheme = lightColorScheme(
    primary = AquiferBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCE5F5),
    onPrimaryContainer = Color(0xFF001E30),
    secondary = EarthBrown,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEFDDD9),
    onSecondaryContainer = Color(0xFF341F1B),
    background = Color(0xFFF8FAFB),
    surface = Color(0xFFF8FAFB),
    error = Color(0xFFB3261E),
)

private val DarkColorScheme = darkColorScheme(
    primary = AquiferBlueDark,
    onPrimary = Color(0xFF003549),
    primaryContainer = Color(0xFF004D6B),
    onPrimaryContainer = Color(0xFFCCE5F5),
    secondary = EarthBrownDark,
    onSecondary = Color(0xFF4E342E),
    secondaryContainer = Color(0xFF6D4C41),
    onSecondaryContainer = Color(0xFFEFDDD9),
    background = Color(0xFF1A1C1E),
    surface = Color(0xFF1A1C1E),
    error = Color(0xFFF2B8B5),
)

/**
 * HydroApp Material 3 theme.
 *
 * Uses a dynamic-colour-style aquifer-blue palette.  Both light and dark
 * variants are defined; the system follows the device's light/dark setting.
 */
@Composable
fun HydroAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
