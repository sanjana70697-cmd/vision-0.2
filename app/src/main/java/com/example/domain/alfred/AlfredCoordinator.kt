package com.example.domain.alfred

import android.content.Context
import com.example.data.local.ActivitySessionEntity
import com.example.data.local.AlarmEntity
import com.example.data.local.CarryForwardOutcome
import com.example.data.local.DailyProductivitySnapshotEntity
import com.example.data.local.DistractingAppEntity
import com.example.data.local.FocusConfigEntity
import com.example.data.local.FocusSessionEntity
import com.example.data.local.GoalEntity
import com.example.data.local.HabitLogEntity
import com.example.data.local.ProductivitySummary
import com.example.data.local.TaskEntity
import com.example.data.local.VisionRepository
import com.example.system.AlarmScheduler
import com.example.system.AlfredAnnouncementScheduler
import com.example.system.FocusEnforcementPrefs
import com.example.system.FocusMonitorService
import com.example.system.UsageStatsHelper
import com.example.system.VisionNotificationHelper
import com.example.worker.ProductivitySyncWorker
import kotlinx.coroutines.flow.Flow

/**
 * Single shared coordinator implementing all six ALFRED AI foundation ports.
 * All four tabs communicate through this single coordinator and local Room repository.
 */
class AlfredCoordinator(
  private val appContext: Context,
  private val repository: VisionRepository,
) : TaskManagementPort,
  ScheduleManagementPort,
  AlarmManagementPort,
  FocusManagementPort,
  ProductivityAnalysisPort,
  NotificationManagementPort {

  // ---- TaskManagementPort ----
  override fun observeTodayTasks(): Flow<List<TaskEntity>> = repository.observeTodayTasks()

  override fun observePendingTasks(): Flow<List<TaskEntity>> = repository.observeAllPendingTasks()

  override fun observeGoals(): Flow<List<GoalEntity>> = repository.observeGoals()

  override suspend fun createOrUpdateTask(task: TaskEntity): Result<Long> =
    repository.saveTask(task).also {
      if (it.isSuccess) {
        runCatching { AlfredAnnouncementScheduler.rescheduleSmartAlerts(appContext) }
        ProductivitySyncWorker.enqueuePeriodicAndImmediate(appContext)
      }
    }

  override suspend fun toggleTaskCompleted(task: TaskEntity): Result<Unit> =
    repository.toggleTaskCompletion(task).also {
      if (it.isSuccess) {
        runCatching { AlfredAnnouncementScheduler.rescheduleSmartAlerts(appContext) }
        ProductivitySyncWorker.enqueuePeriodicAndImmediate(appContext)
      }
    }

  override suspend fun deleteTask(taskId: Long): Result<Unit> =
    repository.deleteTask(taskId).also {
      if (it.isSuccess) {
        runCatching { AlfredAnnouncementScheduler.rescheduleSmartAlerts(appContext) }
        ProductivitySyncWorker.enqueuePeriodicAndImmediate(appContext)
      }
    }

  override suspend fun createOrUpdateGoal(goal: GoalEntity): Result<Long> =
    repository.saveGoal(goal)

  override suspend fun adjustGoalProgress(goal: GoalEntity, delta: Int): Result<Unit> =
    repository.incrementGoal(goal, delta)

  override suspend fun deleteGoal(goalId: Long): Result<Unit> =
    repository.deleteGoal(goalId)

  override suspend fun logHabit(
    trackType: String,
    defaultLabel: String,
    addMinutes: Int,
    completed: Boolean?,
    updatedNotes: String?,
  ): Result<Unit> = repository.logHabitProgress(
    trackType = trackType,
    defaultLabel = defaultLabel,
    addMinutes = addMinutes,
    markCompleted = completed,
    updatedNotes = updatedNotes,
  )

  // ---- ScheduleManagementPort ----
  override fun observeTomorrowTasks(): Flow<List<TaskEntity>> = repository.observeTomorrowTasks()

  override fun observeTodayHabits(): Flow<List<HabitLogEntity>> = repository.observeTodayHabits()

  override suspend fun scheduleTaskForTomorrow(task: TaskEntity): Result<Unit> =
    repository.moveTaskToTomorrow(task)

  override suspend fun carryForwardTasksToTomorrow(tasks: List<TaskEntity>): Result<CarryForwardOutcome> =
    repository.carryForwardTasksToTomorrow(tasks)

  override suspend fun updateNextDayPlanNote(note: String): Result<FocusConfigEntity> =
    repository.updateFocusConfig { it.copy(nextDayPlanNote = note) }

  // ---- AlarmManagementPort ----
  override fun observeAlarms(): Flow<List<AlarmEntity>> = repository.observeAlarms()

  override suspend fun saveAndScheduleAlarm(alarm: AlarmEntity): Result<Long> =
    repository.saveAlarm(alarm).map { savedId ->
      val persisted = alarm.copy(id = savedId)
      if (persisted.enabled) {
        val nextTrigger = AlarmScheduler.schedule(appContext, persisted)
        repository.saveAlarm(persisted.copy(nextTriggerMillis = nextTrigger))
      } else {
        AlarmScheduler.cancel(appContext, savedId)
      }
      savedId
    }

  override suspend fun toggleAlarmEnabled(alarm: AlarmEntity, enabled: Boolean): Result<Unit> {
    val updated = if (enabled) {
      val nextTrigger = AlarmScheduler.schedule(appContext, alarm.copy(enabled = true))
      alarm.copy(enabled = true, nextTriggerMillis = nextTrigger)
    } else {
      AlarmScheduler.cancel(appContext, alarm.id)
      alarm.copy(enabled = false, nextTriggerMillis = 0L)
    }
    return repository.saveAlarm(updated).map { }
  }

  override suspend fun deleteAndCancelAlarm(alarmId: Long): Result<Unit> {
    AlarmScheduler.cancel(appContext, alarmId)
    return repository.deleteAlarm(alarmId)
  }

  // ---- FocusManagementPort ----
  override fun observeFocusConfig(): Flow<FocusConfigEntity> = repository.observeFocusConfig()

  override fun observeDistractingApps(): Flow<List<DistractingAppEntity>> =
    repository.observeDistractingApps()

  override fun observeRecentFocusSessions(): Flow<List<FocusSessionEntity>> =
    repository.observeRecentFocusSessions()

  override suspend fun setFocusMode(enabled: Boolean): Result<Unit> =
    repository.updateFocusConfig {
      it.copy(
        focusModeEnabled = enabled,
        blockUntilMillis = if (enabled) System.currentTimeMillis() + it.blockDurationMinutes * 60_000L else 0L,
      )
    }.map { nextCfg ->
      FocusEnforcementPrefs.syncFromRoom(appContext, nextCfg, repository.getDistractingAppsSync())
    }

  override suspend fun setStudyMode(enabled: Boolean): Result<Unit> =
    repository.updateFocusConfig {
      it.copy(
        studyModeEnabled = enabled,
        studyWarningActive = if (!enabled) false else it.studyWarningActive,
        studyWarningMessage = if (!enabled) "" else it.studyWarningMessage,
      )
    }.map { nextCfg ->
      if (!enabled) FocusEnforcementPrefs.setYoutubeRestrictedNow(appContext, false)
      FocusEnforcementPrefs.syncFromRoom(appContext, nextCfg, repository.getDistractingAppsSync())
    }

  override suspend fun setBlockDuration(minutes: Int): Result<Unit> =
    repository.updateFocusConfig {
      it.copy(
        blockDurationMinutes = minutes,
        blockUntilMillis = if (it.focusModeEnabled) System.currentTimeMillis() + minutes * 60_000L else it.blockUntilMillis,
      )
    }.map { nextCfg ->
      FocusEnforcementPrefs.syncFromRoom(appContext, nextCfg, repository.getDistractingAppsSync())
    }

  override suspend fun setGracePeriod(seconds: Int): Result<Unit> =
    repository.updateFocusConfig { it.copy(gracePeriodSeconds = seconds) }.map { }

  override suspend fun toggleAppBlock(app: DistractingAppEntity): Result<Unit> =
    repository.upsertDistractingApp(app.copy(isBlocked = !app.isBlocked)).also {
      FocusEnforcementPrefs.syncFromRoom(
        appContext,
        repository.getFocusConfigSync(),
        repository.getDistractingAppsSync(),
      )
    }

  override suspend fun addOrSelectDistractingApp(
    packageName: String,
    appName: String,
    shortTag: String,
  ): Result<Unit> = repository.upsertDistractingApp(
    DistractingAppEntity(
      packageName = packageName,
      appName = appName,
      shortTag = shortTag,
      isSelectedForMonitoring = true,
      isBlocked = true,
      allowEducational = packageName == UsageStatsHelper.YOUTUBE_PACKAGE,
      dailyLimitMinutes = 30,
      todayUsageMinutes = 0,
    ),
  ).also {
    refreshRealAppUsage()
  }

  override suspend fun refreshRealAppUsage(): Result<Unit> = runCatching {
    val apps = repository.getDistractingAppsSync()
    if (!UsageStatsHelper.isUsageAccessGranted(appContext)) return@runCatching
    val usageMap = UsageStatsHelper.queryTodayAppUsageMinutes(
      appContext,
      apps.map { it.packageName }.toSet(),
    )
    val now = System.currentTimeMillis()
    val updated = apps.map { app ->
      app.copy(
        todayUsageMinutes = usageMap[app.packageName] ?: 0,
        lastSyncedMillis = now,
      )
    }
    repository.updateAppUsages(updated)
    FocusEnforcementPrefs.syncFromRoom(appContext, repository.getFocusConfigSync(), updated)
  }

  override fun startFocusTimer(plannedMinutes: Int) {
    FocusMonitorService.startFocus(appContext, plannedMinutes)
  }

  override fun pauseFocusTimer() {
    FocusMonitorService.pauseFocus(appContext)
  }

  override fun resetFocusTimer(plannedMinutes: Int) {
    FocusMonitorService.resetFocus(appContext, plannedMinutes)
  }

  override fun startStudySession(plannedMinutes: Int) {
    FocusMonitorService.startStudySession(appContext, plannedMinutes)
  }

  override fun stopStudySession() {
    FocusMonitorService.stopStudySession(appContext)
  }

  override fun dismissStudyModeWarning() {
    FocusMonitorService.cancelStudyWarning(appContext)
  }

  // ---- ProductivityAnalysisPort ----
  override fun observeTodayProductivity(): Flow<ProductivitySummary> =
    repository.observeTodayProductivity()

  override fun observeWeeklySnapshots(): Flow<List<Pair<String, DailyProductivitySnapshotEntity?>>> =
    repository.observeWeeklySnapshots()

  override fun observeTodayActivitySessions(): Flow<List<ActivitySessionEntity>> =
    repository.observeTodayActivitySessions()

  override fun observeRecentActivitySessions(): Flow<List<ActivitySessionEntity>> =
    repository.observeRecentActivitySessions()

  override suspend fun saveActivitySession(session: ActivitySessionEntity): Result<Long> =
    repository.saveActivitySession(session).also {
      if (it.isSuccess) ProductivitySyncWorker.enqueuePeriodicAndImmediate(appContext)
    }

  override suspend fun deleteActivitySession(sessionId: Long): Result<Unit> =
    repository.deleteActivitySession(sessionId).also {
      if (it.isSuccess) ProductivitySyncWorker.enqueuePeriodicAndImmediate(appContext)
    }

  override suspend fun syncProductivitySnapshot(): Result<Unit> =
    repository.syncTodaySnapshot()

  // ---- NotificationManagementPort ----
  override suspend fun updateNotificationRule(
    gentleChime: Boolean?,
    aiBriefing: Boolean?,
    focusSafeSnooze: Boolean?,
  ): Result<Unit> = repository.updateFocusConfig { cfg ->
    cfg.copy(
      gentleEscalatingChime = gentleChime ?: cfg.gentleEscalatingChime,
      aiMorningBriefing = aiBriefing ?: cfg.aiMorningBriefing,
      focusSafeSnoozeLimit = focusSafeSnooze ?: cfg.focusSafeSnoozeLimit,
    )
  }.map { }

  override fun sendScheduleReminderNow(title: String, body: String) {
    VisionNotificationHelper.postReminderNotification(
      context = appContext,
      notificationId = 7100,
      title = title,
      body = body,
    )
  }
}
