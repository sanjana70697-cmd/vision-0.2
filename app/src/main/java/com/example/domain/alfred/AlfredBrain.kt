package com.example.domain.alfred

import com.example.data.local.VisionRepository
import kotlinx.coroutines.flow.first

/**
 * Unified ALFRED orchestrator.
 * - Routes deterministic commands through [AlfredRuleEngine] offline immediately.
 * - Routes open-ended planning or nuanced natural-language commands through [AlfredCloudAdvisor] when online.
 * - Falls back gracefully to [AlfredRuleEngine] when offline, while surfacing real cloud errors transparently.
 */
class AlfredBrain(
  private val repository: VisionRepository,
  private val coordinator: AlfredCoordinator,
  private val cloudAdvisor: AlfredCloudAdvisor,
) {

  suspend fun captureLiveSnapshot(): AlfredContextSnapshot {
    val todayTasks = repository.observeTodayTasks().first()
    val tomorrowTasks = repository.observeTomorrowTasks().first()
    val pendingTasks = repository.observeAllPendingTasks().first()
    val goals = repository.observeGoals().first()
    val todayHabits = repository.observeTodayHabits().first()
    val alarms = repository.observeAlarms().first()
    val distractingApps = repository.observeDistractingApps().first()
    val focusSessions = repository.observeRecentFocusSessions().first()
    val focusConfig = repository.getFocusConfigSync()
    val todayProductivity = repository.observeTodayProductivity().first()
    val weeklySnapshots = repository.observeWeeklySnapshots().first()

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
    )
  }

  fun isOnline(): Boolean = cloudAdvisor.isNetworkOnline()

  suspend fun handleCommand(
    command: String,
    preferCloudReasoning: Boolean = false,
  ): AlfredExecutionResult {
    val snapshot = captureLiveSnapshot()

    // 1. Try deterministic offline rule engine first for crisp instant command execution
    if (!preferCloudReasoning) {
      val directMatch = AlfredRuleEngine.tryExecute(
        rawCommand = command,
        snapshot = snapshot,
        coordinator = coordinator,
        allowOpenFallback = false,
      )
      if (directMatch != null) return directMatch
    }

    // 2. If online, engage Firebase AI Logic Gemini for natural-language planning & multi-tab understanding
    if (cloudAdvisor.isNetworkOnline()) {
      val cloudResult = cloudAdvisor.processWithGemini(command, snapshot, coordinator)
      cloudResult.onSuccess { return it }

      val err = cloudResult.exceptionOrNull()?.localizedMessage ?: "Cloud AI unavailable."
      // Still try deterministic offline execution if cloud failed so user isn't blocked, while reporting cloudError!
      val offlineBest = AlfredRuleEngine.tryExecute(
        rawCommand = command,
        snapshot = snapshot,
        coordinator = coordinator,
        allowOpenFallback = true,
      )!!
      return offlineBest.copy(cloudError = err)
    }

    // 3. Strictly offline deterministic fallback
    return AlfredRuleEngine.tryExecute(
      rawCommand = command,
      snapshot = snapshot,
      coordinator = coordinator,
      allowOpenFallback = true,
    )!!
  }
}
