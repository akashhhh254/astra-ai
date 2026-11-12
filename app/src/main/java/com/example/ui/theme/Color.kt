package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ===================================================================
// ASTRA AI — STRICT BLACK & WHITE MASTER DESIGN TOKENS
// ===================================================================

// Allowed Dark Palette
val AstraBlack = Color(0xFF000000)
val AstraDarkBlack = Color(0xFF080808)
val AstraDarkSurface = Color(0xFF111111)
val AstraDarkCard = Color(0xFF181818)
val AstraDarkBorder = Color(0xFF303030)
val AstraDarkSecondaryText = Color(0xFFA3A3A3)

// Allowed Light Palette
val AstraWhite = Color(0xFFFFFFFF)
val AstraLightBackground = Color(0xFFFFFFFF)
val AstraLightSurface = Color(0xFFF7F7F7)
val AstraLightCard = Color(0xFFF1F1F1)
val AstraLightBorder = Color(0xFFDDDDDD)
val AstraLightSecondaryText = Color(0xFF666666)

// Neutral Grey Tones
val AstraGreyMutedDark = Color(0xFF555555)
val AstraGreyMutedLight = Color(0xFF888888)

// ===================================================================
// Semantic Compatibility Mappings (Preserves existing code references
// while strictly enforcing 100% black & white aesthetics)
// ===================================================================
val AstraBackgroundDark = AstraBlack
val AstraSurfaceDark = AstraDarkSurface
val AstraSurfaceVariantDark = AstraDarkCard
val AstraCardDark = AstraDarkCard
val AstraCardBorderDark = AstraDarkBorder

// Action & Text colors in Dark Mode
val AstraPrimary = AstraWhite
val AstraPrimaryLight = AstraWhite
val AstraPrimaryDark = AstraWhite
val AstraPrimaryContainer = AstraDarkCard
val AstraPrimaryContainerDark = AstraDarkCard

val AstraBlue = AstraWhite
val AstraBlueLight = AstraWhite
val AstraBlueContainer = AstraDarkCard

val AstraCyan = AstraWhite
val AstraCyanDark = AstraWhite
val AstraCyanContainer = AstraDarkCard
val AstraCyanContainerDark = AstraDarkCard

val AstraMagenta = AstraWhite

val AstraTextPrimary = AstraWhite
val AstraTextSecondary = AstraDarkSecondaryText
val AstraTextMuted = AstraGreyMutedDark

// Light Theme Mappings
val AstraBackgroundLight = AstraLightBackground
val AstraSurfaceLight = AstraLightSurface
val AstraSurfaceVariantLight = AstraLightCard
val AstraCardLight = AstraLightCard
val AstraCardBorderLight = AstraLightBorder
val AstraTextPrimaryLight = AstraBlack
val AstraTextSecondaryLight = AstraLightSecondaryText
val AstraOutlineLight = AstraLightBorder
