package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ActivitySessionEntity
import com.example.data.local.AlarmEntity
import com.example.data.local.DailyProductivitySnapshotEntity
import com.example.data.local.DateKeyHelper
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
import com.example.domain.alfred.AlfredExecutionResult
import com.example.domain.alfred.AlfredVoiceState
import com.example.domain.alfred.AnnouncementType
import com.example.domain.alfred.NextTaskRecommendation
import com.example.domain.alfred.PendingAlfredAction
import com.example.system.ActiveTimerStore
import com.example.system.AlarmScheduler
import com.example.system.AlfredAnnouncementScheduler
import com.example.system.AlfredVoiceEngine
import com.example.system.InstalledAppCandidate
import com.example.system.TimerActivityKind
import com.example.system.UsageStatsHelper
import com.example.system.VisionNotificationHelper
import com.example.ui.theme.VisionAppearancePrefs
import com.example.ui.theme.VisionTab
import com.example.ui.theme.VisionThemeMode
import com.example.worker.ProductivitySyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PermissionStatusSnapshot(
  val notificationsGranted: Boolean = false,
  val exactAlarmsGranted: Boolean = false,
  val fullScreenIntentGranted: Boolean = true,
  val usageAccessGranted: Boolean = false,
  val batteryOptimizationIgnored: Boolean = false,
  val recordAudioGranted: Boolean = false,
)

data class ActivityTimerUiState(
  val isActive: Boolean = false,
  val isRunning: Boolean = false,
  val elapsedSeconds: Long = 0L,
  val titleOrSkillName: String = "",
  val notes: String = "",
)

data class FocusTimerUiState(
  val isActive: Boolean = false,
  val isRunning: Boolean = false,
  val elapsedSeconds: Long = 0L,
  val remainingSeconds: Int = 25 * 60,
  val plannedMinutes: Int = 25,
)

@OptIn(ExperimentalCoroutinesApi::class)
class VisionViewModel(application: Application) : AndroidViewModel(application) {

  private val db = VisionDatabase.getInstance(application)
  private val repository = VisionRepository(db.visionDao())
  val alfred = AlfredCoordinator(application.applicationContext, repository)
  private val cloudAdvisor = AlfredCloudAdvisor(application.applicationContext)
  private val alfredBrain = AlfredBrain(repository, alfred, cloudAdvisor)
  private val voiceEngine = AlfredVoiceEngine(application.applicationContext)

  private val _themeMode = MutableStateFlow(VisionAppearancePrefs.getThemeMode(application))
  val themeMode: StateFlow<VisionThemeMode> = _themeMode.asStateFlow()

  private val _currentDateKey = MutableStateFlow(DateKeyHelper.todayKey())
  val currentDateKey: StateFlow<String> = _currentDateKey.asStateFlow()

