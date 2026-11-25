package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.ActivitySessionEntity
import com.example.data.local.DateKeyHelper
import com.example.data.local.GoalEntity
import com.example.data.local.HabitLogEntity
import com.example.data.local.ProductivitySummary
import com.example.data.local.TaskEntity
import com.example.ui.ActivityTimerUiState
import com.example.ui.components.ActivitySessionEditorSheet
import com.example.ui.components.CentralAiBannerCard
import com.example.ui.components.SKILL_SUGGESTION_PRESETS
import com.example.ui.components.VisionTopBar
import com.example.ui.theme.LocalVisionPalette
import com.example.ui.theme.OliveMossAccent
import com.example.ui.theme.OliveMossDeep
import com.example.ui.theme.PlayfairDisplayFamily
import com.example.ui.theme.VisionDangerRed
import com.example.ui.theme.visionPrimaryCardCheckboxColors
import com.example.ui.theme.visionPrimaryCardFieldColors

@Composable
fun DashboardScreen(
  tasks: List<TaskEntity>,
  goals: List<GoalEntity>,
  todayHabits: List<HabitLogEntity>,
  todayActivitySessions: List<ActivitySessionEntity>,
  productivity: ProductivitySummary,
  yogaStreakDays: Int,
  yogaTimer: ActivityTimerUiState,
  skillTimer: ActivityTimerUiState,
  onToggleTask: (TaskEntity) -> Unit,
  onSaveTask: (TaskEntity) -> Unit,
  onDeleteTask: (Long) -> Unit,
  onSaveGoal: (GoalEntity) -> Unit,
  onIncrementGoal: (GoalEntity, Int) -> Unit,
  onDeleteGoal: (Long) -> Unit,
  onStartYogaTimer: (String, String) -> Unit,
  onPauseYogaTimer: () -> Unit,
  onResumeYogaTimer: () -> Unit,
  onFinishYogaTimer: (String?) -> Unit,
  onStartSkillTimer: (String, String) -> Unit,
  onPauseSkillTimer: () -> Unit,
  onResumeSkillTimer: () -> Unit,
  onFinishSkillTimer: (String?) -> Unit,
  onSaveActivitySession: (ActivitySessionEntity) -> Unit,
  onDeleteActivitySession: (Long) -> Unit,
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

  var activityEditorTarget by remember { mutableStateOf<Pair<ActivitySessionEntity?, String>?>(null) }
  var deleteSessionConfirm by remember { mutableStateOf<ActivitySessionEntity?>(null) }

  val scheduleTasks = remember(tasks, showPendingOnly) {
    if (showPendingOnly) tasks.filter { !it.isCompleted } else tasks
  }
  val upcomingTasks = remember(tasks) { tasks.filter { !it.isCompleted && it.scheduledMinutesOfDay >= 0 } }
  val progressRatio = (productivity.productivityPercent / 100f).coerceIn(0f, 1f)

  val yogaHabit = remember(todayHabits) { todayHabits.firstOrNull { it.trackType == "YOGA_EXERCISE" } }
  val skillHabit = remember(todayHabits) { todayHabits.firstOrNull { it.trackType == "SKILL_DEV" } }
  val yogaSessionsToday = remember(todayActivitySessions) {
    todayActivitySessions.filter { it.activityType == "YOGA" }
  }
  val skillSessionsToday = remember(todayActivitySessions) {
    todayActivitySessions.filter { it.activityType == "SKILL" }
  }

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
                Text(
                  text = "Daily Progress",
                  style = MaterialTheme.typography.titleSmall,
                  color = palette.onPrimaryCard,
                )
                Icon(
                  imageVector = Icons.Filled.CheckCircle,
                  contentDescription = "Daily progress",
                  tint = palette.accentOnPrimaryCard,
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
                color = palette.accentOnPrimaryCard,
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
            contentColor = palette.onPrimaryCard,
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
                  color = palette.accentOnPrimaryCard,
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

      // Today's Schedule, Routines & Task CRUD
      item {
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .testTag("todays_schedule_card"),
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
                  border = BorderStroke(1.dp, palette.cardBorder),
                  onClick = { showPendingOnly = !showPendingOnly },
                  modifier = Modifier.testTag("filter_pending_button"),
                ) {
                  Text(
                    text = if (showPendingOnly) "Pending" else "All",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (showPendingOnly) Color.White else palette.onPrimaryCard,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
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
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
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
                text = if (showPendingOnly) "All scheduled tasks are completed!"
                else "No tasks yet. Tap Add to create your first task or daily routine.",
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
                      if (!entry.isCompleted) palette.secondaryCard.copy(alpha = 0.72f)
                      else palette.primaryCard,
                    )
                    .clickable { onToggleTask(entry) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                  ) {
                    Icon(
                      imageVector = if (entry.isCompleted) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                      contentDescription = if (entry.isCompleted) "Completed" else "Pending",
                      tint = if (entry.isCompleted) palette.accentOnPrimaryCard else palette.onPrimaryCardMuted,
                      modifier = Modifier.size(22.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = entry.title,
                        style = MaterialTheme.typography.titleSmall.copy(
                          textDecoration = if (entry.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                        ),
                        color = if (entry.isCompleted) palette.onPrimaryCardMuted else palette.onPrimaryCard,
                      )
                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                      ) {
                        if (entry.scheduledTimeText.isNotBlank()) {
                          Icon(
                            imageVector = Icons.Filled.Schedule,
                            contentDescription = null,
                            tint = palette.accentOnPrimaryCard,
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

                  IconButton(
                    onClick = {
                      editingTask = entry
                      showTaskSheet = true
                    },
                    modifier = Modifier.minimumInteractiveComponentSize(),
                  ) {
                    Icon(
                      imageVector = Icons.Filled.Edit,
                      contentDescription = "Edit task",
                      tint = palette.onPrimaryCard,
                      modifier = Modifier.size(18.dp),
                    )
                  }
                }
              }
            }
          }
        }
      }

      // Morning Yoga & Exercise — Real Timer + Saved Session Records
      item {
        MorningYogaSessionCard(
          loggedMinutes = productivity.yogaMinutesToday,
          targetMinutes = 30,
          streakDays = yogaStreakDays,
          timer = yogaTimer,
          savedSessions = yogaSessionsToday,
          fallbackNotes = yogaHabit?.notes.orEmpty(),
          onStart = { notes -> onStartYogaTimer("Morning Yoga & Exercise", notes) },
          onPause = onPauseYogaTimer,
          onResume = onResumeYogaTimer,
          onFinish = onFinishYogaTimer,
          onLogManual = { activityEditorTarget = null to "YOGA" },
          onEditSession = { session -> activityEditorTarget = session to "YOGA" },
          onDeleteSession = { session -> deleteSessionConfirm = session },
          modifier = Modifier.widthIn(max = 600.dp),
        )
      }

      // Skill Development — Selected Skill + Real Timer + Completed Sessions Today
      item {
        SkillDevelopmentSessionCard(
          loggedMinutes = productivity.skillMinutesToday,
          targetMinutes = 60,
          timer = skillTimer,
          savedSessions = skillSessionsToday,
          fallbackNotes = skillHabit?.notes.orEmpty(),
          onStart = { skillName, notes -> onStartSkillTimer(skillName, notes) },
          onPause = onPauseSkillTimer,
          onResume = onResumeSkillTimer,
          onFinish = onFinishSkillTimer,
          onLogManual = { activityEditorTarget = null to "SKILL" },
          onEditSession = { session -> activityEditorTarget = session to "SKILL" },
          onDeleteSession = { session -> deleteSessionConfirm = session },
          modifier = Modifier.widthIn(max = 600.dp),
        )
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
          contentColor = palette.onPrimaryCard,
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
                  tint = palette.accentOnPrimaryCard,
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
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Icon(Icons.Filled.Add, contentDescription = "Add Goal", tint = Color.White, modifier = Modifier.size(14.dp))
                  Text("New Goal", style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
              }
            }

            if (goals.isEmpty()) {
              Text(
                text = "No personal goals added yet. Tap New Goal to set a milestone.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.onPrimaryCardMuted,
              )
            } else {
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
                      horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                      Surface(
                        shape = CircleShape,
                        color = palette.secondaryCard,
                        border = BorderStroke(1.dp, palette.cardBorder),
                        onClick = { onIncrementGoal(goal, -1) },
                        modifier = Modifier.size(34.dp),
                      ) {
                        Box(contentAlignment = Alignment.Center) {
                          Text("-", style = MaterialTheme.typography.titleSmall, color = palette.onPrimaryCard)
                        }
                      }
                      Surface(
                        shape = CircleShape,
                        color = OliveMossAccent,
                        onClick = { onIncrementGoal(goal, 1) },
                        modifier = Modifier.size(34.dp),
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
                    color = palette.accentOnPrimaryCard,
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

  activityEditorTarget?.let { (session, defaultType) ->
    ActivitySessionEditorSheet(
      initialSession = session,
      defaultType = session?.activityType ?: defaultType,
      allowCategorySwitch = false,
      onSave = { saved ->
        onSaveActivitySession(saved)
        activityEditorTarget = null
      },
      onDelete = { id ->
        onDeleteActivitySession(id)
        activityEditorTarget = null
      },
      onDismiss = { activityEditorTarget = null },
    )
  }

  deleteSessionConfirm?.let { session ->
    AlertDialog(
      onDismissRequest = { deleteSessionConfirm = null },
      title = { Text("Delete Session?") },
      text = {
        Text("Remove \"${session.titleOrSkillName}\" (${session.durationMinutes}m) from today's records?")
      },
      confirmButton = {
        Button(
          onClick = {
            onDeleteActivitySession(session.id)
            deleteSessionConfirm = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = VisionDangerRed),
        ) {
          Text("Delete", color = Color.White)
        }
      },
      dismissButton = {
        TextButton(onClick = { deleteSessionConfirm = null }) {
          Text("Cancel")
        }
      },
    )
  }
}

@Composable
private fun MorningYogaSessionCard(
  loggedMinutes: Int,
  targetMinutes: Int,
  streakDays: Int,
  timer: ActivityTimerUiState,
  savedSessions: List<ActivitySessionEntity>,
  fallbackNotes: String,
  onStart: (String) -> Unit,
  onPause: () -> Unit,
  onResume: () -> Unit,
  onFinish: (String?) -> Unit,
  onLogManual: () -> Unit,
  onEditSession: (ActivitySessionEntity) -> Unit,
  onDeleteSession: (ActivitySessionEntity) -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalVisionPalette.current
  var notesDraft by remember(timer.isActive, fallbackNotes) {
    mutableStateOf(if (timer.isActive) timer.notes else fallbackNotes)
  }
  val ratio = (loggedMinutes.toFloat() / targetMinutes.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
  val fieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = palette.onDarkCard,
    unfocusedTextColor = palette.onDarkCard,
    focusedLabelColor = palette.accentOnDarkCard,
    unfocusedLabelColor = palette.onDarkCardMuted,
    focusedPlaceholderColor = palette.onDarkCardMuted,
    unfocusedPlaceholderColor = palette.onDarkCardMuted,
    focusedBorderColor = palette.accentOnDarkCard,
    unfocusedBorderColor = palette.darkCardBorder,
    cursorColor = palette.accentOnDarkCard,
  )

  Surface(
    modifier = modifier
      .fillMaxWidth()
      .testTag("morning_yoga_card"),
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
          modifier = Modifier.weight(1f).padding(end = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Box(
            modifier = Modifier
              .size(40.dp)
              .clip(CircleShape)
              .background(palette.accent.copy(alpha = 0.24f)),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Filled.SelfImprovement,
              contentDescription = "Morning Yoga & Exercise",
              tint = palette.accentOnDarkCard,
              modifier = Modifier.size(22.dp),
            )
          }
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Morning Yoga & Exercise",
              style = MaterialTheme.typography.titleMedium,
              color = palette.onDarkCard,
            )
            Text(
              text = "${DateKeyHelper.formatMinutesSummary(loggedMinutes)} today · $streakDays active day(s)",
              style = MaterialTheme.typography.bodySmall,
              color = palette.onDarkCardMuted,
            )
          }
        }

        Surface(
          shape = RoundedCornerShape(50),
          color = when {
            timer.isRunning -> OliveMossAccent
            timer.isActive -> Color(0xFFB36B00)
            loggedMinutes > 0 -> OliveMossAccent
            else -> palette.secondaryCard.copy(alpha = if (palette.isDarkMode) 0.2f else 0.7f)
          },
          border = BorderStroke(1.dp, if (timer.isActive || loggedMinutes > 0) OliveMossAccent else palette.darkCardBorder),
        ) {
          Text(
            text = when {
              timer.isRunning -> "Running · ${DateKeyHelper.formatElapsedClock(timer.elapsedSeconds)}"
              timer.isActive -> "Paused · ${DateKeyHelper.formatElapsedClock(timer.elapsedSeconds)}"
              loggedMinutes > 0 -> "${loggedMinutes}m Logged"
              else -> "0/${targetMinutes}m"
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (timer.isActive || loggedMinutes > 0) Color.White else palette.onDarkCard,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
          )
        }
      }

      LinearProgressIndicator(
        progress = { ratio },
        modifier = Modifier
          .fillMaxWidth()
          .height(7.dp)
          .clip(RoundedCornerShape(50)),
        color = palette.accentOnDarkCard,
        trackColor = palette.onDarkCard.copy(alpha = 0.16f),
        strokeCap = StrokeCap.Round,
      )

      Text(
        text = "Guided practice: 10m Surya Namaskar · 10m deep stretching · 5m Pranayama breathing.",
        style = MaterialTheme.typography.bodySmall,
        color = palette.onDarkCardMuted,
      )

      if (timer.isActive) {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = palette.onDarkCard.copy(alpha = 0.08f),
          border = BorderStroke(1.dp, palette.accentOnDarkCard.copy(alpha = 0.55f)),
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = if (timer.isRunning) "Yoga Session Active" else "Yoga Session Paused",
                style = MaterialTheme.typography.labelLarge,
                color = palette.accentOnDarkCard,
              )
              Text(
                text = DateKeyHelper.formatElapsedClock(timer.elapsedSeconds),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = palette.onDarkCard,
                modifier = Modifier.testTag("yoga_timer_clock"),
              )
            }

            OutlinedTextField(
              value = notesDraft,
              onValueChange = { notesDraft = it },
              label = { Text("Practice notes (optional)") },
              placeholder = { Text("e.g., Surya Namaskar & pranayama") },
              colors = fieldColors,
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (!timer.isActive) {
          Button(
            onClick = { onStart(notesDraft) },
            colors = ButtonDefaults.buttonColors(containerColor = OliveMossAccent),
            shape = RoundedCornerShape(50),
            modifier = Modifier
              .weight(1f)
              .height(48.dp)
              .testTag("start_yoga_button"),
          ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text("Start Yoga", color = Color.White, style = MaterialTheme.typography.labelLarge)
          }
          OutlinedButton(
            onClick = onLogManual,
            border = BorderStroke(1.dp, palette.darkCardBorder),
            shape = RoundedCornerShape(50),
            modifier = Modifier
              .height(48.dp)
              .testTag("log_yoga_manual_button"),
          ) {
            Icon(Icons.Filled.Add, contentDescription = "Log Yoga manually", tint = palette.accentOnDarkCard, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(4.dp))
            Text("Log", color = palette.onDarkCard)
          }
        } else {
          if (timer.isRunning) {
            OutlinedButton(
              onClick = onPause,
              border = BorderStroke(1.dp, palette.accentOnDarkCard),
              shape = RoundedCornerShape(50),
              modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("pause_yoga_button"),
            ) {
              Icon(Icons.Filled.Pause, contentDescription = null, tint = palette.onDarkCard, modifier = Modifier.size(18.dp))
              Spacer(Modifier.size(6.dp))
              Text("Pause", color = palette.onDarkCard)
            }
          } else {
            Button(
              onClick = onResume,
              colors = ButtonDefaults.buttonColors(containerColor = OliveMossAccent),
              shape = RoundedCornerShape(50),
              modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("resume_yoga_button"),
            ) {
              Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
              Spacer(Modifier.size(6.dp))
              Text("Resume", color = Color.White)
            }
          }

          Button(
            onClick = { onFinish(notesDraft) },
            colors = ButtonDefaults.buttonColors(containerColor = OliveMossDeep),
            shape = RoundedCornerShape(50),
            modifier = Modifier
              .weight(1f)
              .height(48.dp)
              .testTag("finish_yoga_button"),
          ) {
            Icon(Icons.Filled.Stop, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text("Finish Yoga", color = Color.White)
          }
        }
      }

      if (savedSessions.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = "Today's Yoga Sessions (${savedSessions.size})",
            style = MaterialTheme.typography.labelMedium,
            color = palette.accentOnDarkCard,
          )
          savedSessions.forEach { session ->
            SavedActivityMiniRow(
              session = session,
              onEdit = { onEditSession(session) },
              onDelete = { onDeleteSession(session) },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun SkillDevelopmentSessionCard(
  loggedMinutes: Int,
  targetMinutes: Int,
  timer: ActivityTimerUiState,
  savedSessions: List<ActivitySessionEntity>,
  fallbackNotes: String,
  onStart: (String, String) -> Unit,
  onPause: () -> Unit,
  onResume: () -> Unit,
  onFinish: (String?) -> Unit,
  onLogManual: () -> Unit,
  onEditSession: (ActivitySessionEntity) -> Unit,
  onDeleteSession: (ActivitySessionEntity) -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalVisionPalette.current
  var skillInput by remember(timer.isActive, timer.titleOrSkillName) {
    mutableStateOf(
      if (timer.isActive && timer.titleOrSkillName.isNotBlank()) timer.titleOrSkillName
      else "Web Development",
    )
  }
  var skillError by remember { mutableStateOf<String?>(null) }
  var notesDraft by remember(timer.isActive, fallbackNotes) {
    mutableStateOf(if (timer.isActive) timer.notes else "")
  }
  val ratio = (loggedMinutes.toFloat() / targetMinutes.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
  val fieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = palette.onDarkCard,
    unfocusedTextColor = palette.onDarkCard,
    focusedLabelColor = palette.accentOnDarkCard,
    unfocusedLabelColor = palette.onDarkCardMuted,
    focusedPlaceholderColor = palette.onDarkCardMuted,
    unfocusedPlaceholderColor = palette.onDarkCardMuted,
    focusedBorderColor = palette.accentOnDarkCard,
    unfocusedBorderColor = palette.darkCardBorder,
    cursorColor = palette.accentOnDarkCard,
    errorTextColor = palette.onDarkCard,
    errorBorderColor = Color(0xFFFF8A80),
    errorLabelColor = Color(0xFFFF8A80),
  )

  Surface(
    modifier = modifier
      .fillMaxWidth()
      .testTag("skill_development_card"),
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
          modifier = Modifier.weight(1f).padding(end = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Box(
            modifier = Modifier
              .size(40.dp)
              .clip(CircleShape)
              .background(palette.accent.copy(alpha = 0.24f)),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Filled.Code,
              contentDescription = "Skill Development",
              tint = palette.accentOnDarkCard,
              modifier = Modifier.size(22.dp),
            )
          }
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Skill Development",
              style = MaterialTheme.typography.titleMedium,
              color = palette.onDarkCard,
            )
            Text(
              text = "${DateKeyHelper.formatMinutesSummary(loggedMinutes)} logged today · Select skill & track live",
              style = MaterialTheme.typography.bodySmall,
              color = palette.onDarkCardMuted,
            )
          }
        }

        Surface(
          shape = RoundedCornerShape(50),
          color = when {
            timer.isRunning -> OliveMossAccent
            timer.isActive -> Color(0xFFB36B00)
            loggedMinutes > 0 -> OliveMossAccent
            else -> palette.secondaryCard.copy(alpha = if (palette.isDarkMode) 0.2f else 0.7f)
          },
          border = BorderStroke(1.dp, if (timer.isActive || loggedMinutes > 0) OliveMossAccent else palette.darkCardBorder),
        ) {
          Text(
            text = when {
              timer.isRunning -> "Active · ${DateKeyHelper.formatElapsedClock(timer.elapsedSeconds)}"
              timer.isActive -> "Paused · ${DateKeyHelper.formatElapsedClock(timer.elapsedSeconds)}"
              loggedMinutes > 0 -> "${loggedMinutes}m / ${targetMinutes}m"
              else -> "0/${targetMinutes}m"
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (timer.isActive || loggedMinutes > 0) Color.White else palette.onDarkCard,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
          )
        }
      }

      LinearProgressIndicator(
        progress = { ratio },
        modifier = Modifier
          .fillMaxWidth()
          .height(7.dp)
          .clip(RoundedCornerShape(50)),
        color = palette.accentOnDarkCard,
        trackColor = palette.onDarkCard.copy(alpha = 0.16f),
        strokeCap = StrokeCap.Round,
      )

      if (!timer.isActive) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          SKILL_SUGGESTION_PRESETS.forEach { preset ->
            val picked = skillInput.equals(preset, ignoreCase = true)
            Surface(
              shape = RoundedCornerShape(50),
              color = if (picked) OliveMossAccent else palette.onDarkCard.copy(alpha = 0.10f),
              border = BorderStroke(1.dp, if (picked) OliveMossAccent else palette.darkCardBorder),
              onClick = {
                skillInput = preset
                skillError = null
              },
            ) {
              Text(
                text = preset,
                style = MaterialTheme.typography.labelSmall,
                color = if (picked) Color.White else palette.onDarkCard,
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
              )
            }
          }
        }

        OutlinedTextField(
          value = skillInput,
          onValueChange = {
            skillInput = it
            if (skillError != null && it.isNotBlank()) skillError = null
          },
          label = { Text("Skill You Are Learning") },
          placeholder = { Text("e.g., Web Development, CPPM, Kotlin") },
          isError = skillError != null,
          supportingText = skillError?.let { msg -> { Text(msg, color = Color(0xFFFF8A80)) } },
          singleLine = true,
          colors = fieldColors,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("skill_name_input"),
        )
      } else {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = palette.onDarkCard.copy(alpha = 0.08f),
          border = BorderStroke(1.dp, palette.accentOnDarkCard.copy(alpha = 0.55f)),
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = if (timer.isRunning) "Learning Now" else "Session Paused",
                  style = MaterialTheme.typography.labelSmall,
                  color = palette.accentOnDarkCard,
                )
                Text(
                  text = timer.titleOrSkillName.ifBlank { "Skill Practice" },
                  style = MaterialTheme.typography.titleMedium,
                  color = palette.onDarkCard,
                )
              }
              Text(
                text = DateKeyHelper.formatElapsedClock(timer.elapsedSeconds),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = palette.onDarkCard,
                modifier = Modifier.testTag("skill_timer_clock"),
              )
            }

            OutlinedTextField(
              value = notesDraft,
              onValueChange = { notesDraft = it },
              label = { Text("Learning Notes (optional)") },
              placeholder = { Text("What topic or module are you practicing?") },
              colors = fieldColors,
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (!timer.isActive) {
          Button(
            onClick = {
              val clean = skillInput.trim()
              if (clean.isEmpty()) {
                skillError = "Please select or enter a skill name first."
                return@Button
              }
              onStart(clean, notesDraft)
            },
            colors = ButtonDefaults.buttonColors(containerColor = OliveMossAccent),
            shape = RoundedCornerShape(50),
            modifier = Modifier
              .weight(1f)
              .height(48.dp)
              .testTag("start_skill_button"),
          ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text("Start Learning", color = Color.White, style = MaterialTheme.typography.labelLarge)
          }

          OutlinedButton(
            onClick = onLogManual,
            border = BorderStroke(1.dp, palette.darkCardBorder),
            shape = RoundedCornerShape(50),
            modifier = Modifier
              .height(48.dp)
              .testTag("log_skill_manual_button"),
          ) {
            Icon(Icons.Filled.Add, contentDescription = "Log Skill manually", tint = palette.accentOnDarkCard, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(4.dp))
            Text("Log", color = palette.onDarkCard)
          }
        } else {
          if (timer.isRunning) {
            OutlinedButton(
              onClick = onPause,
              border = BorderStroke(1.dp, palette.accentOnDarkCard),
              shape = RoundedCornerShape(50),
              modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("pause_skill_button"),
            ) {
              Icon(Icons.Filled.Pause, contentDescription = null, tint = palette.onDarkCard, modifier = Modifier.size(18.dp))
              Spacer(Modifier.size(6.dp))
              Text("Pause", color = palette.onDarkCard)
            }
          } else {
            Button(
              onClick = onResume,
              colors = ButtonDefaults.buttonColors(containerColor = OliveMossAccent),
              shape = RoundedCornerShape(50),
              modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("resume_skill_button"),
            ) {
              Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
              Spacer(Modifier.size(6.dp))
              Text("Resume", color = Color.White)
            }
          }

          Button(
            onClick = { onFinish(notesDraft) },
            colors = ButtonDefaults.buttonColors(containerColor = OliveMossDeep),
            shape = RoundedCornerShape(50),
            modifier = Modifier
              .weight(1f)
              .height(48.dp)
              .testTag("finish_skill_button"),
          ) {
            Icon(Icons.Filled.Stop, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text("Finish", color = Color.White)
          }
        }
      }

      if (savedSessions.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = "Completed Skill Sessions Today (${savedSessions.size})",
            style = MaterialTheme.typography.labelMedium,
            color = palette.accentOnDarkCard,
          )
          savedSessions.forEach { session ->
            SavedActivityMiniRow(
              session = session,
              onEdit = { onEditSession(session) },
              onDelete = { onDeleteSession(session) },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun SavedActivityMiniRow(
  session: ActivitySessionEntity,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
) {
  val palette = LocalVisionPalette.current
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
          text = "${session.titleOrSkillName} · ${session.durationMinutes}m",
          style = MaterialTheme.typography.labelLarge,
          color = palette.onDarkCard,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = buildString {
            append("${DateKeyHelper.formatMillisTime12h(session.startTimeMillis)} – ${DateKeyHelper.formatMillisTime12h(session.endTimeMillis)}")
            if (session.notes.isNotBlank()) append(" · ${session.notes}")
          },
          style = MaterialTheme.typography.bodySmall,
          color = palette.onDarkCardMuted,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
          onClick = onEdit,
          modifier = Modifier.minimumInteractiveComponentSize(),
        ) {
          Icon(
            imageVector = Icons.Filled.Edit,
            contentDescription = "Edit ${session.titleOrSkillName}",
            tint = palette.accentOnDarkCard,
            modifier = Modifier.size(18.dp),
          )
        }
        IconButton(
          onClick = onDelete,
          modifier = Modifier.minimumInteractiveComponentSize(),
        ) {
          Icon(
            imageVector = Icons.Filled.DeleteOutline,
            contentDescription = "Delete ${session.titleOrSkillName}",
            tint = if (palette.isDarkMode) Color(0xFFFF8A80) else VisionDangerRed,
            modifier = Modifier.size(18.dp),
          )
        }
      }
    }
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
  val palette = LocalVisionPalette.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var title by remember { mutableStateOf(initialTask?.title.orEmpty()) }
  var titleError by remember { mutableStateOf<String?>(null) }
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
  var isRoutine by remember { mutableStateOf(initialTask?.isRoutine ?: false) }

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
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = if (initialTask == null) "Create Task or Routine" else "Edit Task",
        style = MaterialTheme.typography.titleLarge,
        color = palette.onPrimaryCard,
      )

      OutlinedTextField(
        value = title,
        onValueChange = {
          title = it
          if (titleError != null && it.isNotBlank()) titleError = null
        },
        label = { Text("Task Title") },
        isError = titleError != null,
        supportingText = titleError?.let { msg ->
          { Text(msg, color = VisionDangerRed) }
        },
        colors = visionPrimaryCardFieldColors(palette),
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
            color = if (active) OliveMossAccent else palette.secondaryCard,
            border = BorderStroke(1.dp, palette.cardBorder),
            onClick = { category = cat },
          ) {
            Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
              Text(cat, style = MaterialTheme.typography.labelSmall, color = if (active) Color.White else palette.onPrimaryCard)
            }
          }
        }
      }

      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
              checked = hasScheduleTime,
              onCheckedChange = { hasScheduleTime = it },
              colors = visionPrimaryCardCheckboxColors(palette),
            )
            Text("Schedule Time", style = MaterialTheme.typography.bodyMedium, color = palette.onPrimaryCard)
          }
          if (hasScheduleTime) {
            Text(
              text = DateKeyHelper.formatFullTime12h(hour24, minute),
              style = MaterialTheme.typography.titleMedium,
              color = palette.accentOnPrimaryCard,
            )
          }
        }
        if (hasScheduleTime) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            OutlinedButton(
              onClick = { hour24 = (hour24 + 23) % 24 },
              modifier = Modifier.weight(1f),
              border = BorderStroke(1.dp, palette.cardBorder),
            ) {
              Text("-1h", color = palette.onPrimaryCard)
            }
            OutlinedButton(
              onClick = { hour24 = (hour24 + 1) % 24 },
              modifier = Modifier.weight(1f),
              border = BorderStroke(1.dp, palette.cardBorder),
            ) {
              Text("+1h", color = palette.onPrimaryCard)
            }
            OutlinedButton(
              onClick = { minute = (minute + 15) % 60 },
              modifier = Modifier.weight(1f),
              border = BorderStroke(1.dp, palette.cardBorder),
            ) {
              Text("+15m", color = palette.onPrimaryCard)
            }
          }
        }
      }

      Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
          checked = isRoutine,
          onCheckedChange = { isRoutine = it },
          colors = visionPrimaryCardCheckboxColors(palette),
        )
        Text("Repeat as Daily Routine", style = MaterialTheme.typography.bodyMedium, color = palette.onPrimaryCard)
      }

      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (initialTask != null) {
          OutlinedButton(
            onClick = { onDelete(initialTask.id) },
            border = BorderStroke(1.dp, VisionDangerRed.copy(alpha = 0.6f)),
            modifier = Modifier.height(48.dp),
          ) {
            Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete task", tint = VisionDangerRed)
            Spacer(Modifier.size(4.dp))
            Text("Delete", color = VisionDangerRed)
          }
        }
        Button(
          onClick = {
            if (title.isBlank()) {
              titleError = "Please enter a task title."
              return@Button
            }
            val mins = if (hasScheduleTime) hour24 * 60 + minute else -1
            val timeText = if (hasScheduleTime) DateKeyHelper.formatFullTime12h(hour24, minute) else ""
            onSave(
              (initialTask ?: TaskEntity(
                title = title.trim(),
                category = category,
                scheduledTimeText = timeText,
                scheduledMinutesOfDay = mins,
                isPriority = false,
                isRoutine = isRoutine,
                isCompleted = false,
                dateKey = defaultDateKey,
              )).copy(
                title = title.trim(),
                category = category,
                scheduledTimeText = timeText,
                scheduledMinutesOfDay = mins,
                isRoutine = isRoutine,
              ),
            )
          },
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
  val palette = LocalVisionPalette.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var title by remember { mutableStateOf(initialGoal?.title.orEmpty()) }
  var titleError by remember { mutableStateOf<String?>(null) }
  var subtitle by remember { mutableStateOf(initialGoal?.subtitle.orEmpty()) }
  var target by remember { mutableIntStateOf(initialGoal?.targetValue ?: 10) }
  var unit by remember { mutableStateOf(initialGoal?.unit ?: "steps") }

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
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = if (initialGoal == null) "Create Personal Goal" else "Edit Personal Goal",
        style = MaterialTheme.typography.titleLarge,
        color = palette.onPrimaryCard,
      )
      OutlinedTextField(
        value = title,
        onValueChange = {
          title = it
          if (titleError != null && it.isNotBlank()) titleError = null
        },
        label = { Text("Goal Title") },
        isError = titleError != null,
        supportingText = titleError?.let { msg -> { Text(msg, color = VisionDangerRed) } },
        colors = visionPrimaryCardFieldColors(palette),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
      OutlinedTextField(
        value = subtitle,
        onValueChange = { subtitle = it },
        label = { Text("Focus Milestone / Notes") },
        colors = visionPrimaryCardFieldColors(palette),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text("Target: $target $unit", style = MaterialTheme.typography.titleSmall, color = palette.onPrimaryCard)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          OutlinedButton(
            onClick = { target = (target - 1).coerceAtLeast(1) },
            border = BorderStroke(1.dp, palette.cardBorder),
          ) { Text("-1", color = palette.onPrimaryCard) }
          OutlinedButton(
            onClick = { target += 1 },
            border = BorderStroke(1.dp, palette.cardBorder),
          ) { Text("+1", color = palette.onPrimaryCard) }
          OutlinedButton(
            onClick = {
              unit = when (unit) {
                "steps" -> "modules"
                "modules" -> "days"
                else -> "steps"
              }
            },
            border = BorderStroke(1.dp, palette.cardBorder),
          ) { Text(unit, color = palette.onPrimaryCard) }
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (initialGoal != null) {
          OutlinedButton(
            onClick = { onDelete(initialGoal.id) },
            border = BorderStroke(1.dp, VisionDangerRed.copy(alpha = 0.6f)),
            modifier = Modifier.height(48.dp),
          ) {
            Text("Delete", color = VisionDangerRed)
          }
        }
        Button(
          onClick = {
            if (title.isBlank()) {
              titleError = "Please enter a goal title."
              return@Button
            }
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
          },
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
