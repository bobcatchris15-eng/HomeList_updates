package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Warm Light Mode — Scandinavian Household Organizer (Terracotta & Sage)
val LinenCream = Color(0xFFFAF6F0)
val WarmParchment = Color(0xFFF3ECE2)
val ElevatedIvory = Color(0xFFFFFDF9)
val SoftStoneBorder = Color(0xFFE4D9CB)
val WarmSandContainer = Color(0xFFECE2D4)

val TerracottaClay = Color(0xFFC66B4F)
val DeepTerracotta = Color(0xFF9E4A31)
val SoftClayBlush = Color(0xFFF6DDD4)
val OnClayBlush = Color(0xFF5A2414)

val BotanicalSage = Color(0xFF5F8068)
val DeepSage = Color(0xFF3E5B47)
val SoftSageMist = Color(0xFFDDE9DF)
val OnSageMist = Color(0xFF1F3826)

val HoneyAmber = Color(0xFFD48F36)
val SoftAmberGlow = Color(0xFFFBEAD2)
val OnAmberGlow = Color(0xFF553307)

val DeepEspresso = Color(0xFF2B241E)
val WarmStoneInk = Color(0xFF6E6258)
val WarmError = Color(0xFFB8443A)
val WarmErrorContainer = Color(0xFFFADCD8)

// Evening Dark Mode — Cozy Slate & Terracotta
val HearthSlateBg = Color(0xFF1C1917)
val HearthSlateSurface = Color(0xFF24201D)
val HearthSlateCard = Color(0xFF2D2723)
val HearthSlateBorder = Color(0xFF423A34)

val DarkTerracotta = Color(0xFFE08B70)
val DarkTerracottaContainer = Color(0xFF5C2D1F)
val DarkSage = Color(0xFF8BB095)
val DarkSageContainer = Color(0xFF27402F)
val DarkAmber = Color(0xFFE8AE5E)
val DarkAmberContainer = Color(0xFF4F3612)
val CreamInk = Color(0xFFF5EFE6)
val MutedCreamInk = Color(0xFFB9ADA0)

// Curated warm avatar / calendar event colors
val MemberPaletteMap: Map<String, Color> = mapOf(
    "terracotta" to Color(0xFFC66B4F),
    "sage" to Color(0xFF5F8068),
    "amber" to Color(0xFFD48F36),
    "slate_blue" to Color(0xFF5B7B8C),
    "plum" to Color(0xFF8B5E73),
    "olive" to Color(0xFF7D8247)
)

fun memberColorForKey(key: String): Color =
    MemberPaletteMap[key] ?: TerracottaClay
