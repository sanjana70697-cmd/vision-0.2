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

  val todayTasks by viewModel.todayTasks.collectAsStateWithLifecycle()
  val tomorrowTasks by viewModel.tomorrowTasks.collectAsStateWithLifecycle()
  val pendingTasks by viewModel.allPendingTasks.collectAsStateWithLifecycle()
  val goals by viewModel.goals.collectAsStateWithLifecycle()
  val yogaStreakDays by viewModel.yogaStreakDays.collectAsStateWithLifecycle()
  val alarms by viewModel.alarms.collectAsStateWithLifecycle()
  val distractingApps by viewModel.distractingApps.collectAsStateWithLifecycle()
  val focusSessions by viewModel.focusSessions.collectAsStateWithLifecycle()
  val focusConfig by viewModel.focusConfig.collectAsStateWithLifecycle()
  val todayProductivity by viewModel.todayProductivity.collectAsStateWithLifecycle()
  val weeklySnapshots by viewModel.weeklySnapshots.collectAsStateWithLifecycle()
  val permissions by viewModel.permissions.collectAsStateWithLifecycle()
  val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
  val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

  // ALFRED Phase 3 State
  val nextRecommendation by viewModel.nextTaskRecommendation.collectAsStateWithLifecycle()
  val alfredVoiceState by viewModel.alfredVoiceState.collectAsStateWithLifecycle()
  val alfredCloudState by viewModel.alfredCloudState.collectAsStateWithLifecycle()
  val alfredChatHistory by viewModel.alfredChatHistory.collectAsStateWithLifecycle()
  val latestAnnouncement by viewModel.latestAnnouncement.collectAsStateWithLifecycle()
  val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()

  val snackbarHostState = remember { SnackbarHostState() }
  LaunchedEffect(errorMessage) {
    errorMessage?.let { msg ->
      snackbarHostState.showSnackbar(msg)
      viewModel.clearError()
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

  VisionTheme(activeTab = activeTab) {
    VisionScreenBackground(activeTab = activeTab) {
      BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
          containerColor = Color.Transparent,
          contentWindowInsets = WindowInsets(0, 0, 0, 0),
          snackbarHost = { SnackbarHost(snackbarHostState) },
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
              isFocusRunning = focusConfig.activeTimerRunning,
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
                    productivity = todayProductivity,
                    yogaStreakDays = yogaStreakDays,
                    onToggleTask = viewModel::toggleTask,
                    onTogglePriority = viewModel::toggleTaskPriority,
                    onSaveTask = viewModel::saveTask,
                    onDeleteTask = viewModel::deleteTask,
                    onSaveGoal = viewModel::saveGoal,
                    onIncrementGoal = viewModel::incrementGoal,
                    onDeleteGoal = viewModel::deleteGoal,
                    onLogYoga = viewModel::logYogaSession,
                    onLogSkill = viewModel::logSkillSession,
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
                    distractingApps = distractingApps,
                    focusSessions = focusSessions,
                    installedApps = installedApps,
                    permissions = permissions,
                    onSetFocusMode = viewModel::setFocusMode,
                    onSetStudyMode = viewModel::setStudyMode,
                    onSetBlockDuration = viewModel::setBlockDurationMinutes,
                    onSetGracePeriod = viewModel::setGracePeriodSeconds,
                    onUpdateAutoRules = viewModel::updateAutoBlockRules,
                    onToggleAppBlock = viewModel::toggleDistractingAppBlock,
                    onAddDistractingApp = viewModel::addDistractingApp,
                    onStartTimer = viewModel::startFocusTimer,
                    onPauseTimer = viewModel::pauseFocusTimer,
                    onResetTimer = viewModel::resetFocusTimer,
                    onTriggerStudyWarning = viewModel::triggerStudyWarningCheck,
                    onDismissStudyWarning = viewModel::dismissStudyWarning,
                    onRefreshUsage = viewModel::refreshPermissionsAndUsage,
                    onOpenAiSheet = { showAiSheet = true },
                    onOpenMenu = { showMenuSheet = true },
                  )

                  VisionTab.Report -> ReportScreen(
                    todayProductivity = todayProductivity,
                    weeklySnapshots = weeklySnapshots,
                    pendingTasks = pendingTasks,
                    tomorrowTasks = tomorrowTasks,
                    focusConfig = focusConfig,
                    onMovePendingToTomorrow = viewModel::schedulePendingTaskForTomorrow,
                    onSaveTomorrowTask = viewModel::saveTask,
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
          latestAnnouncement = latestAnnouncement,
          recordAudioGranted = permissions.recordAudioGranted,
          onStartVoiceListening = viewModel::startAlfredVoiceListening,
          onStopVoiceListening = viewModel::stopAlfredVoiceListening,
          onStopSpeaking = viewModel::stopAlfredSpeaking,
          onToggleMute = viewModel::toggleAlfredMute,
          onToggleOfflineVoice = viewModel::toggleOfflineSpeechPreference,
          onSubmitCommand = viewModel::submitAlfredCommand,
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
          permissions = permissions,
          onSelectTab = { activeTab = it },
          onRefreshPermissions = viewModel::refreshPermissionsAndUsage,
          onDismiss = { showMenuSheet = false },
        )
      }
    }
  }
}
