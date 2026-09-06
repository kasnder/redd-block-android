package net.kollnig.reddblockandroid.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = DarkNavy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE4F1),
    onPrimaryContainer = DarkNavy,

    secondary = SlateBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8E2D8),
    onSecondaryContainer = DarkNavy,

    tertiary = BadgeGreen,
    onTertiary = Color.White,
    tertiaryContainer = BadgeGreenBg,
    onTertiaryContainer = ChipGreenText,

    error = SoftRed,
    onError = Color.White,
    errorContainer = SoftRedBg,
    onErrorContainer = Color(0xFF7C2D2D),

    background = CoolGrey,
    onBackground = TextPrimary,
    surface = White,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceLight,
    onSurfaceVariant = TextSecondary,

    outline = Color(0xFFC6BFB5),
    outlineVariant = Color(0xFFE5DED4),

    surfaceContainerLowest = White,
    surfaceContainerLow = SurfaceLight,
    surfaceContainer = Color(0xFFF0EBE3),
    surfaceContainerHigh = White,
    surfaceContainerHighest = Color(0xFFE5DED4),
)

private val DarkColorScheme = darkColorScheme(
    primary = IndigoPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF304666),
    onPrimaryContainer = Color(0xFFDCE4F1),

    secondary = SlateBlueLight,
    onSecondary = Color.White,
    secondaryContainer = DarkSurfaceHigh,
    onSecondaryContainer = Color(0xFFE5E7EB),

    tertiary = BadgeGreen,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF22543D),
    onTertiaryContainer = Color(0xFFC6F6D5),

    error = Color(0xFFFC8181),
    onError = Color.White,
    errorContainer = Color(0xFF742A2A),
    onErrorContainer = Color(0xFFFED7D7),

    background = DarkBackground,
    onBackground = Color(0xFFE5E7EB),
    surface = DarkSurface,
    onSurface = Color(0xFFE5E7EB),
    surfaceVariant = DarkSurfaceHigh,
    onSurfaceVariant = Color(0xFFB8C1CC),

    outline = Color(0xFF64748B),
    outlineVariant = Color(0xFF334155),

    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurfaceHigh,
    surfaceContainerHigh = DarkSurfaceHigh,
    surfaceContainerHighest = DarkSurfaceHigh,
)

@Composable
fun ReDDBlockAndroidTheme(
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
