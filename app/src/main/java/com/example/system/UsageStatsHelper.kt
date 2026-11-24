package com.example.system

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
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
)

object UsageStatsHelper {

  const val YOUTUBE_PACKAGE = "com.google.android.youtube"

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

  fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expected = ComponentName(context, FocusAccessibilityService::class.java).flattenToString()
    val enabledServices = Settings.Secure.getString(
      context.contentResolver,
      Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ) ?: return false
    return enabledServices.split(":").any {
      it.equals(expected, ignoreCase = true) ||
        it.endsWith("/${FocusAccessibilityService::class.java.name}", ignoreCase = true)
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

  fun openAccessibilitySettings(context: Context) {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
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
    if (!isUsageAccessGranted(context)) return emptyMap()
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

  fun discoverInstalledApps(context: Context): List<InstalledAppCandidate> {
    val pm = context.packageManager
    val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
      addCategory(Intent.CATEGORY_LAUNCHER)
    }
    val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0L))
    } else {
      @Suppress("DEPRECATION")
      pm.queryIntentActivities(launcherIntent, 0)
    }

    return resolved
      .mapNotNull { info ->
        val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
        if (pkg == context.packageName) return@mapNotNull null
        val label = info.loadLabel(pm)?.toString()?.trim().orEmpty()
        if (label.isEmpty()) return@mapNotNull null
        val tag = label.split(" ").filter { it.isNotBlank() }
          .take(2)
          .joinToString("") { it.first().uppercaseChar().toString() }
          .ifEmpty { label.take(2).uppercase() }
        InstalledAppCandidate(pkg, label, tag)
      }
      .distinctBy { it.packageName }
      .sortedBy { it.appName }
  }
}
