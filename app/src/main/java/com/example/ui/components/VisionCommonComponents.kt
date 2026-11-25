package com.example.ui.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.AlarmEntity
import com.example.data.local.DateKeyHelper
import com.example.data.local.ProductivitySummary
import com.example.data.local.TaskEntity
import com.example.domain.alfred.AlfredAnnouncement
import com.example.domain.alfred.AlfredChatEntry
import com.example.domain.alfred.AlfredCloudState
import com.example.domain.alfred.AlfredVoiceState
import com.example.domain.alfred.AnnouncementType
import com.example.domain.alfred.NextTaskRecommendation
import com.example.domain.alfred.PendingAlfredAction
import com.example.domain.alfred.VoiceEnginePhase
import com.example.system.UsageStatsHelper
import com.example.ui.PermissionStatusSnapshot
import com.example.ui.theme.DeepTealDark
import com.example.ui.theme.DeepTealGlow
import com.example.ui.theme.DeepTealPrimary
import com.example.ui.theme.LocalVisionPalette
import com.example.ui.theme.OnDeepTeal
import com.example.ui.theme.OnDeepTealMuted
import com.example.ui.theme.PlayfairDisplayFamily
import com.example.ui.theme.PolishedCopper
import com.example.ui.theme.PolishedCopperBorder
import com.example.ui.theme.PolishedCopperBright
import com.example.ui.theme.VisionDangerRed
import com.example.ui.theme.VisionTab
import com.example.ui.theme.VisionThemeMode
import com.example.ui.theme.paletteFor

@Composable
fun VisionScreenBackground(
  activeTab: VisionTab,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  val palette = LocalVisionPalette.current
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(
        Brush.verticalGradient(
          colors = listOf(palette.backgroundTop, palette.backgroundBottom, palette.backgroundTop),
        ),
      )
      .drawBehind {
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(palette.atmosphericGlow, Color.Transparent),
            center = Offset(size.width * 0.85f, size.height * 0.14f),
            radius = size.width * 0.72f,
          ),
          center = Offset(size.width * 0.85f, size.height * 0.14f),
          radius = size.width * 0.72f,
        )
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(palette.atmosphericGlow.copy(alpha = 0.25f), Color.Transparent),
            center = Offset(size.width * 0.12f, size.height * 0.78f),
            radius = size.width * 0.65f,
          ),
          center = Offset(size.width * 0.12f, size.height * 0.78f),
          radius = size.width * 0.65f,
        )
        val veinColor = when (activeTab) {
          VisionTab.Dashboard -> palette.primaryCard.copy(alpha = 0.05f)
          VisionTab.Alarm -> palette.primaryCard.copy(alpha = 0.06f)
          VisionTab.Distraction -> palette.primaryCard.copy(alpha = 0.05f)
          VisionTab.Report -> palette.accentSoft.copy(alpha = 0.05f)
        }
        val path = Path().apply {
          moveTo(0f, size.height * 0.28f)
          cubicTo(
            size.width * 0.35f,
            size.height * 0.20f,
            size.width * 0.65f,
            size.height * 0.36f,
            size.width,
            size.height * 0.25f,
          )
        }
        drawPath(path, color = veinColor, style = Stroke(width = 2.5f, cap = StrokeCap.Round))
      },
  ) {
    content()
  }
}

@Composable
fun VisionLogo(
  modifier: Modifier = Modifier,
  iconSize: Dp = 32.dp,
  showWordmark: Boolean = true,
) {
  val palette = LocalVisionPalette.current
  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Image(
      painter = painterResource(id = R.drawable.vision_logo_mark),
      contentDescription = stringResource(id = R.string.cd_vision_logo),
      modifier = Modifier.size(iconSize),
    )
    if (showWordmark) {
      Text(
        text = "VISION",
        fontFamily = PlayfairDisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        letterSpacing = 3.sp,
        color = palette.onBackground,
      )
    }
  }
}

