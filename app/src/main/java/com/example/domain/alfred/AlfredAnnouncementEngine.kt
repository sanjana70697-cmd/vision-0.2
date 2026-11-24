package com.example.domain.alfred

import com.example.data.local.DateKeyHelper
import com.example.data.local.ProductivitySummary
import com.example.data.local.TaskEntity
import java.util.Calendar

/**
 * Generates contextual announcements and next-task recommendations strictly from real stored Room data.
 */
object AlfredAnnouncementEngine {

  fun recommendNextTask(
    snapshot: AlfredContextSnapshot,
    nowMillis: Long = System.currentTimeMillis(),
  ): NextTaskRecommendation {
    val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val nowMinutesOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    val pendingToday = snapshot.todayTasks.filter { !it.isCompleted }

    if (pendingToday.isEmpty()) {
      val yogaPending = !snapshot.todayProductivity.yogaDoneToday
      val skillLow = snapshot.todayProductivity.skillMinutesToday < 30
      val headline = when {
        yogaPending -> "Complete Morning Yoga & Exercise"
        skillLow -> "Log a 25m Skill Development session"
        snapshot.tomorrowTasks.isNotEmpty() -> "Review tomorrow's ${snapshot.tomorrowTasks.size} planned tasks"
        else -> "All today's tasks complete — review Daily Report"
      }
      val rationale = when {
        snapshot.todayTasks.isEmpty() ->
          "You have no tasks scheduled today yet. Create one or ask ALFRED to add a task."
        else ->
          "All ${snapshot.todayProductivity.completedTasks} scheduled tasks today are completed (${snapshot.todayProductivity.productivityPercent}%). Keep momentum with habits or next-day prep."
      }
      return NextTaskRecommendation(
        task = null,
        headline = headline,
        rationale = rationale,
        suggestedFocusMinutes = 25,
      )
    }

    // Score pending tasks: prefer upcoming/due soonest + priority tasks
    val best = pendingToday.minWithOrNull(
      compareBy<TaskEntity>(
        { task ->
          if (task.scheduledMinutesOfDay < 0) 2000
          else {
            val delta = task.scheduledMinutesOfDay - nowMinutesOfDay
            // Task starting within [-45m, +90m] gets top temporal urgency
            when {
              delta in -45..90 -> kotlin.math.abs(delta)
              delta > 90 -> 200 + delta
              else -> 1200 + kotlin.math.abs(delta)
            }
          }
        },
        { if (it.isPriority) 0 else 1 },
        { it.id },
      ),
    )!!

    val timeTag = if (best.scheduledTimeText.isNotBlank()) " at ${best.scheduledTimeText}" else ""
    val priorityTag = if (best.isPriority) "priority " else ""
    val duration = if (best.category.contains("Study", ignoreCase = true) ||
      best.category.contains("Skill", ignoreCase = true)
    ) 45 else 25

    val rationale = buildString {
      append("Selected ${priorityTag}${best.category} task$timeTag. ")
      append("${snapshot.todayProductivity.completedTasks} of ${snapshot.todayProductivity.totalTasks} tasks complete (${snapshot.todayProductivity.productivityPercent}%).")
    }

    return NextTaskRecommendation(
      task = best,
      headline = "${best.title}$timeTag",
      rationale = rationale,
      suggestedFocusMinutes = duration,
    )
  }

  fun findUpcomingTaskSoon(
    tasks: List<TaskEntity>,
    nowMillis: Long = System.currentTimeMillis(),
    windowMinutes: Int = 30,
  ): TaskEntity? {
    val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val nowMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    return tasks
      .filter { !it.isCompleted && it.scheduledMinutesOfDay >= 0 }
      .firstOrNull { task ->
        val delta = task.scheduledMinutesOfDay - nowMinutes
        delta in 1..windowMinutes
      }
  }

  fun buildMorningWakeUpAnnouncement(
    snapshot: AlfredContextSnapshot,
    nowMillis: Long = System.currentTimeMillis(),
  ): AlfredAnnouncement {
    val pending = snapshot.todayTasks.filter { !it.isCompleted }
    val nextTask = pending.firstOrNull()
    val summary = snapshot.todayProductivity
    val msg = buildString {
      append("Hello Master, good morning. It is time to wake up. ")
      if (snapshot.todayTasks.isEmpty()) {
        append("You have no tasks scheduled yet for today. Ask me to organize your day whenever you are ready.")
      } else if (pending.isEmpty()) {
        append("All ${summary.totalTasks} tasks on today's schedule are already marked done.")
      } else {
        append("You have ${pending.size} pending ${if (pending.size == 1) "task" else "tasks"} today")
        if (nextTask != null) {
          val whenText = if (nextTask.scheduledTimeText.isNotBlank()) " at ${nextTask.scheduledTimeText}" else ""
          append(", starting with ${nextTask.title}$whenText")
        }
        append(".")
      }
    }
    return AlfredAnnouncement(
      type = AnnouncementType.MORNING_WAKEUP,
      title = "ALFRED Morning Briefing",
      message = msg,
      spokenMessage = msg,
      createdAtMillis = nowMillis,
    )
  }

