package net.kollnig.reddblockandroid.ui.theme

import androidx.compose.ui.graphics.Color

// Canonical ReDD palette shared with desktop/iOS styling.

val White = Color(0xFFFFFFFF)
val PrimaryOnDark = Color(0xFF0C1620)

// Light mode tokens
val ReddCream = Color(0xFFFAF8F5)
val ReddWarmGrey = Color(0xFFF1EEE9)
val ReddCard = Color(0xFFFFFFFF)
val ReddNavy = Color(0xFF1E2D3E)
val ReddBody = Color(0xFF2C2C35)
val ReddMuted = Color(0xFF696977)
val ReddSubtle = Color(0xFF8A8A96)
val ReddBorder = Color(0xFFE1DCD6)
val ReddTeal = Color(0xFF2A9D8F)
val ReddTealHover = Color(0xFF21806F)
val ReddTealSoft = Color(0xFFE5F5F3)
val ReddCoral = Color(0xFFD4605A)
val ReddCoralSoft = Color(0xFFFAEBEA)
val ReddBlue = Color(0xFF4A90E2)
val ReddBlueSoft = Color(0xFFE8F1FA)

// Dark mode tokens
val ReddDarkCanvas = Color(0xFF14202C)
val ReddDarkWarmGrey = Color(0xFF1A2735)
val ReddDarkCard = Color(0xFF1E2D3E)
val ReddDarkTextPrimary = Color(0xFFF5F0E8)
val ReddDarkTextSecondary = Color(0xFFE6E2D8)
val ReddDarkMuted = Color(0xFF9AA3B0)
val ReddDarkBorder = Color(0x1AFFFFFF)
val ReddDarkTeal = Color(0xFF399E90)
val ReddDarkTealHover = Color(0xFF4EC0B0)
val ReddDarkTealSoft = Color(0x29399E90)
val ReddDarkCoral = Color(0xFFE07B75)
val ReddDarkCoralSoft = Color(0x29D4605A)
val ReddDarkElement = Color(0x0AFFFFFF)

// Legacy aliases kept so screen code can refresh without layout changes.
val SlateBlue = ReddMuted
val SlateBlueLight = ReddDarkMuted
val IndigoPrimary = ReddTeal
val IndigoPrimaryLight = ReddDarkTeal
val DarkNavy = ReddNavy
val DarkNavyLight = ReddDarkTextPrimary
val CoolGrey = ReddCream
val SurfaceLight = ReddWarmGrey
val BadgeGreen = ReddTeal
val BadgeGreenBg = ReddTealSoft
val ChipGreen = ReddTealSoft
val ChipGreenText = ReddTealHover
val SoftRed = ReddCoral
val SoftRedBg = ReddCoralSoft
val TextPrimary = ReddNavy
val TextSecondary = ReddBody
val TextHint = ReddSubtle
val DayChipSelected = ReddTeal
val DayChipUnselected = ReddWarmGrey
val DarkBackground = ReddDarkCanvas
val DarkSurface = ReddDarkCard
val DarkSurfaceHigh = ReddDarkWarmGrey