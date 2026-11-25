package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import java.util.TimeZone
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.AlarmEntity
import com.example.data.local.DateKeyHelper
import com.example.data.local.FocusConfigEntity
import com.example.system.UsageStatsHelper
import com.example.ui.PermissionStatusSnapshot
import com.example.ui.components.CentralAiBannerCard
import com.example.ui.components.VisionTopBar
import com.example.ui.theme.LocalVisionPalette
import com.example.ui.theme.OliveGroveActive
import com.example.ui.theme.OliveGroveLeaf
import com.example.ui.theme.VisionDangerRed
import com.example.ui.theme.visionPrimaryCardCheckboxColors
import com.example.ui.theme.visionPrimaryCardFieldColors
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

private enum class AlarmTabSegment(val label: String, val singularLabel: String, val kindKey: String) {
  Alarms("Alarms", "Alarm", "ALARM"),
  Reminders("Reminders", "Reminder", "REMINDER"),
  Schedule("Schedule", "Schedule", "SCHEDULE"),
}

@Composable
fun AlarmScreen(
  alarms: List<AlarmEntity>,
  focusConfig: FocusConfigEntity,
  permissions: PermissionStatusSnapshot,
  onSaveAlarm: (AlarmEntity) -> Unit,
  onToggleAlarm: (AlarmEntity, Boolean) -> Unit,
  onDeleteAlarm: (Long) -> Unit,
  onUpdateNotificationRule: (Boolean?, Boolean?, Boolean?) -> Unit,
  onRefreshPermissions: () -> Unit,
  onOpenAiSheet: () -> Unit,
  onOpenMenu: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalVisionPalette.current
  val context = LocalContext.current
  var selectedSegment by remember { mutableStateOf(AlarmTabSegment.Alarms) }
  var editingAlarm by remember { mutableStateOf<AlarmEntity?>(null) }
  var showAlarmEditor by remember { mutableStateOf(false) }

  var currentMillis by remember { mutableStateOf(System.currentTimeMillis()) }
  LaunchedEffect(Unit) {
    while (true) {
      currentMillis = System.currentTimeMillis()
      delay(1000L)
    }
  }

  val cal = remember(currentMillis) { Calendar.getInstance().apply { timeInMillis = currentMillis } }
  val hour24 = cal.get(Calendar.HOUR_OF_DAY)
  val minute = cal.get(Calendar.MINUTE)
  val (timeText, periodText) = DateKeyHelper.formatTime12h(hour24, minute)
  val dateLabel = DateKeyHelper.formatDisplayDate(currentMillis)

  var showNotificationRationale by remember { mutableStateOf(false) }

  val notificationLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) {
    onRefreshPermissions()
  }

  val filteredAlarms = remember(alarms, selectedSegment) {
    alarms.filter { it.kind == selectedSegment.kindKey }
  }
  val armedCount = alarms.count { it.enabled }

  Column(modifier = modifier.fillMaxSize()) {
    VisionTopBar(
      rightIcon = Icons.Filled.Settings,
      rightContentDescription = "Permissions & alarm settings",
      onMenuClick = onOpenMenu,
      onRightActionClick = onOpenMenu,
    )

    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .testTag("alarm_screen"),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 104.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      // Live Analog & Digital Clock Hero Card
      item {
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .testTag("clock_hero_card"),
          shape = RoundedCornerShape(24.dp),
          color = palette.darkGlassCard,
          contentColor = palette.onDarkCard,
          border = BorderStroke(1.dp, palette.darkCardBorder),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
          ) {
            AnalogClockFace(
              hour = hour24,
              minute = minute,
              second = cal.get(Calendar.SECOND),
              faceTint = palette.onDarkCard,
              ringTint = palette.darkCardBorder,
              secondHandTint = OliveGroveActive,
              modifier = Modifier.size(116.dp),
            )

            Column(
              modifier = Modifier.weight(1f),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                  text = timeText,
                  style = MaterialTheme.typography.displayLarge,
                  color = palette.onDarkCard,
                )
                Text(
                  text = periodText,
                  style = MaterialTheme.typography.titleMedium,
                  color = palette.onDarkCardMuted,
                  modifier = Modifier.padding(bottom = 6.dp),
                )
              }
              Text(
                text = dateLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.onDarkCardMuted,
              )
              Surface(
                shape = RoundedCornerShape(50),
                color = OliveGroveLeaf.copy(alpha = if (palette.isDarkMode) 0.35f else 0.16f),
                border = BorderStroke(1.dp, OliveGroveActive.copy(alpha = 0.6f)),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Icon(
                    imageVector = Icons.Filled.WbSunny,
                    contentDescription = null,
                    tint = if (palette.isDarkMode) Color(0xFFF3C669) else Color(0xFFB36B00),
                    modifier = Modifier.size(15.dp),
                  )
                  Text(
                    text = if (armedCount > 0) "$armedCount Armed via AlarmManager" else "No active alarms armed",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.onDarkCard,
                  )
                }
              }
            }
          }
        }
      }

      // Permission Readiness Banner if Notifications, Exact Alarms, or Lock-Screen Display missing
      if (!permissions.notificationsGranted || !permissions.exactAlarmsGranted || !permissions.fullScreenIntentGranted) {
        item {
          val warnContainer = if (palette.isDarkMode) Color(0xFF3E3119) else Color(0xFFFFF4DE)
          val warnInk = if (palette.isDarkMode) Color.White else Color(0xFF3E2900)
          val warnMuted = if (palette.isDarkMode) Color(0xFFE7DFCF) else Color(0xFF5D4415)
          Surface(
            modifier = Modifier
              .fillMaxWidth()
              .widthIn(max = 600.dp),
            shape = RoundedCornerShape(18.dp),
            color = warnContainer,
            border = BorderStroke(1.dp, Color(0xFFE0B45C)),
          ) {
            Column(
              modifier = Modifier.padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = Color(0xFFC87D00))
                Text(
                  text = "Alarm & Notification Permissions Needed",
                  style = MaterialTheme.typography.titleSmall,
                  color = warnInk,
                )
              }
              Text(
                text = "Grant Notifications, Exact Alarms, and Lock-Screen Alarm Display so VISION rings reliably when closed or locked.",
                style = MaterialTheme.typography.bodySmall,
                color = warnMuted,
              )
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!permissions.notificationsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                  OutlinedButton(
                    onClick = { showNotificationRationale = true },
                    border = BorderStroke(1.dp, warnInk.copy(alpha = 0.5f)),
                  ) {
                    Text("Allow Notifications", color = warnInk)
                  }
                }
                if (!permissions.exactAlarmsGranted) {
                  OutlinedButton(
                    onClick = { UsageStatsHelper.openExactAlarmSettings(context) },
                    border = BorderStroke(1.dp, warnInk.copy(alpha = 0.5f)),
                  ) {
                    Text("Exact Alarm Access", color = warnInk)
                  }
                }
                if (!permissions.fullScreenIntentGranted) {
                  OutlinedButton(
                    onClick = { UsageStatsHelper.openFullScreenIntentSettings(context) },
                    border = BorderStroke(1.dp, warnInk.copy(alpha = 0.5f)),
                  ) {
                    Text("Lock-Screen Display", color = warnInk)
                  }
                }
              }
            }
          }
        }
      }

      // Segment Switcher: Alarms | Reminders | Schedule
      item {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          AlarmTabSegment.entries.forEach { segment ->
            val selected = segment == selectedSegment
            val icon = when (segment) {
              AlarmTabSegment.Alarms -> Icons.Filled.Alarm
              AlarmTabSegment.Reminders -> Icons.Filled.NotificationsActive
              AlarmTabSegment.Schedule -> Icons.Filled.CalendarMonth
            }
            Surface(
              modifier = Modifier
                .weight(1f)
                .minimumInteractiveComponentSize()
                .testTag("alarm_segment_${segment.name.lowercase()}"),
              shape = RoundedCornerShape(50),
              color = if (selected) palette.primaryCard else palette.darkGlassCard,
              border = BorderStroke(1.dp, if (selected) palette.accent else palette.darkCardBorder),
              onClick = { selectedSegment = segment },
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
              ) {
                Icon(
                  imageVector = icon,
                  contentDescription = null,
                  tint = if (selected) palette.accentOnPrimaryCard else palette.onDarkCardMuted,
                  modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text(
                  text = segment.label,
                  style = MaterialTheme.typography.labelLarge,
                  color = if (selected) palette.onPrimaryCard else palette.onDarkCard,
                  maxLines = 1,
                )
              }
            }
          }
        }
      }

      if (filteredAlarms.isEmpty()) {
        item {
          Surface(
            modifier = Modifier
              .fillMaxWidth()
              .widthIn(max = 600.dp),
            shape = RoundedCornerShape(20.dp),
            color = palette.primaryCard,
            border = BorderStroke(1.dp, palette.cardBorder),
          ) {
            Text(
              text = "No ${selectedSegment.label.lowercase()} saved yet. Tap \"Add ${selectedSegment.singularLabel}\" below to create one.",
              style = MaterialTheme.typography.bodyMedium,
              color = palette.onPrimaryCardMuted,
              modifier = Modifier.padding(18.dp),
            )
          }
        }
      } else {
        items(filteredAlarms, key = { it.id }) { alarm ->
          AlarmEntryCard(
            alarm = alarm,
            onClick = {
              editingAlarm = alarm
              showAlarmEditor = true
            },
            onToggle = { enabled ->
              if (enabled && !permissions.notificationsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                showNotificationRationale = true
              }
              onToggleAlarm(alarm, enabled)
            },
            onDelete = { onDeleteAlarm(alarm.id) },
            modifier = Modifier.widthIn(max = 600.dp),
          )
        }
      }

      // Add Alarm / Reminder / Schedule CTA
      item {
        Button(
          onClick = {
            editingAlarm = null
            showAlarmEditor = true
          },
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .height(52.dp)
            .testTag("add_alarm_button"),
          shape = RoundedCornerShape(50),
          colors = ButtonDefaults.buttonColors(
            containerColor = OliveGroveActive,
            contentColor = Color.White,
          ),
        ) {
          Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = "Add ${selectedSegment.singularLabel}",
            modifier = Modifier.size(20.dp),
          )
          Spacer(Modifier.size(8.dp))
          Text(
            text = "Add ${selectedSegment.singularLabel}",
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
          )
        }
      }

      // Notification Management Section (Persisted in Room FocusConfigEntity)
      item {
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .testTag("notification_management_card"),
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
              Icon(
                imageVector = Icons.Filled.NotificationsNone,
                contentDescription = "Notification Management",
                tint = palette.accentOnPrimaryCard,
                modifier = Modifier.size(20.dp),
              )
              Text(
                text = "Notification Management",
                style = MaterialTheme.typography.titleMedium,
                color = palette.onPrimaryCard,
              )
            }

            NotificationToggleRow(
              title = "Gentle Escalating Chime",
              subtitle = "Gradual alarm volume ramp-up on trigger",
              checked = focusConfig.gentleEscalatingChime,
              onCheckedChange = { onUpdateNotificationRule(it, null, null) },
            )
            NotificationToggleRow(
              title = "Morning Schedule Summary",
              subtitle = "Prepare morning agenda cue with wake-up alarm",
              checked = focusConfig.aiMorningBriefing,
              onCheckedChange = { onUpdateNotificationRule(null, it, null) },
            )
            NotificationToggleRow(
              title = "Focus-Safe Snooze Limit",
              subtitle = "Restrict repeated snoozes during study windows",
              checked = focusConfig.focusSafeSnoozeLimit,
              onCheckedChange = { onUpdateNotificationRule(null, null, it) },
            )
          }
        }
      }

      // Shared ALFRED Architecture Hub Strip
      item {
        CentralAiBannerCard(
          summaryText = "$armedCount active alarms & reminders synced across reboot schedules",
          statusLabel = "ALARM PORT",
          onOpenAssistantSheet = onOpenAiSheet,
          contextHint = "ALFRED Alarm Management Port",
          modifier = Modifier.widthIn(max = 600.dp),
        )
      }
    }
  }

  if (showNotificationRationale && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    AlertDialog(
      onDismissRequest = { showNotificationRationale = false },
      title = { Text("Allow Alarm Notifications") },
      text = {
        Text("VISION uses notification permission only to ring alarms, show Snooze/Dismiss controls, and deliver your scheduled reminders. No personal messages or contacts are accessed.")
      },
      confirmButton = {
        Button(
          onClick = {
            showNotificationRationale = false
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
          },
          colors = ButtonDefaults.buttonColors(containerColor = OliveGroveActive),
        ) {
          Text("Continue", color = Color.White)
        }
      },
      dismissButton = {
        TextButton(onClick = { showNotificationRationale = false }) {
          Text("Not Now")
        }
      },
    )
  }

  if (showAlarmEditor) {
    AlarmEditorSheet(
      initialAlarm = editingAlarm,
      defaultKind = selectedSegment.kindKey,
      onSave = { alarm ->
        onSaveAlarm(alarm)
        showAlarmEditor = false
      },
      onDelete = { id ->
        onDeleteAlarm(id)
        showAlarmEditor = false
      },
      onDismiss = { showAlarmEditor = false },
    )
  }
}

