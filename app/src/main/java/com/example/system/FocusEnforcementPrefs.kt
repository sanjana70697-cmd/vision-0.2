package com.example.system

import android.content.Context
import com.example.data.local.DistractingAppEntity
import com.example.data.local.FocusConfigEntity

/**
 * Lightweight SharedPreferences mirror of Focus enforcement state so FocusAccessibilityService
 * can perform non-blocking synchronous checks on window state changes.
 */
object FocusEnforcementPrefs {
  private const val PREF_NAME = "vision_focus_enforcement"
  private const val KEY_FOCUS_ACTIVE = "focus_active"
  private const val KEY_STUDY_MODE = "study_mode"
  private const val KEY_BLOCK_UNTIL = "block_until_millis"
  private const val KEY_BLOCKED_PACKAGES = "blocked_packages"
  private const val KEY_YOUTUBE_RESTRICTED_NOW = "youtube_restricted_now"

  fun syncFromRoom(
    context: Context,
    config: FocusConfigEntity,
    apps: List<DistractingAppEntity>,
  ) {
    val blockedSet = apps.filter { it.isBlocked }.map { it.packageName }.toSet()
    context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
      .edit()
      .putBoolean(KEY_FOCUS_ACTIVE, config.focusModeEnabled || config.activeTimerRunning)
      .putBoolean(KEY_STUDY_MODE, config.studyModeEnabled)
      .putLong(KEY_BLOCK_UNTIL, config.blockUntilMillis)
      .putStringSet(KEY_BLOCKED_PACKAGES, blockedSet)
      .apply()
  }

  fun setYoutubeRestrictedNow(context: Context, restricted: Boolean) {
    context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
      .edit()
      .putBoolean(KEY_YOUTUBE_RESTRICTED_NOW, restricted)
      .apply()
  }

  fun shouldBlockPackage(context: Context, packageName: String): Boolean {
    if (packageName == context.packageName) return false
    val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    val focusActive = prefs.getBoolean(KEY_FOCUS_ACTIVE, false)
    val blockUntil = prefs.getLong(KEY_BLOCK_UNTIL, 0L)
    val timedBlockActive = blockUntil > System.currentTimeMillis()
    if (!focusActive && !timedBlockActive) return false

    val blockedSet = prefs.getStringSet(KEY_BLOCKED_PACKAGES, emptySet()) ?: emptySet()
    if (packageName == UsageStatsHelper.YOUTUBE_PACKAGE) {
      val studyMode = prefs.getBoolean(KEY_STUDY_MODE, true)
      if (studyMode) {
        return prefs.getBoolean(KEY_YOUTUBE_RESTRICTED_NOW, false)
      }
    }
    return packageName in blockedSet
  }
}
