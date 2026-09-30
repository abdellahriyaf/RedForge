package com.redforge.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// RedForge brand palette — energetic red on near-black, modern gym aesthetic.
val ForgeRed = Color(0xFFE4141B)
val ForgeRedDark = Color(0xFFA50E13)
val ForgeRedBright = Color(0xFFFF3B30)
val ForgeBlack = Color(0xFF0B0B0D)
val ForgeCharcoal = Color(0xFF17171A)
val ForgeSurface = Color(0xFF1F1F23)
val ForgeSurfaceHigh = Color(0xFF2A2A2F)
val ForgeAsh = Color(0xFF8A8A90)
val ForgeWhite = Color(0xFFF5F5F7)
val ForgeGold = Color(0xFFFFB020)
val ForgeGreen = Color(0xFF3DDC84)

// Reserved for intentional hero/splash accents; keep the app body surface neutral.
val ForgeGradientStart = Color(0xFF1B0D0F)
val ForgeGradientMid = Color(0xFF57151A)
val ForgeGradientEnd = Color(0xFF0B0B0D)

val ForgeHeroGradient = Brush.linearGradient(
    colors = listOf(ForgeGradientStart, ForgeGradientMid, ForgeRedDark, ForgeGradientEnd)
)

val ForgeSurfaceGradient = Brush.linearGradient(
    colors = listOf(ForgeSurfaceHigh, ForgeSurface, ForgeCharcoal)
)

val ForgeGlowGradient = Brush.radialGradient(
    colors = listOf(ForgeRed.copy(alpha = 0.12f), Color.Transparent)
)
