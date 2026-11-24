package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class VisionTab(val label: String, val themeName: String) {
  Dashboard("Dashboard", "Olive Moss + Warm Sandstone"),
  Alarm("Alarm", "Olive Grove + Smoked Marble"),
  Distraction("Distraction", "Bordeaux + Taupe Marble"),
  Report("Report", "Dark Chocolate + Carrara White"),
}

@Immutable
data class VisionScreenPalette(
  val backgroundTop: Color,
  val backgroundBottom: Color,
  val atmosphericGlow: Color,
  val primaryCard: Color,
  val secondaryCard: Color,
  val darkGlassCard: Color,
  val cardBorder: Color,
  val darkCardBorder: Color,
  val accent: Color,
  val accentSoft: Color,
  val onBackground: Color,
  val onBackgroundMuted: Color,
  val onPrimaryCard: Color,
  val onPrimaryCardMuted: Color,
  val onDarkCard: Color,
  val onDarkCardMuted: Color,
  val navBarContainer: Color,
  val navSelectedContainer: Color,
  val navSelectedContent: Color,
  val navUnselectedContent: Color,
)

private val DashboardPalette = VisionScreenPalette(
  backgroundTop = OliveMossDeep,
  backgroundBottom = OliveMossMid,
  atmosphericGlow = OliveMossLight.copy(alpha = 0.45f),
  primaryCard = WarmSandstone,
  secondaryCard = WarmSandstoneMuted,
  darkGlassCard = OliveMossMid.copy(alpha = 0.85f),
  cardBorder = WarmSandstoneBorder,
  darkCardBorder = OliveMossAccent.copy(alpha = 0.45f),
  accent = OliveMossAccent,
  accentSoft = OliveMossLight,
  onBackground = WarmSandstone,
  onBackgroundMuted = WarmSandstoneMuted,
  onPrimaryCard = SandstoneInk,
  onPrimaryCardMuted = SandstoneSubtleInk,
  onDarkCard = WarmSandstone,
  onDarkCardMuted = WarmSandstoneMuted,
  navBarContainer = Color(0xFF141E13),
  navSelectedContainer = OliveMossAccent.copy(alpha = 0.28f),
  navSelectedContent = WarmSandstone,
  navUnselectedContent = Color(0xFFA7B39F),
)

private val AlarmPalette = VisionScreenPalette(
  backgroundTop = OliveGroveDeep,
  backgroundBottom = OliveGroveMid,
  atmosphericGlow = OliveGroveLeaf.copy(alpha = 0.42f),
  primaryCard = SmokedMarbleLight,
  secondaryCard = SmokedMarbleVein,
  darkGlassCard = SmokedMarbleDark.copy(alpha = 0.88f),
  cardBorder = SmokedMarbleVein,
  darkCardBorder = OliveGroveLeaf.copy(alpha = 0.50f),
  accent = OliveGroveActive,
  accentSoft = OliveGroveLeaf,
  onBackground = SmokedMarbleLight,
  onBackgroundMuted = SmokedMarbleVein,
  onPrimaryCard = SmokedMarbleInk,
  onPrimaryCardMuted = SmokedMarbleSubtle,
  onDarkCard = SmokedMarbleLight,
  onDarkCardMuted = SmokedMarbleVein,
  navBarContainer = Color(0xFF101810),
  navSelectedContainer = OliveGroveActive.copy(alpha = 0.28f),
  navSelectedContent = SmokedMarbleLight,
  navUnselectedContent = Color(0xFFA3AEA1),
)

private val DistractionPalette = VisionScreenPalette(
  backgroundTop = BordeauxDeep,
  backgroundBottom = BordeauxMid,
  atmosphericGlow = BordeauxRose.copy(alpha = 0.36f),
  primaryCard = TaupeMarble,
  secondaryCard = TaupeMarbleMuted,
  darkGlassCard = BordeauxCard.copy(alpha = 0.90f),
  cardBorder = TaupeMarbleMuted,
  darkCardBorder = BordeauxRose.copy(alpha = 0.45f),
  accent = BordeauxRose,
  accentSoft = BordeauxCrimson,
  onBackground = TaupeMarble,
  onBackgroundMuted = TaupeMarbleMuted,
  onPrimaryCard = TaupeInk,
  onPrimaryCardMuted = TaupeSubtleInk,
  onDarkCard = TaupeMarble,
  onDarkCardMuted = TaupeMarbleMuted,
  navBarContainer = Color(0xFF1F060D),
  navSelectedContainer = BordeauxRose.copy(alpha = 0.34f),
  navSelectedContent = TaupeMarble,
  navUnselectedContent = Color(0xFFC2A6AC),
)

private val ReportPalette = VisionScreenPalette(
  backgroundTop = DarkChocolateDeep,
  backgroundBottom = DarkChocolateRoast,
  atmosphericGlow = DarkChocolateAccent.copy(alpha = 0.32f),
  primaryCard = CarraraWhite,
  secondaryCard = CarraraVein,
  darkGlassCard = DarkChocolateCard.copy(alpha = 0.92f),
  cardBorder = CarraraVein,
  darkCardBorder = DarkChocolateAccent.copy(alpha = 0.45f),
  accent = DarkChocolateAccent,
  accentSoft = DarkChocolateGold,
  onBackground = CarraraWhite,
  onBackgroundMuted = CarraraVein,
  onPrimaryCard = CarraraInk,
  onPrimaryCardMuted = CarraraSubtleInk,
  onDarkCard = CarraraWhite,
  onDarkCardMuted = CarraraVein,
  navBarContainer = Color(0xFF140D0A),
  navSelectedContainer = DarkChocolateAccent.copy(alpha = 0.30f),
  navSelectedContent = CarraraWhite,
  navUnselectedContent = Color(0xFFB5A79E),
)

fun paletteFor(tab: VisionTab): VisionScreenPalette = when (tab) {
  VisionTab.Dashboard -> DashboardPalette
  VisionTab.Alarm -> AlarmPalette
  VisionTab.Distraction -> DistractionPalette
  VisionTab.Report -> ReportPalette
}

private fun colorSchemeFor(palette: VisionScreenPalette): ColorScheme = darkColorScheme(
  primary = palette.accent,
  onPrimary = palette.onDarkCard,
  secondary = palette.accentSoft,
  onSecondary = palette.onDarkCard,
  background = palette.backgroundTop,
  onBackground = palette.onBackground,
  surface = palette.darkGlassCard,
  onSurface = palette.onDarkCard,
  surfaceVariant = palette.primaryCard,
  onSurfaceVariant = palette.onPrimaryCard,
  outline = palette.cardBorder,
)

val LocalVisionPalette = staticCompositionLocalOf { DashboardPalette }

@Composable
fun VisionTheme(
  activeTab: VisionTab,
  content: @Composable () -> Unit,
) {
  val palette = paletteFor(activeTab)
  CompositionLocalProvider(LocalVisionPalette provides palette) {
    MaterialTheme(
      colorScheme = colorSchemeFor(palette),
      typography = VisionTypography,
      content = content,
    )
  }
}
