package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.DateKeyHelper
import com.example.data.local.GoalEntity
import com.example.data.local.ProductivitySummary
import com.example.data.local.TaskEntity
import com.example.ui.components.CentralAiBannerCard
import com.example.ui.components.VisionTopBar
import com.example.ui.theme.LocalVisionPalette
import com.example.ui.theme.OliveMossAccent
import com.example.ui.theme.OliveMossDeep
import com.example.ui.theme.PlayfairDisplayFamily
import com.example.ui.theme.SandstoneInk
import com.example.ui.theme.WarmSandstone

@Composable
fun DashboardScreen(
  tasks: List<TaskEntity>,
  goals: List<GoalEntity>,
  productivity: ProductivitySummary,
  yogaStreakDays: Int,
  onToggleTask: (TaskEntity) -> Unit,
  onTogglePriority: (TaskEntity) -> Unit,
  onSaveTask: (TaskEntity) -> Unit,
  onDeleteTask: (Long) -> Unit,
  onSaveGoal: (GoalEntity) -> Unit,
  onIncrementGoal: (GoalEntity, Int) -> Unit,
  onDeleteGoal: (Long) -> Unit,
  onLogYoga: (Int, Boolean?) -> Unit,
  onLogSkill: (Int, Boolean?) -> Unit,
  onOpenAiSheet: () -> Unit,
  onOpenMenu: () -> Unit,
  onNavigateToReport: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalVisionPalette.current
  var showPendingOnly by remember { mutableStateOf(false) }
  var editingTask by remember { mutableStateOf<TaskEntity?>(null) }
  var showTaskSheet by remember { mutableStateOf(false) }
  var editingGoal by remember { mutableStateOf<GoalEntity?>(null) }
  var showGoalSheet by remember { mutableStateOf(false) }

  val priorityTasks = remember(tasks) { tasks.filter { it.isPriority } }
  val scheduleTasks = remember(tasks, showPendingOnly) {
    val base = if (showPendingOnly) tasks.filter { !it.isCompleted } else tasks
    base
  }
  val upcomingTasks = remember(tasks) { tasks.filter { !it.isCompleted && it.scheduledMinutesOfDay >= 0 } }
  val progressRatio = (productivity.productivityPercent / 100f).coerceIn(0f, 1f)

  Column(modifier = modifier.fillMaxSize()) {
    VisionTopBar(
      rightIcon = Icons.Filled.WorkspacePremium,
      rightContentDescription = "View productivity report",
      onMenuClick = onOpenMenu,
      onRightActionClick = onNavigateToReport,
    )

    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .testTag("dashboard_screen"),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 104.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item {
        Text(
          text = stringResource(id = R.string.vision_tagline),
          style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
          color = palette.onBackgroundMuted,
          textAlign = TextAlign.Center,
          modifier = Modifier
            .widthIn(max = 600.dp)
            .padding(horizontal = 12.dp),
        )
      }

      // Shared Central AI Hub Strip (Deep Teal + Polished Copper)
      item {
        CentralAiBannerCard(
          summaryText = "${productivity.completedTasks} of ${productivity.totalTasks} tasks complete · ${upcomingTasks.size} upcoming today",
          statusLabel = "${productivity.productivityPercent}% LIVE",
          onOpenAssistantSheet = onOpenAiSheet,
          contextHint = "ALFRED Shared Data Hub",
          modifier = Modifier.widthIn(max = 600.dp),
        )
      }

      // Daily Progress + Completed & Pending Tasks (Real Room Data)
      item {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Surface(
            modifier = Modifier
              .weight(1.15f)
              .testTag("daily_progress_card"),
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = "Daily Progress",
                  style = MaterialTheme.typography.titleSmall,
                  color = palette.onPrimaryCard,
                )
                Icon(
                  imageVector = Icons.Filled.CheckCircle,
                  contentDescription = "Daily progress",
                  tint = OliveMossAccent,
                  modifier = Modifier.size(22.dp),
                )
              }
              Text(
                text = "${productivity.productivityPercent}%",
                style = MaterialTheme.typography.displayLarge,
                color = palette.onPrimaryCard,
              )
              LinearProgressIndicator(
                progress = { progressRatio },
                modifier = Modifier
                  .fillMaxWidth()
                  .height(8.dp)
                  .clip(RoundedCornerShape(50)),
                color = OliveMossAccent,
                trackColor = palette.secondaryCard,
                strokeCap = StrokeCap.Round,
              )
            }
          }

          Surface(
            modifier = Modifier
              .weight(0.85f)
              .testTag("tasks_summary_card"),
            shape = RoundedCornerShape(22.dp),
            color = palette.primaryCard,
            border = BorderStroke(1.dp, palette.cardBorder),
          ) {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text(
                text = "Total Tasks",
                style = MaterialTheme.typography.titleSmall,
                color = palette.onPrimaryCard,
              )
              Text(
                text = "${productivity.totalTasks}",
                style = MaterialTheme.typography.displayLarge,
                color = palette.onPrimaryCard,
              )
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(
                  text = "Done: ${productivity.completedTasks}",
                  style = MaterialTheme.typography.bodySmall,
                  fontWeight = FontWeight.SemiBold,
                  color = OliveMossAccent,
                )
                Text(
                  text = "Pending: ${productivity.pendingTasks}",
                  style = MaterialTheme.typography.bodySmall,
                  color = palette.onPrimaryCardMuted,
                )
              }
            }
          }
        }
      }

      // Today's Priorities
      item {
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .testTag("todays_priorities_card"),
          shape = RoundedCornerShape(22.dp),
          color = palette.darkGlassCard,
          border = BorderStroke(1.dp, palette.darkCardBorder),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(
                  imageVector = Icons.Filled.TaskAlt,
                  contentDescription = "Today's Priorities",
                  tint = palette.accent,
                  modifier = Modifier.size(18.dp),
                )
                Text(
                  text = "Today's Priorities",
                  style = MaterialTheme.typography.titleMedium,
                  color = palette.onDarkCard,
                )
              }
              Text(
                text = "${priorityTasks.count { it.isCompleted }}/${priorityTasks.size} Done",
                style = MaterialTheme.typography.labelMedium,
                color = palette.onDarkCardMuted,
              )
            }

            if (priorityTasks.isEmpty()) {
              Text(
                text = "Tap the star on any task below to mark it as a priority.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.onDarkCardMuted,
              )
            } else {
              priorityTasks.forEach { item ->
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onToggleTask(item) }
                    .minimumInteractiveComponentSize()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                  ) {
                    Icon(
                      imageVector = if (item.isCompleted) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                      contentDescription = if (item.isCompleted) "Completed" else "Pending",
                      tint = if (item.isCompleted) palette.accent else palette.onDarkCardMuted,
                      modifier = Modifier.size(20.dp),
                    )
                    Text(
                      text = item.title,
                      style = MaterialTheme.typography.bodyMedium.copy(
                        textDecoration = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                      ),
                      color = if (item.isCompleted) palette.onDarkCardMuted else palette.onDarkCard,
                    )
                  }
                  Text(
                    text = item.scheduledTimeText.ifBlank { item.category },
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.onDarkCardMuted,
                  )
                }
              }
            }
          }
        }
      }

      // Today's Schedule, Routines & Task CRUD (Warm Sandstone)
      item {
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .testTag("todays_schedule_card"),
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
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column {
                Text(
                  text = "Today's Schedule & Tasks",
                  style = MaterialTheme.typography.titleMedium,
                  color = palette.onPrimaryCard,
                )
                Text(
                  text = "${upcomingTasks.size} upcoming · ${tasks.count { it.isRoutine }} daily routines",
                  style = MaterialTheme.typography.bodySmall,
                  color = palette.onPrimaryCardMuted,
                )
              }
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                  shape = RoundedCornerShape(50),
                  color = if (showPendingOnly) OliveMossAccent else palette.secondaryCard,
                  onClick = { showPendingOnly = !showPendingOnly },
                  modifier = Modifier.testTag("filter_pending_button"),
                ) {
                  Text(
                    text = if (showPendingOnly) "Pending" else "All",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (showPendingOnly) Color.White else palette.onPrimaryCard,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                  )
                }
                Surface(
                  shape = RoundedCornerShape(50),
                  color = OliveMossAccent,
                  onClick = {
                    editingTask = null
                    showTaskSheet = true
                  },
                  modifier = Modifier.testTag("add_task_button"),
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Filled.Add,
                      contentDescription = "Add Task",
                      tint = Color.White,
                      modifier = Modifier.size(14.dp),
                    )
                    Text(
                      text = "Add",
                      style = MaterialTheme.typography.labelSmall,
                      color = Color.White,
                    )
                  }
                }
              }
            }

            if (scheduleTasks.isEmpty()) {
              Text(
                text = if (showPendingOnly) "All scheduled tasks are completed!" else "No tasks yet. Tap Add to create your first task or daily routine.",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.onPrimaryCardMuted,
                modifier = Modifier.padding(vertical = 8.dp),
              )
            } else {
              scheduleTasks.forEach { entry ->
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                      if (!entry.isCompleted) palette.secondaryCard.copy(alpha = 0.55f)
                      else palette.primaryCard,
                    )
                    .clickable { onToggleTask(entry) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                  ) {
                    Icon(
                      imageVector = if (entry.isCompleted) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                      contentDescription = if (entry.isCompleted) "Completed" else "Pending",
                      tint = if (entry.isCompleted) OliveMossAccent else palette.onPrimaryCardMuted,
                      modifier = Modifier.size(22.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = entry.title,
                        style = MaterialTheme.typography.titleSmall.copy(
                          textDecoration = if (entry.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                        ),
                        color = palette.onPrimaryCard,
                      )
                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                      ) {
                        if (entry.scheduledTimeText.isNotBlank()) {
                          Icon(
                            imageVector = Icons.Filled.Schedule,
                            contentDescription = null,
                            tint = OliveMossAccent,
                            modifier = Modifier.size(12.dp),
                          )
                          Text(
                            text = entry.scheduledTimeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.onPrimaryCardMuted,
                          )
                        }
                        Text(
                          text = buildString {
                            append(entry.category)
                            if (entry.isRoutine) append(" · Routine")
                          },
                          style = MaterialTheme.typography.bodySmall,
                          color = palette.onPrimaryCardMuted,
                        )
                      }
                    }
                  }

                  Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                      onClick = { onTogglePriority(entry) },
                      modifier = Modifier.size(34.dp),
                    ) {
                      Icon(
                        imageVector = if (entry.isPriority) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = "Toggle Priority",
                        tint = if (entry.isPriority) Color(0xFFC47F2D) else palette.onPrimaryCardMuted,
                        modifier = Modifier.size(18.dp),
                      )
                    }
                    IconButton(
                      onClick = {
                        editingTask = entry
                        showTaskSheet = true
                      },
                      modifier = Modifier.size(34.dp),
                    ) {
                      Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Edit task",
                        tint = palette.onPrimaryCardMuted,
                        modifier = Modifier.size(17.dp),
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }

      // Morning Yoga & Exercise + Skill Development Tracking (Interactive Room Habit Logs)
      item {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Surface(
            modifier = Modifier
              .weight(1f)
              .testTag("morning_yoga_card"),
            shape = RoundedCornerShape(20.dp),
            color = palette.darkGlassCard,
            border = BorderStroke(1.dp, palette.darkCardBorder),
          ) {
            Column(
              modifier = Modifier.padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Box(
                  modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(palette.accent.copy(alpha = 0.25f)),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(
                    imageVector = Icons.Filled.SelfImprovement,
                    contentDescription = "Morning Yoga and Exercise",
                    tint = palette.onDarkCard,
                    modifier = Modifier.size(18.dp),
                  )
                }
                Text(
                  text = "Morning Yoga",
                  style = MaterialTheme.typography.titleSmall,
                  color = palette.onDarkCard,
                )
              }
              Text(
                text = "${productivity.yogaMinutesToday}m today · $yogaStreakDays total sessions",
                style = MaterialTheme.typography.bodySmall,
                color = palette.onDarkCardMuted,
              )
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                  shape = RoundedCornerShape(50),
                  color = OliveMossAccent,
                  onClick = { onLogYoga(15, true) },
                ) {
                  Text(
                    text = "+15m",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                  )
                }
                Surface(
                  shape = RoundedCornerShape(50),
                  color = if (productivity.yogaDoneToday) OliveMossAccent.copy(alpha = 0.3f) else palette.primaryCard.copy(alpha = 0.14f),
                  onClick = { onLogYoga(0, !productivity.yogaDoneToday) },
                ) {
                  Text(
                    text = if (productivity.yogaDoneToday) "Done" else "Mark Done",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.onDarkCard,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                  )
                }
              }
            }
          }

          Surface(
            modifier = Modifier
              .weight(1f)
              .testTag("skill_development_card"),
            shape = RoundedCornerShape(20.dp),
            color = palette.darkGlassCard,
            border = BorderStroke(1.dp, palette.darkCardBorder),
          ) {
            Column(
              modifier = Modifier.padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Box(
                  modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(palette.accent.copy(alpha = 0.25f)),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(
                    imageVector = Icons.Filled.Code,
                    contentDescription = "Skill Development",
                    tint = palette.onDarkCard,
                    modifier = Modifier.size(18.dp),
                  )
                }
                Text(
                  text = "Skill Dev",
                  style = MaterialTheme.typography.titleSmall,
                  color = palette.onDarkCard,
                )
              }
              Text(
                text = "${productivity.skillMinutesToday}m logged today",
                style = MaterialTheme.typography.bodySmall,
                color = palette.onDarkCardMuted,
              )
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                  shape = RoundedCornerShape(50),
                  color = OliveMossAccent,
                  onClick = { onLogSkill(30, true) },
                ) {
                  Text(
                    text = "+30m",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                  )
                }
                Surface(
                  shape = RoundedCornerShape(50),
                  color = if (productivity.skillDoneToday) OliveMossAccent.copy(alpha = 0.3f) else palette.primaryCard.copy(alpha = 0.14f),
                  onClick = { onLogSkill(0, !productivity.skillDoneToday) },
                ) {
                  Text(
                    text = if (productivity.skillDoneToday) "Done" else "Complete",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.onDarkCard,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                  )
                }
              }
            }
          }
        }
      }

      // Personal Goals (Create, Adjust Progress, Edit, Delete)
      item {
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .testTag("personal_goals_card"),
          shape = RoundedCornerShape(22.dp),
          color = palette.primaryCard,
          border = BorderStroke(1.dp, palette.cardBorder),
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
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(
                  imageVector = Icons.Filled.Flag,
                  contentDescription = "Personal Goals",
                  tint = OliveMossAccent,
                  modifier = Modifier.size(20.dp),
                )
                Text(
                  text = "Personal Goals",
                  fontFamily = PlayfairDisplayFamily,
                  style = MaterialTheme.typography.titleLarge,
                  color = palette.onPrimaryCard,
                )
              }

              Surface(
                shape = RoundedCornerShape(50),
                color = OliveMossAccent,
                onClick = {
                  editingGoal = null
                  showGoalSheet = true
                },
                modifier = Modifier.testTag("add_goal_button"),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Icon(Icons.Filled.Add, contentDescription = "Add Goal", tint = Color.White, modifier = Modifier.size(14.dp))
                  Text("New Goal", style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
              }
            }

            goals.forEach { goal ->
              val ratio = if (goal.targetValue > 0) {
                (goal.currentValue.toFloat() / goal.targetValue.toFloat()).coerceIn(0f, 1f)
              } else 0f
              Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Column(
                    modifier = Modifier
                      .weight(1f)
                      .clickable {
                        editingGoal = goal
                        showGoalSheet = true
                      },
                  ) {
                    Text(
                      text = goal.title,
                      style = MaterialTheme.typography.titleSmall,
                      color = palette.onPrimaryCard,
                    )
                    Text(
                      text = "${goal.currentValue}/${goal.targetValue} ${goal.unit} · ${goal.subtitle}",
                      style = MaterialTheme.typography.bodySmall,
                      color = palette.onPrimaryCardMuted,
                    )
                  }
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                  ) {
                    Surface(
                      shape = CircleShape,
                      color = palette.secondaryCard,
                      onClick = { onIncrementGoal(goal, -1) },
                      modifier = Modifier.size(28.dp),
                    ) {
                      Box(contentAlignment = Alignment.Center) {
                        Text("-", style = MaterialTheme.typography.titleSmall, color = palette.onPrimaryCard)
                      }
                    }
                    Surface(
                      shape = CircleShape,
                      color = OliveMossAccent,
                      onClick = { onIncrementGoal(goal, 1) },
                      modifier = Modifier.size(28.dp),
                    ) {
                      Box(contentAlignment = Alignment.Center) {
                        Text("+", style = MaterialTheme.typography.titleSmall, color = Color.White)
                      }
                    }
                  }
                }
                LinearProgressIndicator(
                  progress = { ratio },
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(50)),
                  color = OliveMossAccent,
                  trackColor = palette.secondaryCard,
                  strokeCap = StrokeCap.Round,
                )
              }
            }
          }
        }
      }
    }
  }

  if (showTaskSheet) {
    TaskEditorSheet(
      initialTask = editingTask,
      onSave = { saved ->
        onSaveTask(saved)
        showTaskSheet = false
      },
      onDelete = { id ->
        onDeleteTask(id)
        showTaskSheet = false
      },
      onDismiss = { showTaskSheet = false },
    )
  }

  if (showGoalSheet) {
    GoalEditorSheet(
      initialGoal = editingGoal,
      onSave = { saved ->
        onSaveGoal(saved)
        showGoalSheet = false
      },
      onDelete = { id ->
        onDeleteGoal(id)
        showGoalSheet = false
      },
      onDismiss = { showGoalSheet = false },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorSheet(
  initialTask: TaskEntity?,
  defaultDateKey: String = DateKeyHelper.todayKey(),
  onSave: (TaskEntity) -> Unit,
  onDelete: (Long) -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var title by remember { mutableStateOf(initialTask?.title.orEmpty()) }
  var category by remember { mutableStateOf(initialTask?.category ?: "Skill Dev") }
  var hour24 by remember {
    mutableIntStateOf(
      if (initialTask != null && initialTask.scheduledMinutesOfDay >= 0) initialTask.scheduledMinutesOfDay / 60 else 9,
    )
  }
  var minute by remember {
    mutableIntStateOf(
      if (initialTask != null && initialTask.scheduledMinutesOfDay >= 0) initialTask.scheduledMinutesOfDay % 60 else 0,
    )
  }
  var hasScheduleTime by remember { mutableStateOf(initialTask == null || initialTask.scheduledMinutesOfDay >= 0) }
  var isPriority by remember { mutableStateOf(initialTask?.isPriority ?: false) }
  var isRoutine by remember { mutableStateOf(initialTask?.isRoutine ?: false) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = WarmSandstone,
    contentColor = SandstoneInk,
    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp)
        .padding(bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = if (initialTask == null) "Create Task or Routine" else "Edit Task",
        style = MaterialTheme.typography.titleLarge,
        color = SandstoneInk,
      )

      OutlinedTextField(
        value = title,
        onValueChange = { title = it },
        label = { Text("Task Title") },
        singleLine = true,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("task_title_input"),
      )

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Wellness", "Skill Dev", "Study", "Personal").forEach { cat ->
          val active = category == cat
          Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(10.dp),
            color = if (active) OliveMossAccent else Color(0xFFE1D1B6),
            onClick = { category = cat },
          ) {
            Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
              Text(cat, style = MaterialTheme.typography.labelSmall, color = if (active) Color.White else SandstoneInk)
            }
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(checked = hasScheduleTime, onCheckedChange = { hasScheduleTime = it })
          Text("Schedule Time", style = MaterialTheme.typography.bodyMedium)
        }
        if (hasScheduleTime) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { hour24 = (hour24 + 23) % 24 }) { Text("-1h") }
            Text(DateKeyHelper.formatFullTime12h(hour24, minute), style = MaterialTheme.typography.titleSmall)
            OutlinedButton(onClick = { hour24 = (hour24 + 1) % 24 }) { Text("+1h") }
            OutlinedButton(onClick = { minute = (minute + 15) % 60 }) { Text("+15m") }
          }
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(checked = isPriority, onCheckedChange = { isPriority = it })
          Text("Today's Priority", style = MaterialTheme.typography.bodyMedium)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(checked = isRoutine, onCheckedChange = { isRoutine = it })
          Text("Daily Routine", style = MaterialTheme.typography.bodyMedium)
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (initialTask != null) {
          OutlinedButton(
            onClick = { onDelete(initialTask.id) },
            modifier = Modifier.height(48.dp),
          ) {
            Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete task", tint = Color(0xFFA93226))
            Spacer(Modifier.size(4.dp))
            Text("Delete", color = Color(0xFFA93226))
          }
        }
        Button(
          onClick = {
            if (title.isNotBlank()) {
              val mins = if (hasScheduleTime) hour24 * 60 + minute else -1
              val timeText = if (hasScheduleTime) DateKeyHelper.formatFullTime12h(hour24, minute) else ""
              onSave(
                (initialTask ?: TaskEntity(
                  title = title.trim(),
                  category = category,
                  scheduledTimeText = timeText,
                  scheduledMinutesOfDay = mins,
                  isPriority = isPriority,
                  isRoutine = isRoutine,
                  isCompleted = false,
                  dateKey = defaultDateKey,
                )).copy(
                  title = title.trim(),
                  category = category,
                  scheduledTimeText = timeText,
                  scheduledMinutesOfDay = mins,
                  isPriority = isPriority,
                  isRoutine = isRoutine,
                ),
              )
            }
          },
          enabled = title.isNotBlank(),
          modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .testTag("save_task_button"),
          colors = ButtonDefaults.buttonColors(containerColor = OliveMossDeep),
        ) {
          Text("Save Task", color = Color.White)
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalEditorSheet(
  initialGoal: GoalEntity?,
  onSave: (GoalEntity) -> Unit,
  onDelete: (Long) -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var title by remember { mutableStateOf(initialGoal?.title.orEmpty()) }
  var subtitle by remember { mutableStateOf(initialGoal?.subtitle.orEmpty()) }
  var target by remember { mutableIntStateOf(initialGoal?.targetValue ?: 10) }
  var unit by remember { mutableStateOf(initialGoal?.unit ?: "steps") }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = WarmSandstone,
    contentColor = SandstoneInk,
    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp)
        .padding(bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = if (initialGoal == null) "Create Personal Goal" else "Edit Personal Goal",
        style = MaterialTheme.typography.titleLarge,
        color = SandstoneInk,
      )
      OutlinedTextField(
        value = title,
        onValueChange = { title = it },
        label = { Text("Goal Title") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
      OutlinedTextField(
        value = subtitle,
        onValueChange = { subtitle = it },
        label = { Text("Focus Milestone / Notes") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text("Target: $target $unit", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          OutlinedButton(onClick = { target = (target - 1).coerceAtLeast(1) }) { Text("-1") }
          OutlinedButton(onClick = { target += 1 }) { Text("+1") }
          OutlinedButton(onClick = {
            unit = when (unit) {
              "steps" -> "modules"
              "modules" -> "days"
              else -> "steps"
            }
          }) { Text(unit) }
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (initialGoal != null) {
          OutlinedButton(onClick = { onDelete(initialGoal.id) }, modifier = Modifier.height(48.dp)) {
            Text("Delete", color = Color(0xFFA93226))
          }
        }
        Button(
          onClick = {
            if (title.isNotBlank()) {
              onSave(
                (initialGoal ?: GoalEntity(
                  title = title.trim(),
                  subtitle = subtitle.trim().ifEmpty { "Personal Milestone" },
                  currentValue = 0,
                  targetValue = target,
                  unit = unit,
                  category = "Personal",
                )).copy(
                  title = title.trim(),
                  subtitle = subtitle.trim().ifEmpty { "Personal Milestone" },
                  targetValue = target,
                  unit = unit,
                ),
              )
            }
          },
          enabled = title.isNotBlank(),
          modifier = Modifier
            .weight(1f)
            .height(48.dp),
          colors = ButtonDefaults.buttonColors(containerColor = OliveMossDeep),
        ) {
          Text("Save Goal", color = Color.White)
        }
      }
    }
  }
}
