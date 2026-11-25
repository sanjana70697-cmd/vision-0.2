package com.example.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.ActivitySessionEntity
import com.example.data.local.DailyProductivitySnapshotEntity
import com.example.data.local.DateKeyHelper
import com.example.data.local.FocusConfigEntity
import com.example.data.local.ProductivitySummary
import com.example.data.local.TaskEntity
import com.example.ui.components.ActivitySessionEditorSheet
import com.example.ui.components.CentralAiBannerCard
import com.example.ui.components.VisionTopBar
import com.example.ui.theme.DarkChocolateAccent
import com.example.ui.theme.DarkChocolateDeep
import com.example.ui.theme.DarkChocolateGold
import com.example.ui.theme.LocalVisionPalette
import com.example.ui.theme.PlayfairDisplayFamily
import com.example.ui.theme.VisionDangerRed
import com.example.ui.theme.visionPrimaryCardCheckboxColors
import com.example.ui.theme.visionPrimaryCardFieldColors

private enum class ReportTabPeriod(val label: String) {
  Daily("Daily"),
  Weekly("Weekly"),
}

private data class RealChartBar(
  val label: String,
  val valuePercent: Int,
  val detail: String,
)

@Composable
fun ReportScreen(
  todayProductivity: ProductivitySummary,
  weeklySnapshots: List<Pair<String, DailyProductivitySnapshotEntity?>>,
  todayActivitySessions: List<ActivitySessionEntity>,
  recentActivitySessions: List<ActivitySessionEntity> = emptyList(),
  todayTasks: List<TaskEntity>,
  pendingTasks: List<TaskEntity>,
  tomorrowTasks: List<TaskEntity>,
  focusConfig: FocusConfigEntity,
  onSaveActivitySession: (ActivitySessionEntity) -> Unit,
  onDeleteActivitySession: (Long) -> Unit,
  onMovePendingToTomorrow: (TaskEntity) -> Unit,
  onCarryForwardTasks: (List<TaskEntity>) -> Unit,
  onSaveTomorrowTask: (TaskEntity) -> Unit,
  onToggleTask: (TaskEntity) -> Unit,
  onDeleteTask: (Long) -> Unit,
  onSaveNextDayNote: (String) -> Unit,
  onOpenAiSheet: () -> Unit,
  onOpenMenu: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalVisionPalette.current
  var selectedPeriod by remember { mutableStateOf(ReportTabPeriod.Daily) }
  var showEditPlanSheet by remember { mutableStateOf(false) }
  var showTomorrowTaskSheet by remember { mutableStateOf(false) }
  var editingTomorrowTask by remember { mutableStateOf<TaskEntity?>(null) }
  var showActivityEditor by remember { mutableStateOf(false) }
  var editingActivitySession by remember { mutableStateOf<ActivitySessionEntity?>(null) }
  var editingActivityDefaultType by remember { mutableStateOf("SKILL") }
  var editingAllowCategorySwitch by remember { mutableStateOf(true) }
  var suggestedActivityStartMillis by remember { mutableStateOf<Long?>(null) }
  var choosingCategoryForEdit by remember { mutableStateOf<Pair<String, String>?>(null) }
  var deletingActivitySession by remember { mutableStateOf<ActivitySessionEntity?>(null) }

  val todayKey = DateKeyHelper.todayKey()
  val tomorrowKey = DateKeyHelper.tomorrowKey()
  val todayTasksForDate = remember(todayTasks, todayKey) {
    todayTasks.filter { it.dateKey == todayKey }
  }
  val actualTotalToday = todayTasksForDate.size
  val actualCompletedToday = todayTasksForDate.count { it.isCompleted }
  val actualPendingToday = (actualTotalToday - actualCompletedToday).coerceAtLeast(0)
  val actualProductivityPercentToday = if (actualTotalToday > 0) {
    ((actualCompletedToday.toFloat() / actualTotalToday.toFloat()) * 100f).toInt()
  } else 0

  val todaySessionsOnly = remember(todayActivitySessions, todayKey) {
    todayActivitySessions.filter { it.dateKey == todayKey }
  }
  val todayFocusSessions = remember(todaySessionsOnly) {
    todaySessionsOnly.filter { it.activityType == "FOCUS" }
  }
  val todayYogaSessions = remember(todaySessionsOnly) {
    todaySessionsOnly.filter { it.activityType == "YOGA" }
  }
  val todaySkillSessions = remember(todaySessionsOnly) {
    todaySessionsOnly.filter { it.activityType == "SKILL" }
  }
  val todayFocusMinutes = todayFocusSessions.sumOf { it.durationMinutes }
  val todayYogaMinutes = todayYogaSessions.sumOf { it.durationMinutes }
  val todaySkillMinutes = todaySkillSessions.sumOf { it.durationMinutes }

  val openEditForCategory: (String, String) -> Unit = { typeKey, categoryLabel ->
    val matching = todaySessionsOnly.filter { it.activityType == typeKey }
    when {
      matching.isEmpty() -> {
        editingActivitySession = null
        editingActivityDefaultType = typeKey
        editingAllowCategorySwitch = false
        suggestedActivityStartMillis = null
        showActivityEditor = true
      }
      matching.size == 1 -> {
        editingActivitySession = matching.first()
        editingActivityDefaultType = typeKey
        editingAllowCategorySwitch = false
        suggestedActivityStartMillis = null
        showActivityEditor = true
      }
      else -> {
        choosingCategoryForEdit = typeKey to categoryLabel
      }
    }
  }

  val pendingCarryForwardTasks = remember(pendingTasks, tomorrowKey) {
    pendingTasks.filter { !it.isCompleted && it.dateKey < tomorrowKey }
  }
  var selectedTaskIds by remember(pendingCarryForwardTasks) {
    mutableStateOf(
      pendingCarryForwardTasks.map { it.id }.toSet(),
    )
  }

  // Real calculations strictly from stored Room data
  val weeklyTotalTasks = weeklySnapshots.sumOf { it.second?.totalTasks ?: 0 }
  val weeklyCompletedTasks = weeklySnapshots.sumOf { it.second?.completedTasks ?: 0 }
  val weeklyPendingTasks = (weeklyTotalTasks - weeklyCompletedTasks).coerceAtLeast(0)
  val weeklyPct = if (weeklyTotalTasks > 0) {
    ((weeklyCompletedTasks.toFloat() / weeklyTotalTasks.toFloat()) * 100f).toInt()
  } else 0
  val weeklyFocusMins = weeklySnapshots.sumOf { it.second?.focusMinutes ?: 0 }

  val activePercent = if (selectedPeriod == ReportTabPeriod.Daily) {
    actualProductivityPercentToday
  } else {
    weeklyPct
  }
  val ratingLabel = when {
    activePercent >= 85 -> "Excellent"
    activePercent >= 65 -> "Strong"
    activePercent >= 40 -> "Building"
    else -> "Starting"
  }

  val displayTotal = if (selectedPeriod == ReportTabPeriod.Daily) actualTotalToday else weeklyTotalTasks
  val displayCompleted = if (selectedPeriod == ReportTabPeriod.Daily) actualCompletedToday else weeklyCompletedTasks
  val displayPending = if (selectedPeriod == ReportTabPeriod.Daily) actualPendingToday else weeklyPendingTasks

  val chartBars = remember(
    selectedPeriod,
    actualTotalToday,
    actualCompletedToday,
    actualProductivityPercentToday,
    todayFocusMinutes,
    todayYogaMinutes,
    todaySkillMinutes,
    todayProductivity.yogaDoneToday,
    weeklySnapshots,
  ) {
    if (selectedPeriod == ReportTabPeriod.Daily) {
      val taskPct = actualProductivityPercentToday
      val focusPct = ((todayFocusMinutes / 120f) * 100f).toInt().coerceIn(0, 100)
      val yogaPct = if (todayYogaMinutes > 0) {
        ((todayYogaMinutes / 30f) * 100f).toInt().coerceIn(1, 100)
      } else if (todayProductivity.yogaDoneToday) 100 else 0
      val skillPct = ((todaySkillMinutes / 60f) * 100f).toInt().coerceIn(0, 100)
      listOf(
        RealChartBar("Tasks", taskPct, "$actualCompletedToday/$actualTotalToday done"),
        RealChartBar("Focus", focusPct, "${todayFocusMinutes}m / 120m"),
        RealChartBar("Yoga", yogaPct, "${todayYogaMinutes}m logged"),
        RealChartBar("Skill", skillPct, "${todaySkillMinutes}m / 60m"),
      )
    } else {
      weeklySnapshots.map { (dayLabel, snap) ->
        val pct = snap?.productivityPercent ?: 0
        val completed = snap?.completedTasks ?: 0
        val total = snap?.totalTasks ?: 0
        RealChartBar(dayLabel, pct, "$completed/$total tasks ($pct%)")
      }
    }
  }

  Column(modifier = modifier.fillMaxSize()) {
    VisionTopBar(
      rightIcon = Icons.Filled.CalendarToday,
      rightContentDescription = "Toggle daily and weekly report",
      onMenuClick = onOpenMenu,
      onRightActionClick = {
        selectedPeriod = if (selectedPeriod == ReportTabPeriod.Daily) ReportTabPeriod.Weekly else ReportTabPeriod.Daily
      },
    )

    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .testTag("report_screen"),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 104.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      // Period Selector: Daily | Weekly
      item {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          ReportTabPeriod.entries.forEach { period ->
            val selected = period == selectedPeriod
            Surface(
              modifier = Modifier
                .weight(1f)
                .minimumInteractiveComponentSize()
                .testTag("report_period_${period.name.lowercase()}"),
              shape = RoundedCornerShape(50),
              color = if (selected) palette.primaryCard else palette.darkGlassCard,
              border = BorderStroke(1.dp, if (selected) palette.accent else palette.darkCardBorder),
              onClick = { selectedPeriod = period },
            ) {
              Box(modifier = Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                Text(
                  text = "${period.label} Report",
                  style = MaterialTheme.typography.labelLarge,
                  color = if (selected) palette.onPrimaryCard else palette.onDarkCard,
                )
              }
            }
          }
        }
      }

      // Productivity Ring + Real Column Chart
      item {
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .testTag("productivity_chart_card"),
          shape = RoundedCornerShape(24.dp),
          color = palette.darkGlassCard,
          contentColor = palette.onDarkCard,
          border = BorderStroke(1.dp, palette.darkCardBorder),
        ) {
          Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column {
                Text(
                  text = "${selectedPeriod.label} Analytics (Room Data)",
                  style = MaterialTheme.typography.labelSmall,
                  color = palette.accentOnDarkCard,
                )
                Text(
                  text = "Your Productivity",
                  fontFamily = PlayfairDisplayFamily,
                  style = MaterialTheme.typography.headlineMedium,
                  color = palette.onDarkCard,
                )
              }

              ProductivityGaugeRing(
                percent = activePercent,
                ratingLabel = ratingLabel,
                modifier = Modifier.size(92.dp),
              )
            }

            ProductivityColumnChart(
              bars = chartBars,
              modifier = Modifier
                .fillMaxWidth()
                .height(172.dp),
            )
          }
        }
      }

      // Real Completed / Pending / Total Tiles
      item {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          StatSummaryTile(
            label = "Total",
            value = "$displayTotal",
            icon = Icons.Filled.AssignmentTurnedIn,
            accentColor = palette.accentOnPrimaryCard,
            modifier = Modifier
              .weight(1f)
              .testTag("report_total_tasks"),
          )
          StatSummaryTile(
            label = "Completed",
            value = "$displayCompleted",
            icon = Icons.Filled.CheckCircle,
            accentColor = Color(0xFF2E7D32),
            modifier = Modifier
              .weight(1f)
              .testTag("report_completed_tasks"),
          )
          StatSummaryTile(
            label = "Pending",
            value = "$displayPending",
            icon = Icons.Filled.HourglassBottom,
            accentColor = Color(0xFFB56A36),
            modifier = Modifier
              .weight(1f)
              .testTag("report_pending_tasks"),
          )
        }
      }

      // Today's Activity Breakdown + Weekly Comparison Highlights
      item {
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .testTag("today_activity_breakdown_card"),
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
                modifier = Modifier.weight(1f),
              ) {
                Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = palette.accentOnDarkCard, modifier = Modifier.size(18.dp))
                Text(
                  text = if (selectedPeriod == ReportTabPeriod.Daily) "Today's Activity Breakdown" else "7-Day Comparison Summary",
                  style = MaterialTheme.typography.titleMedium,
                  color = palette.onDarkCard,
                )
              }
              if (selectedPeriod == ReportTabPeriod.Daily) {
                Surface(
                  shape = RoundedCornerShape(50),
                  color = palette.accentOnDarkCard.copy(alpha = 0.16f),
                  border = BorderStroke(1.dp, palette.accentOnDarkCard.copy(alpha = 0.45f)),
                ) {
                  Text(
                    text = "From Today",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.accentOnDarkCard,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                  )
                }
              }
            }

            if (selectedPeriod == ReportTabPeriod.Daily) {
              Text(
                text = "Local date: ${DateKeyHelper.formatDisplayDate()} · Saved records only",
                style = MaterialTheme.typography.bodySmall,
                color = palette.onDarkCardMuted,
              )

              Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                EditableActivityBreakdownRow(
                  title = "Focus Time",
                  minutes = todayFocusMinutes,
                  sessions = todayFocusSessions,
                  accentColor = Color(0xFFA92F4C),
                  durationTag = "today_focus_duration",
                  editTag = "edit_today_focus_button",
                  onEdit = { openEditForCategory("FOCUS", "Focus Time") },
                )
                EditableActivityBreakdownRow(
                  title = "Yoga",
                  minutes = todayYogaMinutes,
                  sessions = todayYogaSessions,
                  accentColor = Color(0xFF476F3B),
                  durationTag = "today_yoga_duration",
                  editTag = "edit_today_yoga_button",
                  onEdit = { openEditForCategory("YOGA", "Yoga") },
                )
                EditableActivityBreakdownRow(
                  title = "Skill Dev",
                  minutes = todaySkillMinutes,
                  sessions = todaySkillSessions,
                  accentColor = palette.accentOnDarkCard,
                  durationTag = "today_skill_duration",
                  editTag = "edit_today_skill_button",
                  onEdit = { openEditForCategory("SKILL", "Skill Dev") },
                )
              }
            } else {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                HighlightPill("Week Focus", DateKeyHelper.formatMinutesSummary(weeklyFocusMins), Modifier.weight(1f))
                HighlightPill("Today vs Week", "$actualProductivityPercentToday% / $weeklyPct%", Modifier.weight(1f))
                HighlightPill("Days Logged", "${weeklySnapshots.count { it.second != null }}/7", Modifier.weight(1f))
              }
            }
          }
        }
      }

      // Dedicated Vertical Column Chart directly below Today's Activity Breakdown
      item {
        ActivityBreakdownColumnChartCard(
          focusMinutes = todayFocusMinutes,
          yogaMinutes = todayYogaMinutes,
          skillMinutes = todaySkillMinutes,
          todayActivitySessions = todaySessionsOnly,
          modifier = Modifier.widthIn(max = 600.dp),
        )
      }

      // Saved Activity Records (From Today Only; Add, Edit, Delete with Confirmation)
      item {
        val visibleRecords = todaySessionsOnly
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .testTag("activity_records_card"),
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
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                  text = "Saved Activity Records (From Today)",
                  style = MaterialTheme.typography.titleMedium,
                  color = palette.onPrimaryCard,
                )
                Text(
                  text = "${visibleRecords.size} session(s) on ${DateKeyHelper.formatDisplayDate()} · Past days kept",
                  style = MaterialTheme.typography.bodySmall,
                  color = palette.onPrimaryCardMuted,
                )
              }

              Surface(
                shape = RoundedCornerShape(50),
                color = DarkChocolateDeep,
                onClick = {
                  editingActivitySession = null
                  editingActivityDefaultType = "SKILL"
                  editingAllowCategorySwitch = true
                  suggestedActivityStartMillis = null
                  showActivityEditor = true
                },
                modifier = Modifier.testTag("add_activity_record_button"),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Icon(Icons.Filled.Add, contentDescription = "Add Activity Record", tint = Color.White, modifier = Modifier.size(14.dp))
                  Text("Add Activity", style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
              }
            }

            if (visibleRecords.isEmpty()) {
              Text(
                text = "No activity recorded from today (${DateKeyHelper.formatDisplayDate()}). Tap Edit on Focus Time, Yoga, or Skill Dev above, or tap Add Activity.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.onPrimaryCardMuted,
              )
            } else {
              visibleRecords.forEach { record ->
                val categoryBadge = when (record.activityType) {
                  "FOCUS" -> "Focus"
                  "YOGA" -> "Yoga"
                  else -> "Skill"
                }
                val badgeColor = when (record.activityType) {
                  "FOCUS" -> Color(0xFF8E243B)
                  "YOGA" -> Color(0xFF476F3B)
                  else -> DarkChocolateAccent
                }
                Surface(
                  shape = RoundedCornerShape(14.dp),
                  color = palette.secondaryCard,
                  border = BorderStroke(1.dp, palette.cardBorder),
                ) {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                  ) {
                    Column(
                      modifier = Modifier.weight(1f),
                      verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                      ) {
                        Surface(
                          shape = RoundedCornerShape(50),
                          color = badgeColor,
                        ) {
                          Text(
                            text = categoryBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                          )
                        }
                        Text(
                          text = record.titleOrSkillName,
                          style = MaterialTheme.typography.titleSmall,
                          color = palette.onPrimaryCard,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis,
                          modifier = Modifier.weight(1f, fill = false),
                        )
                      }
                      Text(
                        text = buildString {
                          append(DateKeyHelper.formatDateKeyDisplay(record.dateKey))
                          append(" · ")
                          append(DateKeyHelper.formatMillisTime12h(record.startTimeMillis))
                          append(" – ")
                          append(DateKeyHelper.formatMillisTime12h(record.endTimeMillis))
                          append(" · ")
                          append(DateKeyHelper.formatMinutesSummary(record.durationMinutes))
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.onPrimaryCardMuted,
                      )
                      if (record.notes.isNotBlank()) {
                        Text(
                          text = record.notes,
                          style = MaterialTheme.typography.bodySmall,
                          color = palette.onPrimaryCardMuted,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis,
                        )
                      }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                      IconButton(
                        onClick = {
                          editingActivitySession = record
                          editingActivityDefaultType = record.activityType
                          editingAllowCategorySwitch = false
                          suggestedActivityStartMillis = null
                          showActivityEditor = true
                        },
                        modifier = Modifier.minimumInteractiveComponentSize(),
                      ) {
                        Icon(
                          imageVector = Icons.Filled.Edit,
                          contentDescription = "Edit ${record.titleOrSkillName}",
                          tint = palette.accentOnPrimaryCard,
                          modifier = Modifier.size(18.dp),
                        )
                      }
                      IconButton(
                        onClick = { deletingActivitySession = record },
                        modifier = Modifier.minimumInteractiveComponentSize(),
                      ) {
                        Icon(
                          imageVector = Icons.Filled.DeleteOutline,
                          contentDescription = "Delete ${record.titleOrSkillName}",
                          tint = VisionDangerRed,
                          modifier = Modifier.size(18.dp),
                        )
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }

      // Next Day Planning & Carry-Forward Manager
      item {
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .testTag("next_day_plan_card"),
          shape = RoundedCornerShape(22.dp),
          color = palette.primaryCard,
          contentColor = palette.onPrimaryCard,
          border = BorderStroke(1.dp, palette.cardBorder),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
              ) {
                Box(
                  modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(palette.accentOnPrimaryCard.copy(alpha = 0.16f)),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = palette.accentOnPrimaryCard, modifier = Modifier.size(20.dp))
                }
                Column {
                  Text("Next Day Planning", style = MaterialTheme.typography.titleMedium, color = palette.onPrimaryCard)
                  Text(
                    text = "For ${DateKeyHelper.tomorrowKey()} · Saved locally",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.onPrimaryCardMuted,
                  )
                }
              }

              Surface(
                shape = RoundedCornerShape(50),
                color = DarkChocolateDeep,
                onClick = {
                  editingTomorrowTask = null
                  showTomorrowTaskSheet = true
                },
                modifier = Modifier.testTag("add_tomorrow_task_button"),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Icon(Icons.Filled.Add, contentDescription = "Add Tomorrow Task", tint = Color.White, modifier = Modifier.size(14.dp))
                  Text("Tomorrow", style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
              }
            }

            // Saved Next-Day Plan Note Box
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = palette.secondaryCard,
              border = BorderStroke(1.dp, palette.cardBorder),
              onClick = { showEditPlanSheet = true },
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                  Text(
                    text = "Next-Day Focus Note",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.accentOnPrimaryCard,
                  )
                  Spacer(Modifier.height(2.dp))
                  Text(
                    text = focusConfig.nextDayPlanNote.ifBlank { "No note written yet. Tap Edit Note to set tomorrow's top priority." },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (focusConfig.nextDayPlanNote.isBlank()) palette.onPrimaryCardMuted else palette.onPrimaryCard,
                  )
                }
                OutlinedButton(
                  onClick = { showEditPlanSheet = true },
                  border = BorderStroke(1.dp, palette.cardBorder),
                  contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                  modifier = Modifier.testTag("edit_next_day_note_button"),
                ) {
                  Icon(Icons.Filled.Edit, contentDescription = null, tint = palette.accentOnPrimaryCard, modifier = Modifier.size(14.dp))
                  Spacer(Modifier.size(4.dp))
                  Text("Edit Note", style = MaterialTheme.typography.labelSmall, color = palette.onPrimaryCard)
                }
              }
            }

            // Pending Incomplete Tasks (Today + Overdue) Multi-Select Carry Forward
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = "Pending Incomplete Tasks (${pendingCarryForwardTasks.size})",
                  style = MaterialTheme.typography.titleSmall,
                  color = palette.onPrimaryCard,
                )
                if (pendingCarryForwardTasks.isNotEmpty()) {
                  TextButton(
                    onClick = {
                      selectedTaskIds = if (selectedTaskIds.size == pendingCarryForwardTasks.size) {
                        emptySet()
                      } else {
                        pendingCarryForwardTasks.map { it.id }.toSet()
                      }
                    },
                  ) {
                    Text(
                      text = if (selectedTaskIds.size == pendingCarryForwardTasks.size) "Clear All" else "Select All",
                      style = MaterialTheme.typography.labelMedium,
                      color = palette.accentOnPrimaryCard,
                    )
                  }
                }
              }

              if (pendingCarryForwardTasks.isEmpty()) {
                Text(
                  text = "No unfinished tasks remaining from today or earlier days.",
                  style = MaterialTheme.typography.bodySmall,
                  color = palette.onPrimaryCardMuted,
                )
              } else {
                pendingCarryForwardTasks.forEach { task ->
                  val checked = task.id in selectedTaskIds
                  Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = palette.secondaryCard,
                    border = BorderStroke(1.dp, palette.cardBorder),
                    onClick = {
                      selectedTaskIds = if (checked) selectedTaskIds - task.id else selectedTaskIds + task.id
                    },
                  ) {
                    Row(
                      modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                      Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                      ) {
                        Checkbox(
                          checked = checked,
                          onCheckedChange = { isChecked ->
                            selectedTaskIds = if (isChecked) selectedTaskIds + task.id else selectedTaskIds - task.id
                          },
                          colors = visionPrimaryCardCheckboxColors(palette),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                          Text(
                            text = task.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.onPrimaryCard,
                          )
                          Text(
                            text = buildString {
                              append(if (task.dateKey == todayKey) "Today" else "Overdue (${task.dateKey})")
                              if (task.scheduledTimeText.isNotBlank()) append(" · ${task.scheduledTimeText}")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.onPrimaryCardMuted,
                          )
                        }
                      }

                      OutlinedButton(
                        onClick = { onMovePendingToTomorrow(task) },
                        border = BorderStroke(1.dp, palette.cardBorder),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                      ) {
                        Text("Move", style = MaterialTheme.typography.labelSmall, color = palette.onPrimaryCard)
                      }
                    }
                  }
                }

                val selectedList = pendingCarryForwardTasks.filter { it.id in selectedTaskIds }
                Button(
                  onClick = {
                    onCarryForwardTasks(selectedList)
                    selectedTaskIds = emptySet()
                  },
                  enabled = selectedList.isNotEmpty(),
                  colors = ButtonDefaults.buttonColors(containerColor = DarkChocolateDeep),
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag("carry_forward_selected_button"),
                ) {
                  Text(
                    text = "Move Selected to Tomorrow (${selectedList.size})",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                  )
                }
              }
            }

            // Tomorrow's Saved Schedule
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text(
                text = "Tomorrow's Saved Schedule (${tomorrowTasks.size})",
                style = MaterialTheme.typography.titleSmall,
                color = palette.accentOnPrimaryCard,
              )
              if (tomorrowTasks.isEmpty()) {
                Text(
                  text = "Nothing scheduled for tomorrow yet. Move unfinished tasks above or tap + Tomorrow.",
                  style = MaterialTheme.typography.bodySmall,
                  color = palette.onPrimaryCardMuted,
                )
              } else {
                tomorrowTasks.forEach { task ->
                  Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = palette.secondaryCard.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, palette.cardBorder),
                  ) {
                    Row(
                      modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleTask(task) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                      Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                      ) {
                        Icon(
                          imageVector = if (task.isCompleted) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                          contentDescription = null,
                          tint = if (task.isCompleted) Color(0xFF2E7D32) else palette.onPrimaryCardMuted,
                          modifier = Modifier.size(18.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                          Text(
                            text = task.title,
                            style = MaterialTheme.typography.bodyMedium.copy(
                              textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                            ),
                            color = palette.onPrimaryCard,
                          )
                          Text(
                            text = task.scheduledTimeText.ifBlank { task.category },
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.onPrimaryCardMuted,
                          )
                        }
                      }

                      Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                          onClick = {
                            editingTomorrowTask = task
                            showTomorrowTaskSheet = true
                          },
                          modifier = Modifier.minimumInteractiveComponentSize(),
                        ) {
                          Icon(Icons.Filled.Edit, contentDescription = "Edit tomorrow task", tint = palette.onPrimaryCardMuted, modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                          onClick = { onDeleteTask(task.id) },
                          modifier = Modifier.minimumInteractiveComponentSize(),
                        ) {
                          Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete tomorrow task", tint = VisionDangerRed, modifier = Modifier.size(18.dp))
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }

      item {
        CentralAiBannerCard(
          summaryText = "Daily $actualProductivityPercentToday% · Weekly $weeklyPct% · ${tomorrowTasks.size} scheduled tomorrow",
          statusLabel = "REPORT PORT",
          onOpenAssistantSheet = onOpenAiSheet,
          contextHint = "ALFRED Productivity Analysis Port",
          modifier = Modifier.widthIn(max = 600.dp),
        )
      }
    }
  }

  if (showEditPlanSheet) {
    NextDayPlanNoteSheet(
      initialPlan = focusConfig.nextDayPlanNote,
      onSave = { note ->
        onSaveNextDayNote(note)
        showEditPlanSheet = false
      },
      onDismiss = { showEditPlanSheet = false },
    )
  }

  if (showTomorrowTaskSheet) {
    TaskEditorSheet(
      initialTask = editingTomorrowTask,
      defaultDateKey = DateKeyHelper.tomorrowKey(),
      onSave = { task ->
        onSaveTomorrowTask(task)
        showTomorrowTaskSheet = false
      },
      onDelete = { id ->
        onDeleteTask(id)
        showTomorrowTaskSheet = false
      },
      onDismiss = { showTomorrowTaskSheet = false },
    )
  }

  choosingCategoryForEdit?.let { (typeKey, categoryLabel) ->
    val matching = todaySessionsOnly.filter { it.activityType == typeKey }
    AlertDialog(
      onDismissRequest = { choosingCategoryForEdit = null },
      title = { Text("Edit Today's $categoryLabel") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = "Select a session from today to edit its duration, or add a new $categoryLabel session.",
            style = MaterialTheme.typography.bodySmall,
          )
          matching.forEach { session ->
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = palette.secondaryCard,
              border = BorderStroke(1.dp, palette.cardBorder),
              onClick = {
                choosingCategoryForEdit = null
                editingActivitySession = session
                editingActivityDefaultType = typeKey
                editingAllowCategorySwitch = false
                suggestedActivityStartMillis = null
                showActivityEditor = true
              },
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = session.titleOrSkillName,
                    style = MaterialTheme.typography.titleSmall,
                    color = palette.onPrimaryCard,
                  )
                  Text(
                    text = "${DateKeyHelper.formatMillisTime12h(session.startTimeMillis)} – ${DateKeyHelper.formatMillisTime12h(session.endTimeMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.onPrimaryCardMuted,
                  )
                }
                Text(
                  text = DateKeyHelper.formatMinutesSummary(session.durationMinutes),
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = FontWeight.Bold,
                  color = palette.accentOnPrimaryCard,
                )
              }
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            choosingCategoryForEdit = null
            val latestEnd = matching.maxOfOrNull { it.endTimeMillis }
            val candidateStart = latestEnd?.plus(60_000L)?.takeIf { DateKeyHelper.todayKey(it) == todayKey }
            editingActivitySession = null
            editingActivityDefaultType = typeKey
            editingAllowCategorySwitch = false
            suggestedActivityStartMillis = candidateStart
            showActivityEditor = true
          },
          colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
        ) {
          Text("Add New", color = Color.White)
        }
      },
      dismissButton = {
        TextButton(onClick = { choosingCategoryForEdit = null }) {
          Text("Cancel")
        }
      },
    )
  }

  if (showActivityEditor) {
    ActivitySessionEditorSheet(
      initialSession = editingActivitySession,
      defaultType = editingActivitySession?.activityType ?: editingActivityDefaultType,
      allowCategorySwitch = editingAllowCategorySwitch,
      suggestedStartTimeMillis = suggestedActivityStartMillis,
      onSave = { saved ->
        onSaveActivitySession(saved)
        showActivityEditor = false
      },
      onDelete = { id ->
        onDeleteActivitySession(id)
        showActivityEditor = false
      },
      onDismiss = { showActivityEditor = false },
    )
  }

  deletingActivitySession?.let { record ->
    AlertDialog(
      onDismissRequest = { deletingActivitySession = null },
      title = { Text("Delete Activity Record?") },
      text = {
        Text("Remove \"${record.titleOrSkillName}\" (${record.durationMinutes}m) from ${DateKeyHelper.formatDateKeyDisplay(record.dateKey)}? Today's totals and charts will recalculate automatically.")
      },
      confirmButton = {
        Button(
          onClick = {
            onDeleteActivitySession(record.id)
            deletingActivitySession = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = VisionDangerRed),
        ) {
          Text("Delete", color = Color.White)
        }
      },
      dismissButton = {
        TextButton(onClick = { deletingActivitySession = null }) {
          Text("Cancel")
        }
      },
    )
  }
}

