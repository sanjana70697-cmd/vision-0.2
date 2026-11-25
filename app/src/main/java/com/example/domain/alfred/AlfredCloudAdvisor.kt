package com.example.domain.alfred

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.data.local.AlarmEntity
import com.example.data.local.DateKeyHelper
import com.example.data.local.TaskEntity
import com.example.ui.theme.VisionTab
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "AlfredCloudAdvisor"

/**
 * Multi-turn conversational Gemini advisor via Firebase AI Logic (App Check attested).
 * Preserves conversation history across turns, grounds replies in real Room data,
 * verifies every tool/coordinator result before reporting success, and proposes confirmation
 * before important or destructive actions.
 */
class AlfredCloudAdvisor(private val appContext: Context) {

  private val actionPlanSchema = Schema.obj(
    properties = mapOf(
      "intentType" to Schema.enumeration(
        values = listOf(
          "NONE",
          "CREATE_TASK",
          "COMPLETE_TASK",
          "RESCHEDULE_TASK",
          "DELETE_TASK",
          "SET_ALARM",
          "TOGGLE_ALARM",
          "DELETE_ALARM",
          "START_STUDY_SESSION",
          "STOP_FOCUS",
          "SET_STUDY_MODE",
          "TOGGLE_APP_BLOCK",
          "SAVE_NEXT_DAY_NOTE",
        ),
        description = "Real action to execute in VISION, or NONE for conversational response",
      ),
      "replyText" to Schema.string(description = "Helpful natural-language reply grounded in the user's real data and conversation history. Never fabricate action results."),
      "spokenText" to Schema.string(description = "Clear 1-2 sentence spoken response for Text-to-Speech"),
      "targetTab" to Schema.enumeration(
        values = listOf("DASHBOARD", "ALARM", "DISTRACTION", "REPORT"),
        description = "Relevant VISION tab",
      ),
      "taskTitle" to Schema.string(description = "Task title for create/complete/reschedule/delete", nullable = true),
      "taskCategory" to Schema.string(description = "Wellness, Skill Dev, Study, or Personal", nullable = true),
      "taskHour24" to Schema.integer(description = "24-hour clock 0..23 for task or alarm", nullable = true),
      "taskMinute" to Schema.integer(description = "Minute 0..59 for task or alarm", nullable = true),
      "taskTomorrow" to Schema.boolean(description = "True if scheduled for tomorrow", nullable = true),
      "alarmLabel" to Schema.string(description = "Alarm or reminder label", nullable = true),
      "alarmKind" to Schema.string(description = "ALARM, REMINDER, or SCHEDULE", nullable = true),
      "alarmEnabled" to Schema.boolean(description = "Target enabled state for alarm", nullable = true),
      "alarmDateKey" to Schema.string(description = "Optional YYYY-MM-DD date for one-time reminder or schedule", nullable = true),
      "focusMinutes" to Schema.integer(description = "Focus/study timer duration in minutes", nullable = true),
      "studyModeEnabled" to Schema.boolean(description = "Enable or disable Study Mode", nullable = true),
      "targetAppName" to Schema.string(description = "App name to block/unblock e.g. Instagram or YouTube", nullable = true),
      "appBlocked" to Schema.boolean(description = "True to block app, false to unblock", nullable = true),
      "nextDayNote" to Schema.string(description = "Next-day planning note text", nullable = true),
    ),
    optionalProperties = listOf(
      "taskTitle",
      "taskCategory",
      "taskHour24",
      "taskMinute",
      "taskTomorrow",
      "alarmLabel",
      "alarmKind",
      "alarmEnabled",
      "alarmDateKey",
      "focusMinutes",
      "studyModeEnabled",
      "targetAppName",
      "appBlocked",
      "nextDayNote",
    ),
  )

  private val model by lazy {
    val cfg = generationConfig {
      responseMimeType = "application/json"
      responseSchema = actionPlanSchema
      temperature = 0.35f
    }
    Firebase.ai.generativeModel(
      modelName = "gemini-3.8-flash",
      generationConfig = cfg,
      systemInstruction = content {
        text(
          """
          You are ALFRED, the AI personal productivity assistant inside the VISION Android app.
          Identity rules:
          - Always be honest that you are an AI software assistant in VISION — NEVER claim to be human or pretend to have personal human experiences.
          - Be genuinely conversational, helpful, clear, and concise. Answer everyday questions, study questions, planning questions, or productivity coaching naturally while remembering earlier turns in the conversation.
          Action & honesty rules:
          - Only select an intentType other than NONE when the user genuinely wants that VISION app action performed and you have real details for it.
          - Never invent tasks, alarms, completion counts, or statistics that are not present in the provided live data.
          - Never pretend an action succeeded in replyText if parameters (like task title or time) are missing — ask the user cleanly instead and keep intentType = NONE.
          """.trimIndent(),
        )
      },
    )
  }

