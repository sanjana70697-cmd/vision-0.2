package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.WarningAmber
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
import com.example.data.local.DateKeyHelper
import com.example.data.local.DistractingAppEntity
import com.example.data.local.FocusConfigEntity
import com.example.data.local.FocusSessionEntity
import com.example.system.InstalledAppCandidate
import com.example.system.UsageStatsHelper
import com.example.ui.PermissionStatusSnapshot
import com.example.ui.components.CentralAiBannerCard
import com.example.ui.components.VisionTopBar
import com.example.ui.theme.BordeauxCrimson
import com.example.ui.theme.BordeauxDeep
import com.example.ui.theme.BordeauxRose
import com.example.ui.theme.DistractionWarnAmber
import com.example.ui.theme.EducationalAllowGreen
import com.example.ui.theme.LocalVisionPalette
import com.example.ui.theme.TaupeInk
import com.example.ui.theme.TaupeMarble

@Composable
fun DistractionScreen(
  focusConfig: FocusConfigEntity,
  distractingApps: List<DistractingAppEntity>,
  focusSessions: List<FocusSessionEntity>,
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
  onResetTimer: (Int) -> Unit,
  onTriggerStudyWarning: () -> Unit,
  onDismissStudyWarning: () -> Unit,
  onRefreshUsage: () -> Unit,
  onOpenAiSheet: () -> Unit,
  onOpenMenu: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalVisionPalette.current
  val context = LocalContext.current
  var selectedPresetMinutes by remember { mutableIntStateOf(25) }
  var showAppPickerSheet by remember { mutableStateOf(false) }

  val notificationPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) {
    onRefreshUsage()
  }

  val todayKey = DateKeyHelper.todayKey()
  val todayFocusMinutes = focusSessions.filter { it.dateKey == todayKey }.sumOf { it.actualMinutes }
  val blockedTotalToday = focusSessions.filter { it.dateKey == todayKey }.sumOf { it.blockedAttemptsCount }
  val totalMonitoredUsage = distractingApps.sumOf { it.todayUsageMinutes }

  val remSecs = focusConfig.activeTimerRemainingSeconds.coerceAtLeast(0)
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
      // Focus Mode Hero Toggle Card (Bordeaux + Taupe Marble)
      item {
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .testTag("focus_mode_card"),
          shape = RoundedCornerShape(24.dp),
          color = palette.darkGlassCard,
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
                  .background(BordeauxRose.copy(alpha = 0.25f))
                  .border(1.5.dp, BordeauxCrimson, CircleShape),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Filled.Shield,
                  contentDescription = "Focus Mode",
                  tint = palette.primaryCard,
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
                checkedTrackColor = BordeauxCrimson,
                uncheckedThumbColor = palette.onDarkCardMuted,
                uncheckedTrackColor = BordeauxDeep,
              ),
            )
          }
        }
      }

      // Permission Consent Cards (Local Usage Access & Notification Alerts — zero Accessibility APIs)
      if (!permissions.usageAccessGranted || !permissions.notificationsGranted) {
        item {
          Surface(
            modifier = Modifier
              .fillMaxWidth()
              .widthIn(max = 600.dp)
              .testTag("focus_permissions_card"),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF421621),
            border = BorderStroke(1.dp, DistractionWarnAmber),
          ) {
            Column(
              modifier = Modifier.padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text(
                text = "Focus & App Monitoring Permissions",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
              )
              if (!permissions.usageAccessGranted) {
                Text(
                  text = "• Usage Access (UsageStatsManager) lets VISION read daily app minutes and detect distracting apps locally on device during Focus sessions. Usage data is never uploaded.",
                  style = MaterialTheme.typography.bodySmall,
                  color = TaupeMarble,
                )
              }
              if (!permissions.notificationsGranted) {
                Text(
                  text = "• Notifications let VISION display Focus Session countdown status and Study Mode distraction alerts.",
                  style = MaterialTheme.typography.bodySmall,
                  color = TaupeMarble,
                )
              }
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!permissions.usageAccessGranted) {
                  OutlinedButton(onClick = { UsageStatsHelper.openUsageAccessSettings(context) }) {
                    Text("Grant Usage Access", color = Color.White)
                  }
                }
                if (!permissions.notificationsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                  OutlinedButton(
                    onClick = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                  ) {
                    Text("Allow Notifications", color = Color.White)
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
                  tint = BordeauxRose,
                  modifier = Modifier.size(16.dp),
                )
                Text(
                  text = "Focus Session",
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

              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Surface(
                  shape = CircleShape,
                  color = BordeauxRose,
                  onClick = {
                    if (focusConfig.activeTimerRunning) onPauseTimer()
                    else onStartTimer(selectedPresetMinutes)
                  },
                  modifier = Modifier
                    .size(38.dp)
                    .testTag("focus_timer_toggle"),
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Icon(
                      imageVector = if (focusConfig.activeTimerRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                      contentDescription = if (focusConfig.activeTimerRunning) "Pause timer" else "Start timer",
                      tint = Color.White,
                      modifier = Modifier.size(20.dp),
                    )
                  }
                }
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
                    onClick = {
                      selectedPresetMinutes = mins
                      if (!focusConfig.activeTimerRunning) {
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

          Surface(
            modifier = Modifier
              .weight(0.8f)
              .testTag("distraction_stats_card"),
            shape = RoundedCornerShape(22.dp),
            color = palette.darkGlassCard,
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
                  tint = BordeauxCrimson,
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
                text = "$blockedTotalToday Blocked · ${totalMonitoredUsage}m App Use",
                style = MaterialTheme.typography.labelSmall,
                color = BordeauxCrimson,
              )
            }
          }
        }
      }

      // Study Mode Card (Real YouTube Allowance + UsageStats Monitoring + Grace Period + Warning)
      item {
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .testTag("study_mode_card"),
          shape = RoundedCornerShape(22.dp),
          color = palette.primaryCard,
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
                    tint = palette.primaryCard,
                    modifier = Modifier.size(22.dp),
                  )
                }
                Column {
                  Text("Study Mode", style = MaterialTheme.typography.titleMedium, color = palette.onPrimaryCard)
                  Text(
                    text = "Allow YouTube for learning; monitor session duration via UsageStats",
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

            // Configurable Grace Period Selector
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                text = "Grace Period:",
                style = MaterialTheme.typography.labelLarge,
                color = palette.onPrimaryCard,
              )
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(15, 30, 60).forEach { secs ->
                  val active = focusConfig.gracePeriodSeconds == secs
                  Surface(
                    shape = RoundedCornerShape(50),
                    color = if (active) BordeauxRose else palette.secondaryCard,
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

            Surface(
              shape = RoundedCornerShape(14.dp),
              color = EducationalAllowGreen.copy(alpha = 0.14f),
              border = BorderStroke(1.dp, EducationalAllowGreen.copy(alpha = 0.5f)),
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Icon(
                  imageVector = Icons.Filled.CheckCircle,
                  contentDescription = null,
                  tint = Color(0xFF237A3A),
                  modifier = Modifier.size(18.dp),
                )
                Text(
                  text = "YouTube stays permitted in Study Mode. VISION monitors foreground time via UsageStatsManager (without reading video content) and warns before applying restriction.",
                  style = MaterialTheme.typography.bodySmall,
                  color = palette.onPrimaryCard,
                )
              }
            }

            // Live Warning / Grace Countdown Status + Interactive Check Trigger
            Surface(
              modifier = Modifier
                .fillMaxWidth()
                .testTag("study_warning_preview"),
              shape = RoundedCornerShape(14.dp),
              color = if (focusConfig.studyWarningActive) BordeauxDeep else palette.secondaryCard,
              border = BorderStroke(
                1.dp,
                if (focusConfig.studyWarningActive) DistractionWarnAmber else palette.cardBorder,
              ),
            ) {
              Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Icon(
                    imageVector = Icons.Filled.WarningAmber,
                    contentDescription = "Study Mode Warning",
                    tint = DistractionWarnAmber,
                    modifier = Modifier.size(20.dp),
                  )
                  Text(
                    text = if (focusConfig.studyWarningActive)
                      "Warning Active · ${focusConfig.studyGraceRemainingSeconds}s Grace Left"
                    else focusConfig.studyWarningMessage.ifBlank { "Study Mode Monitoring Ready" },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (focusConfig.studyWarningActive) Color.White else palette.onPrimaryCard,
                  )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  if (!focusConfig.studyWarningActive) {
                    OutlinedButton(onClick = onTriggerStudyWarning) {
                      Text("Run Warning Check", color = palette.onPrimaryCard)
                    }
                  } else {
                    Button(
                      onClick = onDismissStudyWarning,
                      colors = ButtonDefaults.buttonColors(containerColor = EducationalAllowGreen),
                    ) {
                      Text("I'm Studying · Cancel", color = Color.White)
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
                  text = "App Blocking & Usage Monitor",
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
                color = BordeauxRose.copy(alpha = 0.25f),
                border = BorderStroke(1.dp, BordeauxCrimson),
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
                  text = "Block ${focusConfig.blockDurationMinutes}m",
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
                  color = if (app.isBlocked) BordeauxRose.copy(alpha = 0.35f) else palette.primaryCard,
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
                      else if (app.isBlocked) "Blocked" else "Open",
                      style = MaterialTheme.typography.labelSmall,
                      color = if (app.isBlocked) BordeauxCrimson else Color(0xFF1F6F36),
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
                  color = if (app.allowEducational) EducationalAllowGreen else BordeauxCrimson,
                  trackColor = BordeauxDeep,
                  strokeCap = StrokeCap.Round,
                )
              }
            }
          }
        }
      }

      // Automatic Blocking Rules
      item {
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .testTag("automatic_blocking_card"),
          shape = RoundedCornerShape(22.dp),
          color = palette.primaryCard,
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
              Icon(Icons.Filled.Block, contentDescription = null, tint = BordeauxRose, modifier = Modifier.size(18.dp))
              Text("Automatic Blocking Rules", style = MaterialTheme.typography.titleMedium, color = palette.onPrimaryCard)
            }

            AutoRuleRow(
              title = "Auto-Block During Focus Timer",
              subtitle = "Restricts selected apps while Focus Session runs",
              checked = focusConfig.autoBlockOnSchedule,
              onCheckedChange = { onUpdateAutoRules(it, null, null) },
            )
            AutoRuleRow(
              title = "Study Mode YouTube Grace Guard",
              subtitle = "Warn after sustained YouTube usage before restricting",
              checked = focusConfig.educationalYoutubeFilter,
              onCheckedChange = { onUpdateAutoRules(null, it, null) },
            )
            AutoRuleRow(
              title = "Strict Session Lock",
              subtitle = "Require manual reset to cancel active timer early",
              checked = focusConfig.strictLockMode,
              onCheckedChange = { onUpdateAutoRules(null, null, it) },
            )
          }
        }
      }

      // Focus History from Room
      item {
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .testTag("focus_history_card"),
          shape = RoundedCornerShape(22.dp),
          color = palette.darkGlassCard,
          border = BorderStroke(1.dp, palette.darkCardBorder),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(Icons.Filled.History, contentDescription = null, tint = BordeauxCrimson, modifier = Modifier.size(18.dp))
              Text("Focus History", style = MaterialTheme.typography.titleMedium, color = palette.onDarkCard)
            }
            if (focusSessions.isEmpty()) {
              Text(
                text = "No Focus sessions recorded yet. Start the Focus Session timer above to record real history.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.onDarkCardMuted,
              )
            } else {
              focusSessions.take(5).forEach { session ->
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Text(
                    text = "${session.dateKey} · ${session.plannedMinutes}m goal (${session.status})",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.onDarkCard,
                  )
                  Text(
                    text = "${session.actualMinutes}m · ${session.blockedAttemptsCount} blk",
                    style = MaterialTheme.typography.labelSmall,
                    color = BordeauxCrimson,
                  )
                }
              }
            }
          }
        }
      }

      item {
        CentralAiBannerCard(
          summaryText = "${todayFocusMinutes}m focused today · ${distractingApps.count { it.isBlocked }} apps blocked",
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
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var customName by remember { mutableStateOf("") }
  var customPackage by remember { mutableStateOf("") }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = TaupeMarble,
    contentColor = TaupeInk,
    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp)
        .padding(bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text("Add App to Monitor", style = MaterialTheme.typography.titleLarge, color = TaupeInk)
      Text(
        text = "Choose a popular app or enter a package name manually. VISION does not scan your full app inventory.",
        style = MaterialTheme.typography.bodySmall,
        color = TaupeInk.copy(alpha = 0.72f),
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
            color = if (isAdded) Color(0xFFC4B4AA) else Color.White.copy(alpha = 0.7f),
            onClick = { onSelect(app) },
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(app.appName, style = MaterialTheme.typography.titleSmall, color = TaupeInk)
                Text(
                  text = if (app.isInstalled) "${app.packageName} · Installed" else app.packageName,
                  style = MaterialTheme.typography.bodySmall,
                  color = TaupeInk.copy(alpha = 0.65f),
                )
              }
              Text(
                text = if (isAdded) "Added" else "Add",
                style = MaterialTheme.typography.labelLarge,
                color = BordeauxRose,
              )
            }
          }
        }
      }

      OutlinedTextField(
        value = customName,
        onValueChange = { customName = it },
        label = { Text("Custom App Name (optional)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
      OutlinedTextField(
        value = customPackage,
        onValueChange = { customPackage = it },
        label = { Text("Package Name (e.g. com.example.app)") },
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