  val todayTasks: StateFlow<List<TaskEntity>> = _currentDateKey
    .flatMapLatest { dayKey -> repository.observeTodayTasks(dayKey) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val tomorrowTasks: StateFlow<List<TaskEntity>> = _currentDateKey
    .flatMapLatest { dayKey ->
      val nextDayKey = DateKeyHelper.parseDateKeyToCalendar(dayKey)?.let { cal ->
        cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
        DateKeyHelper.todayKey(cal.timeInMillis)
      } ?: DateKeyHelper.tomorrowKey()
      repository.observeTomorrowTasks(nextDayKey)
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val allPendingTasks: StateFlow<List<TaskEntity>> = alfred.observePendingTasks()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val goals: StateFlow<List<GoalEntity>> = alfred.observeGoals()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val todayHabits: StateFlow<List<HabitLogEntity>> = _currentDateKey
    .flatMapLatest { dayKey -> repository.observeTodayHabits(dayKey) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val todayActivitySessions: StateFlow<List<ActivitySessionEntity>> = _currentDateKey
    .flatMapLatest { dayKey -> repository.observeTodayActivitySessions(dayKey) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val recentActivitySessions: StateFlow<List<ActivitySessionEntity>> = alfred.observeRecentActivitySessions()
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

  val todayProductivity: StateFlow<ProductivitySummary> = _currentDateKey
    .flatMapLatest { dayKey -> repository.observeTodayProductivity(dayKey) }
    .stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      ProductivitySummary("", 0, 0, 0, 0, 0, 0, false, 0, false),
    )

  val weeklySnapshots: StateFlow<List<Pair<String, DailyProductivitySnapshotEntity?>>> = _currentDateKey
    .flatMapLatest { repository.observeWeeklySnapshots() }
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
      todayActivitySessions = todayActivitySessions.value,
    )
    AlfredAnnouncementEngine.recommendNextTask(snapshot)
  }.stateIn(
    viewModelScope,
    SharingStarted.WhileSubscribed(5000),
    NextTaskRecommendation(null, "Analyzing schedule...", "Loading stored tasks.", 25),
  )

  val alfredVoiceState: StateFlow<AlfredVoiceState> = voiceEngine.state

  // Persistent ALFRED Conversations backed by Room alfred_chat_messages
  private val _activeConversationId = MutableStateFlow("")
  val activeConversationId: StateFlow<String> = _activeConversationId.asStateFlow()

  val conversationIds: StateFlow<List<String>> = repository.observeConversationIds()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val alfredChatHistory: StateFlow<List<AlfredChatEntry>> = _activeConversationId
    .flatMapLatest { convId ->
      if (convId.isBlank()) flowOf(emptyList())
      else repository.observeChatMessages(convId).map { entities ->
        entities.map { entity ->
          AlfredChatEntry(
            id = entity.id,
            isUser = entity.isUser,
            text = entity.text,
            executedActions = entity.executedActionsSerialized
              .split("\n")
              .map { it.trim() }
              .filter { it.isNotEmpty() },
            sourceLabel = entity.sourceLabel,
            timestampMillis = entity.timestampMillis,
          )
        }
      }
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  private val _alfredCloudState = MutableStateFlow<AlfredCloudState>(AlfredCloudState.Idle)
  val alfredCloudState: StateFlow<AlfredCloudState> = _alfredCloudState.asStateFlow()

  private val _pendingAlfredAction = MutableStateFlow<PendingAlfredAction?>(null)
  val pendingAlfredAction: StateFlow<PendingAlfredAction?> = _pendingAlfredAction.asStateFlow()

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

  private val _yogaTimerState = MutableStateFlow(ActivityTimerUiState())
  val yogaTimerState: StateFlow<ActivityTimerUiState> = _yogaTimerState.asStateFlow()

  private val _skillTimerState = MutableStateFlow(ActivityTimerUiState())
  val skillTimerState: StateFlow<ActivityTimerUiState> = _skillTimerState.asStateFlow()

  private val _focusTimerState = MutableStateFlow(FocusTimerUiState())
  val focusTimerState: StateFlow<FocusTimerUiState> = _focusTimerState.asStateFlow()

  private val _errorMessage = MutableStateFlow<String?>(null)
  val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

  private val _statusMessage = MutableStateFlow<String?>(null)
  val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

  init {
    refreshActiveTimers()
    startTimerTickerLoop()

    viewModelScope.launch {
      repository.ensureSeeded()
      repository.syncTodaySnapshot()
      alfred.refreshRealAppUsage()
      AlfredAnnouncementScheduler.rescheduleSmartAlerts(getApplication())

      val snapshot = alfredBrain.captureLiveSnapshot()
      val initialBriefing = AlfredAnnouncementEngine.buildMorningWakeUpAnnouncement(snapshot)
      _latestAnnouncement.value = initialBriefing

      val latestConvId = repository.getLatestConversationIdSync()
      if (latestConvId.isNullOrBlank()) {
        val newConvId = createNewConversationId()
        _activeConversationId.value = newConvId
        val nextRec = AlfredAnnouncementEngine.recommendNextTask(snapshot)
        repository.appendChatMessage(
          conversationId = newConvId,
          isUser = false,
          text = "${initialBriefing.message}\n\nNext suggestion: ${nextRec.headline}",
          sourceLabel = "ALFRED · Shared Data",
        )
      } else {
        _activeConversationId.value = latestConvId
      }
    }
    ProductivitySyncWorker.enqueuePeriodicAndImmediate(application.applicationContext)
    refreshPermissionsAndUsage()
  }

  private fun createNewConversationId(): String = "conv_${System.currentTimeMillis()}"

  private suspend fun ensureActiveConversationId(): String {
    val current = _activeConversationId.value
    if (current.isNotBlank()) return current
    val latest = repository.getLatestConversationIdSync()
    val resolved = if (!latest.isNullOrBlank()) latest else createNewConversationId()
    _activeConversationId.value = resolved
    return resolved
  }

  private fun startTimerTickerLoop() {
    viewModelScope.launch {
      while (isActive) {
        refreshActiveTimers()
        delay(1000L)
      }
    }
  }

  fun refreshActiveTimers() {
    val ctx = getApplication<Application>()
    val now = System.currentTimeMillis()
    val liveTodayKey = DateKeyHelper.todayKey(now)
    if (_currentDateKey.value != liveTodayKey) {
      _currentDateKey.value = liveTodayKey
      viewModelScope.launch { repository.syncTodaySnapshot(liveTodayKey) }
    }
    val yoga = ActiveTimerStore.get(ctx, TimerActivityKind.YOGA)
    _yogaTimerState.value = ActivityTimerUiState(
      isActive = yoga.isActive,
      isRunning = yoga.isRunning,
      elapsedSeconds = yoga.elapsedSeconds(now),
      titleOrSkillName = yoga.titleOrSkillName.ifBlank { "Morning Yoga & Exercise" },
      notes = yoga.notes,
    )

    val skill = ActiveTimerStore.get(ctx, TimerActivityKind.SKILL)
    _skillTimerState.value = ActivityTimerUiState(
      isActive = skill.isActive,
      isRunning = skill.isRunning,
      elapsedSeconds = skill.elapsedSeconds(now),
      titleOrSkillName = skill.titleOrSkillName,
      notes = skill.notes,
    )

    val focus = ActiveTimerStore.get(ctx, TimerActivityKind.FOCUS)
    val cfg = focusConfig.value
    _focusTimerState.value = if (focus.isActive) {
      FocusTimerUiState(
        isActive = true,
        isRunning = focus.isRunning,
        elapsedSeconds = focus.elapsedSeconds(now),
        remainingSeconds = focus.remainingCountdownSeconds(now),
        plannedMinutes = focus.plannedMinutes,
      )
    } else {
      FocusTimerUiState(
        isActive = cfg.activeTimerRunning,
        isRunning = cfg.activeTimerRunning,
        elapsedSeconds = 0L,
        remainingSeconds = cfg.activeTimerRemainingSeconds.coerceAtLeast(0),
        plannedMinutes = (cfg.activeTimerRemainingSeconds / 60).coerceIn(5, 180),
      )
    }
  }

  fun setThemeMode(mode: VisionThemeMode) {
    VisionAppearancePrefs.setThemeMode(getApplication(), mode)
    _themeMode.value = mode
    _statusMessage.value = "Appearance set to ${mode.label}."
  }

  fun clearError() {
    _errorMessage.value = null
  }

  fun clearStatusMessage() {
    _statusMessage.value = null
  }

  fun consumeSuggestedTab() {
    _suggestedTabEvent.value = null
  }

  fun refreshPermissionsAndUsage() {
    val ctx = getApplication<Application>()
    _permissions.value = PermissionStatusSnapshot(
      notificationsGranted = VisionNotificationHelper.hasNotificationPermission(ctx),
      exactAlarmsGranted = AlarmScheduler.canScheduleExactAlarms(ctx),
      fullScreenIntentGranted = UsageStatsHelper.canUseFullScreenIntent(ctx),
      usageAccessGranted = UsageStatsHelper.isUsageAccessGranted(ctx),
      batteryOptimizationIgnored = UsageStatsHelper.isIgnoringBatteryOptimizations(ctx),
      recordAudioGranted = VisionNotificationHelper.hasRecordAudioPermission(ctx),
    )
    _isOnline.value = alfredBrain.isOnline()
    refreshActiveTimers()
    viewModelScope.launch {
      alfred.refreshRealAppUsage()
    }
  }

  fun loadCuratedDistractingApps() {
    viewModelScope.launch(Dispatchers.IO) {
      val ctx = getApplication<Application>()
      _installedApps.value = UsageStatsHelper.curatedDistractingApps(ctx)
    }
  }

  // ---- Real Wall-Clock Yoga & Skill Timers + Manual Activity CRUD ----
  fun startYogaTimer(
    title: String = "Morning Yoga & Exercise",
    notes: String = "",
  ) {
    val ctx = getApplication<Application>()
    ActiveTimerStore.start(
      context = ctx,
      kind = TimerActivityKind.YOGA,
      titleOrSkillName = title.trim().ifEmpty { "Morning Yoga & Exercise" },
      notes = notes.trim(),
      plannedMinutes = 30,
    )
    refreshActiveTimers()
    _statusMessage.value = "Morning Yoga timer started."
  }

  fun pauseYogaTimer() {
    ActiveTimerStore.pause(getApplication(), TimerActivityKind.YOGA)
    refreshActiveTimers()
    _statusMessage.value = "Morning Yoga timer paused."
  }

  fun resumeYogaTimer() {
    ActiveTimerStore.resume(getApplication(), TimerActivityKind.YOGA)
    refreshActiveTimers()
    _statusMessage.value = "Morning Yoga timer resumed."
  }

  fun finishYogaTimer(notesOverride: String? = null) {
    val completed = ActiveTimerStore.finishAndClear(getApplication(), TimerActivityKind.YOGA)
    refreshActiveTimers()
    if (completed == null || completed.durationMinutes <= 0) {
      _statusMessage.value = "Yoga session ended under 1 second — nothing saved."
      return
    }
    viewModelScope.launch {
      val finalNotes = (notesOverride ?: completed.notes).trim()
      val entity = ActivitySessionEntity(
        activityType = "YOGA",
        dateKey = DateKeyHelper.todayKey(completed.startTimeMillis),
        titleOrSkillName = completed.titleOrSkillName.ifBlank { "Morning Yoga & Exercise" },
        notes = finalNotes,
        startTimeMillis = completed.startTimeMillis,
        endTimeMillis = completed.endTimeMillis,
        durationMinutes = completed.durationMinutes,
        source = "TIMER",
      )
      alfred.saveActivitySession(entity)
        .onSuccess {
          _statusMessage.value = "Saved ${completed.durationMinutes}m Morning Yoga session."
        }
        .onFailure {
          _errorMessage.value = it.localizedMessage ?: "Failed to save Yoga session."
        }
    }
  }

  fun startSkillTimer(skillName: String, notes: String = "") {
    val cleanSkill = skillName.trim()
    if (cleanSkill.isEmpty()) {
      _errorMessage.value = "Please select or enter a skill name before starting."
      return
    }
    ActiveTimerStore.start(
      context = getApplication(),
      kind = TimerActivityKind.SKILL,
      titleOrSkillName = cleanSkill,
      notes = notes.trim(),
      plannedMinutes = 60,
    )
    refreshActiveTimers()
    _statusMessage.value = "Started \"$cleanSkill\" learning timer."
  }

  fun pauseSkillTimer() {
    ActiveTimerStore.pause(getApplication(), TimerActivityKind.SKILL)
    refreshActiveTimers()
    _statusMessage.value = "Skill Development timer paused."
  }

  fun resumeSkillTimer() {
    ActiveTimerStore.resume(getApplication(), TimerActivityKind.SKILL)
    refreshActiveTimers()
    _statusMessage.value = "Skill Development timer resumed."
  }

  fun finishSkillTimer(notesOverride: String? = null) {
    val completed = ActiveTimerStore.finishAndClear(getApplication(), TimerActivityKind.SKILL)
    refreshActiveTimers()
    if (completed == null || completed.durationMinutes <= 0) {
      _statusMessage.value = "Skill session ended under 1 second — nothing saved."
      return
    }
    viewModelScope.launch {
      val finalNotes = (notesOverride ?: completed.notes).trim()
      val skillLabel = completed.titleOrSkillName.ifBlank { "Skill Practice" }
      val entity = ActivitySessionEntity(
        activityType = "SKILL",
        dateKey = DateKeyHelper.todayKey(completed.startTimeMillis),
        titleOrSkillName = skillLabel,
        notes = finalNotes,
        startTimeMillis = completed.startTimeMillis,
        endTimeMillis = completed.endTimeMillis,
        durationMinutes = completed.durationMinutes,
        source = "TIMER",
      )
      alfred.saveActivitySession(entity)
        .onSuccess {
          _statusMessage.value = "Saved ${completed.durationMinutes}m for \"$skillLabel\"."
        }
        .onFailure {
          _errorMessage.value = it.localizedMessage ?: "Failed to save Skill session."
        }
    }
  }

  fun saveActivitySession(session: ActivitySessionEntity) {
    viewModelScope.launch {
      alfred.saveActivitySession(session)
        .onSuccess {
          _statusMessage.value = if (session.id == 0L) {
            "Added ${session.durationMinutes}m ${session.titleOrSkillName}."
          } else {
            "Updated \"${session.titleOrSkillName}\" (${session.durationMinutes}m)."
          }
        }
        .onFailure {
          _errorMessage.value = it.localizedMessage ?: "Failed to save activity record."
        }
    }
  }

  fun deleteActivitySession(sessionId: Long) {
    viewModelScope.launch {
      alfred.deleteActivitySession(sessionId)
        .onSuccess { _statusMessage.value = "Activity record deleted." }
        .onFailure { _errorMessage.value = it.localizedMessage ?: "Failed to delete activity record." }
    }
  }

  // ---- ALFRED Central Voice & Persistent Conversational Control ----
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

  fun startNewAlfredConversation() {
    viewModelScope.launch {
      val convId = createNewConversationId()
      _activeConversationId.value = convId
      _pendingAlfredAction.value = null
      _alfredCloudState.value = AlfredCloudState.Idle
      val snapshot = alfredBrain.captureLiveSnapshot()
      val nextRec = AlfredAnnouncementEngine.recommendNextTask(snapshot)
      repository.appendChatMessage(
        conversationId = convId,
        isUser = false,
        text = "Started a fresh conversation. Next on your schedule: ${nextRec.headline}. How can I help?",
        sourceLabel = "ALFRED · New Chat",
      )
      _statusMessage.value = "Started new ALFRED chat."
    }
  }

  fun selectAlfredConversation(conversationId: String) {
    _activeConversationId.value = conversationId
    _pendingAlfredAction.value = null
  }

  fun deleteCurrentAlfredConversation(deleteAllHistory: Boolean = false) {
    viewModelScope.launch {
      if (deleteAllHistory) {
        repository.clearAllChatHistory()
      } else {
        val current = _activeConversationId.value
        if (current.isNotBlank()) {
          repository.deleteConversation(current)
        }
      }
      _pendingAlfredAction.value = null
      _alfredCloudState.value = AlfredCloudState.Idle
      val nextExisting = if (deleteAllHistory) null else repository.getLatestConversationIdSync()
      if (!nextExisting.isNullOrBlank()) {
        _activeConversationId.value = nextExisting
      } else {
        val freshId = createNewConversationId()
        _activeConversationId.value = freshId
        repository.appendChatMessage(
          conversationId = freshId,
          isUser = false,
          text = "Chat history cleared. Ask me about your tasks, alarms, focus sessions, or productivity report anytime.",
          sourceLabel = "ALFRED · Shared Data",
        )
      }
      _statusMessage.value = if (deleteAllHistory) "All ALFRED chat history deleted." else "Conversation deleted."
    }
  }

  fun submitAlfredCommand(
    rawCommand: String,
    preferCloudReasoning: Boolean = false,
  ) {
    val cmd = rawCommand.trim()
    if (cmd.isEmpty()) return

    val priorHistory = alfredChatHistory.value
    voiceEngine.markProcessing()
    _isOnline.value = alfredBrain.isOnline()
    _alfredCloudState.value = if (_isOnline.value) {
      AlfredCloudState.Loading
    } else {
      AlfredCloudState.Idle
    }

    viewModelScope.launch {
      val convId = ensureActiveConversationId()
      repository.appendChatMessage(
        conversationId = convId,
        isUser = true,
        text = cmd,
        sourceLabel = "You",
      )
      try {
        val result = alfredBrain.handleCommand(
          command = cmd,
          preferCloudReasoning = preferCloudReasoning,
          conversationHistory = priorHistory,
          requireConfirmation = true,
        )
        applyAlfredResult(convId, result)
      } catch (e: Exception) {
        voiceEngine.finishProcessing()
        val err = e.localizedMessage ?: "Command failed."
        _alfredCloudState.value = AlfredCloudState.Error(err)
        repository.appendChatMessage(
          conversationId = convId,
          isUser = false,
          text = "I couldn't process that request ($err). No changes were made.",
          sourceLabel = "ALFRED · Error",
        )
      }
    }
  }

  fun confirmPendingAlfredAction() {
    val pending = _pendingAlfredAction.value ?: return
    _pendingAlfredAction.value = null
    voiceEngine.markProcessing()

    viewModelScope.launch {
      val convId = ensureActiveConversationId()
      try {
        val result = alfredBrain.executePendingAction(pending)
        applyAlfredResult(convId, result)
      } catch (e: Exception) {
        voiceEngine.finishProcessing()
        _errorMessage.value = e.localizedMessage ?: "Failed to apply confirmed action."
      }
    }
  }

  fun cancelPendingAlfredAction() {
    val pending = _pendingAlfredAction.value ?: return
    _pendingAlfredAction.value = null
    viewModelScope.launch {
      val convId = ensureActiveConversationId()
      repository.appendChatMessage(
        conversationId = convId,
        isUser = false,
        text = "Cancelled: ${pending.title}. Nothing was changed.",
        sourceLabel = "ALFRED · Cancelled",
      )
    }
  }

  private suspend fun applyAlfredResult(convId: String, result: AlfredExecutionResult) {
    _pendingAlfredAction.value = result.pendingConfirmation
    _alfredCloudState.value = when {
      result.cloudError != null -> AlfredCloudState.Error(result.cloudError)
      result.usedCloudAi -> AlfredCloudState.Success("Gemini Cloud + Room")
      else -> AlfredCloudState.Success("Local Command Engine")
    }

    val sourceTag = when {
      result.pendingConfirmation != null -> "ALFRED · Confirmation Required"
      result.usedCloudAi -> "ALFRED · Gemini Cloud"
      result.cloudError != null -> "ALFRED · Local Fallback"
      else -> "ALFRED · Local Engine"
    }
    repository.appendChatMessage(
      conversationId = convId,
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
  }

  fun triggerPersonalizedAnnouncement(type: AnnouncementType, speakAloud: Boolean = true) {
    viewModelScope.launch {
      val convId = ensureActiveConversationId()
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
      repository.appendChatMessage(
        conversationId = convId,
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
    val lastReply = alfredChatHistory.value.lastOrNull { !it.isUser }?.text
      ?: _latestAnnouncement.value?.spokenMessage
      ?: return
    voiceEngine.speak(lastReply, forceOfflineAudio = !_isOnline.value)
  }

  // ---- Dashboard / Task / Goal / Habit Actions ----
  fun saveTask(task: TaskEntity) {
    viewModelScope.launch {
      alfred.createOrUpdateTask(task)
        .onSuccess {
          _statusMessage.value = if (task.id == 0L) "Task added." else "Task updated."
        }
        .onFailure {
          _errorMessage.value = it.localizedMessage ?: "Failed to save task."
        }
    }
  }

  fun toggleTask(task: TaskEntity) {
    viewModelScope.launch {
      alfred.toggleTaskCompleted(task)
        .onSuccess {
          if (!task.isCompleted) {
            val updatedSummary = repository.observeTodayProductivity().first()
            val milestone = AlfredAnnouncementEngine.buildCompletedMilestoneAnnouncement(updatedSummary)
            _latestAnnouncement.value = milestone
            if (focusConfig.value.aiMorningBriefing) {
              voiceEngine.speak(milestone.spokenMessage, forceOfflineAudio = !_isOnline.value)
            }
          }
        }
        .onFailure { _errorMessage.value = it.localizedMessage ?: "Failed to update task." }
    }
  }

  fun deleteTask(taskId: Long) {
    viewModelScope.launch {
      alfred.deleteTask(taskId)
        .onSuccess { _statusMessage.value = "Task deleted." }
        .onFailure { _errorMessage.value = it.localizedMessage ?: "Failed to delete task." }
    }
  }

  fun saveGoal(goal: GoalEntity) {
    viewModelScope.launch {
      alfred.createOrUpdateGoal(goal)
        .onSuccess { _statusMessage.value = if (goal.id == 0L) "Goal created." else "Goal updated." }
        .onFailure { _errorMessage.value = it.localizedMessage ?: "Failed to save goal." }
    }
  }

  fun incrementGoal(goal: GoalEntity, delta: Int) {
    viewModelScope.launch {
      alfred.adjustGoalProgress(goal, delta)
        .onFailure { _errorMessage.value = it.localizedMessage ?: "Failed to update goal." }
    }
  }

  fun deleteGoal(goalId: Long) {
    viewModelScope.launch {
      alfred.deleteGoal(goalId)
        .onSuccess { _statusMessage.value = "Goal deleted." }
        .onFailure { _errorMessage.value = it.localizedMessage ?: "Failed to delete goal." }
    }
  }

  fun logYogaSession(
    addMinutes: Int,
    markCompleted: Boolean?,
    updatedNotes: String? = null,
  ) {
    viewModelScope.launch {
      alfred.logHabit(
        trackType = "YOGA_EXERCISE",
        defaultLabel = "Morning Yoga & Exercise",
        addMinutes = addMinutes,
        completed = markCompleted,
        updatedNotes = updatedNotes,
      ).onSuccess {
        _statusMessage.value = when {
          addMinutes > 0 -> "Logged +${addMinutes}m Morning Yoga."
          updatedNotes != null -> "Morning Yoga session saved."
          markCompleted == true -> "Morning Yoga marked complete."
          markCompleted == false -> "Morning Yoga marked pending."
          else -> "Morning Yoga updated."
        }
      }.onFailure {
        _errorMessage.value = it.localizedMessage ?: "Failed to save Morning Yoga."
      }
    }
  }

  fun logSkillSession(
    addMinutes: Int,
    markCompleted: Boolean?,
    updatedNotes: String? = null,
  ) {
    viewModelScope.launch {
      alfred.logHabit(
        trackType = "SKILL_DEV",
        defaultLabel = "Skill Development · Web & CPPM",
        addMinutes = addMinutes,
        completed = markCompleted,
        updatedNotes = updatedNotes,
      ).onSuccess {
        _statusMessage.value = when {
          addMinutes > 0 -> "Logged +${addMinutes}m Skill Development."
          updatedNotes != null -> "Skill Development session saved."
          markCompleted == true -> "Skill Development marked complete."
          markCompleted == false -> "Skill Development marked pending."
          else -> "Skill Development updated."
        }
      }.onFailure {
        _errorMessage.value = it.localizedMessage ?: "Failed to save Skill Development."
      }
    }
  }

  // ---- Alarm & Reminder Actions ----
  fun saveAlarm(alarm: AlarmEntity) {
    viewModelScope.launch {
      val itemType = when (alarm.kind) {
        "REMINDER" -> "Reminder"
        "SCHEDULE" -> "Schedule"
        else -> "Alarm"
      }
      val dateNote = if (alarm.scheduledDateKey.isNotBlank() && alarm.repeatDaysCsv.isBlank()) {
        " for ${DateKeyHelper.formatDateKeyDisplay(alarm.scheduledDateKey)}"
      } else ""
      alfred.saveAndScheduleAlarm(alarm)
        .onSuccess {
          _statusMessage.value = if (_permissions.value.notificationsGranted) {
            "$itemType \"${alarm.label.trim()}\" saved and armed$dateNote."
          } else {
            "$itemType \"${alarm.label.trim()}\" saved$dateNote, but notification alerts are disabled until Notification permission is granted."
          }
        }
        .onFailure { _errorMessage.value = it.localizedMessage ?: "Failed to save $itemType." }
    }
  }

  fun toggleAlarm(alarm: AlarmEntity, enabled: Boolean) {
    viewModelScope.launch {
      alfred.toggleAlarmEnabled(alarm, enabled)
        .onFailure { _errorMessage.value = it.localizedMessage ?: "Failed to update alarm." }
    }
  }

  fun deleteAlarm(alarmId: Long) {
    viewModelScope.launch {
      alfred.deleteAndCancelAlarm(alarmId)
        .onSuccess { _statusMessage.value = "Removed successfully." }
        .onFailure { _errorMessage.value = it.localizedMessage ?: "Failed to delete item." }
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
        .onSuccess { _statusMessage.value = "Added ${candidate.appName} to monitoring." }
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
      delay(150L)
      refreshActiveTimers()
    }
  }

  fun pauseFocusTimer() {
    alfred.pauseFocusTimer()
    viewModelScope.launch {
      delay(150L)
      refreshActiveTimers()
    }
  }

  fun resumeFocusTimer() {
    val planned = _focusTimerState.value.plannedMinutes.coerceIn(5, 180)
    alfred.startFocusTimer(planned)
    viewModelScope.launch {
      delay(150L)
      refreshActiveTimers()
    }
  }

  fun resetFocusTimer(plannedMinutes: Int) {
    alfred.resetFocusTimer(plannedMinutes)
    viewModelScope.launch {
      delay(150L)
      refreshActiveTimers()
    }
  }

  fun endFocusTimer(plannedMinutes: Int) {
    alfred.resetFocusTimer(plannedMinutes)
    _statusMessage.value = "Focus session ended and saved."
    viewModelScope.launch {
      delay(200L)
      refreshActiveTimers()
    }
  }

  fun startStudySession(plannedMinutes: Int) {
    viewModelScope.launch {
      refreshPermissionsAndUsage()
      alfred.startStudySession(plannedMinutes)
      val startAnnouncement = AlfredAnnouncementEngine.buildStudyStartAnnouncement(
        plannedMinutes = plannedMinutes,
        studyModeEnabled = true,
      )
      _latestAnnouncement.value = startAnnouncement
      _statusMessage.value = if (_permissions.value.usageAccessGranted) {
        "Study Session started (${plannedMinutes}m) with live app-switch alerts."
      } else {
        "Study timer started (${plannedMinutes}m). Grant Usage Access to detect app switches."
      }
      delay(150L)
      refreshActiveTimers()
    }
  }

  fun stopStudySession() {
    alfred.stopStudySession()
    _statusMessage.value = "Study Session stopped and saved."
    viewModelScope.launch {
      delay(200L)
      refreshActiveTimers()
    }
  }

  fun dismissStudyWarning() = alfred.dismissStudyModeWarning()

  // ---- Report & Next Day Planning ----
  fun schedulePendingTaskForTomorrow(task: TaskEntity) {
    viewModelScope.launch {
      alfred.scheduleTaskForTomorrow(task)
        .onSuccess { _statusMessage.value = "Moved \"${task.title}\" to tomorrow." }
        .onFailure { _errorMessage.value = it.localizedMessage ?: "Could not move task to tomorrow." }
    }
  }

  fun carryForwardTasksToTomorrow(tasks: List<TaskEntity>) {
    if (tasks.isEmpty()) {
      _errorMessage.value = "Select at least one pending task to carry forward."
      return
    }
    viewModelScope.launch {
      alfred.carryForwardTasksToTomorrow(tasks)
        .onSuccess { outcome ->
          _statusMessage.value = when {
            outcome.movedCount > 0 && outcome.skippedDuplicatesCount > 0 ->
              "Moved ${outcome.movedCount} task(s) to tomorrow (${outcome.skippedDuplicatesCount} duplicate skipped)."
            outcome.movedCount > 0 ->
              "Moved ${outcome.movedCount} task(s) to tomorrow."
            else ->
              "Already scheduled for tomorrow (skipped ${outcome.skippedDuplicatesCount} duplicate)."
          }
        }
        .onFailure { _errorMessage.value = it.localizedMessage ?: "Failed to carry forward tasks." }
    }
  }

  fun saveNextDayPlanNote(note: String) {
    viewModelScope.launch {
      alfred.updateNextDayPlanNote(note.trim())
        .onSuccess { _statusMessage.value = "Next-day plan saved." }
        .onFailure { _errorMessage.value = it.localizedMessage ?: "Failed to save next-day plan." }
    }
  }

  override fun onCleared() {
    voiceEngine.shutdown()
    super.onCleared()
  }
}
