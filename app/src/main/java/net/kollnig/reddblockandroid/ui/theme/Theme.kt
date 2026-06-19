package net.kollnig.reddblockandroid.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = ReddNavy,
    onPrimary = White,
    primaryContainer = ReddWarmGrey,
    onPrimaryContainer = ReddNavy,

    secondary = ReddBlue,
    onSecondary = White,
    secondaryContainer = ReddBlueSoft,
    onSecondaryContainer = ReddNavy,

    tertiary = ReddTeal,
    onTertiary = White,
    tertiaryContainer = ReddTealSoft,
    onTertiaryContainer = ReddTealHover,

    error = ReddCoral,
    onError = White,
    errorContainer = ReddCoralSoft,
    onErrorContainer = ReddCoral,

    background = ReddCream,
    onBackground = ReddNavy,
    surface = ReddCard,
    onSurface = ReddNavy,
    surfaceVariant = ReddWarmGrey,
    onSurfaceVariant = ReddBody,

    outline = ReddMuted,
    outlineVariant = ReddBorder,

    surfaceContainerLowest = ReddCard,
    surfaceContainerLow = ReddCard,
    surfaceContainer = ReddWarmGrey,
    surfaceContainerHigh = ReddCard,
    surfaceContainerHighest = ReddWarmGrey,
)

private val DarkColorScheme = darkColorScheme(
    primary = ReddDarkTeal,
    onPrimary = PrimaryOnDark,
    primaryContainer = ReddDarkTealSoft,
    onPrimaryContainer = ReddDarkTextPrimary,

    secondary = ReddBlue,
    onSecondary = PrimaryOnDark,
    secondaryContainer = ReddDarkWarmGrey,
    onSecondaryContainer = ReddDarkTextPrimary,

    tertiary = ReddDarkTeal,
    onTertiary = White,
    tertiaryContainer = ReddDarkTealSoft,
    onTertiaryContainer = ReddDarkTealHover,

    error = ReddDarkCoral,
    onError = PrimaryOnDark,
    errorContainer = ReddDarkCoralSoft,
    onErrorContainer = ReddDarkTextPrimary,

    background = ReddDarkCanvas,
    onBackground = ReddDarkTextPrimary,
    surface = ReddDarkCard,
    onSurface = ReddDarkTextPrimary,
    surfaceVariant = ReddDarkWarmGrey,
    onSurfaceVariant = ReddDarkTextSecondary,

    outline = ReddDarkMuted,
    outlineVariant = ReddDarkBorder,

    surfaceContainerLowest = ReddDarkCanvas,
    surfaceContainerLow = ReddDarkElement,
    surfaceContainer = ReddDarkWarmGrey,
    surfaceContainerHigh = ReddDarkCard,
    surfaceContainerHighest = ReddDarkWarmGrey,
)

@Composable
fun FristedAndroidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}