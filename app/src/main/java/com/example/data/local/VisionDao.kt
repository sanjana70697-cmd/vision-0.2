package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VisionDao {

  // ---- Tasks ----
  @Query("SELECT * FROM tasks WHERE dateKey = :dateKey ORDER BY isCompleted ASC, scheduledMinutesOfDay ASC, id ASC")
  fun observeTasksForDate(dateKey: String): Flow<List<TaskEntity>>

  @Query("SELECT * FROM tasks WHERE dateKey = :dateKey ORDER BY scheduledMinutesOfDay ASC, id ASC")
  suspend fun getTasksForDateSync(dateKey: String): List<TaskEntity>

  @Query("SELECT * FROM tasks WHERE isCompleted = 0 ORDER BY dateKey ASC, scheduledMinutesOfDay ASC")
  fun observeAllPendingTasks(): Flow<List<TaskEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertTask(task: TaskEntity): Long

  @Update
  suspend fun updateTask(task: TaskEntity)

  @Delete
  suspend fun deleteTask(task: TaskEntity)

  @Query("DELETE FROM tasks WHERE id = :id")
  suspend fun deleteTaskById(id: Long)

  // ---- Goals ----
  @Query("SELECT * FROM goals ORDER BY id ASC")
  fun observeGoals(): Flow<List<GoalEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertGoal(goal: GoalEntity): Long

  @Update
  suspend fun updateGoal(goal: GoalEntity)

  @Query("DELETE FROM goals WHERE id = :id")
  suspend fun deleteGoalById(id: Long)

  // ---- Habit Logs (Yoga & Exercise, Skill Dev) ----
  @Query("SELECT * FROM habit_logs WHERE dateKey = :dateKey ORDER BY id ASC")
  fun observeHabitsForDate(dateKey: String): Flow<List<HabitLogEntity>>

  @Query("SELECT * FROM habit_logs WHERE dateKey = :dateKey")
  suspend fun getHabitsForDateSync(dateKey: String): List<HabitLogEntity>

  @Query("SELECT COUNT(*) FROM habit_logs WHERE trackType = :trackType AND completed = 1")
  fun observeCompletedHabitCount(trackType: String): Flow<Int>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertHabit(habit: HabitLogEntity): Long

  @Update
  suspend fun updateHabit(habit: HabitLogEntity)

  // ---- Alarms ----
  @Query("SELECT * FROM alarms ORDER BY hour24 ASC, minute ASC, id ASC")
  fun observeAlarms(): Flow<List<AlarmEntity>>

  @Query("SELECT * FROM alarms WHERE enabled = 1")
  suspend fun getEnabledAlarmsSync(): List<AlarmEntity>

  @Query("SELECT * FROM alarms WHERE id = :id LIMIT 1")
  suspend fun getAlarmByIdSync(id: Long): AlarmEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAlarm(alarm: AlarmEntity): Long

  @Update
  suspend fun updateAlarm(alarm: AlarmEntity)

  @Query("DELETE FROM alarms WHERE id = :id")
  suspend fun deleteAlarmById(id: Long)

  // ---- Distracting Apps ----
  @Query("SELECT * FROM distracting_apps ORDER BY isSelectedForMonitoring DESC, todayUsageMinutes DESC, appName ASC")
  fun observeDistractingApps(): Flow<List<DistractingAppEntity>>

  @Query("SELECT * FROM distracting_apps")
  suspend fun getDistractingAppsSync(): List<DistractingAppEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertOrUpdateApps(apps: List<DistractingAppEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertOrUpdateApp(app: DistractingAppEntity)

  // ---- Focus Sessions ----
  @Query("SELECT * FROM focus_sessions ORDER BY startTimeMillis DESC LIMIT 20")
  fun observeRecentFocusSessions(): Flow<List<FocusSessionEntity>>

  @Query("SELECT * FROM focus_sessions WHERE dateKey = :dateKey ORDER BY startTimeMillis DESC")
  fun observeFocusSessionsForDate(dateKey: String): Flow<List<FocusSessionEntity>>

  @Query("SELECT * FROM focus_sessions WHERE dateKey = :dateKey")
  suspend fun getFocusSessionsForDateSync(dateKey: String): List<FocusSessionEntity>

  @Query("SELECT * FROM focus_sessions WHERE id = :id LIMIT 1")
  suspend fun getFocusSessionByIdSync(id: Long): FocusSessionEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertFocusSession(session: FocusSessionEntity): Long

  @Update
  suspend fun updateFocusSession(session: FocusSessionEntity)

  // ---- Focus & App Config ----
  @Query("SELECT * FROM focus_config WHERE id = 1 LIMIT 1")
  fun observeFocusConfig(): Flow<FocusConfigEntity?>

  @Query("SELECT * FROM focus_config WHERE id = 1 LIMIT 1")
  suspend fun getFocusConfigSync(): FocusConfigEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertFocusConfig(config: FocusConfigEntity)

  // ---- Daily Productivity Snapshots ----
  @Query("SELECT * FROM daily_productivity_snapshots WHERE dateKey IN (:dateKeys) ORDER BY dateKey ASC")
  fun observeSnapshotsForDates(dateKeys: List<String>): Flow<List<DailyProductivitySnapshotEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertDailySnapshot(snapshot: DailyProductivitySnapshotEntity)
}
