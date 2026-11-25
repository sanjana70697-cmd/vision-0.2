package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.ActivitySessionEntity
import com.example.data.local.DateKeyHelper
import com.example.data.local.DistractingAppEntity
import com.example.data.local.FocusConfigEntity
import com.example.data.local.FocusSessionEntity
import com.example.system.InstalledAppCandidate
import com.example.system.UsageStatsHelper
import com.example.ui.FocusTimerUiState
import com.example.ui.PermissionStatusSnapshot
import com.example.ui.components.ActivitySessionEditorSheet
import com.example.ui.components.CentralAiBannerCard
import com.example.ui.components.VisionTopBar
import com.example.ui.theme.BordeauxCrimson
import com.example.ui.theme.BordeauxDeep
import com.example.ui.theme.BordeauxRose
import com.example.ui.theme.DistractionWarnAmber
import com.example.ui.theme.EducationalAllowGreen
import com.example.ui.theme.LocalVisionPalette
import com.example.ui.theme.VisionDangerRed
import com.example.ui.theme.visionPrimaryCardFieldColors

@Composable
fun DistractionScreen(
  focusConfig: FocusConfigEntity,
  focusTimer: FocusTimerUiState,
  distractingApps: List<DistractingAppEntity>,
  focusSessions: List<FocusSessionEntity>,
  todayActivitySessions: List<ActivitySessionEntity>,
  installedApps: List<InstalledAppCandidate>,
  permissions: PermissionStatusSnapshot,
  onSetFocusMode: (Boolean) -> Unit,
  onSetStudyMode: (Boolean) -> Unit,
  onSetBlockDuration: (Int) -> Unit,
  onSetGracePeriod: (Int) -> Unit,
  onUpdateAutoRules: (Boolean?, Boolean?, Boolean?) -> Unit,
  onToggleAppBlock: (DistractingAppEntity) -> Unit,
  onAddDistractingApp: (InstalledAppCandidate) -> Unit,
  onOpenAppPicker: () -> Unit = {},
  onStartTimer: (Int) -> Unit,
  onPauseTimer: () -> Unit,
  onResumeTimer: () -> Unit,
  onResetTimer: (Int) -> Unit,
  onEndTimer: (Int) -> Unit,
  onStartStudySession: (Int) -> Unit,
  onStopStudySession: () -> Unit,
  onDismissStudyWarning: () -> Unit,
  onSaveActivitySession: (ActivitySessionEntity) -> Unit,
  onDeleteActivitySession: (Long) -> Unit,
  onRefreshUsage: () -> Unit,
  onOpenAiSheet: () -> Unit,
  onOpenMenu: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalVisionPalette.current
  val context = LocalContext.current
  var selectedPresetMinutes by remember { mutableIntStateOf(25) }
  var showAppPickerSheet by remember { mutableStateOf(false) }
  var showFocusEditor by remember { mutableStateOf(false) }
  var editingFocusRecord by remember { mutableStateOf<ActivitySessionEntity?>(null) }
  var deleteFocusConfirm by remember { mutableStateOf<ActivitySessionEntity?>(null) }

  val notificationPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) {
    onRefreshUsage()
  }

  val todayKey = DateKeyHelper.todayKey()
  val focusRecordsToday = remember(todayActivitySessions) {
    todayActivitySessions.filter { it.activityType == "FOCUS" }
  }
  val liveRunningMins = if (focusTimer.isActive) (focusTimer.elapsedSeconds / 60L).toInt() else 0
  val todayFocusMinutes = focusRecordsToday.sumOf { it.durationMinutes } + liveRunningMins
  val blockedTotalToday = focusSessions.filter { it.dateKey == todayKey }.sumOf { it.blockedAttemptsCount }
  val totalMonitoredUsage = distractingApps.sumOf { it.todayUsageMinutes }

  val remSecs = (if (focusTimer.isActive) focusTimer.remainingSeconds else focusConfig.activeTimerRemainingSeconds).coerceAtLeast(0)
  val dispHrs = remSecs / 3600
  val dispMins = (remSecs % 3600) / 60
  val dispSecs = remSecs % 60
  val timerString = "%02d:%02d:%02d".format(dispHrs, dispMins, dispSecs)

  Column(modifier = modifier.fillMaxSize()) {
    VisionTopBar(
      rightIcon = Icons.Filled.Security,
      rightContentDescription = "Refresh permissions & usage",
      onMenuClick = onOpenMenu,
      onRightActionClick = onRefreshUsage,
    )

    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .testTag("distraction_screen"),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 104.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      // Focus Mode Hero Toggle Card
      item {
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .testTag("focus_mode_card"),
          shape = RoundedCornerShape(24.dp),
          color = palette.darkGlassCard,
          contentColor = palette.onDarkCard,
          border = BorderStroke(1.2.dp, palette.darkCardBorder),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              modifier = Modifier.weight(1f),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
              Box(
                modifier = Modifier
                  .size(54.dp)
                  .clip(CircleShape)
                  .background(BordeauxRose.copy(alpha = 0.22f))
                  .border(1.5.dp, BordeauxRose, CircleShape),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Filled.Shield,
                  contentDescription = "Focus Mode",
                  tint = palette.accentOnDarkCard,
                  modifier = Modifier.size(28.dp),
                )
              }
              Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                  text = "Focus Mode",
                  style = MaterialTheme.typography.titleLarge,
                  color = palette.onDarkCard,
                )
                Text(
                  text = if (focusConfig.focusModeEnabled)
                    "Active (${focusConfig.blockDurationMinutes}m window) · Monitoring enabled"
                  else "Standby · Toggle to activate focus shield",
                  style = MaterialTheme.typography.bodySmall,
                  color = palette.onDarkCardMuted,
                )
              }
            }

            Switch(
              checked = focusConfig.focusModeEnabled,
              onCheckedChange = onSetFocusMode,
              modifier = Modifier.testTag("focus_mode_switch"),
              colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = BordeauxRose,
                uncheckedThumbColor = palette.onDarkCardMuted,
                uncheckedTrackColor = palette.secondaryCard,
              ),
            )
          }
        }
      }

      // Permission Consent Cards (Local Usage Access & Notification Alerts — zero Accessibility APIs)
      if (!permissions.usageAccessGranted || !permissions.notificationsGranted) {
        item {
          val warnContainer = if (palette.isDarkMode) Color(0xFF421621) else Color(0xFFFFEDF1)
          val warnInk = if (palette.isDarkMode) Color.White else Color(0xFF4A0E1B)
          val warnMuted = if (palette.isDarkMode) Color(0xFFEBD9DD) else Color(0xFF6B2432)
          Surface(
            modifier = Modifier
              .fillMaxWidth()
              .widthIn(max = 600.dp)
              .testTag("focus_permissions_card"),
            shape = RoundedCornerShape(20.dp),
            color = warnContainer,
            border = BorderStroke(1.dp, DistractionWarnAmber),
          ) {
            Column(
              modifier = Modifier.padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text(
                text = "Focus & App Monitoring Permissions",
                style = MaterialTheme.typography.titleSmall,
                color = warnInk,
              )
              if (!permissions.usageAccessGranted) {
                Text(
                  text = "• Usage Access (UsageStatsManager) allows VISION to read daily app usage minutes and detect when you switch to YouTube or other monitored apps during active sessions. Data stays on your device.",
                  style = MaterialTheme.typography.bodySmall,
                  color = warnMuted,
                )
              }
              if (!permissions.notificationsGranted) {
                Text(
                  text = "• Notifications let VISION show the live Study Session foreground timer and alert you when you switch to a monitored distracting app.",
                  style = MaterialTheme.typography.bodySmall,
                  color = warnMuted,
                )
              }
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!permissions.usageAccessGranted) {
                  OutlinedButton(
                    onClick = { UsageStatsHelper.openUsageAccessSettings(context) },
                    border = BorderStroke(1.dp, warnInk.copy(alpha = 0.5f)),
                  ) {
                    Text("Grant Usage Access", color = warnInk)
                  }
                }
                if (!permissions.notificationsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                  OutlinedButton(
                    onClick = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    border = BorderStroke(1.dp, warnInk.copy(alpha = 0.5f)),
                  ) {
                    Text("Allow Notifications", color = warnInk)
                  }
                }
              }
            }
          }
        }
      }

      // Focus Session Timer + Real Distraction Statistics
      item {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Surface(
            modifier = Modifier
              .weight(1.2f)
              .testTag("focus_timer_card"),
            shape = RoundedCornerShape(22.dp),
            color = palette.primaryCard,
            contentColor = palette.onPrimaryCard,
            border = BorderStroke(1.dp, palette.cardBorder),
          ) {
            Column(
              modifier = Modifier.padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Icon(
                  imageVector = Icons.Filled.Timer,
                  contentDescription = "Focus Session Timer",
                  tint = palette.accentOnPrimaryCard,
                  modifier = Modifier.size(16.dp),
                )
                Text(
                  text = "Focus Timer",
                  style = MaterialTheme.typography.titleSmall,
                  color = palette.onPrimaryCard,
                )
              }

              Text(
                text = timerString,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = palette.onPrimaryCard,
              )

              if (focusTimer.isActive) {
                Text(
                  text = "Elapsed: ${DateKeyHelper.formatElapsedClock(focusTimer.elapsedSeconds)} · ${if (focusTimer.isRunning) "Running" else "Paused"}",
                  style = MaterialTheme.typography.labelSmall,
                  color = palette.accentOnPrimaryCard,
                )
              }

              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Surface(
                  shape = RoundedCornerShape(50),
                  color = BordeauxRose,
                  onClick = {
                    when {
                      focusTimer.isRunning || focusConfig.activeTimerRunning -> onPauseTimer()
                      focusTimer.isActive -> onResumeTimer()
                      else -> onStartTimer(selectedPresetMinutes)
                    }
                  },
                  modifier = Modifier
                    .height(40.dp)
                    .testTag("focus_timer_toggle"),
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                  ) {
                    Icon(
                      imageVector = if (focusTimer.isRunning || focusConfig.activeTimerRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                      contentDescription = if (focusTimer.isRunning) "Pause timer" else if (focusTimer.isActive) "Resume timer" else "Start timer",
                      tint = Color.White,
                      modifier = Modifier.size(16.dp),
                    )
                    Text(
                      text = when {
                        focusTimer.isRunning || focusConfig.activeTimerRunning -> "Pause"
                        focusTimer.isActive -> "Resume"
                        else -> "Start"
                      },
                      style = MaterialTheme.typography.labelMedium,
                      color = Color.White,
                    )
                  }
                }
                if (focusTimer.isActive) {
                  Surface(
                    shape = RoundedCornerShape(50),
                    color = BordeauxDeep,
                    onClick = { onEndTimer(selectedPresetMinutes) },
                    modifier = Modifier
                      .height(40.dp)
                      .testTag("end_focus_timer_button"),
                  ) {
                    Row(
                      modifier = Modifier.padding(horizontal = 10.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                      Icon(Icons.Filled.Stop, contentDescription = "End and save focus", tint = Color.White, modifier = Modifier.size(15.dp))
                      Text("End", style = MaterialTheme.typography.labelMedium, color = Color.White)
                    }
                  }
                } else {
                  IconButton(
                    onClick = { onResetTimer(selectedPresetMinutes) },
                    modifier = Modifier.size(36.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Filled.Refresh,
                      contentDescription = "Reset timer",
                      tint = palette.onPrimaryCardMuted,
                      modifier = Modifier.size(18.dp),
                    )
                  }
                  listOf(25, 45).forEach { mins ->
                    Surface(
                      shape = RoundedCornerShape(50),
                      color = if (selectedPresetMinutes == mins) BordeauxDeep else palette.secondaryCard,
                      border = BorderStroke(1.dp, palette.cardBorder),
                      onClick = {
                        selectedPresetMinutes = mins
                        if (!focusConfig.activeTimerRunning && !focusTimer.isActive) {
                          onResetTimer(mins)
                        }
                      },
                    ) {
                      Text(
                        text = "${mins}m",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selectedPresetMinutes == mins) Color.White else palette.onPrimaryCard,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                      )
                    }
                  }
                }
              }
            }
          }

          Surface(
            modifier = Modifier
              .weight(0.8f)
              .testTag("distraction_stats_card"),
            shape = RoundedCornerShape(22.dp),
            color = palette.darkGlassCard,
            contentColor = palette.onDarkCard,
            border = BorderStroke(1.dp, palette.darkCardBorder),
          ) {
            Column(
              modifier = Modifier.padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Icon(
                  imageVector = Icons.Filled.TrendingUp,
                  contentDescription = "Distraction Statistics",
                  tint = palette.accentOnDarkCard,
                  modifier = Modifier.size(16.dp),
                )
                Text(
                  text = "Stats",
                  style = MaterialTheme.typography.titleSmall,
                  color = palette.onDarkCard,
                )
              }
              Text(
                text = "${todayFocusMinutes}m",
                style = MaterialTheme.typography.titleLarge,
                color = palette.onDarkCard,
              )
              Text(
                text = "Focus Today",
                style = MaterialTheme.typography.bodySmall,
                color = palette.onDarkCardMuted,
              )
              Text(
                text = "$blockedTotalToday Alerts · ${totalMonitoredUsage}m Use",
                style = MaterialTheme.typography.labelSmall,
                color = palette.accentOnDarkCard,
              )
            }
          }
        }
      }

      // Real User-Controlled Study Session & Distraction Monitoring Card
      item {
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .testTag("study_mode_card"),
          shape = RoundedCornerShape(22.dp),
          color = palette.primaryCard,
          contentColor = palette.onPrimaryCard,
          border = BorderStroke(1.2.dp, palette.cardBorder),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Box(
                  modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(BordeauxDeep),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(
                    imageVector = Icons.Filled.School,
                    contentDescription = "Study Mode",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                  )
                }
                Column {
                  Text("Study Mode Session", style = MaterialTheme.typography.titleMedium, color = palette.onPrimaryCard)
                  Text(
                    text = if (focusConfig.activeTimerRunning && focusConfig.studyModeEnabled)
                      "Active Session · $timerString remaining"
                    else "Start a supervised session to monitor app switches",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.onPrimaryCardMuted,
                  )
                }
              }
              Switch(
                checked = focusConfig.studyModeEnabled,
                onCheckedChange = onSetStudyMode,
                modifier = Modifier.testTag("study_mode_switch"),
                colors = SwitchDefaults.colors(
                  checkedThumbColor = Color.White,
                  checkedTrackColor = BordeauxRose,
                  uncheckedThumbColor = palette.onPrimaryCardMuted,
                  uncheckedTrackColor = palette.secondaryCard,
                ),
              )
            }

            // Duration selector for Study Session
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                text = "Session Length:",
                style = MaterialTheme.typography.labelLarge,
                color = palette.onPrimaryCard,
              )
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(25, 45, 60).forEach { mins ->
                  val active = selectedPresetMinutes == mins
                  Surface(
                    shape = RoundedCornerShape(50),
                    color = if (active) BordeauxRose else palette.secondaryCard,
                    border = BorderStroke(1.dp, palette.cardBorder),
                    onClick = { selectedPresetMinutes = mins },
                  ) {
                    Text(
                      text = "${mins}m",
                      style = MaterialTheme.typography.labelSmall,
                      color = if (active) Color.White else palette.onPrimaryCard,
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                  }
                }
              }
            }

            // Configurable Grace Period Selector
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                text = "Reminder Grace:",
                style = MaterialTheme.typography.labelLarge,
                color = palette.onPrimaryCard,
              )
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(15, 30, 60).forEach { secs ->
                  val active = focusConfig.gracePeriodSeconds == secs
                  Surface(
                    shape = RoundedCornerShape(50),
                    color = if (active) BordeauxRose else palette.secondaryCard,
                    border = BorderStroke(1.dp, palette.cardBorder),
                    onClick = { onSetGracePeriod(secs) },
                  ) {
                    Text(
                      text = "${secs}s",
                      style = MaterialTheme.typography.labelSmall,
                      color = if (active) Color.White else palette.onPrimaryCard,
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                  }
                }
              }
            }

            // Transparent Android Scope Disclosure
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = EducationalAllowGreen.copy(alpha = 0.14f),
              border = BorderStroke(1.dp, EducationalAllowGreen.copy(alpha = 0.45f)),
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Icon(
                  imageVector = Icons.Filled.Info,
                  contentDescription = null,
                  tint = Color(0xFF237A3A),
                  modifier = Modifier.size(18.dp),
                )
                Text(
                  text = "Using Android UsageStatsManager, VISION detects switches away from VISION to YouTube or monitored distracting apps during an active Study Session (throttled every 2 minutes). VISION does not track scrolling or read content inside third-party apps.",
                  style = MaterialTheme.typography.bodySmall,
                  color = palette.onPrimaryCard,
                )
              }
            }

            // Live Study Session Control & Switch Alert Status
            Surface(
              modifier = Modifier
                .fillMaxWidth()
                .testTag("study_session_control_card"),
              shape = RoundedCornerShape(16.dp),
              color = if (focusConfig.studyWarningActive) BordeauxDeep else palette.secondaryCard,
              border = BorderStroke(
                1.dp,
                if (focusConfig.studyWarningActive) DistractionWarnAmber else palette.cardBorder,
              ),
            ) {
              Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Icon(
                    imageVector = if (focusConfig.studyWarningActive) Icons.Filled.WarningAmber
                    else if (focusConfig.activeTimerRunning && focusConfig.studyModeEnabled) Icons.Filled.CheckCircle
                    else Icons.Filled.School,
                    contentDescription = "Study Session Status",
                    tint = if (focusConfig.studyWarningActive) DistractionWarnAmber else palette.accentOnPrimaryCard,
                    modifier = Modifier.size(20.dp),
                  )
                  Text(
                    text = when {
                      focusConfig.studyWarningActive ->
                        focusConfig.studyWarningMessage.ifBlank { "Distracting app switch detected during Study Session." }
                      focusConfig.activeTimerRunning && focusConfig.studyModeEnabled ->
                        focusConfig.studyWarningMessage.ifBlank {
                          if (permissions.usageAccessGranted) "Study Session running · App-switch monitor active"
                          else "Study Session running · Usage Access needed for switch alerts"
                        }
                      !permissions.usageAccessGranted ->
                        "Usage Access not granted yet — timer runs locally without foreground app detection."
                      else ->
                        "Ready to start a ${selectedPresetMinutes}m supervised Study Session."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = if (focusConfig.studyWarningActive) Color.White else palette.onPrimaryCard,
                  )
                }

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  if (focusConfig.activeTimerRunning) {
                    Button(
                      onClick = onStopStudySession,
                      colors = ButtonDefaults.buttonColors(containerColor = VisionDangerRed),
                      modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("stop_study_session_button"),
                    ) {
                      Icon(Icons.Filled.Stop, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                      Spacer(Modifier.size(6.dp))
                      Text("Stop Session", color = Color.White)
                    }
                  } else {
                    Button(
                      onClick = { onStartStudySession(selectedPresetMinutes) },
                      colors = ButtonDefaults.buttonColors(containerColor = BordeauxRose),
                      modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("start_study_session_button"),
                    ) {
                      Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                      Spacer(Modifier.size(6.dp))
                      Text("Start ${selectedPresetMinutes}m Study Session", color = Color.White)
                    }
                  }

                  if (focusConfig.studyWarningActive) {
                    OutlinedButton(
                      onClick = onDismissStudyWarning,
                      border = BorderStroke(1.dp, Color.White.copy(alpha = 0.7f)),
                      modifier = Modifier
                        .height(44.dp)
                        .testTag("dismiss_study_warning_button"),
                    ) {
                      Text("Dismiss Alert", color = Color.White)
                    }
                  }
                }
              }
            }
          }
        }
      }

      // Selected Distracting Applications & Real App Usage Monitoring
      item {
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .testTag("app_blocking_card"),
          shape = RoundedCornerShape(22.dp),
          color = palette.darkGlassCard,
          contentColor = palette.onDarkCard,
          border = BorderStroke(1.dp, palette.darkCardBorder),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Monitored Apps & Usage",
                  style = MaterialTheme.typography.titleMedium,
                  color = palette.onDarkCard,
                )
                Text(
                  text = if (permissions.usageAccessGranted)
                    "Live UsageStatsManager · ${totalMonitoredUsage}m today"
                  else "Grant Usage Access to read live app minutes",
                  style = MaterialTheme.typography.bodySmall,
                  color = palette.onDarkCardMuted,
                )
              }
              Surface(
                shape = RoundedCornerShape(50),
                color = BordeauxRose.copy(alpha = 0.22f),
                border = BorderStroke(1.dp, palette.accentOnDarkCard),
                onClick = {
                  val next = when (focusConfig.blockDurationMinutes) {
                    30 -> 60
                    60 -> 120
                    120 -> 240
                    else -> 30
                  }
                  onSetBlockDuration(next)
                },
              ) {
                Text(
                  text = "Window ${focusConfig.blockDurationMinutes}m",
                  style = MaterialTheme.typography.labelSmall,
                  color = palette.onDarkCard,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
              }
            }

            // Quick Toggle App Chips + Add Installed App
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              distractingApps.take(4).forEach { app ->
                Surface(
                  modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize(),
                  shape = RoundedCornerShape(14.dp),
                  color = if (app.isBlocked) BordeauxDeep else palette.primaryCard,
                  border = BorderStroke(
                    1.dp,
                    if (app.isBlocked) BordeauxCrimson else EducationalAllowGreen,
                  ),
                  onClick = { onToggleAppBlock(app) },
                ) {
                  Column(
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                  ) {
                    Text(
                      text = app.shortTag,
                      style = MaterialTheme.typography.titleSmall,
                      color = if (app.isBlocked) Color.White else palette.onPrimaryCard,
                    )
                    Text(
                      text = if (app.allowEducational && focusConfig.studyModeEnabled && !app.isBlocked) "Study"
                      else if (app.isBlocked) "Monitored" else "Open",
                      style = MaterialTheme.typography.labelSmall,
                      color = if (app.isBlocked) Color(0xFFFFB4C2) else Color(0xFF1F6F36),
                    )
                  }
                }
              }

              Surface(
                modifier = Modifier.minimumInteractiveComponentSize(),
                shape = RoundedCornerShape(14.dp),
                color = palette.darkGlassCard,
                border = BorderStroke(1.dp, palette.darkCardBorder),
                onClick = {
                  onOpenAppPicker()
                  showAppPickerSheet = true
                },
              ) {
                Box(
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Select app to monitor",
                    tint = palette.onDarkCard,
                  )
                }
              }
            }

            // Real App Usage Progress Bars
            distractingApps.forEach { app ->
              val ratio = (app.todayUsageMinutes.toFloat() / app.dailyLimitMinutes.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
              Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Text(
                    text = app.appName,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.onDarkCard,
                  )
                  Text(
                    text = "${app.todayUsageMinutes}m / ${app.dailyLimitMinutes}m",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.onDarkCardMuted,
                  )
                }
                LinearProgressIndicator(
                  progress = { ratio },
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50)),
                  color = if (app.allowEducational) EducationalAllowGreen else palette.accentOnDarkCard,
                  trackColor = palette.onDarkCard.copy(alpha = 0.14f),
                  strokeCap = StrokeCap.Round,
                )
              }
            }
          }
        }
      }

      // Automatic Monitoring Rules
      item {
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .testTag("automatic_blocking_card"),
          shape = RoundedCornerShape(22.dp),
          color = palette.primaryCard,
          contentColor = palette.onPrimaryCard,
          border = BorderStroke(1.dp, palette.cardBorder),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(Icons.Filled.Block, contentDescription = null, tint = palette.accentOnPrimaryCard, modifier = Modifier.size(18.dp))
              Text("Focus & Monitoring Rules", style = MaterialTheme.typography.titleMedium, color = palette.onPrimaryCard)
            }

            AutoRuleRow(
              title = "Monitor Apps During Focus Timer",
              subtitle = "Alerts when switching to selected apps while timer runs",
              checked = focusConfig.autoBlockOnSchedule,
              onCheckedChange = { onUpdateAutoRules(it, null, null) },
            )
            AutoRuleRow(
              title = "Study Mode YouTube Alert Guard",
              subtitle = "Notify once per cooldown when switching to YouTube in Study Mode",
              checked = focusConfig.educationalYoutubeFilter,
              onCheckedChange = { onUpdateAutoRules(null, it, null) },
            )
            AutoRuleRow(
              title = "Strict Session Lock",
              subtitle = "Require manual stop to end active session early",
              checked = focusConfig.strictLockMode,
              onCheckedChange = { onUpdateAutoRules(null, null, it) },
            )
          }
        }
      }

      // Focus History (Shared Activity Records + Alerts)
      item {
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .testTag("focus_history_card"),
          shape = RoundedCornerShape(22.dp),
          color = palette.darkGlassCard,
          contentColor = palette.onDarkCard,
          border = BorderStroke(1.dp, palette.darkCardBorder),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(Icons.Filled.History, contentDescription = null, tint = palette.accentOnDarkCard, modifier = Modifier.size(18.dp))
                Text("Focus & Study History", style = MaterialTheme.typography.titleMedium, color = palette.onDarkCard)
              }

              Surface(
                shape = RoundedCornerShape(50),
                color = BordeauxRose,
                onClick = {
                  editingFocusRecord = null
                  showFocusEditor = true
                },
                modifier = Modifier.testTag("add_focus_record_button"),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Icon(Icons.Filled.Add, contentDescription = "Log focus session", tint = Color.White, modifier = Modifier.size(14.dp))
                  Text("Log Focus", style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
              }
            }

            if (focusRecordsToday.isEmpty() && focusSessions.isEmpty()) {
              Text(
                text = "No focus sessions recorded today yet. Start the Focus timer or tap Log Focus to record one.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.onDarkCardMuted,
              )
            } else {
              focusRecordsToday.forEach { record ->
                Surface(
                  shape = RoundedCornerShape(14.dp),
                  color = palette.onDarkCard.copy(alpha = 0.07f),
                  border = BorderStroke(1.dp, palette.darkCardBorder.copy(alpha = 0.7f)),
                ) {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                  ) {
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = "${record.titleOrSkillName} · ${record.durationMinutes}m",
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.onDarkCard,
                      )
                      Text(
                        text = "${DateKeyHelper.formatMillisTime12h(record.startTimeMillis)} – ${DateKeyHelper.formatMillisTime12h(record.endTimeMillis)} · ${record.source}",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.onDarkCardMuted,
                      )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      IconButton(
                        onClick = {
                          editingFocusRecord = record
                          showFocusEditor = true
                        },
                        modifier = Modifier.minimumInteractiveComponentSize(),
                      ) {
                        Icon(
                          imageVector = Icons.Filled.Edit,
                          contentDescription = "Edit focus session",
                          tint = palette.accentOnDarkCard,
                          modifier = Modifier.size(18.dp),
                        )
                      }
                      IconButton(
                        onClick = { deleteFocusConfirm = record },
                        modifier = Modifier.minimumInteractiveComponentSize(),
                      ) {
                        Icon(
                          imageVector = Icons.Filled.DeleteOutline,
                          contentDescription = "Delete focus session",
                          tint = if (palette.isDarkMode) Color(0xFFFF8A80) else VisionDangerRed,
                          modifier = Modifier.size(18.dp),
                        )
                      }
                    }
                  }
                }
              }

              if (blockedTotalToday > 0) {
                Text(
                  text = "Study Mode alerts triggered today: $blockedTotalToday",
                  style = MaterialTheme.typography.labelSmall,
                  color = palette.accentOnDarkCard,
                )
              }
            }
          }
        }
      }

      item {
        CentralAiBannerCard(
          summaryText = "${todayFocusMinutes}m focused today · ${distractingApps.count { it.isBlocked }} apps monitored",
          statusLabel = "FOCUS PORT",
          onOpenAssistantSheet = onOpenAiSheet,
          contextHint = "ALFRED Focus Management Port",
          modifier = Modifier.widthIn(max = 600.dp),
        )
      }
    }
  }

  if (showAppPickerSheet) {
    InstalledAppPickerSheet(
      installedApps = installedApps,
      alreadySelected = distractingApps.map { it.packageName }.toSet(),
      onSelect = { candidate ->
        onAddDistractingApp(candidate)
        showAppPickerSheet = false
      },
      onDismiss = { showAppPickerSheet = false },
    )
  }

  if (showFocusEditor) {
    ActivitySessionEditorSheet(
      initialSession = editingFocusRecord,
      defaultType = "FOCUS",
      allowCategorySwitch = false,
      onSave = { saved ->
        onSaveActivitySession(saved)
        showFocusEditor = false
      },
      onDelete = { id ->
        onDeleteActivitySession(id)
        showFocusEditor = false
      },
      onDismiss = { showFocusEditor = false },
    )
  }

  deleteFocusConfirm?.let { record ->
    AlertDialog(
      onDismissRequest = { deleteFocusConfirm = null },
      title = { Text("Delete Focus Session?") },
      text = {
        Text("Remove \"${record.titleOrSkillName}\" (${record.durationMinutes}m) from your stored focus logs?")
      },
      confirmButton = {
        Button(
          onClick = {
            onDeleteActivitySession(record.id)
            deleteFocusConfirm = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = VisionDangerRed),
        ) {
          Text("Delete", color = Color.White)
        }
      },
      dismissButton = {
        TextButton(onClick = { deleteFocusConfirm = null }) {
          Text("Cancel")
        }
      },
    )
  }
}

