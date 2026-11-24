package com.example.domain.alfred

import com.example.data.local.AlarmEntity
import com.example.data.local.DateKeyHelper
import com.example.data.local.TaskEntity
import com.example.system.UsageStatsHelper
import com.example.ui.theme.VisionTab
import java.util.Locale

/**
 * Deterministic offline rule-based engine for ALFRED.
 * Executes cross-tab actions cleanly through the single shared [AlfredCoordinator] without needing internet.
 */
object AlfredRuleEngine {

  private val timeRegex = Regex("""\b(\d{1,2})(?::(\d{2}))?\s*(am|pm)?\b""", RegexOption.IGNORE_CASE)
  private val minutesRegex = Regex("""\b(\d{1,3})\s*(?:min|mins|minute|minutes|m)\b""", RegexOption.IGNORE_CASE)

  private val startSessionRegex = Regex(
    """\b(start|begin)\s+(?:a\s+|an\s+)?(?:\d{1,3}\s*(?:min|mins|minute|minutes|m)\s+)?(focus|study)\b""",
    RegexOption.IGNORE_CASE,
  )

  /**
   * Attempts deterministic execution of [rawCommand].
   * Returns null only when [allowOpenFallback] is false and the input looks like an open-ended
   * planning question better handled by Cloud Gemini when online.
   */
  suspend fun tryExecute(
    rawCommand: String,
    snapshot: AlfredContextSnapshot,
    coordinator: AlfredCoordinator,
    allowOpenFallback: Boolean = true,
  ): AlfredExecutionResult? {
    val cmd = rawCommand.trim()
    if (cmd.isEmpty()) return null
    val lower = cmd.lowercase(Locale.US)

    // 1. Morning / Greeting / Briefing
    if (
      lower.contains("good morning") ||
      lower.contains("wake up briefing") ||
      lower.contains("morning briefing") ||
      lower == "hello alfred" ||
      lower == "hi alfred"
    ) {
      val announcement = AlfredAnnouncementEngine.buildMorningWakeUpAnnouncement(snapshot)
      return AlfredExecutionResult(
        replyText = announcement.message,
        spokenText = announcement.spokenMessage,
        executedActions = listOf("Dashboard · Generated live morning briefing"),
        suggestedTab = VisionTab.Dashboard,
      )
    }

    // 2. Organize Day / Next Task Suggestions
    if (
      lower.contains("next task") ||
      lower.contains("what should i do") ||
      lower.contains("suggest task") ||
      lower.contains("recommend")
    ) {
      val rec = AlfredAnnouncementEngine.recommendNextTask(snapshot)
      val text = "Next recommended step: ${rec.headline}. ${rec.rationale}"
      return AlfredExecutionResult(
        replyText = text,
        spokenText = text,
        executedActions = listOf("Dashboard · Analyzed schedule & priorities"),
        suggestedTab = VisionTab.Dashboard,
      )
    }

    if (
      lower.contains("organize my day") ||
      lower.contains("plan my day") ||
      lower.contains("my schedule") ||
      lower.contains("brief me") ||
      lower.contains("status")
    ) {
      val summary = AlfredAnnouncementEngine.organizeDaySummary(snapshot)
      val rec = AlfredAnnouncementEngine.recommendNextTask(snapshot)
      val spoken = "You have completed ${snapshot.todayProductivity.completedTasks} of ${snapshot.todayProductivity.totalTasks} tasks today (${snapshot.todayProductivity.productivityPercent}%). Your next recommended step is ${rec.headline}."
      return AlfredExecutionResult(
        replyText = summary,
        spokenText = spoken,
        executedActions = listOf("All Tabs · Read live tasks, alarms & focus state"),
        suggestedTab = VisionTab.Dashboard,
      )
    }

    // 3. Reports (Daily / Weekly / Next-day plan)
    if (lower.contains("weekly report") || lower.contains("week report") || lower.contains("weekly productivity")) {
      val weeklyTotal = snapshot.weeklySnapshots.sumOf { it.second?.totalTasks ?: 0 }
      val weeklyDone = snapshot.weeklySnapshots.sumOf { it.second?.completedTasks ?: 0 }
      val weeklyPct = if (weeklyTotal > 0) (weeklyDone * 100) / weeklyTotal else 0
      val weeklyFocus = snapshot.weeklySnapshots.sumOf { it.second?.focusMinutes ?: 0 }
      val reply = "Weekly Report: $weeklyDone of $weeklyTotal tasks completed ($weeklyPct% productivity) and $weeklyFocus focus minutes across logged days."
      return AlfredExecutionResult(
        replyText = reply,
        spokenText = reply,
        executedActions = listOf("Report · Read 7-day Room productivity snapshots"),
        suggestedTab = VisionTab.Report,
      )
    }

    if (lower.contains("daily report") || lower.contains("report") || lower.contains("productivity") || lower.contains("progress")) {
      val announcement = AlfredAnnouncementEngine.buildDailyReportReadyAnnouncement(snapshot.todayProductivity)
      return AlfredExecutionResult(
        replyText = announcement.message,
        spokenText = announcement.spokenMessage,
        executedActions = listOf("Report · Calculated live daily productivity metrics"),
        suggestedTab = VisionTab.Report,
      )
    }

    if (lower.startsWith("plan tomorrow") || lower.startsWith("next day plan") || lower.startsWith("note for tomorrow")) {
      val note = cmd
        .replace(Regex("""^(plan tomorrow|next day plan|note for tomorrow)\s*[:,-]?\s*""", RegexOption.IGNORE_CASE), "")
        .trim()
      if (note.isNotBlank()) {
        coordinator.updateNextDayPlanNote(note)
        val reply = "Saved tomorrow's plan note: \"$note\"."
        return AlfredExecutionResult(
          replyText = reply,
          spokenText = reply,
          executedActions = listOf("Report · Updated next-day planning note"),
          suggestedTab = VisionTab.Report,
        )
      }
    }

    // 4. Focus / Study Mode / App Blocking
    val startMatch = startSessionRegex.find(lower)
    if (
      startMatch != null &&
      !lower.startsWith("add ") &&
      !lower.startsWith("create ") &&
      !lower.startsWith("new ") &&
      !lower.startsWith("schedule ")
    ) {
      val mins = extractMinutes(lower) ?: 25
      val enableStudy = lower.contains("study")
      if (enableStudy) coordinator.setStudyMode(true)
      coordinator.setFocusMode(true)
      coordinator.startFocusTimer(mins)
      val studyOn = enableStudy || snapshot.focusConfig.studyModeEnabled
      val announcement = AlfredAnnouncementEngine.buildStudyStartAnnouncement(mins, studyOn)
      return AlfredExecutionResult(
        replyText = announcement.message,
        spokenText = announcement.spokenMessage,
        executedActions = listOf("Distraction · Started ${mins}m timer & Focus Shield"),
        suggestedTab = VisionTab.Distraction,
      )
    }

    if (lower.contains("pause focus") || lower.contains("pause timer") || lower.contains("pause study")) {
      coordinator.pauseFocusTimer()
      val reply = "Focus timer paused."
      return AlfredExecutionResult(
        replyText = reply,
        spokenText = reply,
        executedActions = listOf("Distraction · Paused active focus timer"),
        suggestedTab = VisionTab.Distraction,
      )
    }

    if (
      lower.contains("end focus") ||
      lower.contains("stop focus") ||
      lower.contains("reset focus") ||
      lower.contains("end study") ||
      lower.contains("stop study") ||
      lower.contains("turn off focus") ||
      lower.contains("disable focus")
    ) {
      coordinator.resetFocusTimer(25)
      coordinator.setFocusMode(false)
      val reply = "Focus session ended and Focus Mode turned off."
      return AlfredExecutionResult(
        replyText = reply,
        spokenText = reply,
        executedActions = listOf("Distraction · Stopped focus timer & disabled Focus Mode"),
        suggestedTab = VisionTab.Distraction,
      )
    }

    if (lower.contains("turn on focus") || lower.contains("enable focus")) {
      coordinator.setFocusMode(true)
      val reply = "Focus Mode enabled for ${snapshot.focusConfig.blockDurationMinutes} minutes."
      return AlfredExecutionResult(
        replyText = reply,
        spokenText = reply,
        executedActions = listOf("Distraction · Enabled Focus Mode"),
        suggestedTab = VisionTab.Distraction,
      )
    }

    if (lower.contains("enable study mode") || lower.contains("turn on study mode")) {
      coordinator.setStudyMode(true)
      val reply = "Study Mode enabled. YouTube stays allowed for educational learning with grace-period monitoring."
      return AlfredExecutionResult(
        replyText = reply,
        spokenText = reply,
        executedActions = listOf("Distraction · Enabled Study Mode"),
        suggestedTab = VisionTab.Distraction,
      )
    }

    if (lower.contains("disable study mode") || lower.contains("turn off study mode")) {
      coordinator.setStudyMode(false)
      val reply = "Study Mode disabled."
      return AlfredExecutionResult(
        replyText = reply,
        spokenText = reply,
        executedActions = listOf("Distraction · Disabled Study Mode"),
        suggestedTab = VisionTab.Distraction,
      )
    }

    if (lower.startsWith("unblock ") || lower.startsWith("allow ")) {
      val target = cmd.substringAfter(" ").trim()
      val matched = snapshot.distractingApps.firstOrNull {
        it.appName.contains(target, ignoreCase = true) ||
          it.shortTag.equals(target, ignoreCase = true)
      }
      if (matched != null) {
        if (matched.isBlocked) coordinator.toggleAppBlock(matched)
        val reply = "${matched.appName} is now unblocked."
        return AlfredExecutionResult(
          replyText = reply,
          spokenText = reply,
          executedActions = listOf("Distraction · Unblocked ${matched.appName}"),
          suggestedTab = VisionTab.Distraction,
        )
      }
    }

    if (lower.startsWith("block ")) {
      val durationMatch = extractMinutes(lower)
      if (lower.contains("block for ") && durationMatch != null) {
        coordinator.setBlockDuration(durationMatch)
        coordinator.setFocusMode(true)
        val reply = "Focus blocking activated for $durationMatch minutes."
        return AlfredExecutionResult(
          replyText = reply,
          spokenText = reply,
          executedActions = listOf("Distraction · Armed Focus blocking for ${durationMatch}m"),
          suggestedTab = VisionTab.Distraction,
        )
      }
      val target = cmd.substringAfter(" ").trim()
      val matched = snapshot.distractingApps.firstOrNull {
        it.appName.contains(target, ignoreCase = true) ||
          it.shortTag.equals(target, ignoreCase = true)
      }
      if (matched != null) {
        if (!matched.isBlocked) coordinator.toggleAppBlock(matched)
        coordinator.setFocusMode(true)
        val reply = "${matched.appName} is now blocked and Focus Mode is active."
        return AlfredExecutionResult(
          replyText = reply,
          spokenText = reply,
          executedActions = listOf("Distraction · Blocked ${matched.appName} & enabled Focus Mode"),
          suggestedTab = VisionTab.Distraction,
        )
      }
    }

    // 5. Alarms & Reminders
    if (
      (lower.contains("set ") || lower.contains("add ") || lower.contains("create ") || lower.contains("wake me")) &&
      (lower.contains("alarm") || lower.contains("reminder") || lower.contains("wake"))
    ) {
      val parsedTime = parseHourMinute24(lower)
      if (parsedTime != null) {
        val (hour24, minute) = parsedTime
        val isReminder = lower.contains("reminder") || lower.contains("remind me")
        val kind = if (isReminder) "REMINDER" else "ALARM"
        val cleanLabel = extractLabelAfterTimeOrKeyword(cmd, isReminder)
        val newAlarm = AlarmEntity(
          hour24 = hour24,
          minute = minute,
          label = cleanLabel,
          kind = kind,
          isWakeUp = kind == "ALARM" && (lower.contains("wake") || hour24 in 4..9),
          repeatDaysCsv = if (lower.contains("daily") || lower.contains("every day")) "1,2,3,4,5,6,7" else "",
          enabled = true,
        )
        coordinator.saveAndScheduleAlarm(newAlarm)
        val timeFmt = DateKeyHelper.formatFullTime12h(hour24, minute)
        val reply = "${if (isReminder) "Reminder" else "Alarm"} set for $timeFmt: \"$cleanLabel\"."
        return AlfredExecutionResult(
          replyText = reply,
          spokenText = reply,
          executedActions = listOf("Alarm · Scheduled $kind for $timeFmt"),
          suggestedTab = VisionTab.Alarm,
        )
      }
    }

    if (lower.startsWith("disable alarm") || lower.startsWith("turn off alarm") || lower.startsWith("cancel alarm")) {
      val query = cmd.replace(Regex("""^(disable|turn off|cancel)\s+alarms?\s*""", RegexOption.IGNORE_CASE), "").trim()
      val targets = if (query.isBlank() || query.equals("all", ignoreCase = true)) {
        snapshot.alarms.filter { it.enabled }
      } else {
        snapshot.alarms.filter {
          it.enabled && (it.label.contains(query, ignoreCase = true) ||
            DateKeyHelper.formatFullTime12h(it.hour24, it.minute).contains(query, ignoreCase = true))
        }
      }
      if (targets.isNotEmpty()) {
        targets.forEach { coordinator.toggleAlarmEnabled(it, false) }
        val reply = "Disabled ${targets.size} ${if (targets.size == 1) "alarm" else "alarms"}."
        return AlfredExecutionResult(
          replyText = reply,
          spokenText = reply,
          executedActions = listOf("Alarm · Disabled ${targets.size} alarm(s)"),
          suggestedTab = VisionTab.Alarm,
        )
      }
    }

    if (lower.startsWith("enable alarm") || lower.startsWith("turn on alarm")) {
      val query = cmd.replace(Regex("""^(enable|turn on)\s+alarms?\s*""", RegexOption.IGNORE_CASE), "").trim()
      val targets = if (query.isBlank()) {
        snapshot.alarms.filter { !it.enabled }.take(1)
      } else {
        snapshot.alarms.filter {
          !it.enabled && (it.label.contains(query, ignoreCase = true) ||
            DateKeyHelper.formatFullTime12h(it.hour24, it.minute).contains(query, ignoreCase = true))
        }
      }
      if (targets.isNotEmpty()) {
        targets.forEach { coordinator.toggleAlarmEnabled(it, true) }
        val reply = "Enabled ${targets.size} ${if (targets.size == 1) "alarm" else "alarms"}."
        return AlfredExecutionResult(
          replyText = reply,
          spokenText = reply,
          executedActions = listOf("Alarm · Armed ${targets.size} alarm(s)"),
          suggestedTab = VisionTab.Alarm,
        )
      }
    }

    if (lower.startsWith("delete alarm") || lower.startsWith("remove alarm")) {
      val query = cmd.replace(Regex("""^(delete|remove)\s+alarm\s*""", RegexOption.IGNORE_CASE), "").trim()
      val target = snapshot.alarms.firstOrNull {
        query.isNotBlank() && (it.label.contains(query, ignoreCase = true) ||
          DateKeyHelper.formatFullTime12h(it.hour24, it.minute).contains(query, ignoreCase = true))
      }
      if (target != null) {
        coordinator.deleteAndCancelAlarm(target.id)
        val reply = "Deleted alarm \"${target.label}\"."
        return AlfredExecutionResult(
          replyText = reply,
          spokenText = reply,
          executedActions = listOf("Alarm · Deleted ${target.label}"),
          suggestedTab = VisionTab.Alarm,
        )
      }
    }

    // 6. Habit Logging (Yoga / Skill)
    if (lower.contains("log yoga") || lower.contains("yoga done") || lower.contains("complete yoga")) {
      val mins = extractMinutes(lower) ?: 20
      coordinator.logHabit("YOGA_EXERCISE", "Morning Yoga & Exercise", mins, true)
      val reply = "Logged $mins minutes of Morning Yoga & Exercise."
      return AlfredExecutionResult(
        replyText = reply,
        spokenText = reply,
        executedActions = listOf("Dashboard · Logged +${mins}m Yoga & Exercise"),
        suggestedTab = VisionTab.Dashboard,
      )
    }

    if (lower.contains("log skill") || lower.contains("skill done")) {
      val mins = extractMinutes(lower) ?: 30
      coordinator.logHabit("SKILL_DEV", "Skill Development", mins, true)
      val reply = "Logged $mins minutes of Skill Development."
      return AlfredExecutionResult(
        replyText = reply,
        spokenText = reply,
        executedActions = listOf("Dashboard · Logged +${mins}m Skill Development"),
        suggestedTab = VisionTab.Dashboard,
      )
    }

    // 7. Task Creation, Completion, Rescheduling, Deletion
    if (
      lower.startsWith("add task") ||
      lower.startsWith("create task") ||
      lower.startsWith("new task") ||
      lower.startsWith("schedule task") ||
      lower.startsWith("remind me to ")
    ) {
      val parsedTime = parseHourMinute24(lower)
      val isTomorrow = lower.contains("tomorrow")
      val isPriority = lower.contains("priority") || lower.contains("urgent") || lower.contains("important")
      val isRoutine = lower.contains("routine") || lower.contains("daily")
      var title = cmd
        .replace(Regex("""^(add task|create task|new task|schedule task|remind me to)\s*[:,-]?\s*""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\b(for|at)\s+\d{1,2}(:\d{2})?\s*(am|pm)?\b""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\b(tomorrow|today|priority|urgent|important|as a routine|routine)\b""", RegexOption.IGNORE_CASE), "")
        .trim()
      if (title.isBlank()) title = "New Focus Task"

      val minsOfDay = parsedTime?.let { it.first * 60 + it.second } ?: -1
      val timeText = parsedTime?.let { DateKeyHelper.formatFullTime12h(it.first, it.second) } ?: ""
      val dateKey = if (isTomorrow) DateKeyHelper.tomorrowKey() else DateKeyHelper.todayKey()
      val category = inferCategory(title)

      val entity = TaskEntity(
        title = title,
        category = category,
        scheduledTimeText = timeText,
        scheduledMinutesOfDay = minsOfDay,
        isPriority = isPriority,
        isRoutine = isRoutine,
        isCompleted = false,
        dateKey = dateKey,
      )
      coordinator.createOrUpdateTask(entity)
      val whenStr = buildString {
        if (isTomorrow) append(" for tomorrow")
        if (timeText.isNotBlank()) append(" at $timeText")
      }
      val reply = "Created task \"$title\"$whenStr in ${if (isTomorrow) "Tomorrow's Plan" else "Today's Schedule"}."
      return AlfredExecutionResult(
        replyText = reply,
        spokenText = reply,
        executedActions = listOf("${if (isTomorrow) "Report" else "Dashboard"} · Created task \"$title\""),
        suggestedTab = if (isTomorrow) VisionTab.Report else VisionTab.Dashboard,
      )
    }

    if (
      lower.startsWith("complete ") ||
      lower.startsWith("finish ") ||
      lower.startsWith("mark ") && (lower.contains("done") || lower.contains("complete"))
    ) {
      val query = cmd
        .replace(Regex("""^(complete|finish|mark)\s+(task\s+)?""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s*(as\s+)?(done|completed|complete)$""", RegexOption.IGNORE_CASE), "")
        .trim()

      val pending = snapshot.todayTasks.filter { !it.isCompleted }
      val target = when {
        query.isBlank() || query.equals("next", ignoreCase = true) || query.equals("next task", ignoreCase = true) ->
          AlfredAnnouncementEngine.recommendNextTask(snapshot).task ?: pending.firstOrNull()
        else -> pending.firstOrNull { it.title.contains(query, ignoreCase = true) }
      }
      if (target != null) {
        coordinator.toggleTaskCompleted(target)
        val doneCount = snapshot.todayProductivity.completedTasks + 1
        val totalCount = snapshot.todayProductivity.totalTasks.coerceAtLeast(doneCount)
        val pct = (doneCount * 100) / totalCount
        val reply = "Marked \"${target.title}\" complete. You have completed $doneCount ${if (doneCount == 1) "task" else "tasks"} today ($pct%)."
        return AlfredExecutionResult(
          replyText = reply,
          spokenText = reply,
          executedActions = listOf("Dashboard · Completed \"${target.title}\""),
          suggestedTab = VisionTab.Dashboard,
        )
      }
    }

    if (lower.startsWith("reschedule ") || lower.startsWith("move ")) {
      val cleaned = cmd
        .replace(Regex("""^(reschedule|move)\s+(task\s+)?""", RegexOption.IGNORE_CASE), "")
        .trim()
      val toTomorrow = lower.contains("tomorrow")
      val newTime = parseHourMinute24(lower)
      val query = cleaned
        .replace(Regex("""\bto\s+tomorrow\b""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\b(to|at|for)\s+\d{1,2}(:\d{2})?\s*(am|pm)?\b""", RegexOption.IGNORE_CASE), "")
        .trim()

      val target = snapshot.allPendingTasks.firstOrNull {
        query.isNotBlank() && it.title.contains(query, ignoreCase = true)
      } ?: snapshot.todayTasks.firstOrNull { !it.isCompleted }

      if (target != null) {
        if (toTomorrow && newTime == null) {
          coordinator.scheduleTaskForTomorrow(target)
          val reply = "Rescheduled \"${target.title}\" to tomorrow."
          return AlfredExecutionResult(
            replyText = reply,
            spokenText = reply,
            executedActions = listOf("Dashboard/Report · Moved \"${target.title}\" to tomorrow"),
            suggestedTab = VisionTab.Report,
          )
        } else {
          val updatedDateKey = if (toTomorrow) DateKeyHelper.tomorrowKey() else target.dateKey
          val updatedMins = newTime?.let { it.first * 60 + it.second } ?: target.scheduledMinutesOfDay
          val updatedTimeText = newTime?.let { DateKeyHelper.formatFullTime12h(it.first, it.second) } ?: target.scheduledTimeText
          coordinator.createOrUpdateTask(
            target.copy(
              dateKey = updatedDateKey,
              scheduledMinutesOfDay = updatedMins,
              scheduledTimeText = updatedTimeText,
            ),
          )
          val reply = "Rescheduled \"${target.title}\" to ${if (toTomorrow) "tomorrow " else ""}${updatedTimeText.ifBlank { "updated slot" }}."
          return AlfredExecutionResult(
            replyText = reply,
            spokenText = reply,
            executedActions = listOf("Dashboard · Rescheduled \"${target.title}\""),
            suggestedTab = if (toTomorrow) VisionTab.Report else VisionTab.Dashboard,
          )
        }
      }
    }

    if (lower.startsWith("delete task ") || lower.startsWith("remove task ")) {
      val query = cmd.replace(Regex("""^(delete|remove)\s+task\s+""", RegexOption.IGNORE_CASE), "").trim()
      val target = (snapshot.todayTasks + snapshot.tomorrowTasks).firstOrNull {
        query.isNotBlank() && it.title.contains(query, ignoreCase = true)
      }
      if (target != null) {
        coordinator.deleteTask(target.id)
        val reply = "Deleted task \"${target.title}\"."
        return AlfredExecutionResult(
          replyText = reply,
          spokenText = reply,
          executedActions = listOf("Dashboard · Deleted \"${target.title}\""),
          suggestedTab = VisionTab.Dashboard,
        )
      }
    }

    if (!allowOpenFallback) return null

    // Offline helpful fallback showing real capabilities & live status
    val rec = AlfredAnnouncementEngine.recommendNextTask(snapshot)
    val offlineFallback = buildString {
      append("Offline Rule Engine active. Right now you have ${snapshot.todayProductivity.completedTasks}/${snapshot.todayProductivity.totalTasks} tasks completed (${snapshot.todayProductivity.productivityPercent}%), next focus recommendation: ${rec.headline}.\n\n")
      append("Try commands like: \"Add task Revise Kotlin at 5 PM\", \"Complete next task\", \"Set alarm for 6:30 AM\", \"Start 25m study session\", \"Block Instagram\", or \"Read daily report\".")
    }
    return AlfredExecutionResult(
      replyText = offlineFallback,
      spokenText = "Offline mode active. Your next recommended step is ${rec.headline}.",
      executedActions = emptyList(),
    )
  }

