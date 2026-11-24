package com.example.domain.alfred

import com.example.data.local.AlarmEntity
import com.example.data.local.DailyProductivitySnapshotEntity
import com.example.data.local.DistractingAppEntity
import com.example.data.local.FocusConfigEntity
import com.example.data.local.FocusSessionEntity
import com.example.data.local.GoalEntity
import com.example.data.local.HabitLogEntity
import com.example.data.local.ProductivitySummary
import com.example.data.local.TaskEntity
import com.example.ui.theme.VisionTab

enum class AnnouncementType(val label: String) {
  MORNING_WAKEUP("Morning Wake-Up"),
  UPCOMING_TASK("Upcoming Task"),
  MILESTONE_COMPLETED("Task Milestone"),
  STUDY_START("Study Session"),
  DISTRACTION_WARNING("Distraction Alert"),
  DAILY_REPORT_READY("Daily Report Ready"),
}

data class AlfredAnnouncement(
  val id: Long = System.currentTimeMillis(),
  val type: AnnouncementType,
  val title: String,
  val message: String,
  val spokenMessage: String = message,
  val createdAtMillis: Long = System.currentTimeMillis(),
)

data class NextTaskRecommendation(
  val task: TaskEntity?,
  val headline: String,
  val rationale: String,
  val suggestedFocusMinutes: Int,
)

data class AlfredContextSnapshot(
  val todayTasks: List<TaskEntity>,
  val tomorrowTasks: List<TaskEntity>,
  val allPendingTasks: List<TaskEntity>,
  val goals: List<GoalEntity>,
  val todayHabits: List<HabitLogEntity>,
  val alarms: List<AlarmEntity>,
  val distractingApps: List<DistractingAppEntity>,
  val focusSessions: List<FocusSessionEntity>,
  val focusConfig: FocusConfigEntity,
  val todayProductivity: ProductivitySummary,
  val weeklySnapshots: List<Pair<String, DailyProductivitySnapshotEntity?>>,
)

data class AlfredChatEntry(
  val id: Long = System.nanoTime(),
  val isUser: Boolean,
  val text: String,
  val executedActions: List<String> = emptyList(),
  val sourceLabel: String = "ALFRED",
  val timestampMillis: Long = System.currentTimeMillis(),
)

data class AlfredExecutionResult(
  val replyText: String,
  val spokenText: String,
  val executedActions: List<String>,
  val suggestedTab: VisionTab? = null,
  val usedCloudAi: Boolean = false,
  val cloudError: String? = null,
)

sealed interface AlfredCloudState {
  data object Idle : AlfredCloudState
  data object Loading : AlfredCloudState
  data class Success(val source: String) : AlfredCloudState
  data class Error(val message: String) : AlfredCloudState
}

enum class VoiceEnginePhase {
  IDLE,
  LISTENING,
  PROCESSING,
  SPEAKING,
  UNAVAILABLE,
}

data class AlfredVoiceState(
  val phase: VoiceEnginePhase = VoiceEnginePhase.IDLE,
  val partialTranscript: String = "",
  val lastTranscript: String = "",
  val ttsReady: Boolean = false,
  val voiceMuted: Boolean = false,
  val preferOfflineRecognition: Boolean = false,
  val statusNote: String = "Tap the mic or type a command across all four tabs.",
  val errorMessage: String? = null,
)