@Composable
private fun AutoRuleRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  val palette = LocalVisionPalette.current
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
      Text(title, style = MaterialTheme.typography.titleSmall, color = palette.onPrimaryCard)
      Text(subtitle, style = MaterialTheme.typography.bodySmall, color = palette.onPrimaryCardMuted)
    }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      colors = SwitchDefaults.colors(
        checkedThumbColor = Color.White,
        checkedTrackColor = BordeauxRose,
        uncheckedThumbColor = palette.onPrimaryCardMuted,
        uncheckedTrackColor = palette.secondaryCard,
      ),
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstalledAppPickerSheet(
  installedApps: List<InstalledAppCandidate>,
  alreadySelected: Set<String>,
  onSelect: (InstalledAppCandidate) -> Unit,
  onDismiss: () -> Unit,
) {
  val palette = LocalVisionPalette.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var customName by remember { mutableStateOf("") }
  var customPackage by remember { mutableStateOf("") }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = palette.primaryCard,
    contentColor = palette.onPrimaryCard,
    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp)
        .padding(bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text("Add App to Monitor", style = MaterialTheme.typography.titleLarge, color = palette.onPrimaryCard)
      Text(
        text = "Choose a popular app or enter a package name manually. VISION does not scan your full app inventory.",
        style = MaterialTheme.typography.bodySmall,
        color = palette.onPrimaryCardMuted,
      )

      LazyColumn(
        modifier = Modifier.height(250.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        items(installedApps, key = { it.packageName }) { app ->
          val isAdded = app.packageName in alreadySelected
          Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = palette.secondaryCard,
            border = BorderStroke(1.dp, palette.cardBorder),
            onClick = { onSelect(app) },
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(app.appName, style = MaterialTheme.typography.titleSmall, color = palette.onPrimaryCard)
                Text(
                  text = if (app.isInstalled) "${app.packageName} · Installed" else app.packageName,
                  style = MaterialTheme.typography.bodySmall,
                  color = palette.onPrimaryCardMuted,
                )
              }
              Text(
                text = if (isAdded) "Added" else "Add",
                style = MaterialTheme.typography.labelLarge,
                color = palette.accentOnPrimaryCard,
              )
            }
          }
        }
      }

      OutlinedTextField(
        value = customName,
        onValueChange = { customName = it },
        label = { Text("Custom App Name (optional)") },
        colors = visionPrimaryCardFieldColors(palette),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
      OutlinedTextField(
        value = customPackage,
        onValueChange = { customPackage = it },
        label = { Text("Package Name (e.g. com.example.app)") },
        colors = visionPrimaryCardFieldColors(palette),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
      Button(
        onClick = {
          val pkg = customPackage.trim()
          if (pkg.contains(".")) {
            val label = customName.trim().ifBlank { pkg.substringAfterLast(".").replaceFirstChar { it.uppercaseChar() } }
            val tag = label.take(2).uppercase()
            onSelect(InstalledAppCandidate(packageName = pkg, appName = label, shortTag = tag))
          }
        },
        enabled = customPackage.trim().contains("."),
        modifier = Modifier.fillMaxWidth().height(48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = BordeauxRose),
      ) {
        Text("Add Custom Package", color = Color.White)
      }
    }
  }
}
