package net.kollnig.reddblockandroid.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = IndigoPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8EAFF),
    onPrimaryContainer = DarkNavy,

    secondary = SlateBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E8F0),
    onSecondaryContainer = DarkNavy,

    tertiary = BadgeGreen,
    onTertiary = Color.White,
    tertiaryContainer = BadgeGreenBg,
    onTertiaryContainer = Color(0xFF276749),

    error = SoftRed,
    onError = Color.White,
    errorContainer = SoftRedBg,
    onErrorContainer = Color(0xFF9B2C2C),

    background = CoolGrey,
    onBackground = TextPrimary,
    surface = White,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceLight,
    onSurfaceVariant = TextSecondary,

    outline = Color(0xFFCBD5E0),
    outlineVariant = Color(0xFFE2E8F0),

    surfaceContainerLowest = White,
    surfaceContainerLow = Color(0xFFF7FAFC),
    surfaceContainer = Color(0xFFF0F4F8),
    surfaceContainerHigh = White,
    surfaceContainerHighest = Color(0xFFE2E8F0),
)

private val DarkColorScheme = darkColorScheme(
    primary = IndigoPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3C4178),
    onPrimaryContainer = Color(0xFFE0E0FF),

    secondary = SlateBlueLight,
    onSecondary = Color.White,
    secondaryContainer = DarkSurfaceHigh,
    onSecondaryContainer = Color(0xFFE2E8F0),

    tertiary = BadgeGreen,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF22543D),
    onTertiaryContainer = Color(0xFFC6F6D5),

    error = Color(0xFFFC8181),
    onError = Color.White,
    errorContainer = Color(0xFF742A2A),
    onErrorContainer = Color(0xFFFED7D7),

    background = DarkBackground,
    onBackground = Color(0xFFE2E8F0),
    surface = DarkSurface,
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = DarkSurfaceHigh,
    onSurfaceVariant = Color(0xFFA0AEC0),

    outline = Color(0xFF4A5568),
    outlineVariant = Color(0xFF2D3748),

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