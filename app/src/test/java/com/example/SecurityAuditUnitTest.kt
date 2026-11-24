package com.example

import com.example.data.local.AlarmEntity
import com.example.data.local.DateKeyHelper
import com.example.data.local.ProductivitySummary
import com.example.data.local.TaskEntity
import com.example.domain.alfred.AlfredAnnouncementEngine
import com.example.domain.alfred.AlfredContextSnapshot
import com.example.system.AlarmScheduler
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityAuditUnitTest {

  private fun locateManifest(): File {
    val candidates = listOf(
      File("src/main/AndroidManifest.xml"),
      File("app/src/main/AndroidManifest.xml"),
    )
    return candidates.firstOrNull { it.exists() }
      ?: error("AndroidManifest.xml not found in expected working directories")
  }

  @Test
  fun `manifest omits prohibited permissions accessibility service and wildcard queries`() {
    val manifest = locateManifest().readText()

    val forbiddenTokens = listOf(
      "READ_CONTACTS",
      "READ_SMS",
      "READ_CALL_LOG",
      "ACCESS_FINE_LOCATION",
      "ACCESS_COARSE_LOCATION",
      "MANAGE_EXTERNAL_STORAGE",
      "QUERY_ALL_PACKAGES",
      "SYSTEM_ALERT_WINDOW",
      "REQUEST_INSTALL_PACKAGES",
      "BIND_ACCESSIBILITY_SERVICE",
      "FocusAccessibilityService",
      "FOREGROUND_SERVICE_DATA_SYNC",
      "android.intent.category.LAUNCHER\"\n        </intent>",
    )
    forbiddenTokens.forEach { token ->
      assertFalse("Unexpected sensitive token in manifest: $token", manifest.contains(token))
    }

    assertTrue(manifest.contains("android:allowBackup=\"false\""))
    assertTrue(manifest.contains("android:usesCleartextTraffic=\"false\""))
    assertTrue(manifest.contains("android:networkSecurityConfig=\"@xml/network_security_config\""))
    assertTrue(manifest.contains("FOREGROUND_SERVICE_SPECIAL_USE"))
    assertTrue(manifest.contains("PROPERTY_SPECIAL_USE_FGS_SUBTYPE"))
  }

  @Test
  fun `alarm trigger calculation advances into future safely`() {
    val now = 1_760_000_000_000L
    val alarm = AlarmEntity(
      id = 1L,
      hour24 = 6,
      minute = 30,
      label = "Morning Wake-Up",
      kind = "ALARM",
      isWakeUp = true,
      repeatDaysCsv = "1,2,3,4,5",
      enabled = true,
    )
    val next = AlarmScheduler.computeNextTriggerMillis(alarm, fromMillis = now)
    assertTrue("Next alarm trigger should be strictly in future", next > now)
  }

  @Test
  fun `alfred recommendation operates strictly on local room snapshot`() {
    val today = DateKeyHelper.todayKey()
    val task = TaskEntity(
      id = 10L,
      title = "Deep Work Session",
      category = "Study",
      scheduledTimeText = "9:00 AM",
      scheduledMinutesOfDay = 540,
      isPriority = true,
      isRoutine = false,
      isCompleted = false,
      dateKey = today,
    )
    val snapshot = AlfredContextSnapshot(
      todayTasks = listOf(task),
      tomorrowTasks = emptyList(),
      allPendingTasks = listOf(task),
      goals = emptyList(),
      todayHabits = emptyList(),
      alarms = emptyList(),
      distractingApps = emptyList(),
      focusSessions = emptyList(),
      focusConfig = com.example.data.local.FocusConfigEntity(),
      todayProductivity = ProductivitySummary(today, 1, 0, 1, 0, 0, 0, false, 0, false),
      weeklySnapshots = emptyList(),
    )
    val rec = AlfredAnnouncementEngine.recommendNextTask(snapshot)
    assertTrue(rec.headline.contains("Deep Work Session"))
  }
}