@Composable
private fun ActivityBreakdownColumnChartCard(
  focusMinutes: Int,
  yogaMinutes: Int,
  skillMinutes: Int,
  todayActivitySessions: List<ActivitySessionEntity>,
  modifier: Modifier = Modifier,
) {
  val palette = LocalVisionPalette.current
  val columns = remember(focusMinutes, yogaMinutes, skillMinutes) {
    listOf(
      Triple("FOCUS", "Focus Time", focusMinutes),
      Triple("YOGA", "Yoga", yogaMinutes),
      Triple("SKILL", "Skill Dev", skillMinutes),
    )
  }
  var selectedIndex by remember { mutableIntStateOf(0) }
  val maxMinutes = columns.maxOf { it.third }.coerceAtLeast(30)
  val totalMinutes = focusMinutes + yogaMinutes + skillMinutes

  Surface(
    modifier = modifier
      .fillMaxWidth()
      .testTag("activity_breakdown_column_chart"),
    shape = RoundedCornerShape(22.dp),
    color = palette.primaryCard,
    contentColor = palette.onPrimaryCard,
    border = BorderStroke(1.dp, palette.cardBorder),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.BarChart,
              contentDescription = "Activity Breakdown Chart",
              tint = palette.accentOnPrimaryCard,
              modifier = Modifier.size(20.dp),
            )
            Text(
              text = "Activity Time Column Chart",
              style = MaterialTheme.typography.titleMedium,
              color = palette.onPrimaryCard,
            )
          }
          Text(
            text = "From Today · ${DateKeyHelper.formatDisplayDate()}",
            style = MaterialTheme.typography.labelSmall,
            color = palette.onPrimaryCardMuted,
          )
        }
        Text(
          text = if (totalMinutes == 0) "0m total" else "${DateKeyHelper.formatMinutesSummary(totalMinutes)} total",
          style = MaterialTheme.typography.labelMedium,
          color = palette.accentOnPrimaryCard,
          modifier = Modifier.testTag("activity_chart_total_duration"),
        )
      }

      if (totalMinutes == 0) {
        Text(
          text = "No Focus, Yoga, or Skill activity recorded from today yet. Columns below update live as you log or edit sessions.",
          style = MaterialTheme.typography.bodySmall,
          color = palette.onPrimaryCardMuted,
        )
      }

      val colColors = listOf(
        Color(0xFFA92F4C),
        Color(0xFF476F3B),
        DarkChocolateAccent,
      )

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(176.dp)
          .padding(top = 8.dp),
      ) {
        val gridLineColor = palette.cardBorder.copy(alpha = 0.7f)
        Canvas(modifier = Modifier.fillMaxSize()) {
          val chartBottom = size.height - 8.dp.toPx()
          val chartHeight = chartBottom - 8.dp.toPx()
          for (step in 0..3) {
            val y = 8.dp.toPx() + chartHeight * (step / 3f)
            drawLine(
              color = gridLineColor,
              start = Offset(0f, y),
              end = Offset(size.width, y),
              strokeWidth = 1.dp.toPx(),
            )
          }

          val slotWidth = size.width / columns.size.toFloat()
          val barWidth = (slotWidth * 0.42f).coerceAtMost(44.dp.toPx())
          columns.forEachIndexed { idx, (_, _, mins) ->
            val ratio = if (maxMinutes > 0) (mins.toFloat() / maxMinutes.toFloat()).coerceIn(0f, 1f) else 0f
            val barH = if (mins <= 0) 4.dp.toPx() else (ratio * chartHeight).coerceAtLeast(10.dp.toPx())
            val left = idx * slotWidth + (slotWidth - barWidth) / 2f
            val top = chartBottom - barH
            val baseColor = colColors[idx]
            val isSelected = idx == selectedIndex
            drawRoundRect(
              color = if (mins <= 0) palette.secondaryCard else if (isSelected) baseColor else baseColor.copy(alpha = 0.75f),
              topLeft = Offset(left, top),
              size = Size(barWidth, barH),
              cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
            )
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        columns.forEachIndexed { idx, (_, label, mins) ->
          val selected = idx == selectedIndex
          Surface(
            modifier = Modifier
              .weight(1f)
              .testTag("activity_chart_col_${label.lowercase().replace(" ", "_")}"),
            shape = RoundedCornerShape(12.dp),
            color = if (selected) palette.secondaryCard else Color.Transparent,
            border = if (selected) BorderStroke(1.dp, colColors[idx]) else null,
            onClick = { selectedIndex = idx },
          ) {
            Column(
              modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Text(
                text = DateKeyHelper.formatMinutesSummary(mins),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (mins > 0) colColors[idx] else palette.onPrimaryCardMuted,
              )
              Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = palette.onPrimaryCard,
                textAlign = TextAlign.Center,
              )
            }
          }
        }
      }

      val picked = columns[selectedIndex]
      val matchingSessions = todayActivitySessions.filter { it.activityType == picked.first }
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = palette.secondaryCard,
        border = BorderStroke(1.dp, palette.cardBorder),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp),
          verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
          Text(
            text = "${picked.second}: ${DateKeyHelper.formatMinutesSummary(picked.third)} (${matchingSessions.size} session${if (matchingSessions.size == 1) "" else "s"})",
            style = MaterialTheme.typography.labelLarge,
            color = palette.onPrimaryCard,
          )
          if (matchingSessions.isNotEmpty()) {
            val grouped = matchingSessions.groupBy { it.titleOrSkillName }
              .entries
              .joinToString(" · ") { (name, list) -> "$name (${list.sumOf { it.durationMinutes }}m)" }
            Text(
              text = grouped,
              style = MaterialTheme.typography.bodySmall,
              color = palette.onPrimaryCardMuted,
            )
          } else {
            Text(
              text = "No ${picked.second.lowercase()} sessions logged today yet.",
              style = MaterialTheme.typography.bodySmall,
              color = palette.onPrimaryCardMuted,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ProductivityGaugeRing(
  percent: Int,
  ratingLabel: String,
  modifier: Modifier = Modifier,
) {
  val palette = LocalVisionPalette.current
  val accentColor = palette.accentOnDarkCard
  Box(modifier = modifier, contentAlignment = Alignment.Center) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val stroke = 10.dp.toPx()
      drawArc(
        color = palette.onDarkCard.copy(alpha = 0.14f),
        startAngle = 0f,
        sweepAngle = 360f,
        useCenter = false,
        style = Stroke(width = stroke, cap = StrokeCap.Round),
      )
      drawArc(
        brush = Brush.sweepGradient(
          colors = listOf(DarkChocolateAccent, DarkChocolateGold, accentColor),
        ),
        startAngle = -90f,
        sweepAngle = (percent.coerceIn(0, 100) / 100f) * 360f,
        useCenter = false,
        style = Stroke(width = stroke, cap = StrokeCap.Round),
      )
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
        text = "$percent%",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = palette.onDarkCard,
      )
      Text(
        text = ratingLabel,
        style = MaterialTheme.typography.labelSmall,
        color = palette.accentOnDarkCard,
      )
    }
  }
}

