package com.example.system

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AlarmRingingService : Service() {

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var mediaPlayer: MediaPlayer? = null
  private var vibrator: Vibrator? = null
  private var timeoutJob: Job? = null
  private var rampJob: Job? = null

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val alarmId = intent?.getLongExtra(AlarmReceiver.EXTRA_ALARM_ID, 1L) ?: 1L
    val label = intent?.getStringExtra(AlarmReceiver.EXTRA_ALARM_LABEL) ?: "VISION Alarm"
    val timeLabel = intent?.getStringExtra(AlarmReceiver.EXTRA_ALARM_TIME) ?: "Alarm"
    val gentleChime = intent?.getBooleanExtra("extra_gentle_chime", true) ?: true
    val vibrateEnabled = intent?.getBooleanExtra("extra_vibrate", true) ?: true
    val soundEnabled = intent?.getBooleanExtra("extra_sound", true) ?: true

    val notification = VisionNotificationHelper.buildAlarmRingingNotification(
      context = this,
      alarmId = alarmId,
      label = label,
      timeLabel = timeLabel,
    )
    val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
    } else {
      0
    }
    ServiceCompat.startForeground(
      this,
      VisionNotificationHelper.ALARM_NOTIFICATION_ID,
      notification,
      fgsType,
    )

    if (soundEnabled) {
      startAlarmAudio(gentleChime)
    }
    if (vibrateEnabled) {
      startVibration()
    }

    timeoutJob?.cancel()
    timeoutJob = serviceScope.launch {
      delay(5 * 60_000L)
      stopSelf()
    }

    return START_NOT_STICKY
  }

  private fun startAlarmAudio(gentleChime: Boolean) {
    stopAlarmAudio()
    try {
      val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
      val player = MediaPlayer().apply {
        setDataSource(this@AlarmRingingService, alarmUri)
        setAudioAttributes(
          AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build(),
        )
        isLooping = true
        prepare()
        val initialVol = if (gentleChime) 0.15f else 1.0f
        setVolume(initialVol, initialVol)
        start()
      }
      mediaPlayer = player

      if (gentleChime) {
        rampJob?.cancel()
        rampJob = serviceScope.launch {
          var vol = 0.15f
          while (vol < 1.0f && mediaPlayer != null) {
            delay(1500L)
            vol = (vol + 0.15f).coerceAtMost(1.0f)
            runCatching { mediaPlayer?.setVolume(vol, vol) }
          }
        }
      }
    } catch (e: Exception) {
      Log.e("AlarmRingingService", "Unable to play alarm ringtone", e)
    }
  }

  private fun startVibration() {
    try {
      val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vm.defaultVibrator
      } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
      }
      vibrator = vib
      val pattern = longArrayOf(0, 600, 400, 600, 800)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vib.vibrate(VibrationEffect.createWaveform(pattern, 0))
      } else {
        @Suppress("DEPRECATION")
        vib.vibrate(pattern, 0)
      }
    } catch (e: Exception) {
      Log.w("AlarmRingingService", "Vibration unavailable", e)
    }
  }

  private fun stopAlarmAudio() {
    rampJob?.cancel()
    runCatching {
      mediaPlayer?.stop()
      mediaPlayer?.release()
    }
    mediaPlayer = null
  }

  override fun onDestroy() {
    timeoutJob?.cancel()
    serviceScope.cancel()
    stopAlarmAudio()
    runCatching { vibrator?.cancel() }
    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    super.onDestroy()
  }
}
