package com.example.system

import android.app.AppOpsManager
import android.app.NotificationManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import com.example.data.local.DateKeyHelper

data class InstalledAppCandidate(
  val packageName: String,
  val appName: String,
  val shortTag: String,
  val isInstalled: Boolean = false,
)

object UsageStatsHelper {

  const val YOUTUBE_PACKAGE = "com.google.android.youtube"

  private val curatedDistractingCatalog = listOf(
    InstalledAppCandidate("com.google.android.youtube", "YouTube", "YT"),
    InstalledAppCandidate("com.instagram.android", "Instagram", "IG"),
    InstalledAppCandidate("com.zhiliaoapp.musically", "TikTok", "TT"),
    InstalledAppCandidate("com.ss.android.ugc.trill", "TikTok (Lite/Global)", "TT"),
    InstalledAppCandidate("com.twitter.android", "X / Twitter", "X"),
    InstalledAppCandidate("com.facebook.katana", "Facebook", "FB"),
    InstalledAppCandidate("com.reddit.frontpage", "Reddit", "RD"),
    InstalledAppCandidate("com.snapchat.android", "Snapchat", "SC"),
    InstalledAppCandidate("com.discord", "Discord", "DC"),
    InstalledAppCandidate("com.netflix.mediaclient", "Netflix", "NF"),
    InstalledAppCandidate("tv.twitch.android.app", "Twitch", "TW"),
    InstalledAppCandidate("com.pinterest", "Pinterest", "PI"),
  )

  fun isUsageAccessGranted(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      appOps.unsafeCheckOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        Process.myUid(),
        context.packageName,
      )
    } else {
      @Suppress("DEPRECATION")
      appOps.checkOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        Process.myUid(),
        context.packageName,
      )
    }
    return mode == AppOpsManager.MODE_ALLOWED
  }

  fun canUseFullScreenIntent(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
      nm?.canUseFullScreenIntent() ?: true
    } else {
      true
    }
  }

  fun openFullScreenIntentSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
        data = Uri.parse("package:${context.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(intent)
    }
  }

  fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
  }

  fun openUsageAccessSettings(context: Context) {
    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
  }

  fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
        data = Uri.parse("package:${context.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(intent)
    }
  }

  fun openBatteryOptimizationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
  }

  fun queryTodayAppUsageMinutes(
    context: Context,
    targetPackages: Set<String>,
  ): Map<String, Int> {
    if (!isUsageAccessGranted(context) || targetPackages.isEmpty()) return emptyMap()
    val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
      ?: return emptyMap()
    val now = System.currentTimeMillis()
    val startOfDay = DateKeyHelper.startOfTodayMillis(now)
    val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfDay, now)
      ?: return emptyMap()

    val result = mutableMapOf<String, Long>()
    stats.forEach { stat ->
      if (stat.packageName in targetPackages && stat.totalTimeInForeground > 0L) {
        val current = result[stat.packageName] ?: 0L
        if (stat.totalTimeInForeground > current) {
          result[stat.packageName] = stat.totalTimeInForeground
        }
      }
    }
    return result.mapValues { (_, ms) -> (ms / 60_000L).toInt() }
  }

  fun getForegroundPackageName(context: Context): String? {
    if (!isUsageAccessGranted(context)) return null
    val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
      ?: return null
    val now = System.currentTimeMillis()
    val events = usm.queryEvents(now - 60_000L, now) ?: return null
    val event = UsageEvents.Event()
    var lastPkg: String? = null
    while (events.hasNextEvent()) {
      events.getNextEvent(event)
      val isForeground = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
      } else {
        @Suppress("DEPRECATION")
        event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
      }
      if (isForeground) {
        lastPkg = event.packageName
      }
    }
    return lastPkg
  }

  /**
   * Returns curated distracting app candidates, checking ONLY the specific packages declared in
   * AndroidManifest `<queries>` instead of enumerating all installed apps on the user's device.
   */
  fun curatedDistractingApps(context: Context): List<InstalledAppCandidate> {
    val pm = context.packageManager
    return curatedDistractingCatalog.map { candidate ->
      val installed = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          pm.getApplicationInfo(candidate.packageName, PackageManager.ApplicationInfoFlags.of(0L))
        } else {
          @Suppress("DEPRECATION")
          pm.getApplicationInfo(candidate.packageName, 0)
        }
      }.isSuccess
      candidate.copy(isInstalled = installed)
    }.sortedByDescending { it.isInstalled }
  }
}
