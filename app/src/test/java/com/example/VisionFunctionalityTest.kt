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

  @Test
  fun `alarm validation rejects blank labels and saves non-blank reminders`() = runBlocking {
    val blankResult = repo.saveAlarm(
      AlarmEntity(
        hour24 = 8,
        minute = 15,
        label = "   ",
        kind = "REMINDER",
        isWakeUp = false,
        repeatDaysCsv = "",
        enabled = true,
      ),
    )
    assertTrue(blankResult.isFailure)

    val validId = repo.saveAlarm(
      AlarmEntity(
        hour24 = 8,
        minute = 15,
        label = "Revise System Design",
        kind = "REMINDER",
        isWakeUp = false,
        repeatDaysCsv = "",
        enabled = true,
      ),
    ).getOrThrow()

    val saved = repo.getAlarmById(validId)
    assertNotNull(saved)
    assertEquals("Revise System Design", saved?.label)
    assertEquals("REMINDER", saved?.kind)
  }

  @Test
  fun `carry forward moves pending tasks cleanly and prevents duplicates`() = runBlocking {
    val today = DateKeyHelper.todayKey()
    val tomorrow = DateKeyHelper.tomorrowKey()

    val t1 = repo.saveTask(
      TaskEntity(
        title = "Finish DBMS Lab",
        category = "Study",
        scheduledTimeText = "5:00 PM",
        scheduledMinutesOfDay = 1020,
        isPriority = false,
        isRoutine = false,
        isCompleted = false,
        dateKey = today,
      ),
    ).getOrThrow()
    repo.saveTask(
      TaskEntity(
        title = "Read Kotlin Docs",
        category = "Skill Dev",
        scheduledTimeText = "10:00 AM",
        scheduledMinutesOfDay = 600,
        isPriority = false,
        isRoutine = false,
        isCompleted = false,
        dateKey = tomorrow,
      ),
    ).getOrThrow()
    val dupToday = repo.saveTask(
      TaskEntity(
        title = "Read Kotlin Docs",
        category = "Skill Dev",
        scheduledTimeText = "10:00 AM",
        scheduledMinutesOfDay = 600,
        isPriority = false,
        isRoutine = false,
        isCompleted = false,
        dateKey = today,
      ),
    ).getOrThrow()

    val todayList = repo.observeTodayTasks(today).first()
    val outcome = repo.carryForwardTasksToTomorrow(todayList).getOrThrow()
    assertEquals(1, outcome.movedCount)
    assertEquals(1, outcome.skippedDuplicatesCount)
    assertTrue(outcome.movedTitles.contains("Finish DBMS Lab"))

    val tomorrowList = repo.observeTomorrowTasks(tomorrow).first()
    assertEquals(2, tomorrowList.size)
    assertTrue(tomorrowList.any { it.id == t1 })
    // dupToday stayed untouched in today
    assertTrue(repo.observeTodayTasks(today).first().any { it.id == dupToday })
  }

  @Test
  fun `habit notes persist across updates`() = runBlocking {
    repo.logHabitProgress(
      trackType = "SKILL_DEV",
      defaultLabel = "Skill Development",
      addMinutes = 30,
      markCompleted = false,
      updatedNotes = "Completed Compose Navigation codelab",
    ).getOrThrow()

    val habits = repo.observeTodayHabits().first()
    val skill = habits.firstOrNull { it.trackType == "SKILL_DEV" }
    assertNotNull(skill)
    assertEquals(30, skill?.minutesLogged)
    assertEquals("Completed Compose Navigation codelab", skill?.notes)
    assertFalse(skill?.completed ?: true)
  }

  @Test
  fun `activity sessions enforce validation reject overlaps and recalculate totals`() = runBlocking {
    val today = DateKeyHelper.todayKey()
    val start1 = DateKeyHelper.combineDateKeyAndTime(today, 9, 0)
    val end1 = start1 + 45 * 60_000L

    // Blank skill title is rejected
    val blankSkillResult = repo.saveActivitySession(
      com.example.data.local.ActivitySessionEntity(
        activityType = "SKILL",
        dateKey = today,
        titleOrSkillName = "   ",
        notes = "",
        startTimeMillis = start1,
        endTimeMillis = end1,
        durationMinutes = 45,
        source = "MANUAL",
      ),
    )
    assertTrue(blankSkillResult.isFailure)

    // Invalid duration (0 or >720) is rejected
    val zeroDurResult = repo.saveActivitySession(
      com.example.data.local.ActivitySessionEntity(
        activityType = "YOGA",
        dateKey = today,
        titleOrSkillName = "Morning Yoga",
        notes = "",
        startTimeMillis = start1,
        endTimeMillis = end1,
        durationMinutes = 0,
        source = "MANUAL",
      ),
    )
    assertTrue(zeroDurResult.isFailure)

    // Valid skill session saves and updates summary
    val skillId = repo.saveActivitySession(
      com.example.data.local.ActivitySessionEntity(
        activityType = "SKILL",
        dateKey = today,
        titleOrSkillName = "Python",
        notes = "Built async scrapers",
        startTimeMillis = start1,
        endTimeMillis = end1,
        durationMinutes = 45,
        source = "MANUAL",
      ),
    ).getOrThrow()

    // Overlapping SKILL session in the same window is rejected
    val overlapResult = repo.saveActivitySession(
      com.example.data.local.ActivitySessionEntity(
        activityType = "SKILL",
        dateKey = today,
        titleOrSkillName = "Kotlin",
        notes = "",
        startTimeMillis = start1 + 10 * 60_000L,
        endTimeMillis = end1 + 10 * 60_000L,
        durationMinutes = 45,
        source = "MANUAL",
      ),
    )
    assertTrue(overlapResult.isFailure)

    // Non-overlapping YOGA session succeeds
    val yogaStart = DateKeyHelper.combineDateKeyAndTime(today, 7, 0)
    val yogaId = repo.saveActivitySession(
      com.example.data.local.ActivitySessionEntity(
        activityType = "YOGA",
        dateKey = today,
        titleOrSkillName = "Sun Salutation",
        notes = "30m flow",
        startTimeMillis = yogaStart,
        endTimeMillis = yogaStart + 30 * 60_000L,
        durationMinutes = 30,
        source = "TIMER",
      ),
    ).getOrThrow()

    val summary1 = repo.observeTodayProductivity(today).first()
    assertEquals(45, summary1.skillMinutesToday)
    assertEquals(30, summary1.yogaMinutesToday)
    assertTrue(summary1.skillDoneToday)
    assertTrue(summary1.yogaDoneToday)

    // Delete skill session recalculates total back to 0m and updates snapshot
    repo.deleteActivitySession(skillId).getOrThrow()
    val summary2 = repo.observeTodayProductivity(today).first()
    assertEquals(0, summary2.skillMinutesToday)
    assertEquals(30, summary2.yogaMinutesToday)

    val weekly = repo.observeWeeklySnapshots(7).first()
    val snap = weekly.last().second
    assertEquals(0, snap?.skillMinutes)
    assertTrue(snap?.yogaDone == true)

    repo.deleteActivitySession(yogaId).getOrThrow()
    assertEquals(0, repo.observeTodayProductivity(today).first().yogaMinutesToday)
  }

  @Test
  fun `active timer store excludes paused time and survives simulated restart`() {
    val t0 = 1_750_000_000_000L
    com.example.system.ActiveTimerStore.clear(context, com.example.system.TimerActivityKind.SKILL)

    com.example.system.ActiveTimerStore.start(
      context = context,
      kind = com.example.system.TimerActivityKind.SKILL,
      titleOrSkillName = "Kotlin",
      notes = "Coroutines",
      plannedMinutes = 60,
      nowMillis = t0,
    )

    // Run for 5 minutes (300s), then pause at t0 + 300s
    val tPause = t0 + 300_000L
    com.example.system.ActiveTimerStore.pause(context, com.example.system.TimerActivityKind.SKILL, nowMillis = tPause)

    // Idle while paused for 10 minutes (600s) -> elapsed must stay 300s
    val tResume = tPause + 600_000L
    val pausedState = com.example.system.ActiveTimerStore.get(context, com.example.system.TimerActivityKind.SKILL)
    assertTrue(pausedState.isActive)
    assertFalse(pausedState.isRunning)
    assertEquals(300L, pausedState.elapsedSeconds(tResume))

    // Resume and run another 3 minutes (180s) -> total active = 480s = 8 minutes
    com.example.system.ActiveTimerStore.resume(context, com.example.system.TimerActivityKind.SKILL, nowMillis = tResume)
    val tFinish = tResume + 180_000L

    // Re-read from SharedPreferences as if app restarted cleanly
    val restored = com.example.system.ActiveTimerStore.get(context, com.example.system.TimerActivityKind.SKILL)
    assertTrue(restored.isRunning)
    assertEquals("Kotlin", restored.titleOrSkillName)
    assertEquals(480L, restored.elapsedSeconds(tFinish))

    val completed = com.example.system.ActiveTimerStore.finishAndClear(
      context,
      com.example.system.TimerActivityKind.SKILL,
      nowMillis = tFinish,
    )
    assertNotNull(completed)
    assertEquals(480L, completed?.activeSeconds)
    assertEquals(8, completed?.durationMinutes)
    assertFalse(com.example.system.ActiveTimerStore.get(context, com.example.system.TimerActivityKind.SKILL).isActive)
  }

  @Test
  fun `date specific reminder triggers on exact scheduledDateKey`() = runBlocking {
    val tomorrow = DateKeyHelper.tomorrowKey()
    val alarmId = repo.saveAlarm(
      AlarmEntity(
        hour24 = 15,
        minute = 45,
        label = "Submit Lab Report",
        kind = "REMINDER",
        isWakeUp = false,
        repeatDaysCsv = "",
        scheduledDateKey = tomorrow,
        enabled = true,
      ),
    ).getOrThrow()

    val saved = repo.getAlarmById(alarmId)
    assertNotNull(saved)
    assertEquals(tomorrow, saved?.scheduledDateKey)

    val triggerMillis = AlarmScheduler.computeNextTriggerMillis(saved!!)
    val expectedMillis = DateKeyHelper.combineDateKeyAndTime(tomorrow, 15, 45)
    assertEquals(expectedMillis, triggerMillis)
  }

  @Test
  fun `persistent alfred chat conversations support append switch and delete`() = runBlocking {
    repo.clearAllChatHistory().getOrThrow()
    repo.appendChatMessage("conv_1", isUser = true, text = "Add task Deep Work", sourceLabel = "You").getOrThrow()
    repo.appendChatMessage("conv_1", isUser = false, text = "Added Deep Work", sourceLabel = "ALFRED", executedActions = listOf("Dashboard")).getOrThrow()
    repo.appendChatMessage("conv_2", isUser = true, text = "Read daily report", sourceLabel = "You").getOrThrow()

    val convIds = repo.observeConversationIds().first()
    assertTrue(convIds.contains("conv_1"))
    assertTrue(convIds.contains("conv_2"))
    assertEquals(2, repo.observeChatMessages("conv_1").first().size)
    assertEquals(1, repo.observeChatMessages("conv_2").first().size)

    repo.deleteConversation("conv_1").getOrThrow()
    assertTrue(repo.observeChatMessages("conv_1").first().isEmpty())
    assertEquals(1, repo.observeChatMessages("conv_2").first().size)

    repo.clearAllChatHistory().getOrThrow()
    assertTrue(repo.observeConversationIds().first().isEmpty())
  }

  @Test
  fun `migration 1 to 2 preserves existing v1 rows and backfills activity sessions`() {
    val dbName = "migration_test_vision_${System.currentTimeMillis()}.db"
    context.deleteDatabase(dbName)

    // Create a genuine Room v1 SQLite database matching exact v1 entity schemas
    val v1Helper = object : android.database.sqlite.SQLiteOpenHelper(context, dbName, null, 1) {
      override fun onCreate(sqDb: android.database.sqlite.SQLiteDatabase) {
        sqDb.execSQL(
          "CREATE TABLE tasks (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `category` TEXT NOT NULL, `scheduledTimeText` TEXT NOT NULL, `scheduledMinutesOfDay` INTEGER NOT NULL, `isPriority` INTEGER NOT NULL, `isRoutine` INTEGER NOT NULL, `isCompleted` INTEGER NOT NULL, `dateKey` TEXT NOT NULL, `completedAtMillis` INTEGER, `createdAtMillis` INTEGER NOT NULL)",
        )
        sqDb.execSQL(
          "CREATE TABLE goals (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `subtitle` TEXT NOT NULL, `currentValue` INTEGER NOT NULL, `targetValue` INTEGER NOT NULL, `unit` TEXT NOT NULL, `category` TEXT NOT NULL, `createdAtMillis` INTEGER NOT NULL)",
        )
        sqDb.execSQL(
          "CREATE TABLE habit_logs (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `dateKey` TEXT NOT NULL, `trackType` TEXT NOT NULL, `label` TEXT NOT NULL, `minutesLogged` INTEGER NOT NULL, `completed` INTEGER NOT NULL, `notes` TEXT NOT NULL)",
        )
        sqDb.execSQL(
          "CREATE TABLE alarms (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `hour24` INTEGER NOT NULL, `minute` INTEGER NOT NULL, `label` TEXT NOT NULL, `kind` TEXT NOT NULL, `isWakeUp` INTEGER NOT NULL, `repeatDaysCsv` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `soundEnabled` INTEGER NOT NULL, `vibrateEnabled` INTEGER NOT NULL, `aiBriefingEnabled` INTEGER NOT NULL, `snoozeMinutes` INTEGER NOT NULL, `nextTriggerMillis` INTEGER NOT NULL)",
        )
        sqDb.execSQL(
          "CREATE TABLE distracting_apps (`packageName` TEXT NOT NULL, `appName` TEXT NOT NULL, `shortTag` TEXT NOT NULL, `isSelectedForMonitoring` INTEGER NOT NULL, `isBlocked` INTEGER NOT NULL, `allowEducational` INTEGER NOT NULL, `dailyLimitMinutes` INTEGER NOT NULL, `todayUsageMinutes` INTEGER NOT NULL, `lastSyncedMillis` INTEGER NOT NULL, PRIMARY KEY(`packageName`))",
        )
        sqDb.execSQL(
          "CREATE TABLE focus_sessions (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `dateKey` TEXT NOT NULL, `startTimeMillis` INTEGER NOT NULL, `endTimeMillis` INTEGER, `plannedMinutes` INTEGER NOT NULL, `actualMinutes` INTEGER NOT NULL, `studyModeEnabled` INTEGER NOT NULL, `blockedAttemptsCount` INTEGER NOT NULL, `warningsTriggeredCount` INTEGER NOT NULL, `status` TEXT NOT NULL)",
        )
        sqDb.execSQL(
          "CREATE TABLE focus_config (`id` INTEGER NOT NULL, `focusModeEnabled` INTEGER NOT NULL, `studyModeEnabled` INTEGER NOT NULL, `blockDurationMinutes` INTEGER NOT NULL, `blockUntilMillis` INTEGER NOT NULL, `gracePeriodSeconds` INTEGER NOT NULL, `autoBlockOnSchedule` INTEGER NOT NULL, `educationalYoutubeFilter` INTEGER NOT NULL, `strictLockMode` INTEGER NOT NULL, `gentleEscalatingChime` INTEGER NOT NULL, `aiMorningBriefing` INTEGER NOT NULL, `focusSafeSnoozeLimit` INTEGER NOT NULL, `nextDayPlanNote` TEXT NOT NULL, `activeTimerRemainingSeconds` INTEGER NOT NULL, `activeTimerRunning` INTEGER NOT NULL, `activeSessionId` INTEGER NOT NULL, `studyWarningActive` INTEGER NOT NULL, `studyWarningMessage` TEXT NOT NULL, `studyGraceRemainingSeconds` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        sqDb.execSQL(
          "CREATE TABLE daily_productivity_snapshots (`dateKey` TEXT NOT NULL, `totalTasks` INTEGER NOT NULL, `completedTasks` INTEGER NOT NULL, `pendingTasks` INTEGER NOT NULL, `productivityPercent` INTEGER NOT NULL, `focusMinutes` INTEGER NOT NULL, `yogaDone` INTEGER NOT NULL, `skillMinutes` INTEGER NOT NULL, `updatedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`dateKey`))",
        )

        val today = DateKeyHelper.todayKey()
        sqDb.execSQL("INSERT INTO tasks (title, category, scheduledTimeText, scheduledMinutesOfDay, isPriority, isRoutine, isCompleted, dateKey, completedAtMillis, createdAtMillis) VALUES ('Preserved Task', 'Study', '9:00 AM', 540, 1, 0, 1, '$today', 1750000000000, 1750000000000)")
        sqDb.execSQL("INSERT INTO alarms (hour24, minute, label, kind, isWakeUp, repeatDaysCsv, enabled, soundEnabled, vibrateEnabled, aiBriefingEnabled, snoozeMinutes, nextTriggerMillis) VALUES (6, 30, 'Preserved Alarm', 'ALARM', 1, '1,2,3,4,5', 1, 1, 1, 1, 5, 0)")
        sqDb.execSQL("INSERT INTO habit_logs (dateKey, trackType, label, minutesLogged, completed, notes) VALUES ('$today', 'SKILL_DEV', 'Kotlin Practice', 40, 1, 'Old note')")
        sqDb.execSQL("INSERT INTO focus_sessions (dateKey, startTimeMillis, endTimeMillis, plannedMinutes, actualMinutes, studyModeEnabled, blockedAttemptsCount, warningsTriggeredCount, status) VALUES ('$today', 1750000000000, 1750001500000, 25, 25, 1, 0, 0, 'COMPLETED')")
      }

      override fun onUpgrade(db: android.database.sqlite.SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
    v1Helper.writableDatabase.close()

    val migratedDb = Room.databaseBuilder(context, VisionDatabase::class.java, dbName)
      .addMigrations(VisionDatabase.MIGRATION_1_2)
      .allowMainThreadQueries()
      .build()

    runBlocking {
      val today = DateKeyHelper.todayKey()
      val dao = migratedDb.visionDao()
      val preservedTasks = dao.getTasksForDateSync(today)
      assertEquals(1, preservedTasks.size)
      assertEquals("Preserved Task", preservedTasks.first().title)

      val preservedAlarms = dao.observeAlarms().first()
      assertEquals(1, preservedAlarms.size)
      assertEquals("", preservedAlarms.first().scheduledDateKey)

      val migratedSessions = dao.observeActivitySessionsForDate(today).first()
      assertTrue(migratedSessions.any { it.activityType == "SKILL" && it.durationMinutes == 40 })
      assertTrue(migratedSessions.any { it.activityType == "FOCUS" && it.durationMinutes == 25 })
    }
    migratedDb.close()
    context.deleteDatabase(dbName)
  }
}
