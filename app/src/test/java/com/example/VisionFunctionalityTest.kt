package com.example

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AlarmEntity
import com.example.data.local.DateKeyHelper
import com.example.data.local.DistractingAppEntity
import com.example.data.local.FocusConfigEntity
import com.example.data.local.GoalEntity
import com.example.data.local.TaskEntity
import com.example.data.local.VisionDatabase
import com.example.data.local.VisionRepository
import com.example.system.AlarmReceiver
import com.example.system.AlarmScheduler
import com.example.system.BootCompletedReceiver
import com.example.system.FocusEnforcementPrefs
import com.example.system.UsageStatsHelper
import com.example.system.VisionNotificationHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VisionFunctionalityTest {

  private lateinit var context: Context
  private lateinit var db: VisionDatabase
  private lateinit var repo: VisionRepository

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    db = Room.inMemoryDatabaseBuilder(context, VisionDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    repo = VisionRepository(db.visionDao())
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `task creation editing completion and progress calculation`() = runBlocking {
    val today = DateKeyHelper.todayKey()
    val id1 = repo.saveTask(
      TaskEntity(
        title = "Study Algorithms",
        category = "Skill Dev",
        scheduledTimeText = "9:00 AM",
        scheduledMinutesOfDay = 540,
        isPriority = true,
        isRoutine = false,
        isCompleted = false,
        dateKey = today,
      ),
    ).getOrThrow()

    val id2 = repo.saveTask(
      TaskEntity(
        title = "Morning Yoga",
        category = "Wellness",
        scheduledTimeText = "7:00 AM",
        scheduledMinutesOfDay = 420,
        isPriority = false,
        isRoutine = true,
        isCompleted = false,
        dateKey = today,
      ),
    ).getOrThrow()

    // Edit task 1
    val loaded = repo.observeTodayTasks(today).first()
    val firstTask = loaded.first { it.id == id1 }
    repo.saveTask(firstTask.copy(title = "Study Algorithms & Data Structures")).getOrThrow()

    // Initial progress 0%
    val summaryBefore = repo.observeTodayProductivity(today).first()
    assertEquals(2, summaryBefore.totalTasks)
    assertEquals(0, summaryBefore.completedTasks)
    assertEquals(0, summaryBefore.productivityPercent)

    // Complete 1 of 2 -> 50%
    val editedTask = repo.observeTodayTasks(today).first().first { it.id == id1 }
    assertEquals("Study Algorithms & Data Structures", editedTask.title)
    repo.toggleTaskCompletion(editedTask).getOrThrow()

    val summaryAfter = repo.observeTodayProductivity(today).first()
    assertEquals(1, summaryAfter.completedTasks)
    assertEquals(1, summaryAfter.pendingTasks)
    assertEquals(50, summaryAfter.productivityPercent)

    // Delete task 2 -> 100%
    repo.deleteTask(id2).getOrThrow()
    val summaryDeleted = repo.observeTodayProductivity(today).first()
    assertEquals(1, summaryDeleted.totalTasks)
    assertEquals(100, summaryDeleted.productivityPercent)
  }

  @Test
  fun `goals habits and next day planning persistence`() = runBlocking {
    val goalId = repo.saveGoal(
      GoalEntity(
        title = "Web Dev",
        subtitle = "Modules",
        currentValue = 2,
        targetValue = 5,
        unit = "modules",
        category = "Skill Development",
      ),
    ).getOrThrow()

    val goal = repo.observeGoals().first().first { it.id == goalId }
    repo.incrementGoal(goal, 1).getOrThrow()
    val updatedGoal = repo.observeGoals().first().first { it.id == goalId }
    assertEquals(3, updatedGoal.currentValue)

    repo.logHabitProgress("YOGA_EXERCISE", "Morning Yoga", 30, true).getOrThrow()
    val summary = repo.observeTodayProductivity().first()
    assertTrue(summary.yogaDoneToday)
    assertEquals(30, summary.yogaMinutesToday)
  }

  @Test
  fun `multiple alarms schedule and cancel in AlarmManager`() = runBlocking {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val shadowAlarmManager = shadowOf(alarmManager)

    val a1 = AlarmEntity(
      id = 101L,
      hour24 = 6,
      minute = 0,
      label = "Wake Up",
      kind = "ALARM",
      isWakeUp = true,
      repeatDaysCsv = "1,2,3,4,5",
      enabled = true,
    )
    val a2 = AlarmEntity(
      id = 102L,
      hour24 = 10,
      minute = 30,
      label = "Study Reminder",
      kind = "REMINDER",
      isWakeUp = false,
      repeatDaysCsv = "1,2,3,4,5,6,7",
      enabled = true,
    )

    val t1 = AlarmScheduler.schedule(context, a1)
    val t2 = AlarmScheduler.schedule(context, a2)
    assertTrue(t1 > System.currentTimeMillis())
    assertTrue(t2 > System.currentTimeMillis())
    assertTrue(shadowAlarmManager.scheduledAlarms.size >= 2)

    AlarmScheduler.cancel(context, 101L)
    AlarmScheduler.cancel(context, 102L)
  }

  @Test
  fun `notification channels and alarm receiver trigger handling`() {
    VisionNotificationHelper.ensureChannels(context)
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      assertNotNull(nm.getNotificationChannel(VisionNotificationHelper.CHANNEL_ALARMS))
      assertNotNull(nm.getNotificationChannel(VisionNotificationHelper.CHANNEL_REMINDERS))
      assertNotNull(nm.getNotificationChannel(VisionNotificationHelper.CHANNEL_FOCUS))
      assertNotNull(nm.getNotificationChannel(VisionNotificationHelper.CHANNEL_WARNINGS))
    }

    val notification = VisionNotificationHelper.buildAlarmRingingNotification(
      context,
      alarmId = 1L,
      label = "Wake Up Alarm",
      timeLabel = "6:00 AM",
    )
    assertNotNull(notification)

    // Verify BootCompletedReceiver handles ACTION_BOOT_COMPLETED without crashing
    val bootIntent = Intent(Intent.ACTION_BOOT_COMPLETED)
    BootCompletedReceiver().onReceive(context, bootIntent)

    // Verify AlarmReceiver handles dismiss intent cleanly
    val dismissIntent = Intent(context, AlarmReceiver::class.java).apply {
      action = AlarmReceiver.ACTION_DISMISS_ALARM
      putExtra(AlarmReceiver.EXTRA_ALARM_ID, 1L)
    }
    AlarmReceiver().onReceive(context, dismissIntent)
  }

  @Test
  fun `focus enforcement and study mode youtube allowance vs restriction`() {
    val config = FocusConfigEntity(
      focusModeEnabled = true,
      studyModeEnabled = true,
      blockDurationMinutes = 60,
      blockUntilMillis = System.currentTimeMillis() + 60 * 60_000L,
    )
    val apps = listOf(
      DistractingAppEntity(
        packageName = UsageStatsHelper.YOUTUBE_PACKAGE,
        appName = "YouTube",
        shortTag = "YT",
        isSelectedForMonitoring = true,
        isBlocked = true,
        allowEducational = true,
      ),
      DistractingAppEntity(
        packageName = "com.instagram.android",
        appName = "Instagram",
        shortTag = "IG",
        isSelectedForMonitoring = true,
        isBlocked = true,
        allowEducational = false,
      ),
    )

    FocusEnforcementPrefs.syncFromRoom(context, config, apps)
    FocusEnforcementPrefs.setYoutubeRestrictedNow(context, false)

    // Instagram should be blocked during Focus Mode
    assertTrue(FocusEnforcementPrefs.shouldBlockPackage(context, "com.instagram.android"))

    // YouTube should be ALLOWED while Study Mode is active and grace period hasn't expired
    assertFalse(FocusEnforcementPrefs.shouldBlockPackage(context, UsageStatsHelper.YOUTUBE_PACKAGE))

    // Once grace period expires, Study Mode applies restriction to YouTube
    FocusEnforcementPrefs.setYoutubeRestrictedNow(context, true)
    assertTrue(FocusEnforcementPrefs.shouldBlockPackage(context, UsageStatsHelper.YOUTUBE_PACKAGE))
  }

  @Test
  fun `weekly snapshots persist real daily analytics`() = runBlocking {
    val today = DateKeyHelper.todayKey()
    repo.saveTask(
      TaskEntity(
        title = "Complete Lab",
        category = "Study",
        scheduledTimeText = "2:00 PM",
        scheduledMinutesOfDay = 840,
        isPriority = true,
        isRoutine = false,
        isCompleted = true,
        dateKey = today,
      ),
    ).getOrThrow()

    repo.syncTodaySnapshot(today).getOrThrow()
    val weekly = repo.observeWeeklySnapshots(7).first()
    assertEquals(7, weekly.size)
    val todaySnapshot = weekly.last().second
    assertNotNull(todaySnapshot)
    assertEquals(1, todaySnapshot?.completedTasks)
    assertEquals(100, todaySnapshot?.productivityPercent)
  }
}