@Composable
private fun ProductivityColumnChart(
  bars: List<RealChartBar>,
  modifier: Modifier = Modifier,
) {
  val palette = LocalVisionPalette.current
  var selectedIndex by remember(bars) { mutableIntStateOf(0) }

  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    val activeBar = bars.getOrNull(selectedIndex)
    if (activeBar != null) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = "${activeBar.label}: ${activeBar.detail}",
          style = MaterialTheme.typography.labelSmall,
          color = palette.onDarkCard,
        )
        Text(
          text = "${activeBar.valuePercent}%",
          style = MaterialTheme.typography.labelMedium,
          color = palette.accentOnDarkCard,
        )
      }
    }

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f),
    ) {
      val gridColor = palette.onDarkCard.copy(alpha = 0.12f)
      val barSelectedTop = palette.accentOnDarkCard
      val barSelectedBottom = palette.accent
      val barMutedTop = palette.accentOnDarkCard.copy(alpha = 0.65f)
      val barMutedBottom = palette.accent.copy(alpha = 0.45f)

      Canvas(modifier = Modifier.fillMaxSize()) {
        val gridLines = 4
        for (i in 0 until gridLines) {
          val y = size.height * (i / gridLines.toFloat())
          drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1.dp.toPx(),
          )
        }

        val slotWidth = size.width / bars.size.coerceAtLeast(1)
        val barWidth = (slotWidth * 0.48f).coerceAtMost(28.dp.toPx())
        bars.forEachIndexed { index, bar ->
          val minVisible = 4.dp.toPx()
          val rawHeight = (bar.valuePercent.coerceIn(0, 100) / 100f) * (size.height - 6.dp.toPx())
          val barHeight = rawHeight.coerceAtLeast(minVisible)
          val left = index * slotWidth + (slotWidth - barWidth) / 2f
          val top = size.height - barHeight
          val isSelected = index == selectedIndex
          drawRoundRect(
            brush = Brush.verticalGradient(
              colors = if (isSelected) listOf(barSelectedTop, barSelectedBottom)
              else listOf(barMutedTop, barMutedBottom),
            ),
            topLeft = Offset(left, top),
            size = Size(barWidth, barHeight),
            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
          )
        }
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceAround,
    ) {
      bars.forEachIndexed { index, bar ->
        Text(
          text = bar.label,
          style = MaterialTheme.typography.labelSmall,
          color = if (index == selectedIndex) palette.onDarkCard else palette.onDarkCardMuted,
          textAlign = TextAlign.Center,
          modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(6.dp))
            .clickable { selectedIndex = index }
            .padding(vertical = 4.dp),
        )
      }
    }
  }
}