  fun isNetworkOnline(): Boolean {
    return runCatching {
      val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return false
      val net = cm.activeNetwork ?: return false
      val caps = cm.getNetworkCapabilities(net) ?: return false
      caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }.getOrDefault(false)
  }

  suspend fun processWithGemini(
    userCommand: String,
    conversationHistory: List<AlfredChatEntry>,
    snapshot: AlfredContextSnapshot,
    coordinator: AlfredCoordinator,
    requireConfirmation: Boolean = true,
  ): Result<AlfredExecutionResult> {
    if (!isNetworkOnline()) {
      return Result.failure(IllegalStateException("Device is currently offline."))
    }

    val historyContents = buildChatHistory(conversationHistory)
    val prompt = buildContextPrompt(userCommand, snapshot)

    return try {
      val response = withTimeout(60_000L) {
        val chat = model.startChat(history = historyContents)
        chat.sendMessage(prompt)
      }
      val jsonStr = response.text
        ?: return Result.failure(IllegalStateException("Cloud AI returned an empty response."))

      val json = JSONObject(jsonStr)
      val execution = applyStructuredAction(json, snapshot, coordinator, requireConfirmation)
      Result.success(execution)
    } catch (e: TimeoutCancellationException) {
      Log.e(TAG, "Gemini request timed out", e)
      Result.failure(IllegalStateException("Cloud AI timed out after 60 seconds. Please check your connection or try again."))
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Firebase AI Logic error", e)
      Result.failure(IllegalStateException(e.localizedMessage ?: "Cloud AI service failed."))
    }
  }

  private fun buildChatHistory(entries: List<AlfredChatEntry>) =
    entries
      .takeLast(10)
      .filter { it.text.isNotBlank() }
      .map { entry ->
        val role = if (entry.isUser) "user" else "model"
        content(role) { text(entry.text.take(600)) }
      }