@Composable
fun VisionTopBar(
  rightIcon: ImageVector,
  rightContentDescription: String,
  onMenuClick: () -> Unit,
  onRightActionClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalVisionPalette.current
  Row(
    modifier = modifier
      .fillMaxWidth()
      .statusBarsPadding()
      .padding(horizontal = 12.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    IconButton(
      onClick = onMenuClick,
      modifier = Modifier
        .minimumInteractiveComponentSize()
        .testTag("menu_button"),
    ) {
      Icon(
        imageVector = Icons.Filled.Menu,
        contentDescription = stringResource(id = R.string.cd_menu_button),
        tint = palette.onBackground,
      )
    }

    VisionLogo(iconSize = 34.dp, showWordmark = true)

    IconButton(
      onClick = onRightActionClick,
      modifier = Modifier
        .minimumInteractiveComponentSize()
        .testTag("top_right_action_button"),
    ) {
      Icon(
        imageVector = rightIcon,
        contentDescription = rightContentDescription,
        tint = if (palette.isDarkMode) palette.accentSoft else palette.accent,
      )
    }
  }
}

private fun tabIcon(tab: VisionTab, selected: Boolean): ImageVector = when (tab) {
  VisionTab.Dashboard -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
  VisionTab.Alarm -> if (selected) Icons.Filled.Alarm else Icons.Outlined.Alarm
  VisionTab.Distraction -> if (selected) Icons.Filled.Shield else Icons.Outlined.Shield
  VisionTab.Report -> if (selected) Icons.Filled.BarChart else Icons.Outlined.BarChart
}

@Composable
fun VisionBottomNavBar(
  activeTab: VisionTab,
  onSelectTab: (VisionTab) -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalVisionPalette.current
  NavigationBar(
    modifier = modifier
      .fillMaxWidth()
      .windowInsetsPadding(WindowInsets.navigationBars),
    containerColor = palette.navBarContainer,
    tonalElevation = 6.dp,
  ) {
    VisionTab.entries.forEach { tab ->
      val selected = tab == activeTab
      NavigationBarItem(
        selected = selected,
        onClick = { onSelectTab(tab) },
        modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}"),
        icon = {
          Icon(
            imageVector = tabIcon(tab, selected),
            contentDescription = tab.label,
          )
        },
        label = {
          Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
          )
        },
        colors = NavigationBarItemDefaults.colors(
          selectedIconColor = palette.navSelectedContent,
          selectedTextColor = palette.navSelectedContent,
          indicatorColor = palette.navSelectedContainer,
          unselectedIconColor = palette.navUnselectedContent,
          unselectedTextColor = palette.navUnselectedContent,
        ),
      )
    }
  }
}

@Composable
fun VisionNavRail(
  activeTab: VisionTab,
  onSelectTab: (VisionTab) -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalVisionPalette.current
  NavigationRail(
    modifier = modifier,
    containerColor = palette.navBarContainer,
  ) {
    Spacer(Modifier.height(16.dp))
    VisionLogo(iconSize = 32.dp, showWordmark = false)
    Spacer(Modifier.height(24.dp))
    VisionTab.entries.forEach { tab ->
      val selected = tab == activeTab
      NavigationRailItem(
        selected = selected,
        onClick = { onSelectTab(tab) },
        modifier = Modifier.testTag("rail_tab_${tab.name.lowercase()}"),
        icon = {
          Icon(
            imageVector = tabIcon(tab, selected),
            contentDescription = tab.label,
          )
        },
        label = {
          Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
          )
        },
        colors = NavigationRailItemDefaults.colors(
          selectedIconColor = palette.navSelectedContent,
          selectedTextColor = palette.navSelectedContent,
          indicatorColor = palette.navSelectedContainer,
          unselectedIconColor = palette.navUnselectedContent,
          unselectedTextColor = palette.navUnselectedContent,
        ),
      )
    }
  }
}

/**
 * Shared Central ALFRED AI Voice & Assistant FAB (Deep Teal + Polished Copper)
 */