@Composable
private fun NotificationToggleRow(
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
    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
      Text(title, style = MaterialTheme.typography.titleSmall, color = palette.onPrimaryCard)
      Text(subtitle, style = MaterialTheme.typography.bodySmall, color = palette.onPrimaryCardMuted)
    }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      colors = SwitchDefaults.colors(
        checkedThumbColor = Color.White,
        checkedTrackColor = OliveGroveActive,
        uncheckedThumbColor = palette.onPrimaryCardMuted,
        uncheckedTrackColor = palette.secondaryCard,
      ),
    )
  }
}

@Composable
private fun AlarmEntryCard(
  alarm: AlarmEntity,
  onClick: () -> Unit,
  onToggle: (Boolean) -> Unit,
  onDelete: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalVisionPalette.current
  val (timeStr, periodStr) = DateKeyHelper.formatTime12h(alarm.hour24, alarm.minute)
  val repeatText = DateKeyHelper.repeatSummary(alarm.repeatDaysCsv)

  Surface(
    modifier = modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .testTag("alarm_card_${alarm.id}"),
    shape = RoundedCornerShape(20.dp),
    color = palette.primaryCard,
    contentColor = palette.onPrimaryCard,
    border = BorderStroke(1.dp, palette.cardBorder),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Box(
          modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(
              if (alarm.isWakeUp) OliveGroveActive.copy(alpha = 0.16f)
              else palette.secondaryCard,
            ),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = if (alarm.isWakeUp) Icons.Filled.WbSunny else Icons.Filled.Alarm,
            contentDescription = alarm.label,
            tint = if (alarm.enabled) palette.accentOnPrimaryCard else palette.onPrimaryCardMuted,
            modifier = Modifier.size(22.dp),
          )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
              text = timeStr,
              style = MaterialTheme.typography.headlineMedium,
              fontWeight = FontWeight.Bold,
              color = palette.onPrimaryCard,
            )
            Text(
              text = periodStr,
              style = MaterialTheme.typography.labelLarge,
              color = palette.onPrimaryCardMuted,
              modifier = Modifier.padding(bottom = 3.dp),
            )
            if (alarm.isWakeUp) {
              Surface(
                shape = RoundedCornerShape(50),
                color = OliveGroveActive.copy(alpha = 0.15f),
                modifier = Modifier.padding(start = 6.dp, bottom = 3.dp),
              ) {
                Text(
                  text = "WAKE-UP",
                  style = MaterialTheme.typography.labelSmall,
                  color = palette.accentOnPrimaryCard,
                  modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                )
              }
            }
          }

          Text(
            text = alarm.label,
            style = MaterialTheme.typography.titleSmall,
            color = palette.onPrimaryCard,
          )

          val scheduleSummary = if (alarm.repeatDaysCsv.isBlank() && alarm.scheduledDateKey.isNotBlank()) {
            DateKeyHelper.formatDateKeyDisplay(alarm.scheduledDateKey)
          } else {
            repeatText
          }
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = if (alarm.repeatDaysCsv.isBlank() && alarm.scheduledDateKey.isNotBlank()) {
                Icons.Filled.CalendarMonth
              } else {
                Icons.Filled.Repeat
              },
              contentDescription = "Schedule date or repeat",
              tint = palette.onPrimaryCardMuted,
              modifier = Modifier.size(13.dp),
            )
            Text(
              text = scheduleSummary,
              style = MaterialTheme.typography.bodySmall,
              color = palette.onPrimaryCardMuted,
            )
          }
        }
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        IconButton(
          onClick = onDelete,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("delete_alarm_${alarm.id}"),
        ) {
          Icon(
            imageVector = Icons.Filled.DeleteOutline,
            contentDescription = "Delete ${alarm.label}",
            tint = VisionDangerRed,
            modifier = Modifier.size(20.dp),
          )
        }
        Switch(
          checked = alarm.enabled,
          onCheckedChange = onToggle,
          modifier = Modifier.testTag("alarm_switch_${alarm.id}"),
          colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = OliveGroveActive,
            uncheckedThumbColor = palette.onPrimaryCardMuted,
            uncheckedTrackColor = palette.secondaryCard,
          ),
        )
      }
    }
  }
}

