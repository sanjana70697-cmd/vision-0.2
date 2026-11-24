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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.local.DailyProductivitySnapshotEntity
import com.example.data.local.DateKeyHelper
import com.example.data.local.FocusConfigEntity
import com.example.data.local.ProductivitySummary
import com.example.data.local.TaskEntity
import com.example.ui.components.CentralAiBannerCard
import com.example.ui.components.VisionTopBar
import com.example.ui.theme.CarraraInk
import com.example.ui.theme.CarraraWhite
import com.example.ui.theme.DarkChocolateAccent
import com.example.ui.theme.DarkChocolateDeep
import com.example.ui.theme.DarkChocolateGold
import com.example.ui.theme.LocalVisionPalette
import com.example.ui.theme.PlayfairDisplayFamily

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
  pendingTasks: List<TaskEntity>,
  tomorrowTasks: List<TaskEntity>,
  focusConfig: FocusConfigEntity,
  onMovePendingToTomorrow: (TaskEntity) -> Unit,
  onSaveTomorrowTask: (TaskEntity) -> Unit,
  onSaveNextDayNote: (String) -> Unit,
  onOpenAiSheet: () -> Unit,
  onOpenMenu: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalVisionPalette.current
  var selectedPeriod by remember { mutableStateOf(ReportTabPeriod.Daily) }
  var showEditPlanSheet by remember { mutableStateOf(false) }
  var showAddTomorrowTaskSheet by remember { mutableStateOf(false) }

  // Real calculations strictly from stored Room data
  val weeklyTotalTasks = weeklySnapshots.sumOf { it.second?.totalTasks ?: 0 }
  val weeklyCompletedTasks = weeklySnapshots.sumOf { it.second?.completedTasks ?: 0 }
  val weeklyPendingTasks = (weeklyTotalTasks - weeklyCompletedTasks).coerceAtLeast(0)
  val weeklyPct = if (weeklyTotalTasks > 0) {
    ((weeklyCompletedTasks.toFloat() / weeklyTotalTasks.toFloat()) * 100f).toInt()
  } else 0
  val weeklyFocusMins = weeklySnapshots.sumOf { it.second?.focusMinutes ?: 0 }

  val activePercent = if (selectedPeriod == ReportTabPeriod.Daily) {
    todayProductivity.productivityPercent
  } else {
    weeklyPct
  }
  val ratingLabel = when {
    activePercent >= 85 -> "Excellent"
    activePercent >= 65 -> "Strong"
    activePercent >= 40 -> "Building"
    else -> "Starting"
  }

  val displayTotal = if (selectedPeriod == ReportTabPeriod.Daily) todayProductivity.totalTasks else weeklyTotalTasks
  val displayCompleted = if (selectedPeriod == ReportTabPeriod.Daily) todayProductivity.completedTasks else weeklyCompletedTasks
  val displayPending = if (selectedPeriod == ReportTabPeriod.Daily) todayProductivity.pendingTasks else weeklyPendingTasks

  val chartBars = remember(selectedPeriod, todayProductivity, weeklySnapshots) {
    if (selectedPeriod == ReportTabPeriod.Daily) {
      val taskPct = todayProductivity.productivityPercent
      val focusPct = ((todayProductivity.focusMinutesToday / 120f) * 100f).toInt().coerceIn(0, 100)
      val yogaPct = if (todayProductivity.yogaDoneToday) 100 else ((todayProductivity.yogaMinutesToday / 30f) * 100f).toInt().coerceIn(0, 100)
      val skillPct = ((todayProductivity.skillMinutesToday / 90f) * 100f).toInt().coerceIn(0, 100)
      listOf(
        RealChartBar("Tasks", taskPct, "${todayProductivity.completedTasks}/${todayProductivity.totalTasks} done"),
        RealChartBar("Focus", focusPct, "${todayProductivity.focusMinutesToday}m / 120m"),
        RealChartBar("Yoga", yogaPct, "${todayProductivity.yogaMinutesToday}m logged"),
        RealChartBar("Skill", skillPct, "${todayProductivity.skillMinutesToday}m / 90m"),
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
              border = BorderStroke(1.dp, if (selected) DarkChocolateGold else palette.darkCardBorder),
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
                  color = DarkChocolateGold,
                )
                Text(
                  text = "Your Productivity",
                  fontFamily = PlayfairDisplayFamily,
                  style = MaterialTheme.typography.headlineMedium,
                  color = CarraraWhite,
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

      // Real Completed / Pending / Total Tiles (Carrara White)
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
            accentColor = DarkChocolateAccent,
            modifier = Modifier
              .weight(1f)
              .testTag("report_total_tasks"),
          )
          StatSummaryTile(
            label = "Completed",
            value = "$displayCompleted",
            icon = Icons.Filled.CheckCircle,
            accentColor = Color(0xFF3E7B47),
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

      // Daily Progress & Weekly Comparison Highlights
      item {
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .testTag("daily_progress_breakdown_card"),
          shape = RoundedCornerShape(22.dp),
          color = palette.darkGlassCard,
          border = BorderStroke(1.dp, palette.darkCardBorder),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(Icons.Filled.TrendingUp, contentDescription = null, tint = DarkChocolateGold, modifier = Modifier.size(18.dp))
              Text(
                text = if (selectedPeriod == ReportTabPeriod.Daily) "Today's Stored Breakdown" else "7-Day Comparison Summary",
                style = MaterialTheme.typography.titleMedium,
                color = CarraraWhite,
              )
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              if (selectedPeriod == ReportTabPeriod.Daily) {
                HighlightPill("Focus Time", "${todayProductivity.focusMinutesToday}m", Modifier.weight(1f))
                HighlightPill(
                  "Yoga",
                  if (todayProductivity.yogaDoneToday) "${todayProductivity.yogaMinutesToday}m Done" else "${todayProductivity.yogaMinutesToday}m",
                  Modifier.weight(1f),
                )
                HighlightPill("Skill Dev", "${todayProductivity.skillMinutesToday}m", Modifier.weight(1f))
              } else {
                HighlightPill("Week Focus", "${weeklyFocusMins}m", Modifier.weight(1f))
                HighlightPill("Today vs Week", "${todayProductivity.productivityPercent}% / $weeklyPct%", Modifier.weight(1f))
                HighlightPill("Days Logged", "${weeklySnapshots.count { it.second != null }}/7", Modifier.weight(1f))
              }
            }
          }
        }
      }

      // Next Day Planning (Real Pending Tasks + Tomorrow Schedule + Planning Note)
      item {
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .testTag("next_day_plan_card"),
          shape = RoundedCornerShape(22.dp),
          color = palette.primaryCard,
          border = BorderStroke(1.dp, palette.cardBorder),
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
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f).clickable { showEditPlanSheet = true },
              ) {
                Box(
                  modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(DarkChocolateAccent.copy(alpha = 0.16f)),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = DarkChocolateAccent, modifier = Modifier.size(20.dp))
                }
                Column {
                  Text("Next Day Planning", style = MaterialTheme.typography.titleMedium, color = CarraraInk)
                  Text(
                    text = focusConfig.nextDayPlanNote.ifBlank { "Tap to write tomorrow's focus plan." },
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.onPrimaryCardMuted,
                  )
                }
              }

              Surface(
                shape = RoundedCornerShape(50),
                color = DarkChocolateDeep,
                onClick = { showAddTomorrowTaskSheet = true },
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Icon(Icons.Filled.Add, contentDescription = "Add Tomorrow Task", tint = CarraraWhite, modifier = Modifier.size(14.dp))
                  Text("Tomorrow", style = MaterialTheme.typography.labelSmall, color = CarraraWhite)
                }
              }
            }

            // Pending tasks available to carry forward to tomorrow
            val todayPending = pendingTasks.filter { it.dateKey == DateKeyHelper.todayKey() }
            if (todayPending.isNotEmpty()) {
              Text(
                text = "Pending Today (${todayPending.size}) — Carry to Tomorrow:",
                style = MaterialTheme.typography.labelLarge,
                color = CarraraInk,
              )
              todayPending.forEach { task ->
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.secondaryCard)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = CarraraInk,
                    modifier = Modifier.weight(1f),
                  )
                  OutlinedButton(
                    onClick = { onMovePendingToTomorrow(task) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                  ) {
                    Text("Move", style = MaterialTheme.typography.labelSmall, color = DarkChocolateDeep)
                  }
                }
              }
            }

            if (tomorrowTasks.isNotEmpty()) {
              Text(
                text = "Tomorrow's Schedule (${tomorrowTasks.size}):",
                style = MaterialTheme.typography.labelLarge,
                color = DarkChocolateAccent,
              )
              tomorrowTasks.forEach { task ->
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Text(
                    text = "• ${task.title}",
                    style = MaterialTheme.typography.bodySmall,
                    color = CarraraInk,
                  )
                  Text(
                    text = task.scheduledTimeText.ifBlank { task.category },
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.onPrimaryCardMuted,
                  )
                }
              }
            }
          }
        }
      }

      item {
        CentralAiBannerCard(
          summaryText = "Daily ${todayProductivity.productivityPercent}% · Weekly $weeklyPct% · ${tomorrowTasks.size} scheduled tomorrow",
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

  if (showAddTomorrowTaskSheet) {
    TaskEditorSheet(
      initialTask = null,
      defaultDateKey = DateKeyHelper.tomorrowKey(),
      onSave = { task ->
        onSaveTomorrowTask(task)
        showAddTomorrowTaskSheet = false
      },
      onDelete = {},
      onDismiss = { showAddTomorrowTaskSheet = false },
    )
  }
}

@Composable
private fun ProductivityGaugeRing(
  percent: Int,
  ratingLabel: String,
  modifier: Modifier = Modifier,
) {
  Box(modifier = modifier, contentAlignment = Alignment.Center) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val stroke = 10.dp.toPx()
      drawArc(
        color = CarraraWhite.copy(alpha = 0.14f),
        startAngle = 0f,
        sweepAngle = 360f,
        useCenter = false,
        style = Stroke(width = stroke, cap = StrokeCap.Round),
      )
      drawArc(
        brush = Brush.sweepGradient(
          colors = listOf(DarkChocolateAccent, DarkChocolateGold, CarraraWhite),
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
        color = CarraraWhite,
      )
      Text(
        text = ratingLabel,
        style = MaterialTheme.typography.labelSmall,
        color = DarkChocolateGold,
      )
    }
  }
}