@Composable
fun CentralAiFab(
  isFocusRunning: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  voicePhase: VoiceEnginePhase = VoiceEnginePhase.IDLE,
) {
  val isVoiceActive = voicePhase == VoiceEnginePhase.LISTENING || voicePhase == VoiceEnginePhase.SPEAKING
  val infiniteTransition = rememberInfiniteTransition(label = "ai_pulse")
  val pulseScale by infiniteTransition.animateFloat(
    initialValue = 1f,
    targetValue = if (isVoiceActive || isFocusRunning) 1.14f else 1.03f,
    animationSpec = infiniteRepeatable(
      animation = tween(900, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse,
    ),
    label = "pulse_scale",
  )

  val iconVector = when (voicePhase) {
    VoiceEnginePhase.LISTENING -> Icons.Filled.GraphicEq
    VoiceEnginePhase.SPEAKING -> Icons.AutoMirrored.Filled.VolumeUp
    else -> Icons.Filled.Mic
  }

  Box(
    modifier = modifier
      .size(62.dp)
      .testTag("central_ai_fab")
      .clip(CircleShape)
      .background(
        Brush.linearGradient(
          colors = listOf(DeepTealDark, DeepTealPrimary, DeepTealGlow),
        ),
      )
      .border(
        width = 2.dp,
        brush = Brush.linearGradient(
          colors = listOf(PolishedCopper, PolishedCopperBright, PolishedCopper),
        ),
        shape = CircleShape,
      )
      .clickable(onClick = onClick)
      .minimumInteractiveComponentSize(),
    contentAlignment = Alignment.Center,
  ) {
    if (isFocusRunning || isVoiceActive) {
      Box(
        modifier = Modifier
          .size((44 * pulseScale).dp)
          .clip(CircleShape)
          .background(PolishedCopper.copy(alpha = 0.26f)),
      )
    }
    Icon(
      imageVector = iconVector,
      contentDescription = stringResource(id = R.string.cd_ai_voice_button),
      tint = PolishedCopperBright,
      modifier = Modifier.size(26.dp),
    )
  }
}

@Composable
fun CentralAiBannerCard(
  summaryText: String,
  statusLabel: String,
  onOpenAssistantSheet: () -> Unit,
  modifier: Modifier = Modifier,
  contextHint: String = "ALFRED · Central Assistant",
) {
  Surface(
    modifier = modifier
      .fillMaxWidth()
      .testTag("central_ai_banner"),
    shape = RoundedCornerShape(22.dp),
    color = Color.Transparent,
    border = BorderStroke(1.2.dp, PolishedCopperBorder.copy(alpha = 0.7f)),
  ) {
    Row(
      modifier = Modifier
        .background(
          Brush.horizontalGradient(
            colors = listOf(DeepTealDark, DeepTealPrimary, DeepTealDark),
          ),
        )
        .clickable(onClick = onOpenAssistantSheet)
        .padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Box(
        modifier = Modifier
          .size(46.dp)
          .clip(CircleShape)
          .background(DeepTealGlow.copy(alpha = 0.45f))
          .border(1.5.dp, PolishedCopperBright, CircleShape),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Filled.Psychology,
          contentDescription = "ALFRED AI",
          tint = PolishedCopperBright,
          modifier = Modifier.size(22.dp),
        )
      }

      Column(modifier = Modifier.weight(1f)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = contextHint,
            style = MaterialTheme.typography.titleSmall,
            color = OnDeepTeal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
          )
          Surface(
            shape = RoundedCornerShape(50),
            color = PolishedCopper.copy(alpha = 0.25f),
            border = BorderStroke(0.8.dp, PolishedCopperBright.copy(alpha = 0.75f)),
          ) {
            Text(
              text = statusLabel,
              style = MaterialTheme.typography.labelSmall,
              color = PolishedCopperBright,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
          }
        }
        Spacer(Modifier.height(3.dp))
        Text(
          text = summaryText,
          style = MaterialTheme.typography.bodySmall,
          color = OnDeepTealMuted,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }

      Box(
        modifier = Modifier
          .size(40.dp)
          .clip(CircleShape)
          .background(PolishedCopper.copy(alpha = 0.25f))
          .border(1.dp, PolishedCopperBright.copy(alpha = 0.75f), CircleShape),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Filled.Mic,
          contentDescription = stringResource(id = R.string.cd_alfred_mic),
          tint = PolishedCopperBright,
          modifier = Modifier.size(20.dp),
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CentralAiAssistantSheet(
  activeTab: VisionTab,
  productivity: ProductivitySummary,
  alarms: List<AlarmEntity>,
  focusActive: Boolean,
  studyModeActive: Boolean,
  nextRecommendation: NextTaskRecommendation,
  voiceState: AlfredVoiceState,
  cloudState: AlfredCloudState,
  isOnline: Boolean,
  chatHistory: List<AlfredChatEntry>,
  conversationIds: List<String>,
  activeConversationId: String,
  latestAnnouncement: AlfredAnnouncement?,
  pendingAction: PendingAlfredAction?,
  recordAudioGranted: Boolean,
  onStartVoiceListening: () -> Unit,
  onStopVoiceListening: () -> Unit,
  onStopSpeaking: () -> Unit,
  onToggleMute: () -> Unit,
  onToggleOfflineVoice: () -> Unit,
  onSubmitCommand: (String, Boolean) -> Unit,
  onConfirmPendingAction: () -> Unit,
  onCancelPendingAction: () -> Unit,
  onStartNewConversation: () -> Unit,
  onSelectConversation: (String) -> Unit,
  onDeleteConversation: (Boolean) -> Unit,
  onStartRecommendedFocus: (Int) -> Unit,
  onCompleteRecommendedTask: (TaskEntity) -> Unit,
  onTriggerAnnouncement: (AnnouncementType) -> Unit,
  onReplayLastAudio: () -> Unit,
  onPermissionsChanged: () -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var draftCommand by remember { mutableStateOf("") }
  var forceCloudReasoning by remember { mutableStateOf(false) }
  var showToolsPanel by remember { mutableStateOf(false) }
  var showDeleteHistoryConfirm by remember { mutableStateOf(false) }
  var pendingVoiceStartAfterGrant by remember { mutableStateOf(false) }
  var showMicRationale by remember { mutableStateOf(false) }
  val listState = rememberLazyListState()

  val audioPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) { granted ->
    onPermissionsChanged()
    if (granted && pendingVoiceStartAfterGrant) {
      pendingVoiceStartAfterGrant = false
      onStartVoiceListening()
    } else {
      pendingVoiceStartAfterGrant = false
    }
  }

  LaunchedEffect(chatHistory.size, cloudState) {
    if (chatHistory.isNotEmpty()) {
      listState.animateScrollToItem((chatHistory.size - 1).coerceAtLeast(0))
    }
  }

  val nextAlarm = alarms.firstOrNull { it.enabled }
  val nextAlarmLabel = nextAlarm?.let {
    "${DateKeyHelper.formatFullTime12h(it.hour24, it.minute)} · ${it.label}"
  } ?: "No active alarms armed"
  val isThinking = voiceState.phase == VoiceEnginePhase.PROCESSING || cloudState is AlfredCloudState.Loading

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = DeepTealDark,
    contentColor = OnDeepTeal,
    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .fillMaxHeight(0.92f)
        .imePadding()
        .padding(horizontal = 16.dp)
        .padding(bottom = 16.dp)
        .testTag("alfred_assistant_sheet"),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      // 1. Header: ALFRED identity + New Chat + Delete History + Audio Controls
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
              .size(44.dp)
              .clip(CircleShape)
              .background(DeepTealPrimary)
              .border(2.dp, PolishedCopperBright, CircleShape),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Filled.RecordVoiceOver,
              contentDescription = "ALFRED Central Assistant",
              tint = PolishedCopperBright,
              modifier = Modifier.size(22.dp),
            )
          }
          Column {
            Text(
              text = "ALFRED Chat",
              style = MaterialTheme.typography.titleLarge,
              color = OnDeepTeal,
            )
            Text(
              text = "${if (isOnline) "Gemini + Local Engine" else "Offline Engine"} · ${activeTab.label}",
              style = MaterialTheme.typography.bodySmall,
              color = PolishedCopperBright,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(
            onClick = {
              if (voiceState.phase == VoiceEnginePhase.SPEAKING) onStopSpeaking()
              else onReplayLastAudio()
            },
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("alfred_replay_button"),
          ) {
            Icon(
              imageVector = if (voiceState.phase == VoiceEnginePhase.SPEAKING) Icons.Filled.Stop else Icons.AutoMirrored.Filled.VolumeUp,
              contentDescription = stringResource(id = R.string.cd_alfred_speak),
              tint = PolishedCopperBright,
            )
          }
          IconButton(
            onClick = onToggleMute,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("alfred_mute_button"),
          ) {
            Icon(
              imageVector = if (voiceState.voiceMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.Filled.GraphicEq,
              contentDescription = if (voiceState.voiceMuted) "Unmute ALFRED voice" else "Mute ALFRED voice",
              tint = if (voiceState.voiceMuted) OnDeepTealMuted else OnDeepTeal,
            )
          }
          IconButton(
            onClick = onStartNewConversation,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("alfred_new_chat_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.Add,
              contentDescription = "New Chat",
              tint = PolishedCopperBright,
            )
          }
          IconButton(
            onClick = { showDeleteHistoryConfirm = true },
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("alfred_delete_history_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.DeleteOutline,
              contentDescription = "Delete chat history",
              tint = Color(0xFFFF8A80),
            )
          }
        }
      }

      // 2. Quick Controls Strip: Tools Toggle, Reasoning Mode, Saved Conversations
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Surface(
          shape = RoundedCornerShape(50),
          color = if (showToolsPanel) PolishedCopper else DeepTealPrimary,
          border = BorderStroke(1.dp, PolishedCopperBorder.copy(alpha = 0.7f)),
          onClick = { showToolsPanel = !showToolsPanel },
          modifier = Modifier.testTag("alfred_tools_toggle"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
          ) {
            Icon(Icons.Filled.Tune, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            Text(
              text = if (showToolsPanel) "Hide Tools" else "Tools & Status",
              style = MaterialTheme.typography.labelSmall,
              color = Color.White,
            )
          }
        }

        Surface(
          shape = RoundedCornerShape(50),
          color = if (forceCloudReasoning && isOnline) PolishedCopper.copy(alpha = 0.34f) else Color(0xFF052427),
          border = BorderStroke(1.dp, PolishedCopperBorder.copy(alpha = 0.6f)),
          enabled = isOnline,
          onClick = { forceCloudReasoning = !forceCloudReasoning },
          modifier = Modifier.testTag("alfred_cloud_mode_toggle"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = PolishedCopperBright, modifier = Modifier.size(13.dp))
            Text(
              text = when {
                !isOnline -> "Offline"
                forceCloudReasoning -> "Always Gemini"
                else -> "Smart Auto"
              },
              style = MaterialTheme.typography.labelSmall,
              color = PolishedCopperBright,
            )
          }
        }

        if (conversationIds.size > 1) {
          conversationIds.take(4).forEachIndexed { idx, convId ->
            val selected = convId == activeConversationId
            Surface(
              shape = RoundedCornerShape(50),
              color = if (selected) PolishedCopper.copy(alpha = 0.35f) else Color(0xFF052427),
              border = BorderStroke(1.dp, if (selected) PolishedCopperBright else DeepTealGlow),
              onClick = { onSelectConversation(convId) },
            ) {
              Text(
                text = "Chat ${idx + 1}",
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) OnDeepTeal else OnDeepTealMuted,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              )
            }
          }
        }
      }

      // Optional Collapsible Tools & Shared Status Drawer
      if (showToolsPanel) {
        Surface(
          shape = RoundedCornerShape(18.dp),
          color = Color(0xFF06292D),
          border = BorderStroke(1.dp, DeepTealGlow.copy(alpha = 0.7f)),
          modifier = Modifier.testTag("alfred_next_task_card"),
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .verticalScroll(rememberScrollState())
              .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "Next Suggestion: ${nextRecommendation.headline}",
                style = MaterialTheme.typography.titleSmall,
                color = OnDeepTeal,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
              )
              Button(
                onClick = { onStartRecommendedFocus(nextRecommendation.suggestedFocusMinutes) },
                colors = ButtonDefaults.buttonColors(containerColor = PolishedCopper),
                modifier = Modifier.height(34.dp),
              ) {
                Text("${nextRecommendation.suggestedFocusMinutes}m Focus", style = MaterialTheme.typography.labelSmall, color = Color.White)
              }
            }
            Text(
              text = "Dashboard: ${productivity.completedTasks}/${productivity.totalTasks} tasks · Next Alarm: $nextAlarmLabel · Focus ${if (focusActive) "Active" else "Standby"} · Study ${if (studyModeActive) "On" else "Off"}",
              style = MaterialTheme.typography.bodySmall,
              color = OnDeepTealMuted,
            )
            if (latestAnnouncement != null) {
              Text(
                text = "Latest Cue: ${latestAnnouncement.message}",
                style = MaterialTheme.typography.bodySmall,
                color = PolishedCopperBright,
              )
            }
            Row(
              modifier = Modifier.horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Surface(
                shape = RoundedCornerShape(50),
                color = if (voiceState.preferOfflineRecognition || !isOnline) PolishedCopper.copy(alpha = 0.28f) else DeepTealDark,
                border = BorderStroke(1.dp, PolishedCopperBorder.copy(alpha = 0.6f)),
                onClick = onToggleOfflineVoice,
                modifier = Modifier.testTag("alfred_offline_voice_toggle"),
              ) {
                Text(
                  text = if (voiceState.preferOfflineRecognition || !isOnline) "Offline Voice: On" else "Voice: Auto",
                  style = MaterialTheme.typography.labelSmall,
                  color = PolishedCopperBright,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
              }
              listOf(
                AnnouncementType.MORNING_WAKEUP to "Morning Briefing",
                AnnouncementType.DAILY_REPORT_READY to "Daily Report Cue",
              ).forEach { (type, label) ->
                Surface(
                  shape = RoundedCornerShape(50),
                  color = DeepTealPrimary,
                  border = BorderStroke(1.dp, DeepTealGlow),
                  onClick = { onTriggerAnnouncement(type) },
                ) {
                  Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = OnDeepTeal,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                  )
                }
              }
            }
          }
        }
      }

      // 3. Full-Height Scrollable Conversation Feed
      Surface(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF041E21),
        border = BorderStroke(1.dp, DeepTealGlow.copy(alpha = 0.45f)),
      ) {
        if (chatHistory.isEmpty() && !isThinking) {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .padding(24.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = "Ask ALFRED about your tasks, schedule, alarms, focus sessions, or productivity report.",
              style = MaterialTheme.typography.bodyMedium,
              color = OnDeepTealMuted,
            )
          }
        } else {
          LazyColumn(
            state = listState,
            modifier = Modifier
              .fillMaxSize()
              .padding(horizontal = 12.dp, vertical = 10.dp)
              .testTag("alfred_chat_list"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            items(chatHistory, key = { "${it.id}_${it.timestampMillis}" }) { entry ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (entry.isUser) Arrangement.End else Arrangement.Start,
              ) {
                Surface(
                  modifier = Modifier.widthIn(max = 310.dp),
                  shape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = if (entry.isUser) 18.dp else 4.dp,
                    bottomEnd = if (entry.isUser) 4.dp else 18.dp,
                  ),
                  color = if (entry.isUser) PolishedCopper.copy(alpha = 0.28f) else DeepTealPrimary.copy(alpha = 0.62f),
                  border = BorderStroke(
                    1.dp,
                    if (entry.isUser) PolishedCopperBright.copy(alpha = 0.6f) else DeepTealGlow.copy(alpha = 0.5f),
                  ),
                ) {
                  Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                  ) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      Text(
                        text = entry.sourceLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = PolishedCopperBright,
                      )
                      Text(
                        text = DateKeyHelper.formatMillisTime12h(entry.timestampMillis),
                        style = MaterialTheme.typography.labelSmall,
                        color = OnDeepTealMuted,
                      )
                    }
                    Text(
                      text = entry.text,
                      style = MaterialTheme.typography.bodyMedium,
                      color = OnDeepTeal,
                    )
                    entry.executedActions.forEach { actionBadge ->
                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                      ) {
                        Icon(
                          imageVector = Icons.Filled.CheckCircle,
                          contentDescription = null,
                          tint = Color(0xFF81C784),
                          modifier = Modifier.size(13.dp),
                        )
                        Text(
                          text = actionBadge,
                          style = MaterialTheme.typography.labelSmall,
                          color = Color(0xFFA5D6A7),
                        )
                      }
                    }
                  }
                }
              }
            }

            if (isThinking) {
              item(key = "alfred_loading_indicator") {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.Start,
                ) {
                  Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = DeepTealPrimary.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, DeepTealGlow.copy(alpha = 0.5f)),
                  ) {
                    Row(
                      modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                      CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = PolishedCopperBright,
                        strokeWidth = 2.dp,
                      )
                      Text(
                        text = "ALFRED is thinking...",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnDeepTeal,
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }

      // 4. Quick Prompt Chips
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        listOf(
          "What is my next task",
          "Read daily report",
          "Organize my day",
          "Start 25m study",
          "Set alarm for 6:30 AM",
        ).forEach { cmd ->
          Surface(
            shape = RoundedCornerShape(50),
            color = DeepTealPrimary,
            border = BorderStroke(1.dp, PolishedCopperBorder.copy(alpha = 0.6f)),
            onClick = { onSubmitCommand(cmd, false) },
          ) {
            Text(
              text = cmd,
              style = MaterialTheme.typography.labelSmall,
              color = OnDeepTeal,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            )
          }
        }
      }

      // Error & Pending Action Confirmation Banners
      val activeErr = voiceState.errorMessage ?: (cloudState as? AlfredCloudState.Error)?.message
      if (!activeErr.isNullOrBlank()) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = Color(0xFF4A191C),
          border = BorderStroke(1.dp, Color(0xFFE57373)),
        ) {
          Text(
            text = activeErr,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFFFEBEE),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
          )
        }
      }

      if (pendingAction != null) {
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .testTag("alfred_confirmation_card"),
          shape = RoundedCornerShape(16.dp),
          color = if (pendingAction.isDestructive) Color(0xFF3E1418) else Color(0xFF0A3236),
          border = BorderStroke(
            1.5.dp,
            if (pendingAction.isDestructive) Color(0xFFE57373) else PolishedCopperBright,
          ),
        ) {
          Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(
              text = "CONFIRM: ${pendingAction.title}",
              style = MaterialTheme.typography.titleSmall,
              color = OnDeepTeal,
            )
            Text(
              text = pendingAction.description,
              style = MaterialTheme.typography.bodySmall,
              color = OnDeepTealMuted,
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              OutlinedButton(
                onClick = onCancelPendingAction,
                border = BorderStroke(1.dp, OnDeepTealMuted.copy(alpha = 0.6f)),
                modifier = Modifier
                  .weight(1f)
                  .height(42.dp)
                  .testTag("alfred_cancel_action_button"),
              ) {
                Text("Cancel", color = OnDeepTeal)
              }
              Button(
                onClick = onConfirmPendingAction,
                colors = ButtonDefaults.buttonColors(
                  containerColor = if (pendingAction.isDestructive) VisionDangerRed else PolishedCopper,
                ),
                modifier = Modifier
                  .weight(1f)
                  .height(42.dp)
                  .testTag("alfred_confirm_action_button"),
              ) {
                Text("Confirm", color = Color.White, fontWeight = FontWeight.SemiBold)
              }
            }
          }
        }
      }

      if (voiceState.phase == VoiceEnginePhase.LISTENING && voiceState.partialTranscript.isNotBlank()) {
        Text(
          text = "Heard: \"${voiceState.partialTranscript}\"",
          style = MaterialTheme.typography.bodySmall,
          color = PolishedCopperBright,
        )
      }

      // 5. Persistent Bottom Composer Bar (Mic + Text Input + Send)
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        val isListening = voiceState.phase == VoiceEnginePhase.LISTENING
        Surface(
          shape = CircleShape,
          color = if (isListening) Color(0xFFB71C1C) else DeepTealPrimary,
          border = BorderStroke(1.5.dp, PolishedCopperBright),
          modifier = Modifier
            .size(48.dp)
            .minimumInteractiveComponentSize()
            .testTag("alfred_mic_button"),
          onClick = {
            if (isListening) {
              onStopVoiceListening()
            } else if (!recordAudioGranted) {
              showMicRationale = true
            } else {
              onStartVoiceListening()
            }
          },
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = if (isListening) Icons.Filled.Stop else if (recordAudioGranted) Icons.Filled.Mic else Icons.Filled.MicOff,
              contentDescription = stringResource(id = R.string.cd_alfred_mic),
              tint = Color.White,
              modifier = Modifier.size(22.dp),
            )
          }
        }

        OutlinedTextField(
          value = draftCommand,
          onValueChange = { draftCommand = it },
          placeholder = { Text("Message ALFRED...", color = OnDeepTealMuted) },
          singleLine = true,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
          keyboardActions = KeyboardActions(
            onSend = {
              if (draftCommand.isNotBlank()) {
                onSubmitCommand(draftCommand, forceCloudReasoning && isOnline)
                draftCommand = ""
              }
            },
          ),
          colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = OnDeepTeal,
            unfocusedTextColor = OnDeepTeal,
            focusedBorderColor = PolishedCopperBright,
            unfocusedBorderColor = PolishedCopperBorder.copy(alpha = 0.6f),
            cursorColor = PolishedCopperBright,
          ),
          modifier = Modifier
            .weight(1f)
            .testTag("alfred_command_input"),
        )

        IconButton(
          onClick = {
            if (draftCommand.isNotBlank()) {
              onSubmitCommand(draftCommand, forceCloudReasoning && isOnline)
              draftCommand = ""
            }
          },
          enabled = draftCommand.isNotBlank() && !isThinking,
          modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (draftCommand.isNotBlank() && !isThinking) PolishedCopper else PolishedCopper.copy(alpha = 0.45f))
            .testTag("alfred_send_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.Send,
            contentDescription = "Send command to ALFRED",
            tint = Color.White,
          )
        }
      }
    }
  }

  if (showDeleteHistoryConfirm) {
    AlertDialog(
      onDismissRequest = { showDeleteHistoryConfirm = false },
      title = { Text("Delete ALFRED Chat History?") },
      text = {
        Text("Delete the current conversation or clear all saved ALFRED conversations from local storage?")
      },
      confirmButton = {
        Button(
          onClick = {
            showDeleteHistoryConfirm = false
            onDeleteConversation(true)
          },
          colors = ButtonDefaults.buttonColors(containerColor = VisionDangerRed),
        ) {
          Text("Clear All", color = Color.White)
        }
      },
      dismissButton = {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          TextButton(onClick = { showDeleteHistoryConfirm = false }) {
            Text("Cancel")
          }
          TextButton(
            onClick = {
              showDeleteHistoryConfirm = false
              onDeleteConversation(false)
            },
          ) {
            Text("This Chat Only")
          }
        }
      },
    )
  }

  if (showMicRationale) {
    AlertDialog(
      onDismissRequest = { showMicRationale = false },
      title = { Text("Allow Microphone for Voice Commands") },
      text = {
        Text("VISION uses the microphone only when you tap the mic button to transcribe your voice command to ALFRED. Audio is never recorded in the background.")
      },
      confirmButton = {
        Button(
          onClick = {
            showMicRationale = false
            pendingVoiceStartAfterGrant = true
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
          },
          colors = ButtonDefaults.buttonColors(containerColor = PolishedCopper),
        ) {
          Text("Continue", color = Color.White)
        }
      },
      dismissButton = {
        TextButton(onClick = { showMicRationale = false }) {
          Text("Type Instead")
        }
      },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemMenuAndPermissionsSheet(
  activeTab: VisionTab,
  themeMode: VisionThemeMode,
  permissions: PermissionStatusSnapshot,
  onSetThemeMode: (VisionThemeMode) -> Unit,
  onSelectTab: (VisionTab) -> Unit,
  onRefreshPermissions: () -> Unit,
  onDismiss: () -> Unit,
) {
  val palette = LocalVisionPalette.current
  val context = LocalContext.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) {
    onRefreshPermissions()
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = palette.backgroundBottom,
    contentColor = palette.onBackground,
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
      VisionLogo(iconSize = 30.dp, showWordmark = true)
      Text(
        text = "Appearance, Permissions & Navigation",
        style = MaterialTheme.typography.bodyMedium,
        color = palette.onBackgroundMuted,
      )

      HorizontalDivider(color = palette.cardBorder.copy(alpha = 0.35f))

      Text(
        text = "APPEARANCE & TEXT CONTRAST",
        style = MaterialTheme.typography.labelSmall,
        color = palette.accentOnDarkCard,
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        VisionThemeMode.entries.forEach { mode ->
          val selected = mode == themeMode
          Surface(
            modifier = Modifier
              .weight(1f)
              .minimumInteractiveComponentSize()
              .testTag("theme_mode_${mode.name.lowercase()}"),
            shape = RoundedCornerShape(14.dp),
            color = if (selected) palette.accent else palette.darkGlassCard,
            border = BorderStroke(1.dp, if (selected) palette.accent else palette.darkCardBorder),
            onClick = { onSetThemeMode(mode) },
          ) {
            Box(
              modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = mode.label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) Color.White else palette.onDarkCard,
                maxLines = 1,
              )
            }
          }
        }
      }

      HorizontalDivider(color = palette.cardBorder.copy(alpha = 0.35f))

      Text(
        text = "ANDROID PERMISSIONS & BACKGROUND STATUS",
        style = MaterialTheme.typography.labelSmall,
        color = palette.accentOnDarkCard,
      )

      PermissionRowCard(
        title = "Microphone (Voice Assistant)",
        explanation = "Allows speaking voice commands directly to ALFRED via SpeechRecognizer.",
        granted = permissions.recordAudioGranted,
        actionLabel = if (permissions.recordAudioGranted) "Granted" else "Allow",
        onAction = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
      )

      PermissionRowCard(
        title = "Notifications",
        explanation = "Required for alarm alerts, ALFRED announcements, and Study Mode warnings.",
        granted = permissions.notificationsGranted,
        actionLabel = if (permissions.notificationsGranted) "Granted" else "Allow",
        onAction = {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
          }
        },
      )

      PermissionRowCard(
        title = "Exact Alarms",
        explanation = "Allows AlarmManager to fire wake-up alarms and ALFRED cues during Doze.",
        granted = permissions.exactAlarmsGranted,
        actionLabel = if (permissions.exactAlarmsGranted) "Granted" else "Open Settings",
        onAction = { UsageStatsHelper.openExactAlarmSettings(context) },
      )

      PermissionRowCard(
        title = "Usage Access (UsageStatsManager)",
        explanation = "Reads selected distracting app minutes locally on device.",
        granted = permissions.usageAccessGranted,
        actionLabel = if (permissions.usageAccessGranted) "Granted" else "Grant Access",
        onAction = { UsageStatsHelper.openUsageAccessSettings(context) },
      )

      PermissionRowCard(
        title = "Full-Screen Alarm Display",
        explanation = "Allows active alarms to display Dismiss and Snooze controls over the lock screen.",
        granted = permissions.fullScreenIntentGranted,
        actionLabel = if (permissions.fullScreenIntentGranted) "Allowed" else "Configure",
        onAction = { UsageStatsHelper.openFullScreenIntentSettings(context) },
      )

      PermissionRowCard(
        title = "Battery Optimization Exemption",
        explanation = "Helps background Focus timer and ALFRED alerts run reliably; Android OS battery policies still govern background execution.",
        granted = permissions.batteryOptimizationIgnored,
        actionLabel = if (permissions.batteryOptimizationIgnored) "Unrestricted" else "Configure",
        onAction = { UsageStatsHelper.openBatteryOptimizationSettings(context) },
      )

      HorizontalDivider(color = palette.cardBorder.copy(alpha = 0.35f))

      Text(
        text = "TABS",
        style = MaterialTheme.typography.labelSmall,
        color = palette.accentOnDarkCard,
      )

      VisionTab.entries.forEach { tab ->
        val tabPalette = paletteFor(tab, isDarkTheme = palette.isDarkMode)
        val isCurrent = tab == activeTab
        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(14.dp),
          color = if (isCurrent) tabPalette.primaryCard else palette.darkGlassCard,
          border = BorderStroke(1.dp, if (isCurrent) tabPalette.accent else palette.darkCardBorder),
          onClick = {
            onSelectTab(tab)
            onDismiss()
          },
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = "${tab.label} · ${tab.themeName}",
              style = MaterialTheme.typography.titleSmall,
              color = if (isCurrent) tabPalette.onPrimaryCard else palette.onDarkCard,
            )
            Text(
              text = if (isCurrent) "Active" else "Open",
              style = MaterialTheme.typography.labelSmall,
              color = if (isCurrent) tabPalette.accentOnPrimaryCard else palette.accentOnDarkCard,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun PermissionRowCard(
  title: String,
  explanation: String,
  granted: Boolean,
  actionLabel: String,
  onAction: () -> Unit,
) {
  val palette = LocalVisionPalette.current
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = palette.darkGlassCard,
    border = BorderStroke(1.dp, palette.darkCardBorder),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleSmall,
          color = palette.onDarkCard,
        )
        Text(
          text = explanation,
          style = MaterialTheme.typography.bodySmall,
          color = palette.onDarkCardMuted,
        )
      }
      Surface(
        shape = RoundedCornerShape(50),
        color = if (granted) Color(0xFF236E29) else palette.accent,
        border = BorderStroke(1.dp, if (granted) Color(0xFF4CAF50) else palette.accent),
        onClick = onAction,
      ) {
        Text(
          text = actionLabel,
          style = MaterialTheme.typography.labelSmall,
          color = Color.White,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
        )
      }
    }
  }
}
