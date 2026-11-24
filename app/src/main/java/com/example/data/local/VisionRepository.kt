package com.example.data.local

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

data class ProductivitySummary(
  val dateKey: String,
  val totalTasks: Int,
  val completedTasks: Int,
  val pendingTasks: Int,
  val productivityPercent: Int,
  val focusMinutesToday: Int,
  val yogaMinutesToday: Int,
  val yogaDoneToday: Boolean,
  val skillMinutesToday: Int,
  val skillDoneToday: Boolean,
)

class VisionRepository(private val dao: VisionDao) {

  private val tag = "VisionRepository"

  fun observeTodayTasks(dateKey: String = DateKeyHelper.todayKey()): Flow<List<TaskEntity>> =
    dao.observeTasksForDate(dateKey).catch { e ->
      Log.e(tag, "Error observing today tasks", e)
      emit(emptyList())
    }

  fun observeTomorrowTasks(dateKey: String = DateKeyHelper.tomorrowKey()): Flow<List<TaskEntity>> =
    dao.observeTasksForDate(dateKey).catch { e ->
      Log.e(tag, "Error observing tomorrow tasks", e)
      emit(emptyList())
    }

  fun observeAllPendingTasks(): Flow<List<TaskEntity>> =
    dao.observeAllPendingTasks().catch { e ->
      Log.e(tag, "Error observing pending tasks", e)
      emit(emptyList())
    }

  fun observeGoals(): Flow<List<GoalEntity>> =
    dao.observeGoals().catch { e ->
      Log.e(tag, "Error observing goals", e)
      emit(emptyList())
    }

  fun observeTodayHabits(dateKey: String = DateKeyHelper.todayKey()): Flow<List<HabitLogEntity>> =
    dao.observeHabitsForDate(dateKey).catch { e ->
      Log.e(tag, "Error observing today habits", e)
      emit(emptyList())
    }

  fun observeYogaStreakCount(): Flow<Int> =
    dao.observeCompletedHabitCount("YOGA_EXERCISE").catch { emit(0) }

  fun observeAlarms(): Flow<List<AlarmEntity>> =
    dao.observeAlarms().catch { e ->
      Log.e(tag, "Error observing alarms", e)
      emit(emptyList())
    }

  fun observeDistractingApps(): Flow<List<DistractingAppEntity>> =
    dao.observeDistractingApps().catch { e ->
      Log.e(tag, "Error observing distracting apps", e)
      emit(emptyList())
    }

  fun observeRecentFocusSessions(): Flow<List<FocusSessionEntity>> =
    dao.observeRecentFocusSessions().catch { e ->
      Log.e(tag, "Error observing focus sessions", e)
      emit(emptyList())
    }

  fun observeTodayFocusSessions(dateKey: String = DateKeyHelper.todayKey()): Flow<List<FocusSessionEntity>> =
    dao.observeFocusSessionsForDate(dateKey).catch { e ->
      Log.e(tag, "Error observing today focus sessions", e)
      emit(emptyList())
    }

  fun observeFocusConfig(): Flow<FocusConfigEntity> =
    dao.observeFocusConfig()
      .map { it ?: FocusConfigEntity() }
      .catch { e ->
        Log.e(tag, "Error observing focus config", e)
        emit(FocusConfigEntity())
      }

  fun observeTodayProductivity(dateKey: String = DateKeyHelper.todayKey()): Flow<ProductivitySummary> =
    combine(
      observeTodayTasks(dateKey),
      observeTodayFocusSessions(dateKey),
      observeTodayHabits(dateKey),
    ) { tasks, sessions, habits ->
      val total = tasks.size
      val completed = tasks.count { it.isCompleted }
      val pending = (total - completed).coerceAtLeast(0)
      val pct = if (total > 0) ((completed.toFloat() / total.toFloat()) * 100f).toInt() else 0
      val focusMins = sessions.sumOf { it.actualMinutes }
      val yoga = habits.firstOrNull { it.trackType == "YOGA_EXERCISE" }
      val skill = habits.firstOrNull { it.trackType == "SKILL_DEV" }
      ProductivitySummary(
        dateKey = dateKey,
        totalTasks = total,
        completedTasks = completed,
        pendingTasks = pending,
        productivityPercent = pct,
        focusMinutesToday = focusMins,
        yogaMinutesToday = yoga?.minutesLogged ?: 0,
        yogaDoneToday = yoga?.completed ?: false,
        skillMinutesToday = skill?.minutesLogged ?: 0,
        skillDoneToday = skill?.completed ?: false,
      )
    }

