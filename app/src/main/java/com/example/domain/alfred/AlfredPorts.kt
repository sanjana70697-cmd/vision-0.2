package com.example.domain.alfred

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
import kotlinx.coroutines.flow.Flow

/**
 * Reusable domain interfaces for the central ALFRED AI Assistant.
 * All four tabs (Dashboard, Alarm, Distraction, Report) share implementations of these ports.
 */

interface TaskManagementPort {
  fun observeTodayTasks(): Flow<List<TaskEntity>>
  fun observePendingTasks(): Flow<List<TaskEntity>>
  fun observeGoals(): Flow<List<GoalEntity>>
  suspend fun createOrUpdateTask(task: TaskEntity): Result<Long>
  suspend fun toggleTaskCompleted(task: TaskEntity): Result<Unit>
  suspend fun deleteTask(taskId: Long): Result<Unit>
  suspend fun createOrUpdateGoal(goal: GoalEntity): Result<Long>
  suspend fun adjustGoalProgress(goal: GoalEntity, delta: Int): Result<Unit>
  suspend fun deleteGoal(goalId: Long): Result<Unit>
  suspend fun logHabit(
    trackType: String,
    defaultLabel: String,
    addMinutes: Int,
    completed: Boolean?,
    updatedNotes: String? = null,
  ): Result<Unit>
}

interface ScheduleManagementPort {
  fun observeTomorrowTasks(): Flow<List<TaskEntity>>
  fun observeTodayHabits(): Flow<List<HabitLogEntity>>
  suspend fun scheduleTaskForTomorrow(task: TaskEntity): Result<Unit>
  suspend fun carryForwardTasksToTomorrow(tasks: List<TaskEntity>): Result<CarryForwardOutcome>
  suspend fun updateNextDayPlanNote(note: String): Result<FocusConfigEntity>
}

interface AlarmManagementPort {
  fun observeAlarms(): Flow<List<AlarmEntity>>
  suspend fun saveAndScheduleAlarm(alarm: AlarmEntity): Result<Long>
  suspend fun toggleAlarmEnabled(alarm: AlarmEntity, enabled: Boolean): Result<Unit>
  suspend fun deleteAndCancelAlarm(alarmId: Long): Result<Unit>
}

interface FocusManagementPort {
  fun observeFocusConfig(): Flow<FocusConfigEntity>
  fun observeDistractingApps(): Flow<List<DistractingAppEntity>>
  fun observeRecentFocusSessions(): Flow<List<FocusSessionEntity>>
  suspend fun setFocusMode(enabled: Boolean): Result<Unit>
  suspend fun setStudyMode(enabled: Boolean): Result<Unit>
  suspend fun setBlockDuration(minutes: Int): Result<Unit>
  suspend fun setGracePeriod(seconds: Int): Result<Unit>
  suspend fun toggleAppBlock(app: DistractingAppEntity): Result<Unit>
  suspend fun addOrSelectDistractingApp(packageName: String, appName: String, shortTag: String): Result<Unit>
  suspend fun refreshRealAppUsage(): Result<Unit>
  fun startFocusTimer(plannedMinutes: Int)
  fun pauseFocusTimer()
  fun resetFocusTimer(plannedMinutes: Int)
  fun startStudySession(plannedMinutes: Int)
  fun stopStudySession()
  fun dismissStudyModeWarning()
}

interface ProductivityAnalysisPort {
  fun observeTodayProductivity(): Flow<ProductivitySummary>
  fun observeWeeklySnapshots(): Flow<List<Pair<String, DailyProductivitySnapshotEntity?>>>
  fun observeTodayActivitySessions(): Flow<List<ActivitySessionEntity>>
  fun observeRecentActivitySessions(): Flow<List<ActivitySessionEntity>>
  suspend fun saveActivitySession(session: ActivitySessionEntity): Result<Long>
  suspend fun deleteActivitySession(sessionId: Long): Result<Unit>
  suspend fun syncProductivitySnapshot(): Result<Unit>
}

interface NotificationManagementPort {
  suspend fun updateNotificationRule(
    gentleChime: Boolean? = null,
    aiBriefing: Boolean? = null,
    focusSafeSnooze: Boolean? = null,
  ): Result<Unit>

  fun sendScheduleReminderNow(title: String, body: String)
}