  private suspend fun applyStructuredAction(
    json: JSONObject,
    snapshot: AlfredContextSnapshot,
    coordinator: AlfredCoordinator,
    requireConfirmation: Boolean,
  ): AlfredExecutionResult {
    val intentType = json.optString("intentType", "NONE")
    var replyText = json.optString("replyText", "").ifBlank { "I'm ready to help." }
    var spokenText = json.optString("spokenText", replyText).ifBlank { replyText }
    val suggestedTab = when (json.optString("targetTab", "DASHBOARD")) {
      "ALARM" -> VisionTab.Alarm
      "DISTRACTION" -> VisionTab.Distraction
      "REPORT" -> VisionTab.Report
      else -> VisionTab.Dashboard
    }

    val actions = mutableListOf<String>()
    var pendingConfirmation: PendingAlfredAction? = null

    when (intentType) {
      "CREATE_TASK" -> {
        val title = json.optString("taskTitle").takeIf { it.isNotBlank() && it != "null" }
        if (title == null) {
          replyText = "What would you like the task to be called?"
          spokenText = replyText
        } else {
          val h = json.optInt("taskHour24", -1)
          val m = json.optInt("taskMinute", 0).coerceIn(0, 59)
          val hasTime = h in 0..23
          val tomorrow = json.optBoolean("taskTomorrow", false)
          val entity = TaskEntity(
            title = title,
            category = json.optString("taskCategory").takeIf { it.isNotBlank() && it != "null" } ?: "Personal",
            scheduledTimeText = if (hasTime) DateKeyHelper.formatFullTime12h(h, m) else "",
            scheduledMinutesOfDay = if (hasTime) h * 60 + m else -1,
            isPriority = false,
            isRoutine = false,
            isCompleted = false,
            dateKey = if (tomorrow) DateKeyHelper.tomorrowKey() else DateKeyHelper.todayKey(),
          )
          val res = coordinator.createOrUpdateTask(entity)
          if (res.isSuccess) {
            actions += "${if (tomorrow) "Report" else "Dashboard"} · Created task \"$title\""
          } else {
            val err = res.exceptionOrNull()?.localizedMessage ?: "Unable to save task."
            replyText = "I could not add \"$title\": $err"
            spokenText = "Could not add task. $err"
          }
        }
      }

      "COMPLETE_TASK" -> {
        val query = json.optString("taskTitle")
        val pending = snapshot.todayTasks.filter { !it.isCompleted }
        val target = pending.firstOrNull {
          query.isNotBlank() && query != "null" && it.title.contains(query, ignoreCase = true)
        }
        if (target == null) {
          replyText = if (pending.isEmpty()) "All of today's tasks are already complete." else "I couldn't match a pending task to complete."
          spokenText = replyText
        } else {
          val res = coordinator.toggleTaskCompleted(target)
          if (res.isSuccess) {
            actions += "Dashboard · Completed \"${target.title}\""
          } else {
            replyText = "Failed to update \"${target.title}\"."
            spokenText = replyText
          }
        }
      }

      "RESCHEDULE_TASK" -> {
        val query = json.optString("taskTitle")
        val target = snapshot.allPendingTasks.firstOrNull {
          query.isNotBlank() && query != "null" && it.title.contains(query, ignoreCase = true)
        }
        if (target == null) {
          replyText = "I couldn't find that pending task to reschedule."
          spokenText = replyText
        } else {
          val tomorrow = json.optBoolean("taskTomorrow", true)
          val h = json.optInt("taskHour24", -1)
          val m = json.optInt("taskMinute", 0).coerceIn(0, 59)
          val res = if (tomorrow && h !in 0..23) {
            coordinator.scheduleTaskForTomorrow(target)
          } else {
            val dateKey = if (tomorrow) DateKeyHelper.tomorrowKey() else target.dateKey
            val mins = if (h in 0..23) h * 60 + m else target.scheduledMinutesOfDay
            val text = if (h in 0..23) DateKeyHelper.formatFullTime12h(h, m) else target.scheduledTimeText
            coordinator.createOrUpdateTask(
              target.copy(dateKey = dateKey, scheduledMinutesOfDay = mins, scheduledTimeText = text),
            ).map { }
          }
          if (res.isSuccess) {
            actions += if (tomorrow) "Report · Moved \"${target.title}\" to tomorrow" else "Dashboard · Rescheduled \"${target.title}\""
          } else {
            val err = res.exceptionOrNull()?.localizedMessage ?: "Reschedule failed."
            replyText = "Could not reschedule \"${target.title}\": $err"
            spokenText = replyText
          }
        }
      }

      "DELETE_TASK" -> {
        val query = json.optString("taskTitle")
        val target = (snapshot.todayTasks + snapshot.tomorrowTasks).firstOrNull {
          query.isNotBlank() && query != "null" && it.title.contains(query, ignoreCase = true)
        }
        if (target == null) {
          replyText = "I couldn't find a task matching \"$query\"."
          spokenText = replyText
        } else if (requireConfirmation) {
          replyText = "Please confirm before I permanently delete task \"${target.title}\"."
          spokenText = "Confirm deleting task ${target.title}."
          pendingConfirmation = PendingAlfredAction(
            title = "Delete Task \"${target.title}\"?",
            description = "This permanently removes \"${target.title}\" from your schedule.",
            isDestructive = true,
            suggestedTab = VisionTab.Dashboard,
            payload = AlfredActionPayload.DeleteTask(target.id, target.title),
          )
        } else {
          val res = coordinator.deleteTask(target.id)
          if (res.isSuccess) actions += "Dashboard · Deleted \"${target.title}\""
          else replyText = "Could not delete \"${target.title}\"."
        }
      }

      "SET_ALARM" -> {
        val h = json.optInt("taskHour24", -1)
        val m = json.optInt("taskMinute", 0).coerceIn(0, 59)
        if (h !in 0..23) {
          replyText = "Please specify a clear time (hour and minute) to set the alarm or reminder."
          spokenText = replyText
        } else {
          val kind = json.optString("alarmKind", "ALARM").let {
            if (it in listOf("ALARM", "REMINDER", "SCHEDULE")) it else "ALARM"
          }
          val label = json.optString("alarmLabel").takeIf { it.isNotBlank() && it != "null" }
            ?: if (kind == "REMINDER") "Study Reminder" else "Wake-Up Alarm"
          val dateKey = json.optString("alarmDateKey")
            .takeIf { it.isNotBlank() && it != "null" && DateKeyHelper.parseDateKeyToCalendar(it) != null }
            ?: if (json.optBoolean("taskTomorrow", false)) DateKeyHelper.tomorrowKey() else ""
          val res = coordinator.saveAndScheduleAlarm(
            AlarmEntity(
              hour24 = h,
              minute = m,
              label = label,
              kind = kind,
              isWakeUp = kind == "ALARM",
              repeatDaysCsv = "",
              enabled = true,
              scheduledDateKey = dateKey,
            ),
          )
          if (res.isSuccess) {
            val dateNote = if (dateKey.isNotBlank()) " on $dateKey" else ""
            actions += "Alarm · Scheduled $kind \"$label\" at ${DateKeyHelper.formatFullTime12h(h, m)}$dateNote"
          } else {
            val err = res.exceptionOrNull()?.localizedMessage ?: "Unable to schedule."
            replyText = "Could not save: $err"
            spokenText = replyText
          }
        }
      }

      "TOGGLE_ALARM" -> {
        val query = json.optString("alarmLabel")
        val enabled = json.optBoolean("alarmEnabled", false)
        val target = snapshot.alarms.firstOrNull {
          query.isNotBlank() && query != "null" && it.label.contains(query, ignoreCase = true)
        }
        if (target == null) {
          replyText = "I couldn't find an alarm or reminder matching \"$query\"."
          spokenText = replyText
        } else if (!enabled && requireConfirmation && target.enabled) {
          replyText = "Confirm if you want me to turn off \"${target.label}\"."
          spokenText = "Confirm disabling ${target.label}."
          pendingConfirmation = PendingAlfredAction(
            title = "Disable \"${target.label}\"?",
            description = "It will no longer ring or alert until re-enabled.",
            isDestructive = false,
            suggestedTab = VisionTab.Alarm,
            payload = AlfredActionPayload.DisableAlarms(listOf(target.id), "\"${target.label}\""),
          )
        } else {
          val res = coordinator.toggleAlarmEnabled(target, enabled)
          if (res.isSuccess) actions += "Alarm · ${if (enabled) "Enabled" else "Disabled"} ${target.label}"
          else replyText = "Could not update \"${target.label}\"."
        }
      }

      "DELETE_ALARM" -> {
        val query = json.optString("alarmLabel")
        val target = snapshot.alarms.firstOrNull {
          query.isNotBlank() && query != "null" && it.label.contains(query, ignoreCase = true)
        }
        if (target == null) {
          replyText = "I couldn't find an alarm or reminder matching \"$query\"."
          spokenText = replyText
        } else if (requireConfirmation) {
          val timeFmt = DateKeyHelper.formatFullTime12h(target.hour24, target.minute)
          replyText = "Confirm before I permanently delete \"${target.label}\" ($timeFmt)."
          spokenText = "Confirm deleting ${target.label}."
          pendingConfirmation = PendingAlfredAction(
            title = "Delete \"${target.label}\"?",
            description = "Scheduled for $timeFmt. Deleting removes it permanently.",
            isDestructive = true,
            suggestedTab = VisionTab.Alarm,
            payload = AlfredActionPayload.DeleteAlarm(target.id, target.label),
          )
        } else {
          val res = coordinator.deleteAndCancelAlarm(target.id)
          if (res.isSuccess) actions += "Alarm · Deleted ${target.label}"
          else replyText = "Could not delete \"${target.label}\"."
        }
      }

      "START_STUDY_SESSION" -> {
        val mins = json.optInt("focusMinutes", 25).coerceIn(5, 180)
        coordinator.startStudySession(mins)
        actions += "Distraction · Started ${mins}m Study Session monitor"
      }

      "STOP_FOCUS" -> {
        if (requireConfirmation && (snapshot.focusConfig.activeTimerRunning || snapshot.focusConfig.focusModeEnabled)) {
          replyText = "Confirm if you want me to stop your active Study / Focus session."
          spokenText = "Confirm stopping your active session."
          pendingConfirmation = PendingAlfredAction(
            title = "Stop Active Study / Focus Session?",
            description = "This stops the countdown timer, saves elapsed minutes, and turns off Focus Shield.",
            isDestructive = false,
            suggestedTab = VisionTab.Distraction,
            payload = AlfredActionPayload.StopFocusAndStudySession,
          )
        } else {
          coordinator.stopStudySession()
          actions += "Distraction · Stopped Study / Focus session"
        }
      }

      "SET_STUDY_MODE" -> {
        val on = json.optBoolean("studyModeEnabled", true)
        val res = coordinator.setStudyMode(on)
        if (res.isSuccess) actions += "Distraction · Study Mode ${if (on) "enabled" else "disabled"}"
        else replyText = "Could not update Study Mode."
      }

      "TOGGLE_APP_BLOCK" -> {
        val appName = json.optString("targetAppName")
        val wantBlocked = json.optBoolean("appBlocked", true)
        val matched = snapshot.distractingApps.firstOrNull {
          appName.isNotBlank() && appName != "null" && it.appName.contains(appName, ignoreCase = true)
        }
        if (matched == null) {
          replyText = "That app isn't in your monitored distracting apps list yet. You can add it in the Distraction tab."
          spokenText = replyText
        } else if (!wantBlocked && matched.isBlocked && requireConfirmation &&
          (snapshot.focusConfig.focusModeEnabled || snapshot.focusConfig.activeTimerRunning)
        ) {
          replyText = "Focus Shield is currently active. Confirm if you want to unblock ${matched.appName}."
          spokenText = "Confirm unblocking ${matched.appName}."
          pendingConfirmation = PendingAlfredAction(
            title = "Unblock ${matched.appName}?",
            description = "Unblocking removes ${matched.appName} from your active Focus block list.",
            isDestructive = false,
            suggestedTab = VisionTab.Distraction,
            payload = AlfredActionPayload.UnblockApp(matched.packageName, matched.appName),
          )
        } else if (matched.isBlocked != wantBlocked) {
          val res = coordinator.toggleAppBlock(matched)
          if (res.isSuccess) {
            if (wantBlocked) coordinator.setFocusMode(true)
            actions += "Distraction · ${if (wantBlocked) "Blocked" else "Unblocked"} ${matched.appName}"
          } else {
            replyText = "Failed to update ${matched.appName}."
          }
        }
      }

      "SAVE_NEXT_DAY_NOTE" -> {
        val note = json.optString("nextDayNote")
        if (note.isNotBlank() && note != "null") {
          val res = coordinator.updateNextDayPlanNote(note)
          if (res.isSuccess) actions += "Report · Saved next-day plan note"
          else replyText = "Could not save next-day plan note."
        }
      }

      else -> Unit
    }

    return AlfredExecutionResult(
      replyText = replyText,
      spokenText = spokenText,
      executedActions = actions,
      suggestedTab = suggestedTab,
      usedCloudAi = true,
      pendingConfirmation = pendingConfirmation,
    )
  }

