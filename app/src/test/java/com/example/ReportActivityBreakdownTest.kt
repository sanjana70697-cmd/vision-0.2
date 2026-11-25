package com.example

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ActivitySessionEntity
import com.example.data.local.DateKeyHelper
import com.example.data.local.FocusConfigEntity
import com.example.data.local.ProductivitySummary
import com.example.data.local.TaskEntity
import com.example.data.local.VisionDatabase
import com.example.data.local.VisionRepository
import com.example.ui.screens.ReportScreen
import com.example.ui.theme.VisionTab
import com.example.ui.theme.VisionTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReportActivityBreakdownTest {

  @get:Rule
  val composeTestRule = createComposeRule()

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
  fun `report shows only from today records calculates real tasks and updates immediately on edit`() {
    val today = DateKeyHelper.todayKey()
    val yesterday = DateKeyHelper.offsetDateKey(-1)

    runBlocking {
      // Save yesterday activity record (must stay preserved in Room, must NOT appear in Today's Breakdown)
      val yesterdayYogaStart = DateKeyHelper.combineDateKeyAndTime(yesterday, 7, 0)
      repo.saveActivitySession(
        ActivitySessionEntity(
          activityType = "YOGA",
          dateKey = yesterday,
          titleOrSkillName = "Yesterday Secret Flow",
          notes = "Previous day workout",
          startTimeMillis = yesterdayYogaStart,
          endTimeMillis = yesterdayYogaStart + 45 * 60_000L,
          durationMinutes = 45,
          source = "MANUAL",
        ),
      ).getOrThrow()

      // Save today's Focus (25m) and Skill (30m) records; leave Yoga 0m initially
      val todayFocusStart = DateKeyHelper.combineDateKeyAndTime(today, 9, 0)
      repo.saveActivitySession(
        ActivitySessionEntity(
          activityType = "FOCUS",
          dateKey = today,
          titleOrSkillName = "Today Deep Work",
          notes = "",
          startTimeMillis = todayFocusStart,
          endTimeMillis = todayFocusStart + 25 * 60_000L,
          durationMinutes = 25,
          source = "MANUAL",
        ),
      ).getOrThrow()

      val todaySkillStart = DateKeyHelper.combineDateKeyAndTime(today, 11, 0)
      repo.saveActivitySession(
        ActivitySessionEntity(
          activityType = "SKILL",
          dateKey = today,
          titleOrSkillName = "Kotlin Compose",
          notes = "",
          startTimeMillis = todaySkillStart,
          endTimeMillis = todaySkillStart + 30 * 60_000L,
          durationMinutes = 30,
          source = "MANUAL",
        ),
      ).getOrThrow()

      // Save 3 tasks for today (2 completed, 1 pending) and 1 yesterday task
      repo.saveTask(
        TaskEntity(
          title = "Task 1 Done",
          category = "Study",
          scheduledTimeText = "8:00 AM",
          scheduledMinutesOfDay = 480,
          isPriority = true,
          isRoutine = false,
          isCompleted = true,
          dateKey = today,
        ),
      ).getOrThrow()
      repo.saveTask(
        TaskEntity(
          title = "Task 2 Done",
          category = "Study",
          scheduledTimeText = "10:00 AM",
          scheduledMinutesOfDay = 600,
          isPriority = false,
          isRoutine = false,
          isCompleted = true,
          dateKey = today,
        ),
      ).getOrThrow()
      repo.saveTask(
        TaskEntity(
          title = "Task 3 Pending",
          category = "Work",
          scheduledTimeText = "4:00 PM",
          scheduledMinutesOfDay = 960,
          isPriority = false,
          isRoutine = false,
          isCompleted = false,
          dateKey = today,
        ),
      ).getOrThrow()
      repo.saveTask(
        TaskEntity(
          title = "Yesterday Task",
          category = "Study",
          scheduledTimeText = "9:00 AM",
          scheduledMinutesOfDay = 540,
          isPriority = false,
          isRoutine = false,
          isCompleted = false,
          dateKey = yesterday,
        ),
      ).getOrThrow()
    }

    composeTestRule.setContent {
      val scope = rememberCoroutineScope()
      val todayTasks by repo.observeTodayTasks(today).collectAsState(initial = emptyList())
      val pendingTasks by repo.observeAllPendingTasks().collectAsState(initial = emptyList())
      val tomorrowTasks by repo.observeTomorrowTasks().collectAsState(initial = emptyList())
      val todaySessions by repo.observeTodayActivitySessions(today).collectAsState(initial = emptyList())
      val recentSessions by repo.observeRecentActivitySessions().collectAsState(initial = emptyList())
      val summary by repo.observeTodayProductivity(today).collectAsState(
        initial = ProductivitySummary(today, 0, 0, 0, 0, 0, 0, false, 0, false),
      )
      val weekly by repo.observeWeeklySnapshots(7).collectAsState(initial = emptyList())

      VisionTheme(activeTab = VisionTab.Report) {
        ReportScreen(
          todayProductivity = summary,
          weeklySnapshots = weekly,
          todayActivitySessions = todaySessions,
          recentActivitySessions = recentSessions,
          todayTasks = todayTasks,
          pendingTasks = pendingTasks,
          tomorrowTasks = tomorrowTasks,
          focusConfig = FocusConfigEntity(),
          onSaveActivitySession = { session ->
            scope.launch { repo.saveActivitySession(session) }
          },
          onDeleteActivitySession = { id ->
            scope.launch { repo.deleteActivitySession(id) }
          },
          onMovePendingToTomorrow = {},
          onCarryForwardTasks = {},
          onSaveTomorrowTask = {},
          onToggleTask = {},
          onDeleteTask = {},
          onSaveNextDayNote = {},
          onOpenAiSheet = {},
          onOpenMenu = {},
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("report_screen").performScrollToNode(hasTestTag("report_total_tasks"))
    composeTestRule.waitForIdle()

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      runCatching {
        composeTestRule.onNodeWithTag("report_total_tasks").assertTextContains("3")
      }.isSuccess
    }
    composeTestRule.waitForIdle()

    // 1. Actual task totals from today's tasks (3 Total, 2 Completed, 1 Pending)
    composeTestRule.onNodeWithTag("report_total_tasks").assertTextContains("3")
    composeTestRule.onNodeWithTag("report_completed_tasks").assertTextContains("2")
    composeTestRule.onNodeWithTag("report_pending_tasks").assertTextContains("1")

    // 2. "From Today" label & today's Focus (25m), Yoga (0m), Skill Dev (30m) displayed
    composeTestRule.onNodeWithTag("report_screen").performScrollToNode(hasTestTag("today_activity_breakdown_card"))
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("today_activity_breakdown_card").assertIsDisplayed()
    composeTestRule.onNodeWithText("From Today").assertIsDisplayed()
    composeTestRule.onNodeWithTag("today_focus_duration").assertTextContains("25m")
    composeTestRule.onNodeWithTag("today_yoga_duration").assertTextContains("0m")
    composeTestRule.onNodeWithTag("today_skill_duration").assertTextContains("30m")

    // Yesterday's session must NOT leak into Today's Breakdown / Records
    assertTrue(composeTestRule.onAllNodesWithText("Yesterday Secret Flow").fetchSemanticsNodes().isEmpty())

    // All three visible Edit buttons exist
    composeTestRule.onNodeWithTag("edit_today_focus_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("edit_today_yoga_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("edit_today_skill_button").assertIsDisplayed()

    // 3. Tap Yoga Edit button to manually add today's Yoga duration (40m)
    composeTestRule.onNodeWithTag("edit_today_yoga_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("activity_session_editor_sheet").assertIsDisplayed()
    composeTestRule.onNodeWithTag("activity_duration_input").performScrollTo().performTextReplacement("40")
    composeTestRule.onNodeWithTag("save_activity_button").performScrollTo().performClick()

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      runCatching {
        composeTestRule.onNodeWithTag("today_yoga_duration").assertTextContains("40m")
      }.isSuccess
    }
    composeTestRule.waitForIdle()

    // Chart updates immediately: 25m + 40m + 30m = 95m = 1h 35m total
    composeTestRule.onNodeWithTag("report_screen").performScrollToNode(hasTestTag("activity_breakdown_column_chart"))
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("activity_breakdown_column_chart").assertIsDisplayed()
    composeTestRule.onNodeWithTag("activity_chart_total_duration").assertTextContains("1h 35m total")

    // 4. Edit existing Focus session (25m -> 50m) via Focus Edit button
    composeTestRule.onNodeWithTag("report_screen").performScrollToNode(hasTestTag("today_activity_breakdown_card"))
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("edit_today_focus_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("activity_duration_input").performScrollTo().performTextReplacement("50")
    composeTestRule.onNodeWithTag("save_activity_button").performScrollTo().performClick()

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      runCatching {
        composeTestRule.onNodeWithTag("today_focus_duration").assertTextContains("50m")
      }.isSuccess
    }
    composeTestRule.waitForIdle()

    // Chart updates immediately again: 50m + 40m + 30m = 120m = 2h total
    composeTestRule.onNodeWithTag("report_screen").performScrollToNode(hasTestTag("activity_breakdown_column_chart"))
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("activity_chart_total_duration").assertTextContains("2h total")

    // Also verify Saved Activity Records section only shows From Today and not yesterday
    composeTestRule.onNodeWithTag("report_screen").performScrollToNode(hasTestTag("activity_records_card"))
    composeTestRule.waitForIdle()
    assertTrue(composeTestRule.onAllNodesWithText("Yesterday Secret Flow").fetchSemanticsNodes().isEmpty())

    // 5. Verify yesterday's record in Room was preserved and never deleted
    runBlocking {
      val allRecent = repo.observeRecentActivitySessions().first()
      val preservedYesterday = allRecent.firstOrNull { it.dateKey == yesterday }
      assertEquals("Yesterday Secret Flow", preservedYesterday?.titleOrSkillName)
      assertEquals(45, preservedYesterday?.durationMinutes)
    }
  }
}
