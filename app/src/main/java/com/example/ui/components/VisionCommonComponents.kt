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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Shield
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
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import com.example.ui.theme.VisionTab
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
        tint = palette.accentSoft,
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
  latestAnnouncement: AlfredAnnouncement?,
  recordAudioGranted: Boolean,
  onStartVoiceListening: () -> Unit,
  onStopVoiceListening: () -> Unit,
  onStopSpeaking: () -> Unit,
  onToggleMute: () -> Unit,
  onToggleOfflineVoice: () -> Unit,
  onSubmitCommand: (String, Boolean) -> Unit,
  onStartRecommendedFocus: (Int) -> Unit,
  onCompleteRecommendedTask: (TaskEntity) -> Unit,
  onTriggerAnnouncement: (AnnouncementType) -> Unit,
  onReplayLastAudio: () -> Unit,
  onPermissionsChanged: () -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var draftCommand by remember { mutableStateOf("") }
  var pendingVoiceStartAfterGrant by remember { mutableStateOf(false) }

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

  val nextAlarm = alarms.firstOrNull { it.enabled }
  val nextAlarmLabel = nextAlarm?.let {
    "${DateKeyHelper.formatFullTime12h(it.hour24, it.minute)} · ${it.label}"
  } ?: "No active alarms armed"

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
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp)
        .padding(bottom = 28.dp)
        .testTag("alfred_assistant_sheet"),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // 1. Header with Speaker Mute + Replay Audio
      Row(
        modifier = Modifier.fillMaxWidth(),
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
              .size(48.dp)
              .clip(CircleShape)
              .background(DeepTealPrimary)
              .border(2.dp, PolishedCopperBright, CircleShape),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Filled.RecordVoiceOver,
              contentDescription = "ALFRED Central Assistant",
              tint = PolishedCopperBright,
              modifier = Modifier.size(24.dp),
            )
          }
          Column {
            Text(
              text = "ALFRED Assistant",
              style = MaterialTheme.typography.titleLarge,
              color = OnDeepTeal,
            )
            Text(
              text = "${if (isOnline) "Online (Gemini + Offline)" else "Offline Rule Engine"} · ${activeTab.label}",
              style = MaterialTheme.typography.bodySmall,
              color = PolishedCopperBright,
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
        }
      }

      // 2. Voice Input Activation & Microphone Controls
      Surface(
        shape = RoundedCornerShape(20.dp),
        color = DeepTealPrimary.copy(alpha = 0.65f),
        border = BorderStroke(1.2.dp, PolishedCopperBright.copy(alpha = 0.7f)),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            val isListening = voiceState.phase == VoiceEnginePhase.LISTENING
            Surface(
              shape = CircleShape,
              color = if (isListening) Color(0xFFB71C1C) else PolishedCopper,
              border = BorderStroke(2.dp, PolishedCopperBright),
              modifier = Modifier
                .size(54.dp)
                .minimumInteractiveComponentSize()
                .testTag("alfred_mic_button"),
              onClick = {
                if (isListening) {
                  onStopVoiceListening()
                } else if (!recordAudioGranted) {
                  pendingVoiceStartAfterGrant = true
                  audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                } else {
                  onStartVoiceListening()
                }
              },
            ) {
              Box(contentAlignment = Alignment.Center) {
                if (voiceState.phase == VoiceEnginePhase.PROCESSING || cloudState is AlfredCloudState.Loading) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Color.White,
                    strokeWidth = 2.5.dp,
                  )
                } else {
                  Icon(
                    imageVector = if (isListening) Icons.Filled.Stop else if (recordAudioGranted) Icons.Filled.Mic else Icons.Filled.MicOff,
                    contentDescription = stringResource(id = R.string.cd_alfred_mic),
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                  )
                }
              }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
              Text(
                text = when (voiceState.phase) {
                  VoiceEnginePhase.LISTENING -> "Listening... Tap mic to stop"
                  VoiceEnginePhase.PROCESSING -> "Processing your command..."
                  VoiceEnginePhase.SPEAKING -> "Speaking response aloud..."
                  VoiceEnginePhase.UNAVAILABLE -> "Speech service unavailable"
                  VoiceEnginePhase.IDLE -> if (recordAudioGranted) "Tap Mic to Speak to ALFRED" else "Grant Microphone Access to Speak"
                },
                style = MaterialTheme.typography.titleSmall,
                color = OnDeepTeal,
              )
              Text(
                text = voiceState.partialTranscript.ifBlank { voiceState.statusNote },
                style = MaterialTheme.typography.bodySmall,
                color = OnDeepTealMuted,
              )
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Surface(
              shape = RoundedCornerShape(50),
              color = if (voiceState.preferOfflineRecognition || !isOnline) PolishedCopper.copy(alpha = 0.28f) else DeepTealDark,
              border = BorderStroke(1.dp, PolishedCopperBorder.copy(alpha = 0.6f)),
              onClick = onToggleOfflineVoice,
              modifier = Modifier.testTag("alfred_offline_voice_toggle"),
            ) {
              Text(
                text = if (voiceState.preferOfflineRecognition || !isOnline) "Offline Voice Pack: Preferred" else "Voice Mode: Auto",
                style = MaterialTheme.typography.labelSmall,
                color = PolishedCopperBright,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              )
            }

            Text(
              text = if (voiceState.ttsReady) "TTS Ready" else "TTS Pending/Text",
              style = MaterialTheme.typography.labelSmall,
              color = OnDeepTealMuted,
            )
          }

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
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
              )
            }
          }
        }
      }

      // 3. Command Input + Offline / Gemini Ask
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(
          value = draftCommand,
          onValueChange = { draftCommand = it },
          placeholder = { Text("Ask ALFRED or issue command...", color = OnDeepTealMuted) },
          singleLine = true,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
          keyboardActions = KeyboardActions(
            onSend = {
              if (draftCommand.isNotBlank()) {
                onSubmitCommand(draftCommand, false)
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

        if (isOnline) {
          IconButton(
            onClick = {
              if (draftCommand.isNotBlank()) {
                onSubmitCommand(draftCommand, true)
                draftCommand = ""
              }
            },
            enabled = draftCommand.isNotBlank(),
            modifier = Modifier
              .size(48.dp)
              .clip(CircleShape)
              .background(DeepTealPrimary)
              .testTag("alfred_gemini_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.AutoAwesome,
              contentDescription = "Ask Gemini Cloud AI",
              tint = PolishedCopperBright,
            )
          }
        }

        IconButton(
          onClick = {
            if (draftCommand.isNotBlank()) {
              onSubmitCommand(draftCommand, false)
              draftCommand = ""
            }
          },
          enabled = draftCommand.isNotBlank(),
          modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(PolishedCopper)
            .testTag("alfred_send_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.Send,
            contentDescription = "Send command to ALFRED",
            tint = Color.White,
          )
        }
      }

      // 4. Next Task Recommendation (Real Room Data)
      Surface(
        modifier = Modifier
          .fillMaxWidth()
          .testTag("alfred_next_task_card"),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF06292D),
        border = BorderStroke(1.dp, PolishedCopperBorder.copy(alpha = 0.65f)),
      ) {
        Column(
          modifier = Modifier.padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = "NEXT TASK SUGGESTION (REAL SCHEDULE DATA)",
            style = MaterialTheme.typography.labelSmall,
            color = PolishedCopperBright,
          )
          Text(
            text = nextRecommendation.headline,
            style = MaterialTheme.typography.titleMedium,
            color = OnDeepTeal,
          )
          Text(
            text = nextRecommendation.rationale,
            style = MaterialTheme.typography.bodySmall,
            color = OnDeepTealMuted,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
              onClick = { onStartRecommendedFocus(nextRecommendation.suggestedFocusMinutes) },
              colors = ButtonDefaults.buttonColors(containerColor = PolishedCopper),
              modifier = Modifier.height(40.dp),
            ) {
              Text("Start ${nextRecommendation.suggestedFocusMinutes}m Focus", style = MaterialTheme.typography.labelLarge, color = Color.White)
            }
            nextRecommendation.task?.let { recTask ->
              OutlinedButton(
                onClick = { onCompleteRecommendedTask(recTask) },
                border = BorderStroke(1.dp, PolishedCopperBright),
                modifier = Modifier.height(40.dp),
              ) {
                Text("Mark Done", style = MaterialTheme.typography.labelLarge, color = PolishedCopperBright)
              }
            }
          }
        }
      }

      // 5. Live Cross-Tab Status Snapshot
      Surface(
        shape = RoundedCornerShape(16.dp),
        color = DeepTealPrimary.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, DeepTealGlow.copy(alpha = 0.55f)),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Text(
            text = "SHARED STATE ACROSS ALL 4 TABS",
            style = MaterialTheme.typography.labelSmall,
            color = PolishedCopperBright,
          )
          Text(
            text = "Dashboard: ${productivity.completedTasks}/${productivity.totalTasks} tasks (${productivity.productivityPercent}%) · Next Alarm: $nextAlarmLabel",
            style = MaterialTheme.typography.bodySmall,
            color = OnDeepTeal,
          )
          Text(
            text = "Distraction: Focus ${if (focusActive) "Active" else "Standby"} · Study ${if (studyModeActive) "On" else "Off"} · ${productivity.focusMinutesToday}m today",
            style = MaterialTheme.typography.bodySmall,
            color = OnDeepTealMuted,
          )
          if (latestAnnouncement != null) {
            Text(
              text = "Latest Cue: \"${latestAnnouncement.message}\"",
              style = MaterialTheme.typography.bodySmall,
              color = PolishedCopperBright,
            )
          }
        }
      }

      // 6. Quick Cross-Tab Commands & Personalised Announcements
      Text(
        text = "QUICK CROSS-TAB COMMANDS & ANNOUNCEMENTS",
        style = MaterialTheme.typography.labelSmall,
        color = PolishedCopperBright,
      )
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        listOf(
          "Organize my day",
          "What is my next task",
          "Read daily report",
          "Start 25m study",
          "Set alarm for 6:30 AM",
          "Block Instagram",
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
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
          }
        }
      }

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        listOf(
          AnnouncementType.MORNING_WAKEUP to "Speak Morning Briefing",
          AnnouncementType.UPCOMING_TASK to "Upcoming Task Alert",
          AnnouncementType.MILESTONE_COMPLETED to "Completed Milestone",
          AnnouncementType.DAILY_REPORT_READY to "Daily Report Cue",
        ).forEach { (type, label) ->
          Surface(
            shape = RoundedCornerShape(50),
            color = Color(0xFF052427),
            border = BorderStroke(1.dp, DeepTealGlow),
            onClick = { onTriggerAnnouncement(type) },
          ) {
            Text(
              text = label,
              style = MaterialTheme.typography.labelSmall,
              color = PolishedCopperBright,
              modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            )
          }
        }
      }

      // 7. Conversation & Executed Action Feed
      Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF041E21),
        border = BorderStroke(1.dp, DeepTealGlow.copy(alpha = 0.45f)),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(
            text = "ALFRED ACTIVITY LOG",
            style = MaterialTheme.typography.labelSmall,
            color = PolishedCopperBright,
          )
          chatHistory.takeLast(6).forEach { entry ->
            Surface(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(14.dp),
              color = if (entry.isUser) PolishedCopper.copy(alpha = 0.22f) else DeepTealPrimary.copy(alpha = 0.48f),
              border = BorderStroke(
                0.8.dp,
                if (entry.isUser) PolishedCopperBright.copy(alpha = 0.5f) else DeepTealGlow.copy(alpha = 0.4f),
              ),
            ) {
              Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Text(
                  text = entry.sourceLabel,
                  style = MaterialTheme.typography.labelSmall,
                  color = PolishedCopperBright,
                )
                Text(
                  text = entry.text,
                  style = MaterialTheme.typography.bodySmall,
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
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemMenuAndPermissionsSheet(
  activeTab: VisionTab,
  permissions: PermissionStatusSnapshot,
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
        text = "Permissions, Background Execution & Navigation",
        style = MaterialTheme.typography.bodyMedium,
        color = palette.onBackgroundMuted,
      )

      HorizontalDivider(color = palette.cardBorder.copy(alpha = 0.35f))

      Text(
        text = "ANDROID PERMISSIONS & BACKGROUND STATUS",
        style = MaterialTheme.typography.labelSmall,
        color = palette.accentSoft,
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
        title = "Accessibility Focus Shield (Optional)",
        explanation = "Enforces app blocking on foreground switch when Focus Mode restricts an app.",
        granted = permissions.accessibilityEnabled,
        actionLabel = if (permissions.accessibilityEnabled) "Enabled" else "Enable",
        onAction = { UsageStatsHelper.openAccessibilitySettings(context) },
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
        color = palette.accentSoft,
      )

      VisionTab.entries.forEach { tab ->
        val tabPalette = paletteFor(tab)
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
              color = if (isCurrent) tabPalette.accent else palette.accentSoft,
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
        color = if (granted) Color(0xFF2E7D32).copy(alpha = 0.28f) else palette.accent,
        border = BorderStroke(1.dp, if (granted) Color(0xFF66BB6A) else palette.accentSoft),
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