  private fun buildContextPrompt(
    userCommand: String,
    snapshot: AlfredContextSnapshot,
  ): String {
    val tasksText = snapshot.todayTasks.joinToString("; ") {
      "${it.title} (${it.scheduledTimeText.ifBlank { it.category }}, done=${it.isCompleted})"
    }.ifEmpty { "None" }

    val tomorrowText = snapshot.tomorrowTasks.joinToString("; ") {
      "${it.title} (${it.scheduledTimeText.ifBlank { it.category }})"
    }.ifEmpty { "None" }

    val alarmsText = snapshot.alarms.joinToString("; ") {
      "${DateKeyHelper.formatFullTime12h(it.hour24, it.minute)} ${it.label} [${it.kind}, enabled=${it.enabled}]"
    }.ifEmpty { "None" }

    val appsText = snapshot.distractingApps.joinToString("; ") {
      "${it.appName} (blocked=${it.isBlocked}, usedToday=${it.todayUsageMinutes}m)"
    }.ifEmpty { "None" }

    val goalsText = snapshot.goals.joinToString("; ") {
      "${it.title} ${it.currentValue}/${it.targetValue} ${it.unit}"
    }.ifEmpty { "None" }

    val activityText = snapshot.todayActivitySessions.joinToString("; ") {
      "${it.activityType}: ${it.titleOrSkillName} (${it.durationMinutes}m, ${DateKeyHelper.formatMillisTime12h(it.startTimeMillis)}-${DateKeyHelper.formatMillisTime12h(it.endTimeMillis)})"
    }.ifEmpty { "No sessions logged today" }

    val p = snapshot.todayProductivity
    return """
      LIVE VISION APP STATE (${DateKeyHelper.todayKey()}):
      - Progress: ${p.completedTasks}/${p.totalTasks} tasks complete (${p.productivityPercent}%), ${p.pendingTasks} pending, Focus=${p.focusMinutesToday}m, Yoga=${p.yogaMinutesToday}m (done=${p.yogaDoneToday}), Skill Dev=${p.skillMinutesToday}m.
      - Today's Activity Records: $activityText
      - Today's Tasks: $tasksText
      - Tomorrow's Planned Tasks (${snapshot.tomorrowTasks.size}): $tomorrowText
      - Next-Day Plan Note: "${snapshot.focusConfig.nextDayPlanNote}"
      - Personal Goals: $goalsText
      - Alarms, Reminders & Schedule: $alarmsText
      - Study & Focus: focusMode=${snapshot.focusConfig.focusModeEnabled}, studyMode=${snapshot.focusConfig.studyModeEnabled}, timerRunning=${snapshot.focusConfig.activeTimerRunning}
      - Monitored Apps: $appsText

      USER MESSAGE:
      "$userCommand"
    """.trimIndent()
  }
}
