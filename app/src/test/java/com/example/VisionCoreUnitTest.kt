package com.example

import com.example.data.local.DateKeyHelper
import com.example.data.local.FocusConfigEntity
import com.example.data.local.ProductivitySummary
import com.example.data.local.TaskEntity
import com.example.domain.alfred.AlfredAnnouncementEngine
import com.example.domain.alfred.AlfredContextSnapshot
import com.example.system.StudyDistractionTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisionCoreUnitTest {

  private fun sampleSnapshot(tasks: List<TaskEntity> = emptyList(), focusActive: Boolean = false): AlfredContextSnapshot {
    val today = DateKeyHelper.todayKey()
    return AlfredContextSnapshot(
      todayTasks = tasks,
      tomorrowTasks = emptyList(),
      allPendingTasks = tasks.filter { !it.isCompleted },
      goals = emptyList(),
      todayHabits = emptyList(),
      alarms = emptyList(),
      distractingApps = emptyList(),
      focusSessions = emptyList(),
      focusConfig = FocusConfigEntity(focusModeEnabled = focusActive, activeTimerRunning = focusActive, studyModeEnabled = focusActive),
      todayProductivity = ProductivitySummary(today, tasks.size, tasks.count { it.isCompleted }, tasks.count { !it.isCompleted }, 0, 0, 0, false, 0, false),
      weeklySnapshots = emptyList(),
    )
  }

  @Test
  fun `study distraction tracker alerts on transition and throttles within cooldown`() {
    val tracker = StudyDistractionTracker(cooldownMillis = 120_000L)
    val ownPkg = "com.aistudio.vision.kxmpzq"
    val monitored = setOf("com.google.android.youtube", "com.instagram.android")

    // Staying in VISION never notifies
    val d1 = tracker.onForegroundObserved(ownPkg, ownPkg, monitored, nowMillis = 1_000L)
    assertFalse(d1.shouldNotify)

    // Switching to YouTube triggers notification
    val d2 = tracker.onForegroundObserved(ownPkg, "com.google.android.youtube", monitored, nowMillis = 5_000L)
    assertTrue(d2.shouldNotify)
    assertEquals("com.google.android.youtube", d2.triggeredPackage)

    // Remaining inside YouTube does NOT spam repeatedly
    val d3 = tracker.onForegroundObserved(ownPkg, "com.google.android.youtube", monitored, nowMillis = 7_000L)
    assertFalse(d3.shouldNotify)

    // Switching back to VISION then immediately returning to YouTube within 120s cooldown does NOT spam
    tracker.onForegroundObserved(ownPkg, ownPkg, monitored, nowMillis = 20_000L)
    val d4 = tracker.onForegroundObserved(ownPkg, "com.google.android.youtube", monitored, nowMillis = 40_000L)
    assertFalse(d4.shouldNotify)

    // Switching to another monitored app (Instagram) triggers alert independently
    val d5 = tracker.onForegroundObserved(ownPkg, "com.instagram.android", monitored, nowMillis = 45_000L)
    assertTrue(d5.shouldNotify)
    assertEquals("com.instagram.android", d5.triggeredPackage)

    // Switching back to YouTube AFTER cooldown expires fires again
    val d6 = tracker.onForegroundObserved(ownPkg, "com.google.android.youtube", monitored, nowMillis = 130_000L)
    assertTrue(d6.shouldNotify)
  }

  @Test
  fun `next task recommendation works without priority flags`() {
    val task = TaskEntity(
      id = 7L,
      title = "Practice Graph Theory",
      category = "Study",
      scheduledTimeText = "9:00 AM",
      scheduledMinutesOfDay = 540,
      isPriority = false,
      isRoutine = false,
      isCompleted = false,
      dateKey = DateKeyHelper.todayKey(),
    )
    val summary = AlfredAnnouncementEngine.organizeDaySummary(sampleSnapshot(listOf(task)))
    assertTrue(summary.contains("Practice Graph Theory"))
    assertFalse(summary.contains("[Priority]"))
    assertNull(AlfredAnnouncementEngine.recommendNextTask(sampleSnapshot()).task)
  }

  @Test
  fun `date key tomorrow is strictly after today`() {
    val today = DateKeyHelper.todayKey(1_760_000_000_000L)
    val tomorrow = DateKeyHelper.tomorrowKey(1_760_000_000_000L)
    assertTrue(tomorrow > today)
  }
}
