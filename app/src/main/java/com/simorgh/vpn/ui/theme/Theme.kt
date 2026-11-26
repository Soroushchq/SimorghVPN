package com.simorgh.vpn.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = FlameGold,
    onPrimary = Color(0xFF2B2013),
    primaryContainer = Color(0xFF4A3A17),
    onPrimaryContainer = Color(0xFFFFE1A0),
    secondary = FeatherTeal,
    onSecondary = Color(0xFF062522),
    secondaryContainer = Color(0xFF124A44),
    onSecondaryContainer = Color(0xFFB7F2EA),
    tertiary = EmberRose,
    onTertiary = Color(0xFF3B1108),
    tertiaryContainer = Color(0xFF5C2417),
    onTertiaryContainer = Color(0xFFFFD9CC),
    background = WarmCoal,
    onBackground = Color(0xFFEDE6D9),
    surface = Color(0xFF191610),
    onSurface = Color(0xFFEDE6D9),
    surfaceVariant = Color(0xFF2A251B),
    onSurfaceVariant = Color(0xFFCFC5B2),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF998F7D)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF9A6B00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDF9E),
    onPrimaryContainer = Color(0xFF2B2013),
    secondary = FeatherTealDeep,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFB7F2EA),
    onSecondaryContainer = Color(0xFF062522),
    tertiary = Color(0xFFB04030),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDAD1),
    onTertiaryContainer = Color(0xFF3B1108),
    background = WarmPaper,
    onBackground = Color(0xFF1F1B13),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F1B13),
    surfaceVariant = Color(0xFFEEE7D8),
    onSurfaceVariant = Color(0xFF4C4639),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF690005),
    outline = Color(0xFF7E7667)
)

@Composable
fun SimorghTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = SimorghTypography,
        content = content
    )
}
