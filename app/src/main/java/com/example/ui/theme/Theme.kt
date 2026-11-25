package com.example.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.CheckboxColors
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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

enum class VisionThemeMode(val label: String) {
  SYSTEM("System Auto"),
  LIGHT("Light"),
  DARK("Dark"),
}

object VisionAppearancePrefs {
  private const val PREF_NAME = "vision_appearance_prefs"
  private const val KEY_THEME_MODE = "theme_mode"

  fun getThemeMode(context: Context): VisionThemeMode {
    val raw = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
      .getString(KEY_THEME_MODE, VisionThemeMode.SYSTEM.name)
    return VisionThemeMode.entries.firstOrNull { it.name == raw } ?: VisionThemeMode.SYSTEM
  }

  fun setThemeMode(context: Context, mode: VisionThemeMode) {
    context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
      .edit()
      .putString(KEY_THEME_MODE, mode.name)
      .apply()
  }
}

@Immutable
data class VisionScreenPalette(
  val isDarkMode: Boolean,
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
  val accentOnDarkCard: Color,
  val accentOnPrimaryCard: Color,
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

private val DashboardDarkPalette = VisionScreenPalette(
  isDarkMode = true,
  backgroundTop = OliveMossDeep,
  backgroundBottom = OliveMossMid,
  atmosphericGlow = OliveMossLight.copy(alpha = 0.40f),
  primaryCard = WarmSandstone,
  secondaryCard = WarmSandstoneMuted,
  darkGlassCard = Color(0xFF233320),
  cardBorder = WarmSandstoneBorder,
  darkCardBorder = OliveMossBright.copy(alpha = 0.45f),
  accent = OliveMossAccent,
  accentSoft = OliveMossBright,
  accentOnDarkCard = OliveMossBright,
  accentOnPrimaryCard = OliveMossAccent,
  onBackground = Color(0xFFF6EFE0),
  onBackgroundMuted = Color(0xFFD8CEB8),
  onPrimaryCard = SandstoneInk,
  onPrimaryCardMuted = SandstoneSubtleInk,
  onDarkCard = Color(0xFFF6EFE0),
  onDarkCardMuted = Color(0xFFD5CCB8),
  navBarContainer = Color(0xFF141E13),
  navSelectedContainer = OliveMossAccent.copy(alpha = 0.34f),
  navSelectedContent = Color(0xFFF8F2E4),
  navUnselectedContent = Color(0xFFB3BFA9),
)

private val DashboardLightPalette = VisionScreenPalette(
  isDarkMode = false,
  backgroundTop = DashboardLightBgTop,
  backgroundBottom = DashboardLightBgBottom,
  atmosphericGlow = OliveMossAccent.copy(alpha = 0.14f),
  primaryCard = Color(0xFFFFFFFF),
  secondaryCard = WarmSandstoneMuted,
  darkGlassCard = DashboardLightTintCard,
  cardBorder = WarmSandstoneBorder,
  darkCardBorder = Color(0xFFBFA985),
  accent = OliveMossAccent,
  accentSoft = OliveMossLight,
  accentOnDarkCard = OliveMossAccent,
  accentOnPrimaryCard = OliveMossAccent,
  onBackground = SandstoneInk,
  onBackgroundMuted = SandstoneSubtleInk,
  onPrimaryCard = SandstoneInk,
  onPrimaryCardMuted = SandstoneSubtleInk,
  onDarkCard = SandstoneInk,
  onDarkCardMuted = SandstoneSubtleInk,
  navBarContainer = Color(0xFFFFFFFF),
  navSelectedContainer = OliveMossAccent.copy(alpha = 0.18f),
  navSelectedContent = OliveMossDeep,
  navUnselectedContent = Color(0xFF5D5344),
)

private val AlarmDarkPalette = VisionScreenPalette(
  isDarkMode = true,
  backgroundTop = OliveGroveDeep,
  backgroundBottom = OliveGroveMid,
  atmosphericGlow = OliveGroveLeaf.copy(alpha = 0.38f),
  primaryCard = SmokedMarbleLight,
  secondaryCard = SmokedMarbleVein,
  darkGlassCard = SmokedMarbleDark,
  cardBorder = SmokedMarbleVein,
  darkCardBorder = OliveGroveBright.copy(alpha = 0.42f),
  accent = OliveGroveActive,
  accentSoft = OliveGroveBright,
  accentOnDarkCard = OliveGroveBright,
  accentOnPrimaryCard = OliveGroveActive,
  onBackground = Color(0xFFF2F0EA),
  onBackgroundMuted = Color(0xFFD2CEC3),
  onPrimaryCard = SmokedMarbleInk,
  onPrimaryCardMuted = SmokedMarbleSubtle,
  onDarkCard = Color(0xFFF2F0EA),
  onDarkCardMuted = Color(0xFFCFCBC0),
  navBarContainer = Color(0xFF101810),
  navSelectedContainer = OliveGroveActive.copy(alpha = 0.32f),
  navSelectedContent = Color(0xFFF2F0EA),
  navUnselectedContent = Color(0xFFAEB9AC),
)

private val AlarmLightPalette = VisionScreenPalette(
  isDarkMode = false,
  backgroundTop = AlarmLightBgTop,
  backgroundBottom = AlarmLightBgBottom,
  atmosphericGlow = OliveGroveActive.copy(alpha = 0.12f),
  primaryCard = Color(0xFFFFFFFF),
  secondaryCard = Color(0xFFE3E7DF),
  darkGlassCard = AlarmLightTintCard,
  cardBorder = Color(0xFFC9D1C3),
  darkCardBorder = Color(0xFFB8C3B1),
  accent = OliveGroveActive,
  accentSoft = OliveGroveLeaf,
  accentOnDarkCard = OliveGroveActive,
  accentOnPrimaryCard = OliveGroveActive,
  onBackground = SmokedMarbleInk,
  onBackgroundMuted = SmokedMarbleSubtle,
  onPrimaryCard = SmokedMarbleInk,
  onPrimaryCardMuted = SmokedMarbleSubtle,
  onDarkCard = SmokedMarbleInk,
  onDarkCardMuted = SmokedMarbleSubtle,
  navBarContainer = Color(0xFFFFFFFF),
  navSelectedContainer = OliveGroveActive.copy(alpha = 0.16f),
  navSelectedContent = OliveGroveDeep,
  navUnselectedContent = Color(0xFF555D53),
)

private val DistractionDarkPalette = VisionScreenPalette(
  isDarkMode = true,
  backgroundTop = BordeauxDeep,
  backgroundBottom = BordeauxMid,
  atmosphericGlow = BordeauxRose.copy(alpha = 0.34f),
  primaryCard = TaupeMarble,
  secondaryCard = TaupeMarbleMuted,
  darkGlassCard = BordeauxCard,
  cardBorder = TaupeMarbleMuted,
  darkCardBorder = BordeauxCrimson.copy(alpha = 0.45f),
  accent = BordeauxRose,
  accentSoft = BordeauxCrimson,
  accentOnDarkCard = BordeauxCrimson,
  accentOnPrimaryCard = BordeauxRose,
  onBackground = Color(0xFFF5ECE8),
  onBackgroundMuted = Color(0xFFD9C7C1),
  onPrimaryCard = TaupeInk,
  onPrimaryCardMuted = TaupeSubtleInk,
  onDarkCard = Color(0xFFF5ECE8),
  onDarkCardMuted = Color(0xFFD9C7C1),
  navBarContainer = Color(0xFF1F060D),
  navSelectedContainer = BordeauxRose.copy(alpha = 0.36f),
  navSelectedContent = Color(0xFFF5ECE8),
  navUnselectedContent = Color(0xFFCBB2B7),
)

private val DistractionLightPalette = VisionScreenPalette(
  isDarkMode = false,
  backgroundTop = DistractionLightBgTop,
  backgroundBottom = DistractionLightBgBottom,
  atmosphericGlow = BordeauxRose.copy(alpha = 0.12f),
  primaryCard = Color(0xFFFFFFFF),
  secondaryCard = Color(0xFFEEDFE3),
  darkGlassCard = DistractionLightTintCard,
  cardBorder = Color(0xFFD4BEC5),
  darkCardBorder = Color(0xFFC5AAB3),
  accent = BordeauxRose,
  accentSoft = BordeauxRose,
  accentOnDarkCard = BordeauxRose,
  accentOnPrimaryCard = BordeauxRose,
  onBackground = TaupeInk,
  onBackgroundMuted = TaupeSubtleInk,
  onPrimaryCard = TaupeInk,
  onPrimaryCardMuted = TaupeSubtleInk,
  onDarkCard = TaupeInk,
  onDarkCardMuted = TaupeSubtleInk,
  navBarContainer = Color(0xFFFFFFFF),
  navSelectedContainer = BordeauxRose.copy(alpha = 0.15f),
  navSelectedContent = BordeauxDeep,
  navUnselectedContent = Color(0xFF634A50),
)

private val ReportDarkPalette = VisionScreenPalette(
  isDarkMode = true,
  backgroundTop = DarkChocolateDeep,
  backgroundBottom = DarkChocolateRoast,
  atmosphericGlow = DarkChocolateAccent.copy(alpha = 0.30f),
  primaryCard = CarraraWhite,
  secondaryCard = CarraraVein,
  darkGlassCard = DarkChocolateCard,
  cardBorder = CarraraVein,
  darkCardBorder = DarkChocolateGold.copy(alpha = 0.42f),
  accent = DarkChocolateAccent,
  accentSoft = DarkChocolateGold,
  accentOnDarkCard = DarkChocolateGold,
  accentOnPrimaryCard = DarkChocolateAccent,
  onBackground = CarraraWhite,
  onBackgroundMuted = Color(0xFFD8CCC3),
  onPrimaryCard = CarraraInk,
  onPrimaryCardMuted = CarraraSubtleInk,
  onDarkCard = CarraraWhite,
  onDarkCardMuted = Color(0xFFD8CCC3),
  navBarContainer = Color(0xFF140D0A),
  navSelectedContainer = DarkChocolateAccent.copy(alpha = 0.32f),
  navSelectedContent = CarraraWhite,
  navUnselectedContent = Color(0xFFC0B1A8),
)

private val ReportLightPalette = VisionScreenPalette(
  isDarkMode = false,
  backgroundTop = ReportLightBgTop,
  backgroundBottom = ReportLightBgBottom,
  atmosphericGlow = DarkChocolateAccent.copy(alpha = 0.12f),
  primaryCard = Color(0xFFFFFFFF),
  secondaryCard = CarraraVein,
  darkGlassCard = ReportLightTintCard,
  cardBorder = Color(0xFFD5CAC0),
  darkCardBorder = Color(0xFFC4B6A9),
  accent = DarkChocolateAccent,
  accentSoft = DarkChocolateAccent,
  accentOnDarkCard = DarkChocolateAccent,
  accentOnPrimaryCard = DarkChocolateAccent,
  onBackground = CarraraInk,
  onBackgroundMuted = CarraraSubtleInk,
  onPrimaryCard = CarraraInk,
  onPrimaryCardMuted = CarraraSubtleInk,
  onDarkCard = CarraraInk,
  onDarkCardMuted = CarraraSubtleInk,
  navBarContainer = Color(0xFFFFFFFF),
  navSelectedContainer = DarkChocolateAccent.copy(alpha = 0.16f),
  navSelectedContent = DarkChocolateDeep,
  navUnselectedContent = Color(0xFF63534A),
)

fun paletteFor(tab: VisionTab, isDarkTheme: Boolean = true): VisionScreenPalette = when (tab) {
  VisionTab.Dashboard -> if (isDarkTheme) DashboardDarkPalette else DashboardLightPalette
  VisionTab.Alarm -> if (isDarkTheme) AlarmDarkPalette else AlarmLightPalette
  VisionTab.Distraction -> if (isDarkTheme) DistractionDarkPalette else DistractionLightPalette
  VisionTab.Report -> if (isDarkTheme) ReportDarkPalette else ReportLightPalette
}

private fun colorSchemeFor(palette: VisionScreenPalette): ColorScheme {
  return if (palette.isDarkMode) {
    darkColorScheme(
      primary = palette.accent,
      onPrimary = Color.White,
      secondary = palette.accentSoft,
      onSecondary = Color(0xFF141414),
      background = palette.backgroundTop,
      onBackground = palette.onBackground,
      surface = palette.darkGlassCard,
      onSurface = palette.onDarkCard,
      surfaceVariant = palette.primaryCard,
      onSurfaceVariant = palette.onPrimaryCard,
      outline = palette.cardBorder,
    )
  } else {
    lightColorScheme(
      primary = palette.accent,
      onPrimary = Color.White,
      secondary = palette.accentSoft,
      onSecondary = Color.White,
      background = palette.backgroundTop,
      onBackground = palette.onBackground,
      surface = palette.primaryCard,
      onSurface = palette.onPrimaryCard,
      surfaceVariant = palette.secondaryCard,
      onSurfaceVariant = palette.onPrimaryCardMuted,
      outline = palette.cardBorder,
    )
  }
}

@Composable
fun visionPrimaryCardFieldColors(
  palette: VisionScreenPalette = LocalVisionPalette.current,
): TextFieldColors = OutlinedTextFieldDefaults.colors(
  focusedTextColor = palette.onPrimaryCard,
  unfocusedTextColor = palette.onPrimaryCard,
  focusedLabelColor = palette.accentOnPrimaryCard,
  unfocusedLabelColor = palette.onPrimaryCardMuted,
  focusedPlaceholderColor = palette.onPrimaryCardMuted,
  unfocusedPlaceholderColor = palette.onPrimaryCardMuted,
  focusedBorderColor = palette.accentOnPrimaryCard,
  unfocusedBorderColor = palette.cardBorder,
  cursorColor = palette.accentOnPrimaryCard,
  errorTextColor = palette.onPrimaryCard,
  errorBorderColor = VisionDangerRed,
  errorLabelColor = VisionDangerRed,
)

@Composable
fun visionPrimaryCardCheckboxColors(
  palette: VisionScreenPalette = LocalVisionPalette.current,
): CheckboxColors = CheckboxDefaults.colors(
  checkedColor = palette.accentOnPrimaryCard,
  uncheckedColor = palette.onPrimaryCardMuted,
  checkmarkColor = Color.White,
)

val LocalVisionPalette = staticCompositionLocalOf { DashboardDarkPalette }

@Composable
fun VisionTheme(
  activeTab: VisionTab,
  themeMode: VisionThemeMode = VisionThemeMode.SYSTEM,
  content: @Composable () -> Unit,
) {
  val systemDark = isSystemInDarkTheme()
  val isDark = when (themeMode) {
    VisionThemeMode.SYSTEM -> systemDark
    VisionThemeMode.DARK -> true
    VisionThemeMode.LIGHT -> false
  }
  val palette = paletteFor(activeTab, isDark)
  CompositionLocalProvider(LocalVisionPalette provides palette) {
    MaterialTheme(
      colorScheme = colorSchemeFor(palette),
      typography = VisionTypography,
      content = content,
    )
  }
}