@Composable
private fun ProductivityColumnChart(
  bars: List<RealChartBar>,
  modifier: Modifier = Modifier,
) {
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
          color = CarraraWhite.copy(alpha = 0.85f),
        )
        Text(
          text = "${activeBar.valuePercent}%",
          style = MaterialTheme.typography.labelMedium,
          color = DarkChocolateGold,
        )
      }
    }

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f),
    ) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        val gridLines = 4
        for (i in 0 until gridLines) {
          val y = size.height * (i / gridLines.toFloat())
          drawLine(
            color = CarraraWhite.copy(alpha = 0.10f),
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
              colors = if (isSelected) listOf(CarraraWhite, DarkChocolateGold)
              else listOf(DarkChocolateGold.copy(alpha = 0.85f), DarkChocolateAccent.copy(alpha = 0.65f)),
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
          color = if (index == selectedIndex) CarraraWhite else CarraraWhite.copy(alpha = 0.60f),
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
    modifier = modifier,
    shape = RoundedCornerShape(18.dp),
    color = palette.primaryCard,
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
        color = CarraraInk,
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
private fun HighlightPill(
  title: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(14.dp),
    color = DarkChocolateDeep,
    border = BorderStroke(1.dp, DarkChocolateAccent.copy(alpha = 0.4f)),
  ) {
    Column(
      modifier = Modifier.padding(10.dp),
      verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
      Text(title, style = MaterialTheme.typography.labelSmall, color = CarraraWhite.copy(alpha = 0.72f))
      Text(value, style = MaterialTheme.typography.titleSmall, color = DarkChocolateGold)
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
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var draft by remember { mutableStateOf(initialPlan) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = CarraraWhite,
    contentColor = CarraraInk,
    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp)
        .padding(bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Text("Next Day Planning Note", style = MaterialTheme.typography.titleLarge, color = CarraraInk)
      OutlinedTextField(
        value = draft,
        onValueChange = { draft = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Tomorrow's Plan") },
        minLines = 3,
      )
      Button(
        onClick = { onSave(draft) },
        modifier = Modifier
          .fillMaxWidth()
          .height(50.dp),
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(containerColor = DarkChocolateDeep),
      ) {
        Text("Save Plan", color = CarraraWhite)
      }
    }
  }
}
