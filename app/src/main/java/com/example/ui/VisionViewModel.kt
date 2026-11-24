package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AlarmEntity
import com.example.data.local.DailyProductivitySnapshotEntity
import com.example.data.local.DistractingAppEntity
import com.example.data.local.FocusConfigEntity
import com.example.data.local.FocusSessionEntity
import com.example.data.local.GoalEntity
import com.example.data.local.HabitLogEntity
import com.example.data.local.ProductivitySummary
import com.example.data.local.TaskEntity
import com.example.data.local.VisionDatabase
import com.example.data.local.VisionRepository
import com.example.domain.alfred.AlfredAnnouncement
import com.example.domain.alfred.AlfredAnnouncementEngine
import com.example.domain.alfred.AlfredBrain
import com.example.domain.alfred.AlfredChatEntry
import com.example.domain.alfred.AlfredCloudAdvisor
import com.example.domain.alfred.AlfredCloudState
import com.example.domain.alfred.AlfredContextSnapshot
import com.example.domain.alfred.AlfredCoordinator
import com.example.domain.alfred.AlfredVoiceState
import com.example.domain.alfred.AnnouncementType
import com.example.domain.alfred.NextTaskRecommendation
import com.example.system.AlarmScheduler
import com.example.system.AlfredAnnouncementScheduler
import com.example.system.AlfredVoiceEngine
import com.example.system.InstalledAppCandidate
import com.example.system.UsageStatsHelper
import com.example.system.VisionNotificationHelper
import com.example.ui.theme.VisionTab
import com.example.worker.ProductivitySyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PermissionStatusSnapshot(
  val notificationsGranted: Boolean = false,
  val exactAlarmsGranted: Boolean = false,
  val usageAccessGranted: Boolean = false,
  val accessibilityEnabled: Boolean = false,
  val batteryOptimizationIgnored: Boolean = false,
  val recordAudioGranted: Boolean = false,
)

class VisionViewModel(application: Application) : AndroidViewModel(application) {

  private val db = VisionDatabase.getInstance(application)
  private val repository = VisionRepository(db.visionDao())
  val alfred = AlfredCoordinator(application.applicationContext, repository)
  private val cloudAdvisor = AlfredCloudAdvisor(application.applicationContext)
  private val alfredBrain = AlfredBrain(repository, alfred, cloudAdvisor)
  private val voiceEngine = AlfredVoiceEngine(application.applicationContext)

  val todayTasks: StateFlow<List<TaskEntity>> = alfred.observeTodayTasks()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val tomorrowTasks: StateFlow<List<TaskEntity>> = alfred.observeTomorrowTasks()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val allPendingTasks: StateFlow<List<TaskEntity>> = alfred.observePendingTasks()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val goals: StateFlow<List<GoalEntity>> = alfred.observeGoals()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val todayHabits: StateFlow<List<HabitLogEntity>> = alfred.observeTodayHabits()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val yogaStreakDays: StateFlow<Int> = repository.observeYogaStreakCount()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