  fun observeWeeklySnapshots(days: Int = 7): Flow<List<Pair<String, DailyProductivitySnapshotEntity?>>> {
    val dayPairs = DateKeyHelper.lastNDaysKeys(days)
    val keys = dayPairs.map { it.first }
    return dao.observeSnapshotsForDates(keys)
      .map { snapshots ->
        val map = snapshots.associateBy { it.dateKey }
        dayPairs.map { (key, shortLabel) -> shortLabel to map[key] }
      }
      .catch { e ->
        Log.e(tag, "Error observing weekly snapshots", e)
        emit(dayPairs.map { it.second to null })
      }
  }

  suspend fun ensureSeeded(): Result<Unit> = runCatching {
    VisionDatabase.seedInitialStructure(dao)
  }.onFailure { Log.e(tag, "ensureSeeded failed", it) }

  suspend fun saveTask(task: TaskEntity): Result<Long> = runCatching {
    val id = if (task.id == 0L) dao.insertTask(task) else {
      dao.updateTask(task)
      task.id
    }
    syncTodaySnapshot()
    id
  }.onFailure { Log.e(tag, "saveTask failed", it) }

  suspend fun toggleTaskCompletion(task: TaskEntity): Result<Unit> = runCatching {
    val nextCompleted = !task.isCompleted
    dao.updateTask(
      task.copy(
        isCompleted = nextCompleted,
        completedAtMillis = if (nextCompleted) System.currentTimeMillis() else null,
      ),
    )
    syncTodaySnapshot().getOrThrow()
  }.onFailure { Log.e(tag, "toggleTaskCompletion failed", it) }

  suspend fun toggleTaskPriority(task: TaskEntity): Result<Unit> = runCatching {
    dao.updateTask(task.copy(isPriority = !task.isPriority))
  }.onFailure { Log.e(tag, "toggleTaskPriority failed", it) }

  suspend fun moveTaskToTomorrow(task: TaskEntity): Result<Unit> = runCatching {
    dao.updateTask(
      task.copy(
        dateKey = DateKeyHelper.tomorrowKey(),
        isCompleted = false,
        completedAtMillis = null,
      ),
    )
    syncTodaySnapshot().getOrThrow()
  }.onFailure { Log.e(tag, "moveTaskToTomorrow failed", it) }

  suspend fun deleteTask(id: Long): Result<Unit> = runCatching {
    dao.deleteTaskById(id)
    syncTodaySnapshot().getOrThrow()
  }.onFailure { Log.e(tag, "deleteTask failed", it) }

  suspend fun saveGoal(goal: GoalEntity): Result<Long> = runCatching {
    if (goal.id == 0L) dao.insertGoal(goal)
    else {
      dao.updateGoal(goal)
      goal.id
    }
  }.onFailure { Log.e(tag, "saveGoal failed", it) }

  suspend fun incrementGoal(goal: GoalEntity, delta: Int): Result<Unit> = runCatching {
    val updated = (goal.currentValue + delta).coerceIn(0, goal.targetValue)
    dao.updateGoal(goal.copy(currentValue = updated))
  }.onFailure { Log.e(tag, "incrementGoal failed", it) }

  suspend fun deleteGoal(id: Long): Result<Unit> = runCatching {
    dao.deleteGoalById(id)
  }.onFailure { Log.e(tag, "deleteGoal failed", it) }

  suspend fun logHabitProgress(
    trackType: String,
    defaultLabel: String,
    addMinutes: Int,
    markCompleted: Boolean?,
    dateKey: String = DateKeyHelper.todayKey(),
  ): Result<Unit> = runCatching {
    val existing = dao.getHabitsForDateSync(dateKey).firstOrNull { it.trackType == trackType }
    if (existing == null) {
      dao.insertHabit(
        HabitLogEntity(
          dateKey = dateKey,
          trackType = trackType,
          label = defaultLabel,
          minutesLogged = addMinutes.coerceAtLeast(0),
          completed = markCompleted ?: (addMinutes > 0),
        ),
      )
    } else {
      val nextMins = (existing.minutesLogged + addMinutes).coerceAtLeast(0)
      dao.updateHabit(
        existing.copy(
          minutesLogged = nextMins,
          completed = markCompleted ?: existing.completed,
        ),
      )
    }
    syncTodaySnapshot().getOrThrow()
  }.onFailure { Log.e(tag, "logHabitProgress failed", it) }

  suspend fun saveAlarm(alarm: AlarmEntity): Result<Long> = runCatching {
    if (alarm.id == 0L) dao.insertAlarm(alarm)
    else {
      dao.updateAlarm(alarm)
      alarm.id
    }
  }.onFailure { Log.e(tag, "saveAlarm failed", it) }

