package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val title: String,
  val category: String,
  val scheduledTimeText: String,
  val scheduledMinutesOfDay: Int,
  val isPriority: Boolean,
  val isRoutine: Boolean,
  val isCompleted: Boolean,
  val dateKey: String,
  val completedAtMillis: Long? = null,
  val createdAtMillis: Long = System.currentTimeMillis(),
)

@Entity(tableName = "goals")
data class GoalEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val title: String,
  val subtitle: String,
  val currentValue: Int,
  val targetValue: Int,
  val unit: String,
  val category: String,
  val createdAtMillis: Long = System.currentTimeMillis(),
)

@Entity(tableName = "habit_logs")
data class HabitLogEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val dateKey: String,
  val trackType: String, // "YOGA_EXERCISE" or "SKILL_DEV"
  val label: String,
  val minutesLogged: Int,
  val completed: Boolean,
  val notes: String = "",
)

@Entity(tableName = "alarms")
data class AlarmEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val hour24: Int,
  val minute: Int,
  val label: String,
  val kind: String, // "ALARM", "REMINDER", "SCHEDULE"
  val isWakeUp: Boolean,
  val repeatDaysCsv: String, // e.g. "1,2,3,4,5,6,7"
  val enabled: Boolean,
  val soundEnabled: Boolean = true,
  val vibrateEnabled: Boolean = true,
  val aiBriefingEnabled: Boolean = true,
  val snoozeMinutes: Int = 5,
  val nextTriggerMillis: Long = 0L,
  val scheduledDateKey: String = "", // "yyyy-MM-dd" for one-time date-specific reminders/schedules
)

@Entity(tableName = "distracting_apps")
data class DistractingAppEntity(
  @PrimaryKey val packageName: String,
  val appName: String,
  val shortTag: String,
  val isSelectedForMonitoring: Boolean,
  val isBlocked: Boolean,
  val allowEducational: Boolean = false,
  val dailyLimitMinutes: Int = 30,
  val todayUsageMinutes: Int = 0,
  val lastSyncedMillis: Long = 0L,
)

@Entity(tableName = "focus_sessions")
data class FocusSessionEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val dateKey: String,
  val startTimeMillis: Long,
  val endTimeMillis: Long? = null,
  val plannedMinutes: Int,
  val actualMinutes: Int,
  val studyModeEnabled: Boolean,
  val blockedAttemptsCount: Int = 0,
  val warningsTriggeredCount: Int = 0,
  val status: String, // "RUNNING", "COMPLETED", "STOPPED"
)

@Entity(tableName = "focus_config")
data class FocusConfigEntity(
  @PrimaryKey val id: Int = 1,
  val focusModeEnabled: Boolean = false,
  val studyModeEnabled: Boolean = true,
  val blockDurationMinutes: Int = 60,
  val blockUntilMillis: Long = 0L,
  val gracePeriodSeconds: Int = 30,
  val autoBlockOnSchedule: Boolean = true,
  val educationalYoutubeFilter: Boolean = true,
  val strictLockMode: Boolean = false,
  val gentleEscalatingChime: Boolean = true,
  val aiMorningBriefing: Boolean = true,
  val focusSafeSnoozeLimit: Boolean = false,
  val nextDayPlanNote: String = "",
  val activeTimerRemainingSeconds: Int = 25 * 60,
  val activeTimerRunning: Boolean = false,
  val activeSessionId: Long = 0L,
  val studyWarningActive: Boolean = false,
  val studyWarningMessage: String = "",
  val studyGraceRemainingSeconds: Int = 0,
)

@Entity(tableName = "daily_productivity_snapshots")
data class DailyProductivitySnapshotEntity(
  @PrimaryKey val dateKey: String,
  val totalTasks: Int,
  val completedTasks: Int,
  val pendingTasks: Int,
  val productivityPercent: Int,
  val focusMinutes: Int,
  val yogaDone: Boolean,
  val skillMinutes: Int,
  val updatedAtMillis: Long = System.currentTimeMillis(),
)

@Entity(tableName = "activity_sessions")
data class ActivitySessionEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val activityType: String, // "FOCUS", "YOGA", "SKILL"
  val dateKey: String, // "yyyy-MM-dd"
  val titleOrSkillName: String,
  val notes: String = "",
  val startTimeMillis: Long,
  val endTimeMillis: Long,
  val durationMinutes: Int,
  val source: String = "TIMER", // "TIMER" or "MANUAL"
  val createdAtMillis: Long = System.currentTimeMillis(),
)

@Entity(tableName = "alfred_chat_messages")
data class AlfredChatMessageEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val conversationId: String,
  val isUser: Boolean,
  val text: String,
  val sourceLabel: String = "ALFRED",
  val executedActionsSerialized: String = "",
  val timestampMillis: Long = System.currentTimeMillis(),
)
