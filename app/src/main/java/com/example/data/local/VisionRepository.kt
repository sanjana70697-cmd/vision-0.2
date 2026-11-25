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

data class CarryForwardOutcome(
  val movedCount: Int,
  val skippedDuplicatesCount: Int,
  val movedTitles: List<String>,
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

  fun observeTodayActivitySessions(dateKey: String = DateKeyHelper.todayKey()): Flow<List<ActivitySessionEntity>> =
    dao.observeActivitySessionsForDate(dateKey).catch { e ->
      Log.e(tag, "Error observing today activity sessions", e)
      emit(emptyList())
    }

  fun observeRecentActivitySessions(): Flow<List<ActivitySessionEntity>> =
    dao.observeRecentActivitySessions().catch { e ->
      Log.e(tag, "Error observing recent activity sessions", e)
      emit(emptyList())
    }

  fun observeYogaStreakCount(): Flow<Int> =
    combine(
      dao.observeCompletedHabitCount("YOGA_EXERCISE").catch { emit(0) },
      dao.observeYogaActiveDaysCount().catch { emit(0) },
    ) { habitCompletedDays, sessionDays ->
      maxOf(habitCompletedDays, sessionDays)
    }

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
      observeTodayActivitySessions(dateKey),
    ) { tasks, focusSessions, habits, activitySessions ->
      val total = tasks.size
      val completed = tasks.count { it.isCompleted }
      val pending = (total - completed).coerceAtLeast(0)
      val pct = if (total > 0) ((completed.toFloat() / total.toFloat()) * 100f).toInt() else 0

      val savedFocusMins = activitySessions.filter { it.activityType == "FOCUS" }.sumOf { it.durationMinutes }
      val runningFocusMins = focusSessions.filter { it.status == "RUNNING" }.sumOf { it.actualMinutes }
      val focusMins = savedFocusMins + runningFocusMins

      val yogaHabit = habits.firstOrNull { it.trackType == "YOGA_EXERCISE" }
      val skillHabit = habits.firstOrNull { it.trackType == "SKILL_DEV" }
      val yogaMins = activitySessions.filter { it.activityType == "YOGA" }.sumOf { it.durationMinutes }
      val skillMins = activitySessions.filter { it.activityType == "SKILL" }.sumOf { it.durationMinutes }

      ProductivitySummary(
        dateKey = dateKey,
        totalTasks = total,
        completedTasks = completed,
        pendingTasks = pending,
        productivityPercent = pct,
        focusMinutesToday = focusMins,
        yogaMinutesToday = yogaMins,
        yogaDoneToday = yogaMins > 0 || (yogaHabit?.completed == true),
        skillMinutesToday = skillMins,
        skillDoneToday = skillMins > 0 || (skillHabit?.completed == true),
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

  // ---- ALFRED Chat Persistence ----
  fun observeConversationIds(): Flow<List<String>> =
    dao.observeConversationIds().catch { emit(emptyList()) }

  fun observeChatMessages(conversationId: String): Flow<List<AlfredChatMessageEntity>> =
    dao.observeChatMessages(conversationId).catch { e ->
      Log.e(tag, "Error observing chat messages", e)
      emit(emptyList())
    }

  suspend fun getChatMessagesSync(conversationId: String): List<AlfredChatMessageEntity> =
    runCatching { dao.getChatMessagesSync(conversationId) }.getOrDefault(emptyList())

  suspend fun getLatestConversationIdSync(): String? =
    runCatching { dao.getLatestConversationIdSync() }.getOrNull()

  suspend fun appendChatMessage(
    conversationId: String,
    isUser: Boolean,
    text: String,
    sourceLabel: String,
    executedActions: List<String> = emptyList(),
    timestampMillis: Long = System.currentTimeMillis(),
  ): Result<Long> = runCatching {
    dao.insertChatMessage(
      AlfredChatMessageEntity(
        conversationId = conversationId,
        isUser = isUser,
        text = text,
        sourceLabel = sourceLabel,
        executedActionsSerialized = executedActions.joinToString("\n"),
        timestampMillis = timestampMillis,
      ),
    )
  }.onFailure { Log.e(tag, "appendChatMessage failed", it) }

  suspend fun deleteConversation(conversationId: String): Result<Unit> = runCatching {
    dao.deleteConversationMessages(conversationId)
  }.onFailure { Log.e(tag, "deleteConversation failed", it) }

  suspend fun clearAllChatHistory(): Result<Unit> = runCatching {
    dao.deleteAllChatMessages()
  }.onFailure { Log.e(tag, "clearAllChatHistory failed", it) }

  suspend fun ensureSeeded(): Result<Unit> = runCatching {
    VisionDatabase.seedInitialStructure(dao)
  }.onFailure { Log.e(tag, "ensureSeeded failed", it) }

  suspend fun saveTask(task: TaskEntity): Result<Long> = runCatching {
    val normalizedTitle = task.title.trim()
    require(normalizedTitle.isNotEmpty()) { "Please enter a task title." }

    val existingForDate = dao.getTasksForDateSync(task.dateKey)
    val duplicate = existingForDate.firstOrNull {
      it.id != task.id && it.title.trim().equals(normalizedTitle, ignoreCase = true)
    }
    if (duplicate != null) {
      throw IllegalStateException("Task \"$normalizedTitle\" already exists for ${task.dateKey}.")
    }

    val sanitized = task.copy(title = normalizedTitle)
    val id = if (sanitized.id == 0L) {
      dao.insertTask(sanitized)
    } else {
      dao.updateTask(sanitized)
      sanitized.id
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
    val outcome = carryForwardTasksToTomorrow(listOf(task)).getOrThrow()
    if (outcome.movedCount == 0 && outcome.skippedDuplicatesCount > 0) {
      throw IllegalStateException("\"${task.title.trim()}\" is already in tomorrow's plan.")
    }
  }.onFailure { Log.e(tag, "moveTaskToTomorrow failed", it) }

  suspend fun carryForwardTasksToTomorrow(tasks: List<TaskEntity>): Result<CarryForwardOutcome> = runCatching {
    val tomorrowKey = DateKeyHelper.tomorrowKey()
    val existingTomorrow = dao.getTasksForDateSync(tomorrowKey)
    val tomorrowTitles = existingTomorrow
      .map { it.title.trim().lowercase() }
      .toMutableSet()

    var movedCount = 0
    var skippedDuplicates = 0
    val movedTitles = mutableListOf<String>()

    tasks.forEach { task ->
      if (task.dateKey == tomorrowKey) {
        skippedDuplicates++
      } else {
        val key = task.title.trim().lowercase()
        if (key in tomorrowTitles) {
          skippedDuplicates++
        } else {
          dao.updateTask(
            task.copy(
              dateKey = tomorrowKey,
              isCompleted = false,
              completedAtMillis = null,
            ),
          )
          tomorrowTitles += key
          movedCount++
          movedTitles += task.title.trim()
        }
      }
    }

    syncTodaySnapshot().getOrThrow()
    CarryForwardOutcome(
      movedCount = movedCount,
      skippedDuplicatesCount = skippedDuplicates,
      movedTitles = movedTitles,
    )
  }.onFailure { Log.e(tag, "carryForwardTasksToTomorrow failed", it) }

  suspend fun deleteTask(id: Long): Result<Unit> = runCatching {
    dao.deleteTaskById(id)
    syncTodaySnapshot().getOrThrow()
  }.onFailure { Log.e(tag, "deleteTask failed", it) }

  suspend fun saveGoal(goal: GoalEntity): Result<Long> = runCatching {
    val cleanTitle = goal.title.trim()
    require(cleanTitle.isNotEmpty()) { "Please enter a goal title." }
    val sanitized = goal.copy(title = cleanTitle)
    if (sanitized.id == 0L) dao.insertGoal(sanitized)
    else {
      dao.updateGoal(sanitized)
      sanitized.id
    }
  }.onFailure { Log.e(tag, "saveGoal failed", it) }

  suspend fun incrementGoal(goal: GoalEntity, delta: Int): Result<Unit> = runCatching {
    val updated = (goal.currentValue + delta).coerceIn(0, goal.targetValue)
    dao.updateGoal(goal.copy(currentValue = updated))
  }.onFailure { Log.e(tag, "incrementGoal failed", it) }

  suspend fun deleteGoal(id: Long): Result<Unit> = runCatching {
    dao.deleteGoalById(id)
  }.onFailure { Log.e(tag, "deleteGoal failed", it) }

  // ---- Activity Sessions CRUD & Validation (Single Source of Truth) ----
  suspend fun saveActivitySession(session: ActivitySessionEntity): Result<Long> = runCatching {
    require(session.activityType in listOf("FOCUS", "YOGA", "SKILL")) {
      "Unsupported activity category."
    }
    require(DateKeyHelper.parseDateKeyToCalendar(session.dateKey) != null) {
      "Please select a valid activity date."
    }
    val cleanTitle = session.titleOrSkillName.trim()
    require(cleanTitle.isNotEmpty()) {
      if (session.activityType == "SKILL") "Please enter a skill name."
      else "Please enter an activity title."
    }
    require(session.durationMinutes in 1..720) {
      "Duration must be between 1 and 720 minutes."
    }
    require(session.endTimeMillis > session.startTimeMillis) {
      "End time must be after start time."
    }

    val existingSameDateAndType = dao.getActivitySessionsForDateAndTypeSync(
      dateKey = session.dateKey,
      activityType = session.activityType,
    )
    existingSameDateAndType.forEach { existing ->
      if (existing.id != session.id) {
        val sameWindow = existing.startTimeMillis == session.startTimeMillis &&
          existing.endTimeMillis == session.endTimeMillis
        if (sameWindow && existing.titleOrSkillName.trim().equals(cleanTitle, ignoreCase = true)) {
          throw IllegalStateException("A duplicate \"$cleanTitle\" session at this time already exists.")
        }
        val overlaps = session.startTimeMillis < existing.endTimeMillis &&
          session.endTimeMillis > existing.startTimeMillis
        if (overlaps) {
          val range = "${DateKeyHelper.formatMillisTime12h(existing.startTimeMillis)} – ${DateKeyHelper.formatMillisTime12h(existing.endTimeMillis)}"
          throw IllegalStateException("Overlaps with existing \"${existing.titleOrSkillName}\" ($range).")
        }
      }
    }

    val sanitized = session.copy(
      titleOrSkillName = cleanTitle,
      notes = session.notes.trim(),
    )
    val id = if (sanitized.id == 0L) {
      dao.insertActivitySession(sanitized)
    } else {
      dao.updateActivitySession(sanitized)
      sanitized.id
    }
    recalculateHabitAndSnapshot(sanitized.dateKey)
    id
  }.onFailure { Log.e(tag, "saveActivitySession failed", it) }

  suspend fun deleteActivitySession(sessionId: Long): Result<Unit> = runCatching {
    val existing = dao.getActivitySessionByIdSync(sessionId) ?: return@runCatching
    dao.deleteActivitySessionById(sessionId)
    recalculateHabitAndSnapshot(existing.dateKey)
  }.onFailure { Log.e(tag, "deleteActivitySession failed", it) }

  suspend fun logHabitProgress(
    trackType: String,
    defaultLabel: String,
    addMinutes: Int,
    markCompleted: Boolean?,
    dateKey: String = DateKeyHelper.todayKey(),
    updatedNotes: String? = null,
    skillOrTitleOverride: String? = null,
  ): Result<Unit> = runCatching {
    val activityType = if (trackType == "YOGA_EXERCISE") "YOGA" else "SKILL"
    if (addMinutes > 0) {
      val duration = addMinutes.coerceIn(1, 720)
      val durationMs = duration * 60_000L
      val existingSessions = dao.getActivitySessionsForDateAndTypeSync(dateKey, activityType)
      val now = System.currentTimeMillis()
      val baseEnd = if (dateKey == DateKeyHelper.todayKey(now)) {
        now
      } else {
        DateKeyHelper.combineDateKeyAndTime(dateKey, 9, 0) + durationMs
      }
      var candidateEnd = baseEnd
      var candidateStart = candidateEnd - durationMs
      // Ensure automatic quick-logs never collide with an existing session window on the same day
      val latestEnd = existingSessions.maxOfOrNull { it.endTimeMillis }
      if (latestEnd != null && candidateStart < latestEnd) {
        candidateStart = latestEnd + 1000L
        candidateEnd = candidateStart + durationMs
      }
      val title = skillOrTitleOverride?.trim()?.takeIf { it.isNotEmpty() } ?: defaultLabel
      dao.insertActivitySession(
        ActivitySessionEntity(
          activityType = activityType,
          dateKey = dateKey,
          titleOrSkillName = title,
          notes = updatedNotes.orEmpty().trim(),
          startTimeMillis = candidateStart,
          endTimeMillis = candidateEnd,
          durationMinutes = duration,
          source = "MANUAL",
          createdAtMillis = System.currentTimeMillis(),
        ),
      )
    }

    val allSessions = dao.getActivitySessionsForDateSync(dateKey)
    val totalTypeMins = allSessions.filter { it.activityType == activityType }.sumOf { it.durationMinutes }
    val existingHabit = dao.getHabitsForDateSync(dateKey).firstOrNull { it.trackType == trackType }
    if (existingHabit == null) {
      dao.insertHabit(
        HabitLogEntity(
          dateKey = dateKey,
          trackType = trackType,
          label = defaultLabel,
          minutesLogged = totalTypeMins,
          completed = markCompleted ?: (totalTypeMins > 0),
          notes = updatedNotes.orEmpty(),
        ),
      )
    } else {
      dao.updateHabit(
        existingHabit.copy(
          minutesLogged = totalTypeMins,
          completed = markCompleted ?: existingHabit.completed,
          notes = updatedNotes ?: existingHabit.notes,
        ),
      )
    }
    syncTodaySnapshot(dateKey).getOrThrow()
  }.onFailure { Log.e(tag, "logHabitProgress failed", it) }

  private suspend fun recalculateHabitAndSnapshot(dateKey: String) {
    val sessions = dao.getActivitySessionsForDateSync(dateKey)
    val yogaMins = sessions.filter { it.activityType == "YOGA" }.sumOf { it.durationMinutes }
    val skillMins = sessions.filter { it.activityType == "SKILL" }.sumOf { it.durationMinutes }
    val habits = dao.getHabitsForDateSync(dateKey)

    val existingYoga = habits.firstOrNull { it.trackType == "YOGA_EXERCISE" }
    if (existingYoga == null) {
      if (yogaMins > 0) {
        dao.insertHabit(
          HabitLogEntity(
            dateKey = dateKey,
            trackType = "YOGA_EXERCISE",
            label = "Morning Yoga & Exercise",
            minutesLogged = yogaMins,
            completed = true,
          ),
        )
      }
    } else {
      dao.updateHabit(
        existingYoga.copy(
          minutesLogged = yogaMins,
          completed = if (yogaMins > 0) true else false,
        ),
      )
    }

    val existingSkill = habits.firstOrNull { it.trackType == "SKILL_DEV" }
    if (existingSkill == null) {
      if (skillMins > 0) {
        dao.insertHabit(
          HabitLogEntity(
            dateKey = dateKey,
            trackType = "SKILL_DEV",
            label = "Skill Development",
            minutesLogged = skillMins,
            completed = true,
          ),
        )
      }
    } else {
      dao.updateHabit(
        existingSkill.copy(
          minutesLogged = skillMins,
          completed = if (skillMins > 0) true else false,
        ),
      )
    }

    syncTodaySnapshot(dateKey).getOrThrow()
  }

  suspend fun saveAlarm(alarm: AlarmEntity): Result<Long> = runCatching {
    val cleanLabel = alarm.label.trim()
    require(cleanLabel.isNotEmpty()) { "Please enter a title before saving." }
    val cleanDateKey = alarm.scheduledDateKey.trim()
    if (cleanDateKey.isNotEmpty()) {
      require(DateKeyHelper.parseDateKeyToCalendar(cleanDateKey) != null) {
        "Please select a valid date."
      }
    }
    val sanitized = alarm.copy(
      label = cleanLabel,
      scheduledDateKey = cleanDateKey,
    )
    if (sanitized.id == 0L) dao.insertAlarm(sanitized)
    else {
      dao.updateAlarm(sanitized)
      sanitized.id
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
    syncTodaySnapshot(existing.dateKey).getOrThrow()
  }.onFailure { Log.e(tag, "updateFocusSessionProgress failed", it) }

  suspend fun syncTodaySnapshot(dateKey: String = DateKeyHelper.todayKey()): Result<Unit> = runCatching {
    val tasks = dao.getTasksForDateSync(dateKey)
    val sessions = dao.getFocusSessionsForDateSync(dateKey)
    val habits = dao.getHabitsForDateSync(dateKey)
    val activitySessions = dao.getActivitySessionsForDateSync(dateKey)

    val total = tasks.size
    val completed = tasks.count { it.isCompleted }
    val pending = (total - completed).coerceAtLeast(0)
    val pct = if (total > 0) ((completed.toFloat() / total.toFloat()) * 100f).toInt() else 0

    val savedFocus = activitySessions.filter { it.activityType == "FOCUS" }.sumOf { it.durationMinutes }
    val runningFocus = sessions.filter { it.status == "RUNNING" }.sumOf { it.actualMinutes }
    val focusMinutes = savedFocus + runningFocus

    val yogaMinutes = activitySessions.filter { it.activityType == "YOGA" }.sumOf { it.durationMinutes }
    val yogaDone = yogaMinutes > 0 || (habits.firstOrNull { it.trackType == "YOGA_EXERCISE" }?.completed ?: false)
    val skillMinutes = activitySessions.filter { it.activityType == "SKILL" }.sumOf { it.durationMinutes }

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
