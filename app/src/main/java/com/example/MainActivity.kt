package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.system.VisionNotificationHelper
import com.example.system.configureAppCheck
import com.example.ui.VisionViewModel
import com.example.ui.components.CentralAiAssistantSheet
import com.example.ui.components.CentralAiFab
import com.example.ui.components.SystemMenuAndPermissionsSheet
import com.example.ui.components.VisionBottomNavBar
import com.example.ui.components.VisionNavRail
import com.example.ui.components.VisionScreenBackground
import com.example.ui.screens.AlarmScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DistractionScreen
import com.example.ui.screens.ReportScreen
import com.example.ui.theme.LocalVisionPalette
import com.example.ui.theme.VisionTab
import com.example.ui.theme.VisionTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Configure Firebase App Check with launch Intent before any UI, ViewModels, or Firebase calls
    configureAppCheck(this, intent)

    VisionNotificationHelper.ensureChannels(this)
    enableEdgeToEdge()
    setContent {
      VisionApp()
    }
  }
}

@Composable
fun VisionApp(
  viewModel: VisionViewModel = viewModel(),
) {
  var activeTab by remember { mutableStateOf(VisionTab.Dashboard) }
  var showAiSheet by remember { mutableStateOf(false) }
  var showMenuSheet by remember { mutableStateOf(false) }

  val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
  val todayTasks by viewModel.todayTasks.collectAsStateWithLifecycle()
  val tomorrowTasks by viewModel.tomorrowTasks.collectAsStateWithLifecycle()
  val pendingTasks by viewModel.allPendingTasks.collectAsStateWithLifecycle()
  val goals by viewModel.goals.collectAsStateWithLifecycle()
  val todayHabits by viewModel.todayHabits.collectAsStateWithLifecycle()
  val todayActivitySessions by viewModel.todayActivitySessions.collectAsStateWithLifecycle()
  val recentActivitySessions by viewModel.recentActivitySessions.collectAsStateWithLifecycle()
  val yogaStreakDays by viewModel.yogaStreakDays.collectAsStateWithLifecycle()
  val yogaTimerState by viewModel.yogaTimerState.collectAsStateWithLifecycle()
  val skillTimerState by viewModel.skillTimerState.collectAsStateWithLifecycle()
  val focusTimerState by viewModel.focusTimerState.collectAsStateWithLifecycle()
  val alarms by viewModel.alarms.collectAsStateWithLifecycle()
  val distractingApps by viewModel.distractingApps.collectAsStateWithLifecycle()
  val focusSessions by viewModel.focusSessions.collectAsStateWithLifecycle()
  val focusConfig by viewModel.focusConfig.collectAsStateWithLifecycle()
  val todayProductivity by viewModel.todayProductivity.collectAsStateWithLifecycle()
  val weeklySnapshots by viewModel.weeklySnapshots.collectAsStateWithLifecycle()
  val permissions by viewModel.permissions.collectAsStateWithLifecycle()
  val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
  val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
  val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

  // ALFRED State
  val nextRecommendation by viewModel.nextTaskRecommendation.collectAsStateWithLifecycle()
  val alfredVoiceState by viewModel.alfredVoiceState.collectAsStateWithLifecycle()
  val alfredCloudState by viewModel.alfredCloudState.collectAsStateWithLifecycle()
  val alfredChatHistory by viewModel.alfredChatHistory.collectAsStateWithLifecycle()
  val conversationIds by viewModel.conversationIds.collectAsStateWithLifecycle()
  val activeConversationId by viewModel.activeConversationId.collectAsStateWithLifecycle()
  val latestAnnouncement by viewModel.latestAnnouncement.collectAsStateWithLifecycle()
  val pendingAlfredAction by viewModel.pendingAlfredAction.collectAsStateWithLifecycle()
  val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
  val suggestedTabEvent by viewModel.suggestedTabEvent.collectAsStateWithLifecycle()

  val snackbarHostState = remember { SnackbarHostState() }
  LaunchedEffect(errorMessage) {
    errorMessage?.let { msg ->
      snackbarHostState.showSnackbar(msg)
      viewModel.clearError()
    }
  }
  LaunchedEffect(statusMessage) {
    statusMessage?.let { msg ->
      snackbarHostState.showSnackbar(msg)
      viewModel.clearStatusMessage()
    }
  }
  LaunchedEffect(suggestedTabEvent) {
    suggestedTabEvent?.let { tab ->
      activeTab = tab
      viewModel.consumeSuggestedTab()
    }
  }

  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        viewModel.refreshPermissionsAndUsage()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  BackHandler(enabled = activeTab != VisionTab.Dashboard) {
    activeTab = VisionTab.Dashboard
  }

  VisionTheme(activeTab = activeTab, themeMode = themeMode) {
    val palette = LocalVisionPalette.current
    VisionScreenBackground(activeTab = activeTab) {
      BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
          containerColor = Color.Transparent,
          contentWindowInsets = WindowInsets(0, 0, 0, 0),
          snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
              Snackbar(
                snackbarData = data,
                containerColor = palette.primaryCard,
                contentColor = palette.onPrimaryCard,
                actionColor = palette.accentOnPrimaryCard,
              )
            }
          },
          bottomBar = {
            if (!useNavRail) {
              VisionBottomNavBar(
                activeTab = activeTab,
                onSelectTab = { activeTab = it },
              )
            }
          },
          floatingActionButton = {
            CentralAiFab(
              isFocusRunning = focusTimerState.isRunning || focusConfig.activeTimerRunning,
              voicePhase = alfredVoiceState.phase,
              onClick = { showAiSheet = true },
            )
          },
        ) { innerPadding ->
          Row(
            modifier = Modifier
              .fillMaxSize()
              .padding(innerPadding),
          ) {
            if (useNavRail) {
              VisionNavRail(
                activeTab = activeTab,
                onSelectTab = { activeTab = it },
              )
            }

            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
              AnimatedContent(
                targetState = activeTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "vision_tab_transition",
              ) { tab ->
                when (tab) {
                  VisionTab.Dashboard -> DashboardScreen(
                    tasks = todayTasks,
                    goals = goals,
                    todayHabits = todayHabits,
                    todayActivitySessions = todayActivitySessions,
                    productivity = todayProductivity,
                    yogaStreakDays = yogaStreakDays,
                    yogaTimer = yogaTimerState,
                    skillTimer = skillTimerState,
                    onToggleTask = viewModel::toggleTask,
                    onSaveTask = viewModel::saveTask,
                    onDeleteTask = viewModel::deleteTask,
                    onSaveGoal = viewModel::saveGoal,
                    onIncrementGoal = viewModel::incrementGoal,
                    onDeleteGoal = viewModel::deleteGoal,
                    onStartYogaTimer = viewModel::startYogaTimer,
                    onPauseYogaTimer = viewModel::pauseYogaTimer,
                    onResumeYogaTimer = viewModel::resumeYogaTimer,
                    onFinishYogaTimer = viewModel::finishYogaTimer,
                    onStartSkillTimer = viewModel::startSkillTimer,
                    onPauseSkillTimer = viewModel::pauseSkillTimer,
                    onResumeSkillTimer = viewModel::resumeSkillTimer,
                    onFinishSkillTimer = viewModel::finishSkillTimer,
                    onSaveActivitySession = viewModel::saveActivitySession,
                    onDeleteActivitySession = viewModel::deleteActivitySession,
                    onOpenAiSheet = { showAiSheet = true },
                    onOpenMenu = { showMenuSheet = true },
                    onNavigateToReport = { activeTab = VisionTab.Report },
                  )

                  VisionTab.Alarm -> AlarmScreen(
                    alarms = alarms,
                    focusConfig = focusConfig,
                    permissions = permissions,
                    onSaveAlarm = viewModel::saveAlarm,
                    onToggleAlarm = viewModel::toggleAlarm,
                    onDeleteAlarm = viewModel::deleteAlarm,
                    onUpdateNotificationRule = viewModel::updateNotificationRule,
                    onRefreshPermissions = viewModel::refreshPermissionsAndUsage,
                    onOpenAiSheet = { showAiSheet = true },
                    onOpenMenu = { showMenuSheet = true },
                  )

                  VisionTab.Distraction -> DistractionScreen(
                    focusConfig = focusConfig,
                    focusTimer = focusTimerState,
                    distractingApps = distractingApps,
                    focusSessions = focusSessions,
                    todayActivitySessions = todayActivitySessions,
                    installedApps = installedApps,
                    permissions = permissions,
                    onSetFocusMode = viewModel::setFocusMode,
                    onSetStudyMode = viewModel::setStudyMode,
                    onSetBlockDuration = viewModel::setBlockDurationMinutes,
                    onSetGracePeriod = viewModel::setGracePeriodSeconds,
                    onUpdateAutoRules = viewModel::updateAutoBlockRules,
                    onToggleAppBlock = viewModel::toggleDistractingAppBlock,
                    onAddDistractingApp = viewModel::addDistractingApp,
                    onOpenAppPicker = viewModel::loadCuratedDistractingApps,
                    onStartTimer = viewModel::startFocusTimer,
                    onPauseTimer = viewModel::pauseFocusTimer,
                    onResumeTimer = viewModel::resumeFocusTimer,
                    onResetTimer = viewModel::resetFocusTimer,
                    onEndTimer = viewModel::endFocusTimer,
                    onStartStudySession = viewModel::startStudySession,
                    onStopStudySession = viewModel::stopStudySession,
                    onDismissStudyWarning = viewModel::dismissStudyWarning,
                    onSaveActivitySession = viewModel::saveActivitySession,
                    onDeleteActivitySession = viewModel::deleteActivitySession,
                    onRefreshUsage = viewModel::refreshPermissionsAndUsage,
                    onOpenAiSheet = { showAiSheet = true },
                    onOpenMenu = { showMenuSheet = true },
                  )

                  VisionTab.Report -> ReportScreen(
                    todayProductivity = todayProductivity,
                    weeklySnapshots = weeklySnapshots,
                    todayActivitySessions = todayActivitySessions,
                    recentActivitySessions = recentActivitySessions,
                    todayTasks = todayTasks,
                    pendingTasks = pendingTasks,
                    tomorrowTasks = tomorrowTasks,
                    focusConfig = focusConfig,
                    onSaveActivitySession = viewModel::saveActivitySession,
                    onDeleteActivitySession = viewModel::deleteActivitySession,
                    onMovePendingToTomorrow = viewModel::schedulePendingTaskForTomorrow,
                    onCarryForwardTasks = viewModel::carryForwardTasksToTomorrow,
                    onSaveTomorrowTask = viewModel::saveTask,
                    onToggleTask = viewModel::toggleTask,
                    onDeleteTask = viewModel::deleteTask,
                    onSaveNextDayNote = viewModel::saveNextDayPlanNote,
                    onOpenAiSheet = { showAiSheet = true },
                    onOpenMenu = { showMenuSheet = true },
                  )
                }
              }
            }
          }
        }
      }

      if (showAiSheet) {
        CentralAiAssistantSheet(
          activeTab = activeTab,
          productivity = todayProductivity,
          alarms = alarms,
          focusActive = focusConfig.focusModeEnabled,
          studyModeActive = focusConfig.studyModeEnabled,
          nextRecommendation = nextRecommendation,
          voiceState = alfredVoiceState,
          cloudState = alfredCloudState,
          isOnline = isOnline,
          chatHistory = alfredChatHistory,
          conversationIds = conversationIds,
          activeConversationId = activeConversationId,
          latestAnnouncement = latestAnnouncement,
          pendingAction = pendingAlfredAction,
          recordAudioGranted = permissions.recordAudioGranted,
          onStartVoiceListening = viewModel::startAlfredVoiceListening,
          onStopVoiceListening = viewModel::stopAlfredVoiceListening,
          onStopSpeaking = viewModel::stopAlfredSpeaking,
          onToggleMute = viewModel::toggleAlfredMute,
          onToggleOfflineVoice = viewModel::toggleOfflineSpeechPreference,
          onSubmitCommand = viewModel::submitAlfredCommand,
          onConfirmPendingAction = viewModel::confirmPendingAlfredAction,
          onCancelPendingAction = viewModel::cancelPendingAlfredAction,
          onStartNewConversation = viewModel::startNewAlfredConversation,
          onSelectConversation = viewModel::selectAlfredConversation,
          onDeleteConversation = viewModel::deleteCurrentAlfredConversation,
          onStartRecommendedFocus = { mins ->
            viewModel.startFocusTimer(mins)
            showAiSheet = false
            activeTab = VisionTab.Distraction
          },
          onCompleteRecommendedTask = viewModel::toggleTask,
          onTriggerAnnouncement = { type -> viewModel.triggerPersonalizedAnnouncement(type) },
          onReplayLastAudio = viewModel::replayLastAlfredReply,
          onPermissionsChanged = viewModel::refreshPermissionsAndUsage,
          onDismiss = { showAiSheet = false },
        )
      }

      if (showMenuSheet) {
        SystemMenuAndPermissionsSheet(
          activeTab = activeTab,
          themeMode = themeMode,
          permissions = permissions,
          onSetThemeMode = viewModel::setThemeMode,
          onSelectTab = { activeTab = it },
          onRefreshPermissions = viewModel::refreshPermissionsAndUsage,
          onDismiss = { showMenuSheet = false },
        )
      }
    }
  }
}