  suspend fun getAlarmById(id: Long): AlarmEntity? = runCatching {
    dao.getAlarmByIdSync(id)
  }.getOrNull()

  suspend fun getEnabledAlarms(): List<AlarmEntity> = runCatching {
    dao.getEnabledAlarmsSync()
  }.getOrDefault(emptyList())

  suspend fun deleteAlarm(id: Long): Result<Unit> = runCatching {
    dao.deleteAlarmById(id)
  }.onFailure { Log.e(tag, "deleteAlarm failed", it) }

  suspend fun getDistractingAppsSync(): List<DistractingAppEntity> = runCatching {
    dao.getDistractingAppsSync()
  }.getOrDefault(emptyList())

  suspend fun upsertDistractingApp(app: DistractingAppEntity): Result<Unit> = runCatching {
    dao.insertOrUpdateApp(app)
  }.onFailure { Log.e(tag, "upsertDistractingApp failed", it) }

  suspend fun updateAppUsages(updated: List<DistractingAppEntity>): Result<Unit> = runCatching {
    dao.insertOrUpdateApps(updated)
  }.onFailure { Log.e(tag, "updateAppUsages failed", it) }

  suspend fun getFocusConfigSync(): FocusConfigEntity = runCatching {
    dao.getFocusConfigSync() ?: FocusConfigEntity()
  }.getOrDefault(FocusConfigEntity())

  suspend fun updateFocusConfig(transform: (FocusConfigEntity) -> FocusConfigEntity): Result<FocusConfigEntity> =
    runCatching {
      val current = dao.getFocusConfigSync() ?: FocusConfigEntity()
      val next = transform(current)
      dao.upsertFocusConfig(next)
      next
    }.onFailure { Log.e(tag, "updateFocusConfig failed", it) }

  suspend fun startFocusSession(plannedMinutes: Int, studyModeEnabled: Boolean): Result<Long> = runCatching {
    val now = System.currentTimeMillis()
    val id = dao.insertFocusSession(
      FocusSessionEntity(
        dateKey = DateKeyHelper.todayKey(now),
        startTimeMillis = now,
        plannedMinutes = plannedMinutes,
        actualMinutes = 0,
        studyModeEnabled = studyModeEnabled,
        status = "RUNNING",
      ),
    )
    id
  }.onFailure { Log.e(tag, "startFocusSession failed", it) }

  suspend fun updateFocusSessionProgress(
    sessionId: Long,
    actualMinutes: Int,
    blockedDelta: Int = 0,
    warningDelta: Int = 0,
    newStatus: String? = null,
    endMillis: Long? = null,
  ): Result<Unit> = runCatching {
    val existing = dao.getFocusSessionByIdSync(sessionId) ?: return@runCatching
    dao.updateFocusSession(
      existing.copy(
        actualMinutes = actualMinutes.coerceAtLeast(existing.actualMinutes),
        blockedAttemptsCount = existing.blockedAttemptsCount + blockedDelta,
        warningsTriggeredCount = existing.warningsTriggeredCount + warningDelta,
        status = newStatus ?: existing.status,
        endTimeMillis = endMillis ?: existing.endTimeMillis,
      ),
    )
    syncTodaySnapshot().getOrThrow()
  }.onFailure { Log.e(tag, "updateFocusSessionProgress failed", it) }

  suspend fun syncTodaySnapshot(dateKey: String = DateKeyHelper.todayKey()): Result<Unit> = runCatching {
    val tasks = dao.getTasksForDateSync(dateKey)
    val sessions = dao.getFocusSessionsForDateSync(dateKey)
    val habits = dao.getHabitsForDateSync(dateKey)
    val total = tasks.size
    val completed = tasks.count { it.isCompleted }
    val pending = (total - completed).coerceAtLeast(0)
    val pct = if (total > 0) ((completed.toFloat() / total.toFloat()) * 100f).toInt() else 0
    val focusMinutes = sessions.sumOf { it.actualMinutes }
    val yogaDone = habits.firstOrNull { it.trackType == "YOGA_EXERCISE" }?.completed ?: false
    val skillMinutes = habits.firstOrNull { it.trackType == "SKILL_DEV" }?.minutesLogged ?: 0

    dao.upsertDailySnapshot(
      DailyProductivitySnapshotEntity(
        dateKey = dateKey,
        totalTasks = total,
        completedTasks = completed,
        pendingTasks = pending,
        productivityPercent = pct,
        focusMinutes = focusMinutes,
        yogaDone = yogaDone,
        skillMinutes = skillMinutes,
      ),
    )
  }.onFailure { Log.e(tag, "syncTodaySnapshot failed", it) }
}
