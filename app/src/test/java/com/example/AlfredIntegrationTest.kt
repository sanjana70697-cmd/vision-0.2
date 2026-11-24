package com.example

import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AlarmEntity
import com.example.data.local.DateKeyHelper
import com.example.data.local.DistractingAppEntity
import com.example.data.local.FocusConfigEntity
import com.example.data.local.ProductivitySummary
import com.example.data.local.TaskEntity
import com.example.data.local.VisionDatabase
import com.example.data.local.VisionRepository
import com.example.domain.alfred.AlfredAnnouncementEngine
import com.example.domain.alfred.AlfredBrain
import com.example.domain.alfred.AlfredCloudAdvisor
import com.example.domain.alfred.AlfredContextSnapshot
import com.example.domain.alfred.AlfredCoordinator
import com.example.domain.alfred.AlfredRuleEngine
import com.example.system.AlarmReceiver
import com.example.system.AlfredAnnouncementScheduler
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
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AlfredIntegrationTest {

  private lateinit var context: Context
  private lateinit var db: VisionDatabase
  private lateinit var repo: VisionRepository
  private lateinit var coordinator: AlfredCoordinator
  private lateinit var brain: AlfredBrain

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    db = Room.inMemoryDatabaseBuilder(context, VisionDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    repo = VisionRepository(db.visionDao())
    coordinator = AlfredCoordinator(context, repo)
    brain = AlfredBrain(repo, coordinator, AlfredCloudAdvisor(context))
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `cross-tab voice and text commands manage tasks alarms distraction and reports`() = runBlocking {
    // Seed initial distracting app
    repo.upsertDistractingApp(
      DistractingAppEntity(
        packageName = "com.instagram.android",
        appName = "Instagram",
        shortTag = "IG",
        isSelectedForMonitoring = true,
        isBlocked = false,
      ),
    ).getOrThrow()

    // 1. Create Task via ALFRED
    val addResult = brain.handleCommand("Add task Review Operating Systems at 4:30 PM priority")
    assertTrue(addResult.executedActions.any { it.contains("Dashboard") })
    val todayTasks = repo.observeTodayTasks().first()
    val createdTask = todayTasks.firstOrNull { it.title.contains("Review Operating Systems") }
    assertNotNull(createdTask)
    assertTrue(createdTask!!.isPriority)
    assertEquals("4:30 PM", createdTask.scheduledTimeText)

    // 2. Complete Task via ALFRED
    val completeResult = brain.handleCommand("Complete Review Operating Systems")
    assertTrue(completeResult.replyText.contains("1 task"))
    val refreshedTask = repo.observeTodayTasks().first().first { it.id == createdTask.id }
    assertTrue(refreshedTask.isCompleted)

    // 3. Add another task and reschedule to tomorrow
    brain.handleCommand("Add task Prepare Lab Notes at 6:00 PM")
    val rescheduleResult = brain.handleCommand("Move Prepare Lab Notes to tomorrow")
    assertTrue(rescheduleResult.executedActions.any { it.contains("Moved") })
    val tomorrowTasks = repo.observeTomorrowTasks().first()
    assertTrue(tomorrowTasks.any { it.title.contains("Prepare Lab Notes") })

    // 4. Set & Disable Alarm via ALFRED
    val alarmResult = brain.handleCommand("Set alarm for 6:30 AM Morning Wake Up daily")
    assertTrue(alarmResult.executedActions.any { it.contains("Alarm") })
    val alarms = repo.observeAlarms().first()
    val wakeAlarm = alarms.firstOrNull { it.hour24 == 6 && it.minute == 30 }
    assertNotNull(wakeAlarm)
    assertTrue(wakeAlarm!!.enabled)

    brain.handleCommand("Disable alarm Morning Wake Up")
    val disabledAlarm = repo.observeAlarms().first().first { it.id == wakeAlarm.id }
    assertFalse(disabledAlarm.enabled)

    // 5. Block distracting app & start study session
    val blockRes = brain.handleCommand("Block Instagram")
    assertTrue(blockRes.executedActions.any { it.contains("Blocked Instagram") })
    val ig = repo.getDistractingAppsSync().first { it.packageName == "com.instagram.android" }
    assertTrue(ig.isBlocked)
    assertTrue(repo.getFocusConfigSync().focusModeEnabled)

    val studyRes = brain.handleCommand("Start 30m study session")
    assertTrue(studyRes.replyText.contains("30 minutes"))
    assertTrue(repo.getFocusConfigSync().studyModeEnabled)

    // 6. Next-day plan & daily/weekly reports
    val planRes = brain.handleCommand("Plan tomorrow Complete algorithms module first")
    assertTrue(planRes.replyText.contains("Complete algorithms module first"))
    assertEquals("Complete algorithms module first", repo.getFocusConfigSync().nextDayPlanNote)

    repo.syncTodaySnapshot().getOrThrow()
    val dailyReportRes = brain.handleCommand("Read daily report")
    assertTrue(dailyReportRes.replyText.contains("Your daily report is ready"))
    assertTrue(dailyReportRes.replyText.contains("1 of 1 tasks"))

    val weeklyReportRes = brain.handleCommand("Read weekly report")
    assertTrue(weeklyReportRes.replyText.contains("Weekly Report"))
  }

  @Test
  fun `personalized announcements and next task recommendation reflect real room data`() = runBlocking {
    val today = DateKeyHelper.todayKey()
    repo.saveTask(
      TaskEntity(
        title = "Morning Yoga",
        category = "Wellness",
        scheduledTimeText = "7:00 AM",
        scheduledMinutesOfDay = 420,
        isPriority = true,
        isRoutine = true,
        isCompleted = true,
        dateKey = today,
      ),
    ).getOrThrow()
    repo.saveTask(
      TaskEntity(
        title = "Study Data Structures",
        category = "Study",
        scheduledTimeText = "10:00 AM",
        scheduledMinutesOfDay = 600,
        isPriority = true,
        isRoutine = false,
        isCompleted = false,
        dateKey = today,
      ),
    ).getOrThrow()
    repo.saveTask(
      TaskEntity(
        title = "Algorithms Practice",
        category = "Skill Dev",
        scheduledTimeText = "3:00 PM",
        scheduledMinutesOfDay = 900,
        isPriority = false,
        isRoutine = false,
        isCompleted = false,
        dateKey = today,
      ),
    ).getOrThrow()
    repo.saveTask(
      TaskEntity(
        title = "Read Design Notes",
        category = "Study",
        scheduledTimeText = "5:00 PM",
        scheduledMinutesOfDay = 1020,
        isPriority = false,
        isRoutine = false,
        isCompleted = true,
        dateKey = today,
      ),
    ).getOrThrow()
    repo.saveTask(
      TaskEntity(
        title = "Code Review",
        category = "Skill Dev",
        scheduledTimeText = "7:00 PM",
        scheduledMinutesOfDay = 1140,
        isPriority = false,
        isRoutine = false,
        isCompleted = true,
        dateKey = today,
      ),
    ).getOrThrow()

    val snapshot = brain.captureLiveSnapshot()

    // Morning wake-up uses real pending count and first pending task
    val morning = AlfredAnnouncementEngine.buildMorningWakeUpAnnouncement(snapshot)
    assertTrue(morning.message.startsWith("Hello Master, good morning."))
    assertTrue(morning.message.contains("2 pending tasks today"))
    assertTrue(morning.message.contains("Study Data Structures"))

    // Completed milestone reflects 3 completed tasks
    val milestone = AlfredAnnouncementEngine.buildCompletedMilestoneAnnouncement(snapshot.todayProductivity)
    assertTrue(milestone.message.contains("completed 3 tasks today"))
    assertTrue(milestone.message.contains("60%"))

    // Upcoming task announcement
    val upcoming = AlfredAnnouncementEngine.buildUpcomingTaskAnnouncement(snapshot.todayTasks.first { !it.isCompleted })
    assertTrue(upcoming.message.contains("Study Data Structures"))
    assertTrue(upcoming.message.contains("starting soon"))

    // Distraction & Study announcements
    val studyAnn = AlfredAnnouncementEngine.buildStudyStartAnnouncement(25, true)
    assertTrue(studyAnn.message.contains("study session is now starting"))
    val distractAnn = AlfredAnnouncementEngine.buildDistractionWarningAnnouncement("Instagram")
    assertTrue(distractAnn.message.contains("Instagram"))
    assertTrue(distractAnn.message.contains("return to your task"))

    // Report ready announcement
    val reportAnn = AlfredAnnouncementEngine.buildDailyReportReadyAnnouncement(snapshot.todayProductivity)
    assertTrue(reportAnn.message.contains("3 of 5 tasks"))
    assertTrue(reportAnn.message.contains("60%"))

    // Recommendation picks an uncompleted task
    val rec = AlfredAnnouncementEngine.recommendNextTask(snapshot)
    assertNotNull(rec.task)
    assertFalse(rec.task!!.isCompleted)
  }

  @Test
  fun `offline fallback handles open ended questions cleanly without internet`() = runBlocking {
    val snapshot = AlfredContextSnapshot(
      todayTasks = emptyList(),
      tomorrowTasks = emptyList(),
      allPendingTasks = emptyList(),
      goals = emptyList(),
      todayHabits = emptyList(),
      alarms = emptyList<AlarmEntity>(),
      distractingApps = emptyList(),
      focusSessions = emptyList(),
      focusConfig = FocusConfigEntity(),
      todayProductivity = ProductivitySummary(DateKeyHelper.todayKey(), 0, 0, 0, 0, 0, 0, false, 0, false),
      weeklySnapshots = emptyList(),
    )

    val result = AlfredRuleEngine.tryExecute(
      rawCommand = "How can I be more disciplined this week?",
      snapshot = snapshot,
      coordinator = coordinator,
      allowOpenFallback = true,
    )
    assertNotNull(result)
    assertTrue(result!!.replyText.contains("Offline Rule Engine"))
    assertFalse(result.usedCloudAi)
  }

  @Test
  fun `background smart alert actions execute cleanly in AlarmReceiver`() {
    val upcomingIntent = Intent(context, AlarmReceiver::class.java).apply {
      action = AlfredAnnouncementScheduler.ACTION_UPCOMING_TASK_ALERT
      putExtra(AlfredAnnouncementScheduler.EXTRA_TASK_ID, 99L)
    }
    AlarmReceiver().onReceive(context, upcomingIntent)

    val reportIntent = Intent(context, AlarmReceiver::class.java).apply {
      action = AlfredAnnouncementScheduler.ACTION_DAILY_REPORT_ALERT
    }
    AlarmReceiver().onReceive(context, reportIntent)
  }
}