  private fun parseHourMinute24(lowerText: String): Pair<Int, Int>? {
    val matches = timeRegex.findAll(lowerText).toList()
    // Prefer a match with explicit am/pm or colon, skip duration matches like "25 minutes"
    val best = matches.firstOrNull { m ->
      val full = m.value
      val after = lowerText.substring(m.range.last + 1).trimStart()
      val looksLikeMinutes = after.startsWith("min") || after.startsWith("m ") || after == "m"
      !looksLikeMinutes && (m.groupValues[3].isNotBlank() || m.groupValues[2].isNotBlank() || lowerText.contains("at $full") || lowerText.contains("for $full"))
    } ?: return null

    val rawHour = best.groupValues[1].toIntOrNull() ?: return null
    val minute = best.groupValues[2].toIntOrNull()?.coerceIn(0, 59) ?: 0
    val period = best.groupValues[3].lowercase(Locale.US)
    val hour24 = when (period) {
      "pm" -> if (rawHour in 1..11) rawHour + 12 else if (rawHour == 12) 12 else rawHour.coerceIn(0, 23)
      "am" -> if (rawHour == 12) 0 else rawHour.coerceIn(0, 23)
      else -> rawHour.coerceIn(0, 23)
    }
    return hour24 to minute
  }

  private fun extractMinutes(lowerText: String): Int? {
    val match = minutesRegex.find(lowerText) ?: return null
    return match.groupValues[1].toIntOrNull()?.coerceIn(1, 240)
  }

