package com.example.system

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.components.VisionLogo
import com.example.ui.theme.OliveGroveActive
import com.example.ui.theme.OliveGroveDeep
import com.example.ui.theme.OliveGroveMid
import com.example.ui.theme.SmokedMarbleInk
import com.example.ui.theme.SmokedMarbleLight
import com.example.ui.theme.VisionTab
import com.example.ui.theme.VisionTheme

class AlarmRingActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    configureLockScreenDisplay()
    enableEdgeToEdge()

    val alarmId = intent.getLongExtra(AlarmReceiver.EXTRA_ALARM_ID, -1L)
    val label = intent.getStringExtra(AlarmReceiver.EXTRA_ALARM_LABEL) ?: "Wake Up Alarm"
    val timeLabel = intent.getStringExtra(AlarmReceiver.EXTRA_ALARM_TIME) ?: "Alarm"

    setContent {
      VisionTheme(activeTab = VisionTab.Alarm) {
        AlarmRingScreen(
          timeLabel = timeLabel,
          label = label,
          onDismiss = {
            sendBroadcast(
              Intent(this, AlarmReceiver::class.java).apply {
                action = AlarmReceiver.ACTION_DISMISS_ALARM
                putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
              },
            )
            finish()
          },
          onSnooze = {
            sendBroadcast(
              Intent(this, AlarmReceiver::class.java).apply {
                action = AlarmReceiver.ACTION_SNOOZE_ALARM
                putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
                putExtra(AlarmReceiver.EXTRA_ALARM_LABEL, label)
              },
            )
            finish()
          },
        )
      }
    }
  }

  private fun configureLockScreenDisplay() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
      setShowWhenLocked(true)
      setTurnScreenOn(true)
      val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
      keyguardManager.requestDismissKeyguard(this, null)
    } else {
      @Suppress("DEPRECATION")
      window.addFlags(
        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
          WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
          WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
      )
    }
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  }
}

@Composable
private fun AlarmRingScreen(
  timeLabel: String,
  label: String,
  onDismiss: () -> Unit,
  onSnooze: () -> Unit,
) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Brush.verticalGradient(listOf(OliveGroveDeep, OliveGroveMid, OliveGroveDeep)))
      .padding(24.dp),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
      VisionLogo(iconSize = 40.dp, showWordmark = true)

      Surface(
        shape = RoundedCornerShape(28.dp),
        color = SmokedMarbleLight,
        border = BorderStroke(1.5.dp, OliveGroveActive),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Box(
            modifier = Modifier
              .size(64.dp)
              .clip(CircleShape)
              .background(OliveGroveActive.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Filled.NotificationsActive,
              contentDescription = "Ringing Alarm",
              tint = OliveGroveActive,
              modifier = Modifier.size(34.dp),
            )
          }
          Text(
            text = timeLabel,
            style = MaterialTheme.typography.displayLarge,
            color = SmokedMarbleInk,
          )
          Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
            color = SmokedMarbleInk,
          )
          Spacer(Modifier.height(6.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            OutlinedButton(
              onClick = onSnooze,
              modifier = Modifier
                .weight(1f)
                .height(52.dp),
              shape = RoundedCornerShape(50),
              border = BorderStroke(1.5.dp, OliveGroveActive),
            ) {
              Icon(Icons.Filled.Snooze, contentDescription = null, tint = OliveGroveActive)
              Spacer(Modifier.size(6.dp))
              Text("Snooze 5m", color = SmokedMarbleInk)
            }
            Button(
              onClick = onDismiss,
              modifier = Modifier
                .weight(1f)
                .height(52.dp),
              shape = RoundedCornerShape(50),
              colors = ButtonDefaults.buttonColors(containerColor = OliveGroveActive),
            ) {
              Icon(Icons.Filled.AlarmOff, contentDescription = null, tint = Color.White)
              Spacer(Modifier.size(6.dp))
              Text("Dismiss", color = Color.White)
            }
          }
        }
      }
    }
  }
}
