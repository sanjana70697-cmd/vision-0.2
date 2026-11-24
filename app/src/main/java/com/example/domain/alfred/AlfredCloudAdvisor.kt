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
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "AlfredCloudAdvisor"

/**
 * Cloud Gemini advisor via Firebase AI Logic.
 * Only used when device has internet access and complex/natural-language planning or parsing is needed.
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
          "SET_ALARM",
          "TOGGLE_ALARM",
          "START_FOCUS",
          "STOP_FOCUS",
          "SET_STUDY_MODE",
          "TOGGLE_APP_BLOCK",
          "SAVE_NEXT_DAY_NOTE",
          "ORGANIZE_DAY",
        ),
        description = "Action to perform across VISION tabs, or NONE for informational answer",
      ),
      "replyText" to Schema.string(description = "Concise, helpful text reply grounded ONLY in provided user data"),
      "spokenText" to Schema.string(description = "Natural 1-2 sentence spoken response for Text-to-Speech"),
      "targetTab" to Schema.enumeration(
        values = listOf("DASHBOARD", "ALARM", "DISTRACTION", "REPORT"),
        description = "Most relevant VISION tab",
      ),
      "taskTitle" to Schema.string(description = "Task title for create/complete/reschedule", nullable = true),
      "taskCategory" to Schema.string(description = "Wellness, Skill Dev, Study, or Personal", nullable = true),
      "taskHour24" to Schema.integer(description = "24-hour clock 0..23 for task or alarm", nullable = true),
      "taskMinute" to Schema.integer(description = "Minute 0..59 for task or alarm", nullable = true),
      "taskPriority" to Schema.boolean(description = "Whether task is priority", nullable = true),
      "taskTomorrow" to Schema.boolean(description = "True if scheduled for tomorrow", nullable = true),
      "alarmLabel" to Schema.string(description = "Alarm or reminder label", nullable = true),
      "alarmKind" to Schema.string(description = "ALARM, REMINDER, or SCHEDULE", nullable = true),
      "alarmEnabled" to Schema.boolean(description = "Target enabled state for alarm", nullable = true),
      "focusMinutes" to Schema.integer(description = "Focus timer duration in minutes", nullable = true),
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
      "taskPriority",
      "taskTomorrow",
      "alarmLabel",
      "alarmKind",
      "alarmEnabled",
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
      temperature = 0.25f
    }
    Firebase.ai.generativeModel(
      modelName = "gemini-3.8-flash",
      generationConfig = cfg,
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
    snapshot: AlfredContextSnapshot,
    coordinator: AlfredCoordinator,
  ): Result<AlfredExecutionResult> {
    if (!isNetworkOnline()) {
      return Result.failure(IllegalStateException("Device is offline."))
    }

    val prompt = buildContextPrompt(userCommand, snapshot)
    return try {
      val response = withTimeout(60_000L) {
        model.generateContent(prompt)
      }
      val jsonStr = response.text
        ?: return Result.failure(IllegalStateException("Gemini returned an empty response."))

      val json = JSONObject(jsonStr)
      val execution = applyStructuredAction(json, snapshot, coordinator)
      Result.success(execution)
    } catch (e: TimeoutCancellationException) {
      Log.e(TAG, "Gemini request timed out", e)
      Result.failure(IllegalStateException("Cloud AI timed out after 60 seconds. Offline engine remains ready."))
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Firebase AI Logic error", e)
      Result.failure(IllegalStateException(e.localizedMessage ?: "Cloud AI request failed."))
    }
  }

  private suspend fun applyStructuredAction(
    json: JSONObject,
    snapshot: AlfredContextSnapshot,
    coordinator: AlfredCoordinator,
  ): AlfredExecutionResult {
    val intentType = json.optString("intentType", "NONE")
    val replyText = json.optString("replyText", "Done.").ifBlank { "Done." }
    val spokenText = json.optString("spokenText", replyText).ifBlank { replyText }
    val suggestedTab = when (json.optString("targetTab", "DASHBOARD")) {
      "ALARM" -> VisionTab.Alarm
      "DISTRACTION" -> VisionTab.Distraction
      "REPORT" -> VisionTab.Report
      else -> VisionTab.Dashboard
    }

    val actions = mutableListOf<String>()

    when (intentType) {
      "CREATE_TASK" -> {
        val title = json.optString("taskTitle").takeIf { it.isNotBlank() && it != "null" }
        if (title != null) {
          val h = json.optInt("taskHour24", -1)
          val m = json.optInt("taskMinute", 0).coerceIn(0, 59)
          val hasTime = h in 0..23
          val tomorrow = json.optBoolean("taskTomorrow", false)
          val entity = TaskEntity(
            title = title,
            category = json.optString("taskCategory").takeIf { it.isNotBlank() && it != "null" } ?: "Personal",
            scheduledTimeText = if (hasTime) DateKeyHelper.formatFullTime12h(h, m) else "",
            scheduledMinutesOfDay = if (hasTime) h * 60 + m else -1,
            isPriority = json.optBoolean("taskPriority", false),
            isRoutine = false,
            isCompleted = false,
            dateKey = if (tomorrow) DateKeyHelper.tomorrowKey() else DateKeyHelper.todayKey(),
          )
          coordinator.createOrUpdateTask(entity)
          actions += "${if (tomorrow) "Report" else "Dashboard"} · Created task \"$title\""
        }
      }

      "COMPLETE_TASK" -> {
        val query = json.optString("taskTitle")
        val pending = snapshot.todayTasks.filter { !it.isCompleted }
        val target = pending.firstOrNull {
          query.isNotBlank() && query != "null" && it.title.contains(query, ignoreCase = true)
        } ?: AlfredAnnouncementEngine.recommendNextTask(snapshot).task
        if (target != null) {
          coordinator.toggleTaskCompleted(target)
          actions += "Dashboard · Completed \"${target.title}\""
        }
      }

      "RESCHEDULE_TASK" -> {
        val query = json.optString("taskTitle")
        val target = snapshot.allPendingTasks.firstOrNull {
          query.isNotBlank() && query != "null" && it.title.contains(query, ignoreCase = true)
        }
        if (target != null) {
          val tomorrow = json.optBoolean("taskTomorrow", true)
          val h = json.optInt("taskHour24", -1)
          val m = json.optInt("taskMinute", 0).coerceIn(0, 59)
          if (tomorrow && h !in 0..23) {
            coordinator.scheduleTaskForTomorrow(target)
            actions += "Report · Moved \"${target.title}\" to tomorrow"
          } else {
            val dateKey = if (tomorrow) DateKeyHelper.tomorrowKey() else target.dateKey
            val mins = if (h in 0..23) h * 60 + m else target.scheduledMinutesOfDay
            val text = if (h in 0..23) DateKeyHelper.formatFullTime12h(h, m) else target.scheduledTimeText
            coordinator.createOrUpdateTask(
              target.copy(dateKey = dateKey, scheduledMinutesOfDay = mins, scheduledTimeText = text),
            )
            actions += "Dashboard · Rescheduled \"${target.title}\""
          }
        }
      }

      "SET_ALARM" -> {
        val h = json.optInt("taskHour24", -1)
        val m = json.optInt("taskMinute", 0).coerceIn(0, 59)
        if (h in 0..23) {
          val kind = json.optString("alarmKind", "ALARM").let {
            if (it in listOf("ALARM", "REMINDER", "SCHEDULE")) it else "ALARM"
          }
          val label = json.optString("alarmLabel").takeIf { it.isNotBlank() && it != "null" }
            ?: "ALFRED $kind"
          coordinator.saveAndScheduleAlarm(
            AlarmEntity(
              hour24 = h,
              minute = m,
              label = label,
              kind = kind,
              isWakeUp = kind == "ALARM",
              repeatDaysCsv = "",
              enabled = true,
            ),
          )
          actions += "Alarm · Scheduled $kind at ${DateKeyHelper.formatFullTime12h(h, m)}"
        }
      }

      "TOGGLE_ALARM" -> {
        val query = json.optString("alarmLabel")
        val enabled = json.optBoolean("alarmEnabled", false)
        val target = snapshot.alarms.firstOrNull {
          query.isNotBlank() && query != "null" && it.label.contains(query, ignoreCase = true)
        } ?: snapshot.alarms.firstOrNull()
        if (target != null) {
          coordinator.toggleAlarmEnabled(target, enabled)
          actions += "Alarm · ${if (enabled) "Enabled" else "Disabled"} ${target.label}"
        }
      }

      "START_FOCUS" -> {
        val mins = json.optInt("focusMinutes", 25).coerceIn(5, 180)
        if (json.has("studyModeEnabled") && !json.isNull("studyModeEnabled")) {
          coordinator.setStudyMode(json.optBoolean("studyModeEnabled"))
        }
        coordinator.setFocusMode(true)
        coordinator.startFocusTimer(mins)
        actions += "Distraction · Started ${mins}m focus session"
      }

      "STOP_FOCUS" -> {
        coordinator.resetFocusTimer(25)
        coordinator.setFocusMode(false)
        actions += "Distraction · Stopped focus session"
      }

      "SET_STUDY_MODE" -> {
        val on = json.optBoolean("studyModeEnabled", true)
        coordinator.setStudyMode(on)
        actions += "Distraction · Study Mode ${if (on) "enabled" else "disabled"}"
      }

      "TOGGLE_APP_BLOCK" -> {
        val appName = json.optString("targetAppName")
        val wantBlocked = json.optBoolean("appBlocked", true)
        val matched = snapshot.distractingApps.firstOrNull {
          appName.isNotBlank() && appName != "null" && it.appName.contains(appName, ignoreCase = true)
        }
        if (matched != null && matched.isBlocked != wantBlocked) {
          coordinator.toggleAppBlock(matched)
          if (wantBlocked) coordinator.setFocusMode(true)
          actions += "Distraction · ${if (wantBlocked) "Blocked" else "Unblocked"} ${matched.appName}"
        }
      }

      "SAVE_NEXT_DAY_NOTE" -> {
        val note = json.optString("nextDayNote")
        if (note.isNotBlank() && note != "null") {
          coordinator.updateNextDayPlanNote(note)
          actions += "Report · Saved next-day plan note"
        }
      }

      else -> {
        // Informational response or schedule planning; no mutation needed
      }
    }

    return AlfredExecutionResult(
      replyText = replyText,
      spokenText = spokenText,
      executedActions = actions,
      suggestedTab = suggestedTab,
      usedCloudAi = true,
    )
  }

  private fun buildContextPrompt(
    userCommand: String,
    snapshot: AlfredContextSnapshot,
  ): String {
    val tasksText = snapshot.todayTasks.joinToString("; ") {
      "${it.title} (${it.scheduledTimeText.ifBlank { it.category }}, done=${it.isCompleted}, priority=${it.isPriority})"
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

    val p = snapshot.todayProductivity
    return """
      You are ALFRED, the single central AI personal assistant inside the VISION Android productivity app.
      Manage Dashboard, Alarm, Distraction, and Report coherently from the user's REAL stored data below.
      Never invent tasks, alarms, or statistics that are not listed below. Keep spokenText concise and natural.

      CURRENT STORED DATA:
      - Daily Progress: ${p.completedTasks}/${p.totalTasks} tasks complete (${p.productivityPercent}%), ${p.pendingTasks} pending, ${p.focusMinutesToday}m focused, Yoga=${p.yogaDoneToday} (${p.yogaMinutesToday}m), Skill=${p.skillMinutesToday}m.
      - Today's Tasks: $tasksText
      - Tomorrow Tasks Count: ${snapshot.tomorrowTasks.size}
      - Personal Goals: $goalsText
      - Alarms & Reminders: $alarmsText
      - Focus & Study Mode: focusMode=${snapshot.focusConfig.focusModeEnabled}, studyMode=${snapshot.focusConfig.studyModeEnabled}, timerRunning=${snapshot.focusConfig.activeTimerRunning}
      - Distracting Apps: $appsText

      USER REQUEST:
      "$userCommand"
    """.trimIndent()
  }
}