  val alarms: StateFlow<List<AlarmEntity>> = alfred.observeAlarms()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val distractingApps: StateFlow<List<DistractingAppEntity>> = alfred.observeDistractingApps()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val focusSessions: StateFlow<List<FocusSessionEntity>> = alfred.observeRecentFocusSessions()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val focusConfig: StateFlow<FocusConfigEntity> = alfred.observeFocusConfig()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FocusConfigEntity())

  val todayProductivity: StateFlow<ProductivitySummary> = alfred.observeTodayProductivity()
    .stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      ProductivitySummary("", 0, 0, 0, 0, 0, 0, false, 0, false),
    )

  val weeklySnapshots: StateFlow<List<Pair<String, DailyProductivitySnapshotEntity?>>> =
    alfred.observeWeeklySnapshots()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val nextTaskRecommendation: StateFlow<NextTaskRecommendation> = combine(
    todayTasks,
    tomorrowTasks,
    todayProductivity,
    focusConfig,
  ) { today, tomorrow, summary, cfg ->
    val snapshot = AlfredContextSnapshot(
      todayTasks = today,
      tomorrowTasks = tomorrow,
      allPendingTasks = today.filter { !it.isCompleted },
      goals = goals.value,
      todayHabits = todayHabits.value,
      alarms = alarms.value,
      distractingApps = distractingApps.value,
      focusSessions = focusSessions.value,
      focusConfig = cfg,
      todayProductivity = summary,
      weeklySnapshots = weeklySnapshots.value,
    )
    AlfredAnnouncementEngine.recommendNextTask(snapshot)
  }.stateIn(
    viewModelScope,
    SharingStarted.WhileSubscribed(5000),
    NextTaskRecommendation(null, "Analyzing schedule...", "Loading stored tasks.", 25),
  )

  val alfredVoiceState: StateFlow<AlfredVoiceState> = voiceEngine.state

  private val _alfredChatHistory = MutableStateFlow<List<AlfredChatEntry>>(emptyList())
  val alfredChatHistory: StateFlow<List<AlfredChatEntry>> = _alfredChatHistory.asStateFlow()

  private val _alfredCloudState = MutableStateFlow<AlfredCloudState>(AlfredCloudState.Idle)
  val alfredCloudState: StateFlow<AlfredCloudState> = _alfredCloudState.asStateFlow()

  private val _latestAnnouncement = MutableStateFlow<AlfredAnnouncement?>(null)
  val latestAnnouncement: StateFlow<AlfredAnnouncement?> = _latestAnnouncement.asStateFlow()

  private val _isOnline = MutableStateFlow(false)
  val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

  private val _suggestedTabEvent = MutableStateFlow<VisionTab?>(null)
  val suggestedTabEvent: StateFlow<VisionTab?> = _suggestedTabEvent.asStateFlow()

  private val _permissions = MutableStateFlow(PermissionStatusSnapshot())
  val permissions: StateFlow<PermissionStatusSnapshot> = _permissions.asStateFlow()

  private val _installedApps = MutableStateFlow<List<InstalledAppCandidate>>(emptyList())
  val installedApps: StateFlow<List<InstalledAppCandidate>> = _installedApps.asStateFlow()

  private val _errorMessage = MutableStateFlow<String?>(null)
  val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

  init {
    viewModelScope.launch {
      repository.ensureSeeded()
      repository.syncTodaySnapshot()
      alfred.refreshRealAppUsage()
      AlfredAnnouncementScheduler.rescheduleSmartAlerts(getApplication())

      val snapshot = alfredBrain.captureLiveSnapshot()
      val initialBriefing = AlfredAnnouncementEngine.buildMorningWakeUpAnnouncement(snapshot)
      _latestAnnouncement.value = initialBriefing
      if (_alfredChatHistory.value.isEmpty()) {
        val nextRec = AlfredAnnouncementEngine.recommendNextTask(snapshot)
        _alfredChatHistory.value = listOf(
          AlfredChatEntry(
            isUser = false,
            text = "${initialBriefing.message}\n\nNext recommendation: ${nextRec.headline}",
            sourceLabel = "ALFRED · Shared Data",
          ),
        )
      }
    }
    ProductivitySyncWorker.enqueuePeriodicAndImmediate(application.applicationContext)
    refreshPermissionsAndUsage()
    loadInstalledApps()
  }

  fun clearError() {
    _errorMessage.value = null
  }

  fun consumeSuggestedTab() {
    _suggestedTabEvent.value = null
  }

  fun refreshPermissionsAndUsage() {
    val ctx = getApplication<Application>()
    _permissions.value = PermissionStatusSnapshot(
      notificationsGranted = VisionNotificationHelper.hasNotificationPermission(ctx),
      exactAlarmsGranted = AlarmScheduler.canScheduleExactAlarms(ctx),
      usageAccessGranted = UsageStatsHelper.isUsageAccessGranted(ctx),
      accessibilityEnabled = UsageStatsHelper.isAccessibilityServiceEnabled(ctx),
      batteryOptimizationIgnored = UsageStatsHelper.isIgnoringBatteryOptimizations(ctx),
      recordAudioGranted = VisionNotificationHelper.hasRecordAudioPermission(ctx),
    )
    _isOnline.value = alfredBrain.isOnline()
    viewModelScope.launch {
      alfred.refreshRealAppUsage()
    }
  }

  private fun loadInstalledApps() {
    viewModelScope.launch(Dispatchers.IO) {
      val ctx = getApplication<Application>()
      _installedApps.value = UsageStatsHelper.discoverInstalledApps(ctx)
    }
  }

  // ---- ALFRED Central Voice & Conversational Control ----
  fun startAlfredVoiceListening() {
    refreshPermissionsAndUsage()
    val forceOffline = !_isOnline.value || alfredVoiceState.value.preferOfflineRecognition
    voiceEngine.startListening(preferOffline = forceOffline) { recognizedText ->
      submitAlfredCommand(recognizedText, preferCloudReasoning = false)
    }
  }

  fun stopAlfredVoiceListening() {
    voiceEngine.stopListening()
  }

  fun stopAlfredSpeaking() {
    voiceEngine.stopSpeaking()
  }

  fun toggleAlfredMute() {
    voiceEngine.setVoiceMuted(!alfredVoiceState.value.voiceMuted)
  }

  fun toggleOfflineSpeechPreference() {
    voiceEngine.setPreferOfflineRecognition(!alfredVoiceState.value.preferOfflineRecognition)
  }

  fun clearAlfredVoiceError() {
    voiceEngine.clearError()
  }

  fun clearAlfredCloudError() {
    _alfredCloudState.value = AlfredCloudState.Idle
  }

  fun submitAlfredCommand(
    rawCommand: String,
    preferCloudReasoning: Boolean = false,
  ) {
    val cmd = rawCommand.trim()
    if (cmd.isEmpty()) return

    _alfredChatHistory.value = _alfredChatHistory.value + AlfredChatEntry(
      isUser = true,
      text = cmd,
      sourceLabel = "You",
    )
    voiceEngine.markProcessing()
    _isOnline.value = alfredBrain.isOnline()
    _alfredCloudState.value = if (preferCloudReasoning && _isOnline.value) {
      AlfredCloudState.Loading
    } else {
      AlfredCloudState.Idle
    }

    viewModelScope.launch {
      try {
        val result = alfredBrain.handleCommand(
          command = cmd,
          preferCloudReasoning = preferCloudReasoning,
        )
        _alfredCloudState.value = when {
          result.cloudError != null -> AlfredCloudState.Error(result.cloudError)
          result.usedCloudAi -> AlfredCloudState.Success("Gemini Cloud + Room")
          else -> AlfredCloudState.Success("Offline Rule Engine")
        }

        val sourceTag = if (result.usedCloudAi) "ALFRED · Gemini Cloud" else "ALFRED · Offline Engine"
        _alfredChatHistory.value = _alfredChatHistory.value + AlfredChatEntry(
          isUser = false,
          text = result.replyText,
          executedActions = result.executedActions,
          sourceLabel = sourceTag,
        )

        if (result.suggestedTab != null && result.executedActions.isNotEmpty()) {
          _suggestedTabEvent.value = result.suggestedTab
        }

        voiceEngine.finishProcessing()
        voiceEngine.speak(result.spokenText, forceOfflineAudio = !_isOnline.value)
      } catch (e: Exception) {
        voiceEngine.finishProcessing()
        _alfredCloudState.value = AlfredCloudState.Error(e.localizedMessage ?: "Command failed.")
      }
    }
  }

  fun triggerPersonalizedAnnouncement(type: AnnouncementType, speakAloud: Boolean = true) {
    viewModelScope.launch {
      val snapshot = alfredBrain.captureLiveSnapshot()
      val announcement = when (type) {
        AnnouncementType.MORNING_WAKEUP ->
          AlfredAnnouncementEngine.buildMorningWakeUpAnnouncement(snapshot)
        AnnouncementType.UPCOMING_TASK -> {
          val upcoming = AlfredAnnouncementEngine.findUpcomingTaskSoon(snapshot.todayTasks, windowMinutes = 180)
            ?: snapshot.todayTasks.firstOrNull { !it.isCompleted }
          if (upcoming != null) {
            AlfredAnnouncementEngine.buildUpcomingTaskAnnouncement(upcoming)
          } else {
            AlfredAnnouncement(
              type = AnnouncementType.UPCOMING_TASK,
              title = "Schedule Clear",
              message = "You have no pending tasks left on today's schedule.",
              spokenMessage = "You have no pending tasks left on today's schedule.",
            )
          }
        }
        AnnouncementType.MILESTONE_COMPLETED ->
          AlfredAnnouncementEngine.buildCompletedMilestoneAnnouncement(snapshot.todayProductivity)
        AnnouncementType.STUDY_START ->
          AlfredAnnouncementEngine.buildStudyStartAnnouncement(25, snapshot.focusConfig.studyModeEnabled)
        AnnouncementType.DISTRACTION_WARNING -> {
          val targetApp = snapshot.distractingApps.firstOrNull { it.isBlocked }?.appName ?: "a distracting app"
          AlfredAnnouncementEngine.buildDistractionWarningAnnouncement(targetApp)
        }
        AnnouncementType.DAILY_REPORT_READY ->
          AlfredAnnouncementEngine.buildDailyReportReadyAnnouncement(snapshot.todayProductivity)
      }

      _latestAnnouncement.value = announcement
      _alfredChatHistory.value = _alfredChatHistory.value + AlfredChatEntry(
        isUser = false,
        text = announcement.message,
        executedActions = listOf("Announcement · ${type.label}"),
        sourceLabel = "ALFRED Announcement",
      )
      if (speakAloud) {
        voiceEngine.speak(announcement.spokenMessage, forceOfflineAudio = !_isOnline.value)
      }
    }
  }

  fun replayLastAlfredReply() {
    val lastReply = _alfredChatHistory.value.lastOrNull { !it.isUser }?.text
      ?: _latestAnnouncement.value?.spokenMessage
      ?: return
    voiceEngine.speak(lastReply, forceOfflineAudio = !_isOnline.value)
  }

  // ---- Dashboard / Task / Goal / Habit Actions ----
  fun saveTask(task: TaskEntity) {
    viewModelScope.launch {
      alfred.createOrUpdateTask(task).onFailure { _errorMessage.value = "Failed to save task." }
    }
  }

  fun toggleTask(task: TaskEntity) {
    viewModelScope.launch {
      alfred.toggleTaskCompleted(task)
        .onSuccess {
          if (!task.isCompleted) {
            // Task was just completed -> emit contextual milestone announcement using real Room counts
            val updatedSummary = repository.observeTodayProductivity().first()
            val milestone = AlfredAnnouncementEngine.buildCompletedMilestoneAnnouncement(updatedSummary)
            _latestAnnouncement.value = milestone
            if (focusConfig.value.aiMorningBriefing) {
              voiceEngine.speak(milestone.spokenMessage, forceOfflineAudio = !_isOnline.value)
            }
          }
        }
        .onFailure { _errorMessage.value = "Failed to update task." }
    }
  }

  fun toggleTaskPriority(task: TaskEntity) {
    viewModelScope.launch {
      alfred.toggleTaskPriority(task).onFailure { _errorMessage.value = "Failed to update priority." }
    }
  }

  fun deleteTask(taskId: Long) {
    viewModelScope.launch {
      alfred.deleteTask(taskId).onFailure { _errorMessage.value = "Failed to delete task." }
    }
  }

  fun saveGoal(goal: GoalEntity) {
    viewModelScope.launch {
      alfred.createOrUpdateGoal(goal).onFailure { _errorMessage.value = "Failed to save goal." }
    }
  }

  fun incrementGoal(goal: GoalEntity, delta: Int) {
    viewModelScope.launch {
      alfred.adjustGoalProgress(goal, delta).onFailure { _errorMessage.value = "Failed to update goal." }
    }
  }

  fun deleteGoal(goalId: Long) {
    viewModelScope.launch {
      alfred.deleteGoal(goalId).onFailure { _errorMessage.value = "Failed to delete goal." }
    }
  }

  fun logYogaSession(addMinutes: Int, markCompleted: Boolean?) {
    viewModelScope.launch {
      alfred.logHabit("YOGA_EXERCISE", "Morning Yoga & Exercise", addMinutes, markCompleted)
    }
  }

  fun logSkillSession(addMinutes: Int, markCompleted: Boolean?) {
    viewModelScope.launch {
      alfred.logHabit("SKILL_DEV", "Skill Development · Web & CPPM", addMinutes, markCompleted)
    }
  }

  // ---- Alarm Actions ----
  fun saveAlarm(alarm: AlarmEntity) {
    viewModelScope.launch {
      alfred.saveAndScheduleAlarm(alarm).onFailure { _errorMessage.value = "Failed to schedule alarm." }
    }
  }

  fun toggleAlarm(alarm: AlarmEntity, enabled: Boolean) {
    viewModelScope.launch {
      alfred.toggleAlarmEnabled(alarm, enabled).onFailure { _errorMessage.value = "Failed to update alarm." }
    }
  }

  fun deleteAlarm(alarmId: Long) {
    viewModelScope.launch {
      alfred.deleteAndCancelAlarm(alarmId).onFailure { _errorMessage.value = "Failed to delete alarm." }
    }
  }

  fun updateNotificationRule(
    gentleChime: Boolean? = null,
    aiBriefing: Boolean? = null,
    focusSafeSnooze: Boolean? = null,
  ) {
    viewModelScope.launch {
      alfred.updateNotificationRule(gentleChime, aiBriefing, focusSafeSnooze)
    }
  }

  // ---- Focus & Study Mode Actions ----
  fun setFocusMode(enabled: Boolean) {
    viewModelScope.launch {
      alfred.setFocusMode(enabled)
    }
  }

  fun setStudyMode(enabled: Boolean) {
    viewModelScope.launch {
      alfred.setStudyMode(enabled)
    }
  }

  fun setBlockDurationMinutes(minutes: Int) {
    viewModelScope.launch {
      alfred.setBlockDuration(minutes)
    }
  }

  fun setGracePeriodSeconds(seconds: Int) {
    viewModelScope.launch {
      alfred.setGracePeriod(seconds)
    }
  }

  fun updateAutoBlockRules(
    autoBlockOnSchedule: Boolean? = null,
    educationalYoutubeFilter: Boolean? = null,
    strictLockMode: Boolean? = null,
  ) {
    viewModelScope.launch {
      repository.updateFocusConfig { cfg ->
        cfg.copy(
          autoBlockOnSchedule = autoBlockOnSchedule ?: cfg.autoBlockOnSchedule,
          educationalYoutubeFilter = educationalYoutubeFilter ?: cfg.educationalYoutubeFilter,
          strictLockMode = strictLockMode ?: cfg.strictLockMode,
        )
      }
    }
  }

  fun toggleDistractingAppBlock(app: DistractingAppEntity) {
    viewModelScope.launch {
      alfred.toggleAppBlock(app)
    }
  }

  fun addDistractingApp(candidate: InstalledAppCandidate) {
    viewModelScope.launch {
      alfred.addOrSelectDistractingApp(candidate.packageName, candidate.appName, candidate.shortTag)
    }
  }

  fun startFocusTimer(plannedMinutes: Int) {
    viewModelScope.launch {
      repository.updateFocusConfig {
        it.copy(
          activeTimerRemainingSeconds = if (it.activeTimerRemainingSeconds > 0 && !it.activeTimerRunning)
            it.activeTimerRemainingSeconds else plannedMinutes * 60,
        )
      }
      alfred.startFocusTimer(plannedMinutes)
      val startAnnouncement = AlfredAnnouncementEngine.buildStudyStartAnnouncement(
        plannedMinutes = plannedMinutes,
        studyModeEnabled = focusConfig.value.studyModeEnabled,
      )
      _latestAnnouncement.value = startAnnouncement
    }
  }

  fun pauseFocusTimer() = alfred.pauseFocusTimer()

  fun resetFocusTimer(plannedMinutes: Int) = alfred.resetFocusTimer(plannedMinutes)

  fun triggerStudyWarningCheck() = alfred.triggerStudyModeWarningPreview()

  fun dismissStudyWarning() = alfred.dismissStudyModeWarning()

  // ---- Report & Next Day Planning ----
  fun schedulePendingTaskForTomorrow(task: TaskEntity) {
    viewModelScope.launch {
      alfred.scheduleTaskForTomorrow(task)
    }
  }

  fun saveNextDayPlanNote(note: String) {
    viewModelScope.launch {
      alfred.updateNextDayPlanNote(note)
    }
  }

  override fun onCleared() {
    voiceEngine.shutdown()
    super.onCleared()
  }
}