  fun buildUpcomingTaskAnnouncement(
    task: TaskEntity,
    nowMillis: Long = System.currentTimeMillis(),
  ): AlfredAnnouncement {
    val whenText = if (task.scheduledTimeText.isNotBlank()) " at ${task.scheduledTimeText}" else ""
    val msg = "Your next task, ${task.title}$whenText, is starting soon."
    return AlfredAnnouncement(
      type = AnnouncementType.UPCOMING_TASK,
      title = "Upcoming Task · ${task.scheduledTimeText.ifBlank { task.category }}",
      message = msg,
      spokenMessage = msg,
      createdAtMillis = nowMillis,
    )
  }

  fun buildCompletedMilestoneAnnouncement(
    summary: ProductivitySummary,
    nowMillis: Long = System.currentTimeMillis(),
  ): AlfredAnnouncement {
    val count = summary.completedTasks
    val taskWord = if (count == 1) "task" else "tasks"
    val msg = if (count == 0) {
      "You have 0 completed tasks today and ${summary.pendingTasks} remaining."
    } else {
      "You have completed $count $taskWord today. Daily progress is now ${summary.productivityPercent}%."
    }
    return AlfredAnnouncement(
      type = AnnouncementType.MILESTONE_COMPLETED,
      title = "Daily Progress · ${summary.productivityPercent}%",
      message = msg,
      spokenMessage = msg,
      createdAtMillis = nowMillis,
    )
  }

  fun buildStudyStartAnnouncement(
    plannedMinutes: Int,
    studyModeEnabled: Boolean,
    nowMillis: Long = System.currentTimeMillis(),
  ): AlfredAnnouncement {
    val modeClause = if (studyModeEnabled) {
      "Study Mode is enabled, keeping YouTube allowed for learning."
    } else {
      "Focus Shield is blocking selected distracting apps."
    }
    val msg = "Your study session is now starting for $plannedMinutes minutes. $modeClause"
    return AlfredAnnouncement(
      type = AnnouncementType.STUDY_START,
      title = "Study Session Starting · ${plannedMinutes}m",
      message = msg,
      spokenMessage = msg,
      createdAtMillis = nowMillis,
    )
  }

  fun buildDistractionWarningAnnouncement(
    appName: String,
    isStudyModeYouTube: Boolean = false,
    graceSeconds: Int = 30,
    nowMillis: Long = System.currentTimeMillis(),
  ): AlfredAnnouncement {
    val msg = if (isStudyModeYouTube) {
      "You are spending extended time on $appName in Study Mode. Please return to your task within $graceSeconds seconds."
    } else {
      "You are spending time on $appName, a distracting app. Please return to your task."
    }
    return AlfredAnnouncement(
      type = AnnouncementType.DISTRACTION_WARNING,
      title = "ALFRED Focus Alert · $appName",
      message = msg,
      spokenMessage = msg,
      createdAtMillis = nowMillis,
    )
  }

  fun buildDailyReportReadyAnnouncement(
    summary: ProductivitySummary,
    nowMillis: Long = System.currentTimeMillis(),
  ): AlfredAnnouncement {
    val msg = "Your daily report is ready. You completed ${summary.completedTasks} of ${summary.totalTasks} tasks today (${summary.productivityPercent}%) with ${summary.focusMinutesToday} focus minutes."
    return AlfredAnnouncement(
      type = AnnouncementType.DAILY_REPORT_READY,
      title = "Daily Report Ready · ${summary.productivityPercent}%",
      message = msg,
      spokenMessage = msg,
      createdAtMillis = nowMillis,
    )
  }

  fun organizeDaySummary(
    snapshot: AlfredContextSnapshot,
    nowMillis: Long = System.currentTimeMillis(),
  ): String {
    val rec = recommendNextTask(snapshot, nowMillis)
    val nextAlarm = snapshot.alarms.firstOrNull { it.enabled }
    val pending = snapshot.todayTasks.filter { !it.isCompleted }
    return buildString {
      append("Today's Organization (${snapshot.todayProductivity.completedTasks}/${snapshot.todayProductivity.totalTasks} done, ${snapshot.todayProductivity.productivityPercent}%):\n")
      if (pending.isEmpty()) {
        append("• Schedule: All today's tasks are complete.\n")
      } else {
        append("• Suggested Next: ${rec.headline}\n")
        pending.take(4).forEachIndexed { index, task ->
          val t = task.scheduledTimeText.ifBlank { task.category }
          val p = if (task.isPriority) " [Priority]" else ""
          append("${index + 1}. ${task.title} ($t)$p\n")
        }
      }
      if (nextAlarm != null) {
        append("• Next Alarm: ${DateKeyHelper.formatFullTime12h(nextAlarm.hour24, nextAlarm.minute)} (${nextAlarm.label})\n")
      }
      append("• Focus Status: ${if (snapshot.focusConfig.focusModeEnabled) "Active" else "Standby"}, Study Mode ${if (snapshot.focusConfig.studyModeEnabled) "On" else "Off"}.")
    }.trim()
  }
}
