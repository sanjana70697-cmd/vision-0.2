package com.example.domain.alfred

import com.example.data.local.AlarmEntity
import com.example.data.local.DateKeyHelper
import com.example.data.local.TaskEntity
import com.example.ui.theme.VisionTab
import java.util.Locale

/**
 * Deterministic offline rule-based engine for ALFRED.
 * Executes verified cross-tab actions through [AlfredCoordinator], checks actual Result outcomes,
 * and proposes confirmation before important or destructive actions when [requireConfirmation] is true.
 */
object AlfredRuleEngine {

  private val timeRegex = Regex("""\b(\d{1,2})(?::(\d{2}))?\s*(am|pm)?\b""", RegexOption.IGNORE_CASE)
  private val minutesRegex = Regex("""\b(\d{1,3})\s*(?:min|mins|minute|minutes|m)\b""", RegexOption.IGNORE_CASE)

  private val startSessionRegex = Regex(
    """\b(start|begin)\s+(?:a\s+|an\s+)?(?:\d{1,3}\s*(?:min|mins|minute|minutes|m)\s+)?(focus|study)\b""",
    RegexOption.IGNORE_CASE,
  )

  suspend fun tryExecute(
    rawCommand: String,
    snapshot: AlfredContextSnapshot,
    coordinator: AlfredCoordinator,
    allowOpenFallback: Boolean = true,
    requireConfirmation: Boolean = false,
  ): AlfredExecutionResult? {
    val cmd = rawCommand.trim()
    if (cmd.isEmpty()) return null
    val lower = cmd.lowercase(Locale.US)

    // 0. Honest AI Identity & Capability Answers (never pretend to be human)
    if (
      lower.contains("are you human") ||
      lower.contains("are you a person") ||
      lower.contains("are you real") ||
      lower == "who are you" ||
      lower == "what are you"
    ) {
      val reply = "I'm ALFRED, an AI assistant built into the VISION Android app—not a human. I can help you manage tasks, set alarms and reminders, monitor study sessions, plan tomorrow's schedule, and discuss your productivity."
      return AlfredExecutionResult(
        replyText = reply,
        spokenText = reply,
        executedActions = emptyList(),
      )
    }

    if (lower == "hi" || lower == "hello" || lower == "hey" || lower == "hey alfred") {
      val rec = AlfredAnnouncementEngine.recommendNextTask(snapshot)
      val reply = "Hello! I'm ALFRED, your VISION AI assistant. Today you've completed ${snapshot.todayProductivity.completedTasks} of ${snapshot.todayProductivity.totalTasks} tasks. Next suggestion: ${rec.headline}. How can I help?"
      return AlfredExecutionResult(
        replyText = reply,
        spokenText = "Hello! Today you have completed ${snapshot.todayProductivity.completedTasks} of ${snapshot.todayProductivity.totalTasks} tasks. How can I help?",
        executedActions = emptyList(),
      )
    }

    // 1. Morning / Briefing
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
        executedActions = listOf("Dashboard · Analyzed today's schedule"),
        suggestedTab = VisionTab.Dashboard,
      )
    }

    if (
      lower.contains("organize my day") ||
      lower.contains("plan my day") ||
      lower.contains("my schedule") ||
      lower.contains("brief me")
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

    if (lower.contains("daily report") || lower.contains("read report") || lower == "report") {
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
      if (note.isBlank()) {
        val reply = "Please include the plan note after \"Plan tomorrow\", for example: \"Plan tomorrow Finish Kotlin project before noon\"."
        return AlfredExecutionResult(replyText = reply, spokenText = reply, executedActions = emptyList())
      }
      val res = coordinator.updateNextDayPlanNote(note)
      return if (res.isSuccess) {
        val reply = "Saved tomorrow's plan note: \"$note\"."
        AlfredExecutionResult(
          replyText = reply,
          spokenText = reply,
          executedActions = listOf("Report · Saved next-day planning note"),
          suggestedTab = VisionTab.Report,
        )
      } else {
        val err = res.exceptionOrNull()?.localizedMessage ?: "Unable to save planning note."
        AlfredExecutionResult(replyText = "Could not save tomorrow's note: $err", spokenText = err, executedActions = emptyList())
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
      if (enableStudy) {
        coordinator.startStudySession(mins)
      } else {
        coordinator.setFocusMode(true)
        coordinator.startFocusTimer(mins)
      }
      val studyOn = enableStudy || snapshot.focusConfig.studyModeEnabled
      val announcement = AlfredAnnouncementEngine.buildStudyStartAnnouncement(mins, studyOn)
      return AlfredExecutionResult(
        replyText = announcement.message,
        spokenText = announcement.spokenMessage,
        executedActions = listOf("Distraction · Started ${mins}m ${if (enableStudy) "Study" else "Focus"} session"),
        suggestedTab = VisionTab.Distraction,
      )
    }

    if (lower.contains("pause focus") || lower.contains("pause timer") || lower.contains("pause study")) {
      coordinator.pauseFocusTimer()
      val reply = "Paused the active timer."
      return AlfredExecutionResult(
        replyText = reply,
        spokenText = reply,
        executedActions = listOf("Distraction · Paused active timer"),
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
      if (requireConfirmation && (snapshot.focusConfig.activeTimerRunning || snapshot.focusConfig.focusModeEnabled)) {
        return AlfredExecutionResult(
          replyText = "Your Focus / Study session is currently active. Confirm if you want me to stop it and turn off Focus Shield.",
          spokenText = "Confirm if you want to stop your active focus session.",
          executedActions = emptyList(),
          suggestedTab = VisionTab.Distraction,
          pendingConfirmation = PendingAlfredAction(
            title = "Stop Active Study / Focus Session?",
            description = "This will stop the active session timer, save current elapsed minutes, and turn off Focus Shield.",
            isDestructive = false,
            suggestedTab = VisionTab.Distraction,
            payload = AlfredActionPayload.StopFocusAndStudySession,
          ),
        )
      }
      coordinator.stopStudySession()
      val reply = "Study / Focus session stopped and Focus Mode turned off."
      return AlfredExecutionResult(
        replyText = reply,
        spokenText = reply,
        executedActions = listOf("Distraction · Stopped session & disabled Focus Mode"),
        suggestedTab = VisionTab.Distraction,
      )
    }

    if (lower.contains("turn on focus") || lower.contains("enable focus")) {
      val res = coordinator.setFocusMode(true)
      return if (res.isSuccess) {
        val reply = "Focus Mode enabled for ${snapshot.focusConfig.blockDurationMinutes} minutes."
        AlfredExecutionResult(
          replyText = reply,
          spokenText = reply,
          executedActions = listOf("Distraction · Enabled Focus Mode"),
          suggestedTab = VisionTab.Distraction,
        )
      } else {
        val err = res.exceptionOrNull()?.localizedMessage ?: "Failed to enable Focus Mode."
        AlfredExecutionResult(replyText = err, spokenText = err, executedActions = emptyList())
      }
    }

    if (lower.contains("enable study mode") || lower.contains("turn on study mode")) {
      val res = coordinator.setStudyMode(true)
      return if (res.isSuccess) {
        val reply = "Study Mode enabled. Switching to YouTube or monitored distracting apps during an active Study Session will trigger a reminder."
        AlfredExecutionResult(
          replyText = reply,
          spokenText = reply,
          executedActions = listOf("Distraction · Enabled Study Mode"),
          suggestedTab = VisionTab.Distraction,
        )
      } else {
        val err = res.exceptionOrNull()?.localizedMessage ?: "Failed to enable Study Mode."
        AlfredExecutionResult(replyText = err, spokenText = err, executedActions = emptyList())
      }
    }

    if (lower.contains("disable study mode") || lower.contains("turn off study mode")) {
      val res = coordinator.setStudyMode(false)
      return if (res.isSuccess) {
        val reply = "Study Mode disabled."
        AlfredExecutionResult(
          replyText = reply,
          spokenText = reply,
          executedActions = listOf("Distraction · Disabled Study Mode"),
          suggestedTab = VisionTab.Distraction,
        )
      } else {
        val err = res.exceptionOrNull()?.localizedMessage ?: "Failed to disable Study Mode."
        AlfredExecutionResult(replyText = err, spokenText = err, executedActions = emptyList())
      }
    }

    if (lower.startsWith("unblock ") || lower.startsWith("allow ")) {
      val target = cmd.substringAfter(" ").trim()
      val matched = snapshot.distractingApps.firstOrNull {
        target.isNotBlank() && (it.appName.contains(target, ignoreCase = true) ||
          it.shortTag.equals(target, ignoreCase = true))
      }
      if (matched != null) {
        if (!matched.isBlocked) {
          return AlfredExecutionResult(
            replyText = "${matched.appName} is already allowed.",
            spokenText = "${matched.appName} is already allowed.",
            executedActions = emptyList(),
            suggestedTab = VisionTab.Distraction,
          )
        }
        if (requireConfirmation && (snapshot.focusConfig.focusModeEnabled || snapshot.focusConfig.activeTimerRunning)) {
          return AlfredExecutionResult(
            replyText = "Focus protection is active. Confirm if you want to unblock ${matched.appName}.",
            spokenText = "Confirm unblocking ${matched.appName}.",
            executedActions = emptyList(),
            suggestedTab = VisionTab.Distraction,
            pendingConfirmation = PendingAlfredAction(
              title = "Unblock ${matched.appName} During Focus?",
              description = "This will remove ${matched.appName} from your active Focus block list.",
              isDestructive = false,
              suggestedTab = VisionTab.Distraction,
              payload = AlfredActionPayload.UnblockApp(matched.packageName, matched.appName),
            ),
          )
        }
        val res = coordinator.toggleAppBlock(matched)
        return if (res.isSuccess) {
          val reply = "${matched.appName} is now unblocked."
          AlfredExecutionResult(
            replyText = reply,
            spokenText = reply,
            executedActions = listOf("Distraction · Unblocked ${matched.appName}"),
            suggestedTab = VisionTab.Distraction,
          )
        } else {
          val err = res.exceptionOrNull()?.localizedMessage ?: "Could not unblock ${matched.appName}."
          AlfredExecutionResult(replyText = err, spokenText = err, executedActions = emptyList())
        }
      }
    }

    if (lower.startsWith("block ")) {
      val durationMatch = extractMinutes(lower)
      if (lower.contains("block for ") && durationMatch != null) {
        val dRes = coordinator.setBlockDuration(durationMatch)
        val fRes = coordinator.setFocusMode(true)
        return if (dRes.isSuccess && fRes.isSuccess) {
          val reply = "Focus blocking activated for $durationMatch minutes."
          AlfredExecutionResult(
            replyText = reply,
            spokenText = reply,
            executedActions = listOf("Distraction · Armed Focus blocking for ${durationMatch}m"),
            suggestedTab = VisionTab.Distraction,
          )
        } else {
          AlfredExecutionResult(replyText = "Unable to update focus blocking.", spokenText = "Unable to update focus blocking.", executedActions = emptyList())
        }
      }
      val target = cmd.substringAfter(" ").trim()
      val matched = snapshot.distractingApps.firstOrNull {
        target.isNotBlank() && (it.appName.contains(target, ignoreCase = true) ||
          it.shortTag.equals(target, ignoreCase = true))
      }
      if (matched != null) {
        if (!matched.isBlocked) {
          val bRes = coordinator.toggleAppBlock(matched)
          if (bRes.isFailure) {
            return AlfredExecutionResult(replyText = "Failed to block ${matched.appName}.", spokenText = "Failed to block ${matched.appName}.", executedActions = emptyList())
          }
        }
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
      if (parsedTime == null) {
        val reply = "Please specify a time for the alarm or reminder, for example: \"Set reminder at 5:30 PM Read notes\"."
        return AlfredExecutionResult(replyText = reply, spokenText = reply, executedActions = emptyList())
      }
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
      val saveRes = coordinator.saveAndScheduleAlarm(newAlarm)
      return if (saveRes.isSuccess) {
        val timeFmt = DateKeyHelper.formatFullTime12h(hour24, minute)
        val reply = "${if (isReminder) "Reminder" else "Alarm"} set for $timeFmt: \"$cleanLabel\"."
        AlfredExecutionResult(
          replyText = reply,
          spokenText = reply,
          executedActions = listOf("Alarm · Scheduled $kind for $timeFmt"),
          suggestedTab = VisionTab.Alarm,
        )
      } else {
        val err = saveRes.exceptionOrNull()?.localizedMessage ?: "Failed to save reminder."
        AlfredExecutionResult(replyText = "Could not save: $err", spokenText = err, executedActions = emptyList())
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
      if (targets.isEmpty()) {
        val reply = "No active alarm matched \"$query\"."
        return AlfredExecutionResult(replyText = reply, spokenText = reply, executedActions = emptyList(), suggestedTab = VisionTab.Alarm)
      }
      if (requireConfirmation) {
        val labelSummary = if (targets.size == 1) "\"${targets.first().label}\"" else "${targets.size} active alarms"
        return AlfredExecutionResult(
          replyText = "Confirm if you want to disable $labelSummary.",
          spokenText = "Confirm disabling $labelSummary.",
          executedActions = emptyList(),
          suggestedTab = VisionTab.Alarm,
          pendingConfirmation = PendingAlfredAction(
            title = "Disable $labelSummary?",
            description = "Disabled alarms and reminders will not ring until re-enabled.",
            isDestructive = false,
            suggestedTab = VisionTab.Alarm,
            payload = AlfredActionPayload.DisableAlarms(targets.map { it.id }, labelSummary),
          ),
        )
      }
      targets.forEach { coordinator.toggleAlarmEnabled(it, false) }
      val reply = "Disabled ${targets.size} ${if (targets.size == 1) "alarm" else "alarms"}."
      return AlfredExecutionResult(
        replyText = reply,
        spokenText = reply,
        executedActions = listOf("Alarm · Disabled ${targets.size} alarm(s)"),
        suggestedTab = VisionTab.Alarm,
      )
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
      if (targets.isEmpty()) {
        val reply = "No disabled alarm matched \"$query\"."
        return AlfredExecutionResult(replyText = reply, spokenText = reply, executedActions = emptyList(), suggestedTab = VisionTab.Alarm)
      }
      targets.forEach { coordinator.toggleAlarmEnabled(it, true) }
      val reply = "Enabled ${targets.size} ${if (targets.size == 1) "alarm" else "alarms"}."
      return AlfredExecutionResult(
        replyText = reply,
        spokenText = reply,
        executedActions = listOf("Alarm · Armed ${targets.size} alarm(s)"),
        suggestedTab = VisionTab.Alarm,
      )
    }

    if (lower.startsWith("delete alarm") || lower.startsWith("remove alarm") ||
      lower.startsWith("delete reminder") || lower.startsWith("remove reminder")
    ) {
      val query = cmd.replace(Regex("""^(delete|remove)\s+(alarm|reminder)\s*""", RegexOption.IGNORE_CASE), "").trim()
      val target = snapshot.alarms.firstOrNull {
        query.isNotBlank() && (it.label.contains(query, ignoreCase = true) ||
          DateKeyHelper.formatFullTime12h(it.hour24, it.minute).contains(query, ignoreCase = true))
      }
      if (target == null) {
        val reply = "I couldn't find an alarm or reminder matching \"$query\"."
        return AlfredExecutionResult(replyText = reply, spokenText = reply, executedActions = emptyList(), suggestedTab = VisionTab.Alarm)
      }
      if (requireConfirmation) {
        val timeFmt = DateKeyHelper.formatFullTime12h(target.hour24, target.minute)
        return AlfredExecutionResult(
          replyText = "Are you sure you want to permanently delete \"${target.label}\" ($timeFmt)?",
          spokenText = "Confirm deleting ${target.label}.",
          executedActions = emptyList(),
          suggestedTab = VisionTab.Alarm,
          pendingConfirmation = PendingAlfredAction(
            title = "Delete \"${target.label}\"?",
            description = "Scheduled for $timeFmt. Deleting removes it permanently from VISION.",
            isDestructive = true,
            suggestedTab = VisionTab.Alarm,
            payload = AlfredActionPayload.DeleteAlarm(target.id, target.label),
          ),
        )
      }
      val delRes = coordinator.deleteAndCancelAlarm(target.id)
      return if (delRes.isSuccess) {
        val reply = "Deleted \"${target.label}\"."
        AlfredExecutionResult(
          replyText = reply,
          spokenText = reply,
          executedActions = listOf("Alarm · Deleted ${target.label}"),
          suggestedTab = VisionTab.Alarm,
        )
      } else {
        AlfredExecutionResult(replyText = "Could not delete ${target.label}.", spokenText = "Could not delete.", executedActions = emptyList())
      }
    }

    // 6. Habit Logging (Yoga / Skill)
    if (
      lower.contains("log yoga") ||
      lower.contains("yoga done") ||
      lower.contains("complete yoga") ||
      Regex("""\blog\b.*\byoga\b""").containsMatchIn(lower)
    ) {
      val mins = extractMinutes(lower) ?: 20
      val res = coordinator.logHabit("YOGA_EXERCISE", "Morning Yoga & Exercise", mins, true)
      return if (res.isSuccess) {
        val reply = "Logged $mins minutes of Morning Yoga & Exercise."
        AlfredExecutionResult(
          replyText = reply,
          spokenText = reply,
          executedActions = listOf("Dashboard · Logged +${mins}m Yoga & Exercise"),
          suggestedTab = VisionTab.Dashboard,
        )
      } else {
        AlfredExecutionResult(replyText = "Could not log yoga progress.", spokenText = "Could not log yoga progress.", executedActions = emptyList())
      }
    }

    if (
      lower.contains("log skill") ||
      lower.contains("skill done") ||
      Regex("""\blog\b.*\bskill\b""").containsMatchIn(lower)
    ) {
      val mins = extractMinutes(lower) ?: 30
      val res = coordinator.logHabit("SKILL_DEV", "Skill Development · Web & CPPM", mins, true)
      return if (res.isSuccess) {
        val reply = "Logged $mins minutes of Skill Development."
        AlfredExecutionResult(
          replyText = reply,
          spokenText = reply,
          executedActions = listOf("Dashboard · Logged +${mins}m Skill Development"),
          suggestedTab = VisionTab.Dashboard,
        )
      } else {
        AlfredExecutionResult(replyText = "Could not log skill development.", spokenText = "Could not log skill development.", executedActions = emptyList())
      }
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
      val isRoutine = lower.contains("routine") || lower.contains("daily")
      val title = cmd
        .replace(Regex("""^(add task|create task|new task|schedule task|remind me to)\s*[:,-]?\s*""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\b(for|at)\s+\d{1,2}(:\d{2})?\s*(am|pm)?\b""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\b(tomorrow|today|priority|urgent|important|as a routine|routine)\b""", RegexOption.IGNORE_CASE), "")
        .trim()

      if (title.isBlank()) {
        val reply = "Please tell me the task title, for example: \"Add task Revise Algorithms at 5 PM\"."
        return AlfredExecutionResult(replyText = reply, spokenText = reply, executedActions = emptyList())
      }

      val minsOfDay = parsedTime?.let { it.first * 60 + it.second } ?: -1
      val timeText = parsedTime?.let { DateKeyHelper.formatFullTime12h(it.first, it.second) } ?: ""
      val dateKey = if (isTomorrow) DateKeyHelper.tomorrowKey() else DateKeyHelper.todayKey()
      val category = inferCategory(title)

      val entity = TaskEntity(
        title = title,
        category = category,
        scheduledTimeText = timeText,
        scheduledMinutesOfDay = minsOfDay,
        isPriority = lower.contains("priority"),
        isRoutine = isRoutine,
        isCompleted = false,
        dateKey = dateKey,
      )
      val saveRes = coordinator.createOrUpdateTask(entity)
      return if (saveRes.isSuccess) {
        val whenStr = buildString {
          if (isTomorrow) append(" for tomorrow")
          if (timeText.isNotBlank()) append(" at $timeText")
        }
        val reply = "Created task \"$title\"$whenStr in ${if (isTomorrow) "Tomorrow's Plan" else "Today's Schedule"}."
        AlfredExecutionResult(
          replyText = reply,
          spokenText = reply,
          executedActions = listOf("${if (isTomorrow) "Report" else "Dashboard"} · Created task \"$title\""),
          suggestedTab = if (isTomorrow) VisionTab.Report else VisionTab.Dashboard,
        )
      } else {
        val err = saveRes.exceptionOrNull()?.localizedMessage ?: "Failed to create task."
        AlfredExecutionResult(replyText = "Could not add task: $err", spokenText = err, executedActions = emptyList())
      }
    }

    if (
      lower.startsWith("complete ") ||
      lower.startsWith("finish ") ||
      (lower.startsWith("mark ") && (lower.contains("done") || lower.contains("complete")))
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
      if (target == null) {
        val reply = if (query.isBlank()) "All of today's tasks are already complete." else "I couldn't find a pending task matching \"$query\"."
        return AlfredExecutionResult(replyText = reply, spokenText = reply, executedActions = emptyList())
      }
      val toggleRes = coordinator.toggleTaskCompleted(target)
      return if (toggleRes.isSuccess) {
        val doneCount = snapshot.todayProductivity.completedTasks + 1
        val totalCount = snapshot.todayProductivity.totalTasks.coerceAtLeast(doneCount)
        val pct = (doneCount * 100) / totalCount
        val reply = "Marked \"${target.title}\" complete. You have completed $doneCount ${if (doneCount == 1) "task" else "tasks"} today ($pct%)."
        AlfredExecutionResult(
          replyText = reply,
          spokenText = reply,
          executedActions = listOf("Dashboard · Completed \"${target.title}\""),
          suggestedTab = VisionTab.Dashboard,
        )
      } else {
        val err = toggleRes.exceptionOrNull()?.localizedMessage ?: "Unable to update task."
        AlfredExecutionResult(replyText = err, spokenText = err, executedActions = emptyList())
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
      } ?: if (query.isBlank()) snapshot.todayTasks.firstOrNull { !it.isCompleted } else null

      if (target == null) {
        val reply = "I couldn't find a pending task matching \"$query\"."
        return AlfredExecutionResult(replyText = reply, spokenText = reply, executedActions = emptyList())
      }

      if (toTomorrow && newTime == null) {
        val res = coordinator.scheduleTaskForTomorrow(target)
        return if (res.isSuccess) {
          val reply = "Rescheduled \"${target.title}\" to tomorrow."
          AlfredExecutionResult(
            replyText = reply,
            spokenText = reply,
            executedActions = listOf("Dashboard/Report · Moved \"${target.title}\" to tomorrow"),
            suggestedTab = VisionTab.Report,
          )
        } else {
          val err = res.exceptionOrNull()?.localizedMessage ?: "Unable to move task."
          AlfredExecutionResult(replyText = "Could not move task: $err", spokenText = err, executedActions = emptyList())
        }
      } else {
        val updatedDateKey = if (toTomorrow) DateKeyHelper.tomorrowKey() else target.dateKey
        val updatedMins = newTime?.let { it.first * 60 + it.second } ?: target.scheduledMinutesOfDay
        val updatedTimeText = newTime?.let { DateKeyHelper.formatFullTime12h(it.first, it.second) } ?: target.scheduledTimeText
        val res = coordinator.createOrUpdateTask(
          target.copy(
            dateKey = updatedDateKey,
            scheduledMinutesOfDay = updatedMins,
            scheduledTimeText = updatedTimeText,
          ),
        )
        return if (res.isSuccess) {
          val reply = "Rescheduled \"${target.title}\" to ${if (toTomorrow) "tomorrow " else ""}${updatedTimeText.ifBlank { "updated slot" }}."
          AlfredExecutionResult(
            replyText = reply,
            spokenText = reply,
            executedActions = listOf("Dashboard · Rescheduled \"${target.title}\""),
            suggestedTab = if (toTomorrow) VisionTab.Report else VisionTab.Dashboard,
          )
        } else {
          val err = res.exceptionOrNull()?.localizedMessage ?: "Unable to reschedule task."
          AlfredExecutionResult(replyText = "Could not reschedule: $err", spokenText = err, executedActions = emptyList())
        }
      }
    }

    if (lower.startsWith("delete task ") || lower.startsWith("remove task ")) {
      val query = cmd.replace(Regex("""^(delete|remove)\s+task\s+""", RegexOption.IGNORE_CASE), "").trim()
      val target = (snapshot.todayTasks + snapshot.tomorrowTasks).firstOrNull {
        query.isNotBlank() && it.title.contains(query, ignoreCase = true)
      }
      if (target == null) {
        val reply = "I couldn't find a task matching \"$query\"."
        return AlfredExecutionResult(replyText = reply, spokenText = reply, executedActions = emptyList(), suggestedTab = VisionTab.Dashboard)
      }
      if (requireConfirmation) {
        return AlfredExecutionResult(
          replyText = "Confirm if you want to permanently delete task \"${target.title}\".",
          spokenText = "Confirm deleting task ${target.title}.",
          executedActions = emptyList(),
          suggestedTab = VisionTab.Dashboard,
          pendingConfirmation = PendingAlfredAction(
            title = "Delete Task \"${target.title}\"?",
            description = "This permanently removes \"${target.title}\" from your schedule.",
            isDestructive = true,
            suggestedTab = VisionTab.Dashboard,
            payload = AlfredActionPayload.DeleteTask(target.id, target.title),
          ),
        )
      }
      val delRes = coordinator.deleteTask(target.id)
      return if (delRes.isSuccess) {
        val reply = "Deleted task \"${target.title}\"."
        AlfredExecutionResult(
          replyText = reply,
          spokenText = reply,
          executedActions = listOf("Dashboard · Deleted \"${target.title}\""),
          suggestedTab = VisionTab.Dashboard,
        )
      } else {
        AlfredExecutionResult(replyText = "Could not delete task.", spokenText = "Could not delete task.", executedActions = emptyList())
      }
    }

    if (!allowOpenFallback) return null

    // Offline helpful fallback showing real capabilities & live status
    val rec = AlfredAnnouncementEngine.recommendNextTask(snapshot)
    val offlineFallback = buildString {
      append("Offline Rule Engine active. Right now you have ${snapshot.todayProductivity.completedTasks}/${snapshot.todayProductivity.totalTasks} tasks completed (${snapshot.todayProductivity.productivityPercent}%), next focus recommendation: ${rec.headline}.\n\n")
      append("Connect to the internet for full AI conversation, or try commands like: \"Add task Revise Kotlin at 5 PM\", \"Complete next task\", \"Set reminder for 6:30 PM\", \"Start 25m study session\", or \"Read daily report\".")
    }
    return AlfredExecutionResult(
      replyText = offlineFallback,
      spokenText = "Offline mode active. Your next recommended step is ${rec.headline}.",
      executedActions = emptyList(),
    )
  }

  private fun parseHourMinute24(lowerText: String): Pair<Int, Int>? {
    val matches = timeRegex.findAll(lowerText).toList()
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
      if (isReminder) "Study Reminder" else "Wake-Up Alarm"
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
