package com.nathzramirez.thesisflow.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * "Aurora" palette: a deep-space base lit by violet and cyan, with a mint accent
 * for success. Dark is the signature look; light keeps the same hues on a
 * cool off-white so both themes read as one brand.
 */

internal val DarkColors = darkColorScheme(
    primary = Color(0xFF8B7CFF),
    onPrimary = Color(0xFF0B0620),
    primaryContainer = Color(0xFF2A2160),
    onPrimaryContainer = Color(0xFFDCD6FF),
    secondary = Color(0xFF2DD4EF),
    onSecondary = Color(0xFF001F26),
    secondaryContainer = Color(0xFF0B3844),
    onSecondaryContainer = Color(0xFFB8F1FB),
    tertiary = Color(0xFF3DE8A0),
    onTertiary = Color(0xFF00210F),
    tertiaryContainer = Color(0xFF0C3F2A),
    onTertiaryContainer = Color(0xFFB6F7D8),
    error = Color(0xFFFF6B8A),
    onError = Color(0xFF2A0010),
    errorContainer = Color(0xFF4A1022),
    onErrorContainer = Color(0xFFFFD9E0),
    background = Color(0xFF070A13),
    onBackground = Color(0xFFE8ECF8),
    surface = Color(0xFF0D1220),
    onSurface = Color(0xFFE8ECF8),
    surfaceVariant = Color(0xFF161C2E),
    onSurfaceVariant = Color(0xFF9AA3BF),
    surfaceContainerLowest = Color(0xFF0A0E19),
    surfaceContainerLow = Color(0xFF0F1424),
    surfaceContainer = Color(0xFF131A2B),
    surfaceContainerHigh = Color(0xFF182036),
    surfaceContainerHighest = Color(0xFF1E2740),
    outline = Color(0xFF2E3857),
    outlineVariant = Color(0xFF1D2439),
    inverseSurface = Color(0xFFE8ECF8),
    inverseOnSurface = Color(0xFF131A2B),
    inversePrimary = Color(0xFF5B3DF5),
    scrim = Color(0xFF000000),
)

internal val LightColors = lightColorScheme(
    primary = Color(0xFF5B3DF5),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE6E0FF),
    onPrimaryContainer = Color(0xFF1C0D6B),
    secondary = Color(0xFF0891B2),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCFF5FC),
    onSecondaryContainer = Color(0xFF003642),
    tertiary = Color(0xFF059669),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD1FAE5),
    onTertiaryContainer = Color(0xFF002B1C),
    error = Color(0xFFE11D48),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFE4E9),
    onErrorContainer = Color(0xFF4C0519),
    background = Color(0xFFF5F6FC),
    onBackground = Color(0xFF0B1024),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0B1024),
    surfaceVariant = Color(0xFFE8EAF5),
    onSurfaceVariant = Color(0xFF4A5270),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F8FD),
    surfaceContainer = Color(0xFFF0F2FA),
    surfaceContainerHigh = Color(0xFFE9ECF7),
    surfaceContainerHighest = Color(0xFFE2E6F3),
    outline = Color(0xFFC3C8DC),
    outlineVariant = Color(0xFFDDE1EE),
    inverseSurface = Color(0xFF131A2B),
    inverseOnSurface = Color(0xFFE8ECF8),
    inversePrimary = Color(0xFF8B7CFF),
    scrim = Color(0xFF000000),
)
