package com.example.domain.alfred

import com.example.ui.theme.VisionTab
import kotlinx.coroutines.flow.first

/**
 * Unified ALFRED orchestrator.
 * - Maintains multi-turn conversation history when conversing via Gemini Cloud AI.
 * - Routes fast deterministic commands or offline queries through [AlfredRuleEngine].
 * - Supports explicit user confirmation for destructive/important actions via [executePendingAction].
 * - Surfaces real errors honestly without faking confirmations or offline/cloud responses.
 */
class AlfredBrain(
  private val repository: VisionRepositoryHolder,
  private val coordinator: AlfredCoordinator,
  private val cloudAdvisor: AlfredCloudAdvisor,
) {
  constructor(
    repository: com.example.data.local.VisionRepository,
    coordinator: AlfredCoordinator,
    cloudAdvisor: AlfredCloudAdvisor,
  ) : this(VisionRepositoryHolder(repository), coordinator, cloudAdvisor)

  suspend fun captureLiveSnapshot(): AlfredContextSnapshot {
    val repo = repository.value
    val todayTasks = repo.observeTodayTasks().first()
    val tomorrowTasks = repo.observeTomorrowTasks().first()
    val pendingTasks = repo.observeAllPendingTasks().first()
    val goals = repo.observeGoals().first()
    val todayHabits = repo.observeTodayHabits().first()
    val alarms = repo.observeAlarms().first()
    val distractingApps = repo.observeDistractingApps().first()
    val focusSessions = repo.observeRecentFocusSessions().first()
    val focusConfig = repo.getFocusConfigSync()
    val todayProductivity = repo.observeTodayProductivity().first()
    val weeklySnapshots = repo.observeWeeklySnapshots().first()
    val todayActivitySessions = repo.observeTodayActivitySessions().first()

    return AlfredContextSnapshot(
      todayTasks = todayTasks,
      tomorrowTasks = tomorrowTasks,
      allPendingTasks = pendingTasks,
      goals = goals,
      todayHabits = todayHabits,
      alarms = alarms,
      distractingApps = distractingApps,
      focusSessions = focusSessions,
      focusConfig = focusConfig,
      todayProductivity = todayProductivity,
      weeklySnapshots = weeklySnapshots,
      todayActivitySessions = todayActivitySessions,
    )
  }

  fun isOnline(): Boolean = cloudAdvisor.isNetworkOnline()

  suspend fun handleCommand(
    command: String,
    preferCloudReasoning: Boolean = false,
    conversationHistory: List<AlfredChatEntry> = emptyList(),
    requireConfirmation: Boolean = false,
  ): AlfredExecutionResult {
    val snapshot = captureLiveSnapshot()
    val online = cloudAdvisor.isNetworkOnline()

    // Try fast deterministic local commands first when user didn't explicitly force cloud-only
    // or when offline, EXCEPT for conversational open-ended questions which go straight to Gemini when online!
    if (!preferCloudReasoning) {
      val directMatch = AlfredRuleEngine.tryExecute(
        rawCommand = command,
        snapshot = snapshot,
        coordinator = coordinator,
        allowOpenFallback = false,
        requireConfirmation = requireConfirmation,
      )
      if (directMatch != null) return directMatch
    }

    // Engage Gemini multi-turn Cloud AI whenever online
    if (online) {
      val cloudResult = cloudAdvisor.processWithGemini(
        userCommand = command,
        conversationHistory = conversationHistory,
        snapshot = snapshot,
        coordinator = coordinator,
        requireConfirmation = requireConfirmation,
      )
      cloudResult.onSuccess { return it }

      val err = cloudResult.exceptionOrNull()?.localizedMessage ?: "Cloud AI unavailable."
      // Only fall back to deterministic rule engine if it actually matches a concrete command,
      // otherwise surface the real cloud error honestly!
      val exactOfflineAction = AlfredRuleEngine.tryExecute(
        rawCommand = command,
        snapshot = snapshot,
        coordinator = coordinator,
        allowOpenFallback = false,
        requireConfirmation = requireConfirmation,
      )
      if (exactOfflineAction != null) {
        return exactOfflineAction.copy(cloudError = err)
      }
      return AlfredExecutionResult(
        replyText = "Cloud AI couldn't answer right now ($err). I've kept your local data untouched—you can try again or use a direct command like \"Add task ...\" or \"Read daily report\".",
        spokenText = "Cloud AI is unavailable right now. Please try again shortly.",
        executedActions = emptyList(),
        usedCloudAi = false,
        cloudError = err,
      )
    }

    // Strictly offline deterministic fallback
    return AlfredRuleEngine.tryExecute(
      rawCommand = command,
      snapshot = snapshot,
      coordinator = coordinator,
      allowOpenFallback = true,
      requireConfirmation = requireConfirmation,
    )!!
  }

  suspend fun executePendingAction(pending: PendingAlfredAction): AlfredExecutionResult {
    val snapshot = captureLiveSnapshot()
    return when (val payload = pending.payload) {
      is AlfredActionPayload.DeleteTask -> {
        val res = coordinator.deleteTask(payload.taskId)
        if (res.isSuccess) {
          val msg = "Confirmed and deleted task \"${payload.taskTitle}\"."
          AlfredExecutionResult(
            replyText = msg,
            spokenText = msg,
            executedActions = listOf("Dashboard · Deleted \"${payload.taskTitle}\""),
            suggestedTab = VisionTab.Dashboard,
          )
        } else {
          val err = res.exceptionOrNull()?.localizedMessage ?: "Delete failed."
          AlfredExecutionResult(replyText = "Could not delete task: $err", spokenText = err, executedActions = emptyList())
        }
      }

      is AlfredActionPayload.DeleteAlarm -> {
        val res = coordinator.deleteAndCancelAlarm(payload.alarmId)
        if (res.isSuccess) {
          val msg = "Confirmed and deleted \"${payload.alarmLabel}\"."
          AlfredExecutionResult(
            replyText = msg,
            spokenText = msg,
            executedActions = listOf("Alarm · Deleted \"${payload.alarmLabel}\""),
            suggestedTab = VisionTab.Alarm,
          )
        } else {
          val err = res.exceptionOrNull()?.localizedMessage ?: "Delete failed."
          AlfredExecutionResult(replyText = "Could not delete alarm: $err", spokenText = err, executedActions = emptyList())
        }
      }

      is AlfredActionPayload.DisableAlarms -> {
        val targets = snapshot.alarms.filter { it.id in payload.alarmIds && it.enabled }
        var failed = 0
        targets.forEach { alarm ->
          if (coordinator.toggleAlarmEnabled(alarm, false).isFailure) failed++
        }
        if (failed == 0 && targets.isNotEmpty()) {
          val msg = "Confirmed and disabled ${payload.summaryLabel}."
          AlfredExecutionResult(
            replyText = msg,
            spokenText = msg,
            executedActions = listOf("Alarm · Disabled ${targets.size} item(s)"),
            suggestedTab = VisionTab.Alarm,
          )
        } else if (targets.isEmpty()) {
          AlfredExecutionResult(replyText = "Those alarms are already disabled.", spokenText = "Already disabled.", executedActions = emptyList())
        } else {
          AlfredExecutionResult(replyText = "Failed to disable $failed item(s).", spokenText = "Failed to disable.", executedActions = emptyList())
        }
      }

      is AlfredActionPayload.StopFocusAndStudySession -> {
        coordinator.stopStudySession()
        val msg = "Confirmed: stopped your active Study / Focus session and turned off Focus Shield."
        AlfredExecutionResult(
          replyText = msg,
          spokenText = msg,
          executedActions = listOf("Distraction · Stopped Study / Focus session"),
          suggestedTab = VisionTab.Distraction,
        )
      }

      is AlfredActionPayload.UnblockApp -> {
        val app = snapshot.distractingApps.firstOrNull { it.packageName == payload.packageName }
        if (app == null) {
          AlfredExecutionResult(replyText = "${payload.appName} was not found.", spokenText = "App not found.", executedActions = emptyList())
        } else {
          val res = if (app.isBlocked) coordinator.toggleAppBlock(app) else Result.success(Unit)
          if (res.isSuccess) {
            val msg = "Confirmed: ${payload.appName} is now unblocked."
            AlfredExecutionResult(
              replyText = msg,
              spokenText = msg,
              executedActions = listOf("Distraction · Unblocked ${payload.appName}"),
              suggestedTab = VisionTab.Distraction,
            )
          } else {
            val err = res.exceptionOrNull()?.localizedMessage ?: "Unable to unblock."
            AlfredExecutionResult(replyText = "Could not unblock ${payload.appName}: $err", spokenText = err, executedActions = emptyList())
          }
        }
      }
    }
  }
}

class VisionRepositoryHolder(val value: com.example.data.local.VisionRepository)
