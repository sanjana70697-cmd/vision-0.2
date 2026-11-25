package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.ActivitySessionEntity
import com.example.data.local.DateKeyHelper
import com.example.ui.theme.LocalVisionPalette
import com.example.ui.theme.VisionDangerRed
import com.example.ui.theme.visionPrimaryCardFieldColors
import java.util.Calendar
import java.util.TimeZone

val SKILL_SUGGESTION_PRESETS = listOf(
  "Web Development",
  "CPPM",
  "Algorithms",
  "Android / Compose",
  "System Design",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivitySessionEditorSheet(
  initialSession: ActivitySessionEntity?,
  defaultType: String = "SKILL",
  allowCategorySwitch: Boolean = true,
  suggestedStartTimeMillis: Long? = null,
  onSave: (ActivitySessionEntity) -> Unit,
  onDelete: ((Long) -> Unit)? = null,
  onDismiss: () -> Unit,
) {
  val palette = LocalVisionPalette.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  val initialCalendar = remember(initialSession, suggestedStartTimeMillis) {
    Calendar.getInstance().apply {
      when {
        initialSession != null && initialSession.startTimeMillis > 0L -> {
          timeInMillis = initialSession.startTimeMillis
        }
        suggestedStartTimeMillis != null && suggestedStartTimeMillis > 0L -> {
          timeInMillis = suggestedStartTimeMillis
        }
        else -> {
          add(Calendar.MINUTE, -30)
        }
      }
    }
  }

  var activityType by remember(initialSession, defaultType) {
    mutableStateOf(initialSession?.activityType ?: defaultType)
  }
  var dateKey by remember(initialSession) {
    mutableStateOf(initialSession?.dateKey?.takeIf { it.isNotBlank() } ?: DateKeyHelper.todayKey())
  }
  var titleOrSkill by remember(initialSession, defaultType) {
    mutableStateOf(
      initialSession?.titleOrSkillName ?: when (defaultType) {
        "YOGA" -> "Morning Yoga & Exercise"
        "FOCUS" -> "Focus Session"
        else -> ""
      },
    )
  }
  var startHour by remember(initialSession, suggestedStartTimeMillis) { mutableIntStateOf(initialCalendar.get(Calendar.HOUR_OF_DAY)) }
  var startMinute by remember(initialSession, suggestedStartTimeMillis) { mutableIntStateOf(initialCalendar.get(Calendar.MINUTE)) }
  var durationMinutes by remember(initialSession) {
    mutableIntStateOf((initialSession?.durationMinutes ?: 30).coerceIn(1, 720))
  }
  var durationText by remember(initialSession) {
    mutableStateOf(((initialSession?.durationMinutes ?: 30).coerceIn(1, 720)).toString())
  }
  var notes by remember(initialSession) { mutableStateOf(initialSession?.notes.orEmpty()) }
  var validationError by remember { mutableStateOf<String?>(null) }
  var showDatePicker by remember { mutableStateOf(false) }
  var showDeleteConfirm by remember { mutableStateOf(false) }

  val startMillisPreview = remember(dateKey, startHour, startMinute) {
    DateKeyHelper.combineDateKeyAndTime(dateKey, startHour, startMinute)
  }
  val effectiveDuration = (durationText.toIntOrNull() ?: durationMinutes).coerceIn(1, 720)
  val endMillisPreview = startMillisPreview + (effectiveDuration * 60_000L)

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
        .padding(bottom = 28.dp)
        .testTag("activity_session_editor_sheet"),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = if (initialSession == null) "Log Activity Session" else "Edit Activity Session",
        style = MaterialTheme.typography.titleLarge,
        color = palette.onPrimaryCard,
      )

      if (allowCategorySwitch) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          listOf(
            "FOCUS" to "Focus Time",
            "YOGA" to "Yoga",
            "SKILL" to "Skill Dev",
          ).forEach { (typeKey, label) ->
            val selected = activityType == typeKey
            Surface(
              modifier = Modifier
                .weight(1f)
                .testTag("activity_type_${typeKey.lowercase()}"),
              shape = RoundedCornerShape(12.dp),
              color = if (selected) palette.accent else palette.secondaryCard,
              border = BorderStroke(1.dp, if (selected) palette.accent else palette.cardBorder),
              onClick = {
                val oldDefault = when (activityType) {
                  "YOGA" -> "Morning Yoga & Exercise"
                  "FOCUS" -> "Focus Session"
                  else -> ""
                }
                activityType = typeKey
                if (initialSession == null && (titleOrSkill.isBlank() || titleOrSkill == oldDefault)) {
                  titleOrSkill = when (typeKey) {
                    "YOGA" -> "Morning Yoga & Exercise"
                    "FOCUS" -> "Focus Session"
                    else -> ""
                  }
                }
                validationError = null
              },
            ) {
              Box(
                modifier = Modifier.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = label,
                  style = MaterialTheme.typography.labelMedium,
                  color = if (selected) Color.White else palette.onPrimaryCard,
                )
              }
            }
          }
        }
      }

      // Date Selector
      Text("Date", style = MaterialTheme.typography.titleSmall, color = palette.onPrimaryCard)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        val today = DateKeyHelper.todayKey()
        val yesterday = DateKeyHelper.offsetDateKey(-1)
        listOf(
          today to "Today",
          yesterday to "Yesterday",
        ).forEach { (key, label) ->
          val picked = dateKey == key
          Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(50),
            color = if (picked) palette.accent else palette.secondaryCard,
            border = BorderStroke(1.dp, palette.cardBorder),
            onClick = { dateKey = key },
          ) {
            Box(modifier = Modifier.padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
              Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (picked) Color.White else palette.onPrimaryCard,
              )
            }
          }
        }
        Surface(
          modifier = Modifier.weight(1.2f),
          shape = RoundedCornerShape(50),
          color = if (dateKey != today && dateKey != yesterday) palette.accent else palette.secondaryCard,
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
              contentDescription = "Pick activity date",
              tint = if (dateKey != today && dateKey != yesterday) Color.White else palette.accentOnPrimaryCard,
              modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.size(4.dp))
            Text(
              text = if (dateKey != today && dateKey != yesterday) dateKey else "Pick Date",
              style = MaterialTheme.typography.labelMedium,
              color = if (dateKey != today && dateKey != yesterday) Color.White else palette.onPrimaryCard,
            )
          }
        }
      }

      if (activityType == "SKILL") {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          SKILL_SUGGESTION_PRESETS.forEach { preset ->
            val picked = titleOrSkill.equals(preset, ignoreCase = true)
            Surface(
              shape = RoundedCornerShape(50),
              color = if (picked) palette.accent else palette.secondaryCard,
              border = BorderStroke(1.dp, palette.cardBorder),
              onClick = {
                titleOrSkill = preset
                validationError = null
              },
            ) {
              Text(
                text = preset,
                style = MaterialTheme.typography.labelSmall,
                color = if (picked) Color.White else palette.onPrimaryCard,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
              )
            }
          }
        }
      }

      OutlinedTextField(
        value = titleOrSkill,
        onValueChange = {
          titleOrSkill = it
          if (validationError != null && it.isNotBlank()) validationError = null
        },
        label = {
          Text(
            if (activityType == "SKILL") "Skill Name (required)" else "Activity Title",
          )
        },
        placeholder = {
          Text(
            when (activityType) {
              "SKILL" -> "e.g., Web Development, CPPM, Algorithms"
              "YOGA" -> "e.g., Morning Yoga & Breathing"
              else -> "e.g., Deep Study Block"
            },
          )
        },
        singleLine = true,
        isError = validationError != null,
        colors = visionPrimaryCardFieldColors(palette),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("activity_title_input"),
      )

      // Start Time & Duration Window
      Surface(
        shape = RoundedCornerShape(16.dp),
        color = palette.secondaryCard,
        border = BorderStroke(1.dp, palette.cardBorder),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column {
              Text("Start Time", style = MaterialTheme.typography.labelSmall, color = palette.onPrimaryCardMuted)
              Text(
                text = DateKeyHelper.formatFullTime12h(startHour, startMinute),
                style = MaterialTheme.typography.titleMedium,
                color = palette.onPrimaryCard,
              )
            }
            Column(horizontalAlignment = Alignment.End) {
              Text("Calculated Window", style = MaterialTheme.typography.labelSmall, color = palette.onPrimaryCardMuted)
              Text(
                text = "${DateKeyHelper.formatMillisTime12h(startMillisPreview)} – ${DateKeyHelper.formatMillisTime12h(endMillisPreview)}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = palette.accentOnPrimaryCard,
              )
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            OutlinedButton(
              onClick = { startHour = (startHour + 23) % 24 },
              modifier = Modifier.weight(1f),
              border = BorderStroke(1.dp, palette.cardBorder),
            ) { Text("-1h", color = palette.onPrimaryCard) }
            OutlinedButton(
              onClick = { startHour = (startHour + 1) % 24 },
              modifier = Modifier.weight(1f),
              border = BorderStroke(1.dp, palette.cardBorder),
            ) { Text("+1h", color = palette.onPrimaryCard) }
            OutlinedButton(
              onClick = {
                val total = (startHour * 60 + startMinute - 15 + 1440) % 1440
                startHour = total / 60
                startMinute = total % 60
              },
              modifier = Modifier.weight(1f),
              border = BorderStroke(1.dp, palette.cardBorder),
            ) { Text("-15m", color = palette.onPrimaryCard) }
            OutlinedButton(
              onClick = {
                val total = (startHour * 60 + startMinute + 15) % 1440
                startHour = total / 60
                startMinute = total % 60
              },
              modifier = Modifier.weight(1f),
              border = BorderStroke(1.dp, palette.cardBorder),
            ) { Text("+15m", color = palette.onPrimaryCard) }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "Duration: ${DateKeyHelper.formatMinutesSummary(effectiveDuration)}",
              style = MaterialTheme.typography.titleSmall,
              color = palette.onPrimaryCard,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              OutlinedButton(
                onClick = {
                  val next = (durationMinutes - 5).coerceAtLeast(1)
                  durationMinutes = next
                  durationText = next.toString()
                  validationError = null
                },
                border = BorderStroke(1.dp, palette.cardBorder),
              ) { Text("-5m", color = palette.onPrimaryCard) }
              OutlinedButton(
                onClick = {
                  val next = (durationMinutes + 5).coerceAtMost(720)
                  durationMinutes = next
                  durationText = next.toString()
                  validationError = null
                },
                border = BorderStroke(1.dp, palette.cardBorder),
              ) { Text("+5m", color = palette.onPrimaryCard) }
            }
          }

          OutlinedTextField(
            value = durationText,
            onValueChange = { raw ->
              val digitsOnly = raw.filter { it.isDigit() }.take(3)
              durationText = digitsOnly
              val parsed = digitsOnly.toIntOrNull()
              if (parsed != null && parsed in 1..720) {
                durationMinutes = parsed
                validationError = null
              }
            },
            label = { Text("Duration (minutes, 1–720)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = visionPrimaryCardFieldColors(palette),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("activity_duration_input"),
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            listOf(15, 30, 45, 60).forEach { preset ->
              val picked = durationText.toIntOrNull() == preset
              Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(50),
                color = if (picked) palette.accent else palette.primaryCard,
                border = BorderStroke(1.dp, palette.cardBorder),
                onClick = {
                  durationMinutes = preset
                  durationText = preset.toString()
                  validationError = null
                },
              ) {
                Box(modifier = Modifier.padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
                  Text(
                    text = "${preset}m",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (picked) Color.White else palette.onPrimaryCard,
                  )
                }
              }
            }
          }
        }
      }

      OutlinedTextField(
        value = notes,
        onValueChange = { notes = it },
        label = { Text("Session Notes (optional)") },
        placeholder = { Text("What did you practice or focus on?") },
        colors = visionPrimaryCardFieldColors(palette),
        minLines = 2,
        maxLines = 3,
        modifier = Modifier.fillMaxWidth(),
      )

      if (validationError != null) {
        Text(
          text = validationError!!,
          style = MaterialTheme.typography.bodySmall,
          color = VisionDangerRed,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        if (initialSession != null && onDelete != null) {
          OutlinedButton(
            onClick = { showDeleteConfirm = true },
            border = BorderStroke(1.dp, VisionDangerRed.copy(alpha = 0.65f)),
            modifier = Modifier.height(50.dp),
          ) {
            Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete activity", tint = VisionDangerRed)
            Spacer(Modifier.size(4.dp))
            Text("Delete", color = VisionDangerRed)
          }
        }

        Button(
          onClick = {
            val cleanTitle = titleOrSkill.trim()
            if (cleanTitle.isEmpty()) {
              validationError = if (activityType == "SKILL") "Please enter a skill name." else "Please enter an activity title."
              return@Button
            }
            val parsedDuration = durationText.trim().toIntOrNull()
            if (parsedDuration == null || parsedDuration !in 1..720) {
              validationError = "Duration must be between 1 and 720 minutes."
              return@Button
            }
            durationMinutes = parsedDuration
            val startMs = DateKeyHelper.combineDateKeyAndTime(dateKey, startHour, startMinute)
            val endMs = startMs + parsedDuration * 60_000L
            onSave(
              (initialSession ?: ActivitySessionEntity(
                activityType = activityType,
                dateKey = dateKey,
                titleOrSkillName = cleanTitle,
                notes = notes.trim(),
                startTimeMillis = startMs,
                endTimeMillis = endMs,
                durationMinutes = parsedDuration,
                source = "MANUAL",
              )).copy(
                activityType = activityType,
                dateKey = dateKey,
                titleOrSkillName = cleanTitle,
                notes = notes.trim(),
                startTimeMillis = startMs,
                endTimeMillis = endMs,
                durationMinutes = parsedDuration,
              ),
            )
          },
          modifier = Modifier
            .weight(1f)
            .height(50.dp)
            .testTag("save_activity_button"),
          shape = RoundedCornerShape(50),
          colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
        ) {
          Text(
            text = if (initialSession == null) "Save Activity" else "Save Changes",
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
          )
        }
      }
    }
  }

  if (showDatePicker) {
    val initialUtcMillis = remember(dateKey) {
      val localCal = DateKeyHelper.parseDateKeyToCalendar(dateKey) ?: Calendar.getInstance()
      val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        set(
          localCal.get(Calendar.YEAR),
          localCal.get(Calendar.MONTH),
          localCal.get(Calendar.DAY_OF_MONTH),
          0,
          0,
          0,
        )
        set(Calendar.MILLISECOND, 0)
      }
      utcCal.timeInMillis
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
              dateKey = DateKeyHelper.todayKey(localCal.timeInMillis)
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

  if (showDeleteConfirm && initialSession != null && onDelete != null) {
    AlertDialog(
      onDismissRequest = { showDeleteConfirm = false },
      title = { Text("Delete Activity Record?") },
      text = {
        Text("Remove \"${initialSession.titleOrSkillName}\" (${initialSession.durationMinutes}m) from ${DateKeyHelper.formatDateKeyDisplay(initialSession.dateKey)}?")
      },
      confirmButton = {
        Button(
          onClick = {
            showDeleteConfirm = false
            onDelete(initialSession.id)
          },
          colors = ButtonDefaults.buttonColors(containerColor = VisionDangerRed),
        ) { Text("Delete", color = Color.White) }
      },
      dismissButton = {
        TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
      },
    )
  }
}