  private fun extractLabelAfterTimeOrKeyword(raw: String, isReminder: Boolean): String {
    val stripped = raw
      .replace(Regex("""^(set|add|create|wake me up)\s+(an?\s+)?(wake[- ]?up\s+)?(alarm|reminder)?\s*""", RegexOption.IGNORE_CASE), "")
      .replace(Regex("""\b(for|at|on)\s+\d{1,2}(:\d{2})?\s*(am|pm)?\b""", RegexOption.IGNORE_CASE), "")
      .replace(Regex("""\b(daily|every day|to)\b""", RegexOption.IGNORE_CASE), "")
      .trim()
    return stripped.ifBlank {
      if (isReminder) "ALFRED Reminder" else "ALFRED Wake-Up Alarm"
    }
  }

  private fun inferCategory(title: String): String {
    val l = title.lowercase(Locale.US)
    return when {
      l.contains("yoga") || l.contains("workout") || l.contains("exercise") || l.contains("walk") -> "Wellness"
      l.contains("study") || l.contains("exam") || l.contains("notes") || l.contains("lecture") -> "Study"
      l.contains("code") || l.contains("kotlin") || l.contains("web") || l.contains("cppm") || l.contains("project") -> "Skill Dev"
      else -> "Personal"
    }
  }
}