@Composable
private fun StatSummaryTile(
  label: String,
  value: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  accentColor: Color,
  modifier: Modifier = Modifier,
) {
  val palette = LocalVisionPalette.current
  Surface(
    modifier = modifier.semantics(mergeDescendants = true) {},
    shape = RoundedCornerShape(18.dp),
    color = palette.primaryCard,
    contentColor = palette.onPrimaryCard,
    border = BorderStroke(1.dp, palette.cardBorder),
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Icon(icon, contentDescription = label, tint = accentColor, modifier = Modifier.size(20.dp))
      Text(
        text = value,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = palette.onPrimaryCard,
      )
      Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = palette.onPrimaryCardMuted,
      )
    }
  }
}

@Composable
private fun EditableActivityBreakdownRow(
  title: String,
  minutes: Int,
  sessions: List<ActivitySessionEntity>,
  accentColor: Color,
  durationTag: String,
  editTag: String,
  onEdit: () -> Unit,
) {
  val palette = LocalVisionPalette.current
  val subtitleText = when {
    sessions.isEmpty() -> "No sessions from today"
    sessions.size == 1 -> sessions.first().titleOrSkillName
    else -> "${sessions.size} sessions from today"
  }
  Surface(
    shape = RoundedCornerShape(14.dp),
    color = palette.secondaryCard.copy(alpha = if (palette.isDarkMode) 0.18f else 0.85f),
    border = BorderStroke(1.dp, palette.darkCardBorder),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 10.dp),
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
            .size(10.dp)
            .clip(CircleShape)
            .background(accentColor),
        )
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = palette.onDarkCard,
          )
          Text(
            text = subtitleText,
            style = MaterialTheme.typography.bodySmall,
            color = palette.onDarkCardMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
          text = DateKeyHelper.formatMinutesSummary(minutes),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = palette.accentOnDarkCard,
          modifier = Modifier.testTag(durationTag),
        )
        Surface(
          shape = RoundedCornerShape(50),
          color = palette.primaryCard,
          border = BorderStroke(1.dp, palette.accentOnPrimaryCard.copy(alpha = 0.55f)),
          onClick = onEdit,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag(editTag),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.Edit,
              contentDescription = "Edit $title duration",
              tint = palette.accentOnPrimaryCard,
              modifier = Modifier.size(14.dp),
            )
            Text(
              text = "Edit",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.SemiBold,
              color = palette.onPrimaryCard,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun HighlightPill(
  title: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  val palette = LocalVisionPalette.current
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(14.dp),
    color = palette.secondaryCard.copy(alpha = if (palette.isDarkMode) 0.16f else 0.72f),
    border = BorderStroke(1.dp, palette.darkCardBorder),
  ) {
    Column(
      modifier = Modifier.padding(10.dp),
      verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
      Text(title, style = MaterialTheme.typography.labelSmall, color = palette.onDarkCardMuted)
      Text(value, style = MaterialTheme.typography.titleSmall, color = palette.accentOnDarkCard)
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NextDayPlanNoteSheet(
  initialPlan: String,
  onSave: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val palette = LocalVisionPalette.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var draft by remember(initialPlan) { mutableStateOf(initialPlan) }

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
        .padding(bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Text("Next Day Planning Note", style = MaterialTheme.typography.titleLarge, color = palette.onPrimaryCard)
      OutlinedTextField(
        value = draft,
        onValueChange = { draft = it },
        modifier = Modifier
          .fillMaxWidth()
          .testTag("next_day_plan_input"),
        label = { Text("Tomorrow's Focus Plan") },
        colors = visionPrimaryCardFieldColors(palette),
        minLines = 3,
      )
      Button(
        onClick = { onSave(draft) },
        modifier = Modifier
          .fillMaxWidth()
          .height(50.dp)
          .testTag("save_next_day_plan_button"),
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(containerColor = DarkChocolateDeep),
      ) {
        Text("Save Plan", color = Color.White)
      }
    }
  }
}