@Composable
private fun AnalogClockFace(
  hour: Int,
  minute: Int,
  second: Int,
  faceTint: Color,
  ringTint: Color,
  secondHandTint: Color,
  modifier: Modifier = Modifier,
) {
  Canvas(modifier = modifier) {
    val radius = size.minDimension / 2f
    val center = Offset(size.width / 2f, size.height / 2f)

    drawCircle(color = faceTint.copy(alpha = 0.08f), radius = radius, center = center)
    drawCircle(
      color = ringTint.copy(alpha = 0.65f),
      radius = radius - 2f,
      center = center,
      style = Stroke(width = 3f),
    )

    for (i in 0 until 12) {
      val angleRad = Math.toRadians((i * 30 - 90).toDouble())
      val start = Offset(
        x = center.x + ((radius - 14f) * cos(angleRad)).toFloat(),
        y = center.y + ((radius - 14f) * sin(angleRad)).toFloat(),
      )
      val end = Offset(
        x = center.x + ((radius - 6f) * cos(angleRad)).toFloat(),
        y = center.y + ((radius - 6f) * sin(angleRad)).toFloat(),
      )
      drawLine(
        color = faceTint.copy(alpha = if (i % 3 == 0) 0.92f else 0.45f),
        start = start,
        end = end,
        strokeWidth = if (i % 3 == 0) 3.5f else 2f,
        cap = StrokeCap.Round,
      )
    }

    val hourAngle = Math.toRadians((((hour % 12) + minute / 60f) * 30f - 90f).toDouble())
    drawLine(
      color = faceTint,
      start = center,
      end = Offset(
        x = center.x + ((radius * 0.48f) * cos(hourAngle)).toFloat(),
        y = center.y + ((radius * 0.48f) * sin(hourAngle)).toFloat(),
      ),
      strokeWidth = 5f,
      cap = StrokeCap.Round,
    )

    val minAngle = Math.toRadians((minute * 6f - 90f).toDouble())
    drawLine(
      color = faceTint.copy(alpha = 0.78f),
      start = center,
      end = Offset(
        x = center.x + ((radius * 0.68f) * cos(minAngle)).toFloat(),
        y = center.y + ((radius * 0.68f) * sin(minAngle)).toFloat(),
      ),
      strokeWidth = 3.5f,
      cap = StrokeCap.Round,
    )

    val secAngle = Math.toRadians((second * 6f - 90f).toDouble())
    drawLine(
      color = secondHandTint,
      start = center,
      end = Offset(
        x = center.x + ((radius * 0.74f) * cos(secAngle)).toFloat(),
        y = center.y + ((radius * 0.74f) * sin(secAngle)).toFloat(),
      ),
      strokeWidth = 2f,
      cap = StrokeCap.Round,
    )

    drawCircle(color = secondHandTint, radius = 5f, center = center)
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmEditorSheet(
  initialAlarm: AlarmEntity?,
  defaultKind: String,
  onSave: (AlarmEntity) -> Unit,
  onDelete: (Long) -> Unit,
  onDismiss: () -> Unit,
) {
  val palette = LocalVisionPalette.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val timePickerState = rememberTimePickerState(
    initialHour = initialAlarm?.hour24 ?: 7,
    initialMinute = initialAlarm?.minute ?: 0,
    is24Hour = false,
  )
  var label by remember(initialAlarm, defaultKind) { mutableStateOf(initialAlarm?.label.orEmpty()) }
  var labelError by remember(initialAlarm, defaultKind) { mutableStateOf<String?>(null) }
  var kind by remember(initialAlarm, defaultKind) { mutableStateOf(initialAlarm?.kind ?: defaultKind) }
  var isWakeUp by remember(initialAlarm, defaultKind) {
    mutableStateOf(initialAlarm?.isWakeUp ?: (defaultKind == "ALARM"))
  }
  var soundEnabled by remember(initialAlarm) { mutableStateOf(initialAlarm?.soundEnabled ?: true) }
  var vibrateEnabled by remember(initialAlarm) { mutableStateOf(initialAlarm?.vibrateEnabled ?: true) }
  var selectedDays by remember(initialAlarm, defaultKind) {
    mutableStateOf(
      if (initialAlarm != null) {
        initialAlarm.repeatDaysCsv
          .split(",")
          .mapNotNull { it.trim().toIntOrNull() }
          .toSet()
      } else if (defaultKind == "ALARM") {
        setOf(1, 2, 3, 4, 5)
      } else {
        emptySet()
      },
    )
  }
  var scheduledDateKey by remember(initialAlarm, defaultKind) {
    mutableStateOf(
      initialAlarm?.scheduledDateKey?.takeIf { it.isNotBlank() } ?: DateKeyHelper.todayKey(),
    )
  }
  var dateValidationError by remember { mutableStateOf<String?>(null) }
  var showDatePicker by remember { mutableStateOf(false) }

  val kindDisplayName = when (kind) {
    "REMINDER" -> "Reminder"
    "SCHEDULE" -> "Schedule"
    else -> "Alarm"
  }

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
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp)
        .padding(bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = if (initialAlarm == null) "Create $kindDisplayName" else "Edit $kindDisplayName",
        style = MaterialTheme.typography.titleLarge,
        color = palette.onPrimaryCard,
      )

      Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        TimePicker(
          state = timePickerState,
          colors = TimePickerDefaults.colors(
            clockDialColor = palette.secondaryCard,
            clockDialSelectedContentColor = Color.White,
            clockDialUnselectedContentColor = palette.onPrimaryCard,
            selectorColor = OliveGroveActive,
            periodSelectorBorderColor = palette.cardBorder,
            periodSelectorSelectedContainerColor = OliveGroveActive,
            periodSelectorUnselectedContainerColor = palette.secondaryCard,
            periodSelectorSelectedContentColor = Color.White,
            periodSelectorUnselectedContentColor = palette.onPrimaryCard,
            timeSelectorSelectedContainerColor = OliveGroveActive.copy(alpha = 0.22f),
            timeSelectorUnselectedContainerColor = palette.secondaryCard,
            timeSelectorSelectedContentColor = palette.onPrimaryCard,
            timeSelectorUnselectedContentColor = palette.onPrimaryCard,
          ),
        )
      }

      OutlinedTextField(
        value = label,
        onValueChange = {
          label = it
          if (labelError != null && it.isNotBlank()) labelError = null
        },
        label = { Text("$kindDisplayName Title") },
        placeholder = {
          Text(
            when (kind) {
              "REMINDER" -> "e.g., Drink Water or Revise Flashcards"
              "SCHEDULE" -> "e.g., Focused Study Block"
              else -> "e.g., Morning Wake-Up"
            },
          )
        },
        isError = labelError != null,
        supportingText = labelError?.let { err -> { Text(err, color = VisionDangerRed) } },
        colors = visionPrimaryCardFieldColors(palette),
        singleLine = true,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("alarm_label_input"),
      )

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
          "ALARM" to "Alarm",
          "REMINDER" to "Reminder",
          "SCHEDULE" to "Schedule",
        ).forEach { (k, display) ->
          val active = kind == k
          Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            color = if (active) OliveGroveActive else palette.secondaryCard,
            border = BorderStroke(1.dp, palette.cardBorder),
            onClick = {
              kind = k
              dateValidationError = null
              if (initialAlarm == null) {
                isWakeUp = (k == "ALARM")
                if (k != "ALARM" && selectedDays == setOf(1, 2, 3, 4, 5)) {
                  selectedDays = emptySet()
                }
              }
            },
          ) {
            Box(modifier = Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
              Text(display, style = MaterialTheme.typography.labelSmall, color = if (active) Color.White else palette.onPrimaryCard)
            }
          }
        }
      }

      // Specific Date vs Weekly Repeat Toggle
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        val isOneTimeDate = selectedDays.isEmpty()
        Surface(
          modifier = Modifier
            .weight(1f)
            .testTag("alarm_mode_specific_date"),
          shape = RoundedCornerShape(50),
          color = if (isOneTimeDate) OliveGroveActive else palette.secondaryCard,
          border = BorderStroke(1.dp, palette.cardBorder),
          onClick = {
            selectedDays = emptySet()
            dateValidationError = null
          },
        ) {
          Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text(
              text = "Specific Date",
              style = MaterialTheme.typography.labelMedium,
              color = if (isOneTimeDate) Color.White else palette.onPrimaryCard,
            )
          }
        }
        Surface(
          modifier = Modifier
            .weight(1f)
            .testTag("alarm_mode_repeat_weekly"),
          shape = RoundedCornerShape(50),
          color = if (!isOneTimeDate) OliveGroveActive else palette.secondaryCard,
          border = BorderStroke(1.dp, palette.cardBorder),
          onClick = {
            if (selectedDays.isEmpty()) {
              selectedDays = setOf(1, 2, 3, 4, 5)
            }
            dateValidationError = null
          },
        ) {
          Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text(
              text = "Repeat Days",
              style = MaterialTheme.typography.labelMedium,
              color = if (!isOneTimeDate) Color.White else palette.onPrimaryCard,
            )
          }
        }
      }

      if (selectedDays.isEmpty()) {
        Text("Scheduled Date", style = MaterialTheme.typography.titleSmall, color = palette.onPrimaryCard)
        val today = DateKeyHelper.todayKey()
        val tomorrow = DateKeyHelper.tomorrowKey()
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          listOf(today to "Today", tomorrow to "Tomorrow").forEach { (key, labelText) ->
            val picked = scheduledDateKey == key
            Surface(
              modifier = Modifier.weight(1f),
              shape = RoundedCornerShape(50),
              color = if (picked) OliveGroveActive else palette.secondaryCard,
              border = BorderStroke(1.dp, palette.cardBorder),
              onClick = {
                scheduledDateKey = key
                dateValidationError = null
              },
            ) {
              Box(modifier = Modifier.padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                Text(
                  text = labelText,
                  style = MaterialTheme.typography.labelMedium,
                  color = if (picked) Color.White else palette.onPrimaryCard,
                )
              }
            }
          }
          Surface(
            modifier = Modifier
              .weight(1.25f)
              .testTag("alarm_pick_date_button"),
            shape = RoundedCornerShape(50),
            color = if (scheduledDateKey != today && scheduledDateKey != tomorrow) OliveGroveActive else palette.secondaryCard,
            border = BorderStroke(1.dp, palette.cardBorder),
            onClick = { showDatePicker = true },
          ) {
            Row(
              modifier = Modifier.padding(vertical = 9.dp, horizontal = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.Center,
            ) {
              Icon(
                imageVector = Icons.Filled.CalendarMonth,
                contentDescription = "Pick reminder date",
                tint = if (scheduledDateKey != today && scheduledDateKey != tomorrow) Color.White else palette.accentOnPrimaryCard,
                modifier = Modifier.size(15.dp),
              )
              Spacer(Modifier.size(4.dp))
              Text(
                text = if (scheduledDateKey != today && scheduledDateKey != tomorrow) scheduledDateKey else "Pick Date",
                style = MaterialTheme.typography.labelMedium,
                color = if (scheduledDateKey != today && scheduledDateKey != tomorrow) Color.White else palette.onPrimaryCard,
              )
            }
          }
        }
        Text(
          text = "Scheduled for: ${DateKeyHelper.formatDateKeyDisplay(scheduledDateKey)} at ${DateKeyHelper.formatFullTime12h(timePickerState.hour, timePickerState.minute)}",
          style = MaterialTheme.typography.bodySmall,
          color = palette.accentOnPrimaryCard,
        )
      } else {
        Text("Repeat Days", style = MaterialTheme.typography.titleSmall, color = palette.onPrimaryCard)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          (1..7).forEach { isoDay ->
            val picked = isoDay in selectedDays
            Surface(
              modifier = Modifier.size(38.dp),
              shape = CircleShape,
              color = if (picked) OliveGroveActive else palette.secondaryCard,
              border = BorderStroke(1.dp, palette.cardBorder),
              onClick = {
                selectedDays = if (picked) selectedDays - isoDay else selectedDays + isoDay
              },
            ) {
              Box(contentAlignment = Alignment.Center) {
                Text(
                  text = DateKeyHelper.dayShortLabel(isoDay).take(2),
                  style = MaterialTheme.typography.labelSmall,
                  color = if (picked) Color.White else palette.onPrimaryCard,
                )
              }
            }
          }
        }
      }

      if (dateValidationError != null) {
        Text(
          text = dateValidationError!!,
          style = MaterialTheme.typography.bodySmall,
          color = VisionDangerRed,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(
            checked = soundEnabled,
            onCheckedChange = { soundEnabled = it },
            colors = visionPrimaryCardCheckboxColors(palette),
          )
          Text("Sound", style = MaterialTheme.typography.bodySmall, color = palette.onPrimaryCard)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(
            checked = vibrateEnabled,
            onCheckedChange = { vibrateEnabled = it },
            colors = visionPrimaryCardCheckboxColors(palette),
          )
          Text("Vibrate", style = MaterialTheme.typography.bodySmall, color = palette.onPrimaryCard)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(
            checked = isWakeUp,
            onCheckedChange = { isWakeUp = it },
            colors = visionPrimaryCardCheckboxColors(palette),
          )
          Text("Wake-Up", style = MaterialTheme.typography.bodySmall, color = palette.onPrimaryCard)
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (initialAlarm != null) {
          OutlinedButton(
            onClick = { onDelete(initialAlarm.id) },
            border = BorderStroke(1.dp, VisionDangerRed.copy(alpha = 0.6f)),
            modifier = Modifier.height(48.dp),
          ) {
            Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete $kindDisplayName", tint = VisionDangerRed)
            Spacer(Modifier.size(4.dp))
            Text("Delete", color = VisionDangerRed)
          }
        }
        Button(
          onClick = {
            val cleanLabel = label.trim()
            if (cleanLabel.isEmpty()) {
              labelError = "Please enter a title for your ${kindDisplayName.lowercase()}."
              return@Button
            }
            val csv = selectedDays.sorted().joinToString(",")
            val finalDateKey = if (csv.isEmpty()) scheduledDateKey else ""
            if (csv.isEmpty() && finalDateKey.isNotBlank()) {
              val targetMillis = DateKeyHelper.combineDateKeyAndTime(
                finalDateKey,
                timePickerState.hour,
                timePickerState.minute,
              )
              if (targetMillis <= System.currentTimeMillis()) {
                dateValidationError = "Selected date and time is in the past. Pick a future time or date."
                return@Button
              }
            }
            onSave(
              (initialAlarm ?: AlarmEntity(
                hour24 = timePickerState.hour,
                minute = timePickerState.minute,
                label = cleanLabel,
                kind = kind,
                isWakeUp = isWakeUp,
                repeatDaysCsv = csv,
                scheduledDateKey = finalDateKey,
                enabled = true,
                soundEnabled = soundEnabled,
                vibrateEnabled = vibrateEnabled,
              )).copy(
                hour24 = timePickerState.hour,
                minute = timePickerState.minute,
                label = cleanLabel,
                kind = kind,
                isWakeUp = isWakeUp,
                repeatDaysCsv = csv,
                scheduledDateKey = finalDateKey,
                enabled = true,
                soundEnabled = soundEnabled,
                vibrateEnabled = vibrateEnabled,
              ),
            )
          },
          modifier = Modifier
            .weight(1f)
            .height(50.dp)
            .testTag("save_alarm_button"),
          shape = RoundedCornerShape(50),
          colors = ButtonDefaults.buttonColors(containerColor = OliveGroveActive),
        ) {
          Text("Save & Arm", color = Color.White, style = MaterialTheme.typography.titleSmall)
        }
      }
    }
  }

  if (showDatePicker) {
    val initialUtcMillis = remember(scheduledDateKey) {
      val localCal = DateKeyHelper.parseDateKeyToCalendar(scheduledDateKey) ?: Calendar.getInstance()
      Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        set(
          localCal.get(Calendar.YEAR),
          localCal.get(Calendar.MONTH),
          localCal.get(Calendar.DAY_OF_MONTH),
          0,
          0,
          0,
        )
        set(Calendar.MILLISECOND, 0)
      }.timeInMillis
    }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialUtcMillis)
    DatePickerDialog(
      onDismissRequest = { showDatePicker = false },
      confirmButton = {
        TextButton(
          onClick = {
            datePickerState.selectedDateMillis?.let { utcMs ->
              val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMs }
              val localCal = Calendar.getInstance().apply {
                set(
                  utcCal.get(Calendar.YEAR),
                  utcCal.get(Calendar.MONTH),
                  utcCal.get(Calendar.DAY_OF_MONTH),
                  12,
                  0,
                  0,
                )
              }
              scheduledDateKey = DateKeyHelper.todayKey(localCal.timeInMillis)
              dateValidationError = null
            }
            showDatePicker = false
          },
        ) { Text("Select") }
      },
      dismissButton = {
        TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
      },
    ) {
      DatePicker(state = datePickerState)
    }
  }
}
