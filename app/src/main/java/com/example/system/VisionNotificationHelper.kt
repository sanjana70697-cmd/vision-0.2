package com.example.system

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

object VisionNotificationHelper {
  const val CHANNEL_ALARMS = "vision_alarms"
  const val CHANNEL_REMINDERS = "vision_reminders"
  const val CHANNEL_FOCUS = "vision_focus"
  const val CHANNEL_WARNINGS = "vision_warnings"

  const val ALARM_NOTIFICATION_ID = 4101
  const val FOCUS_NOTIFICATION_ID = 4201
  const val STUDY_WARNING_NOTIFICATION_ID = 4301
  const val ANNOUNCEMENT_FG_NOTIFICATION_ID = 4401
  const val MORNING_BRIEFING_NOTIFICATION_ID = 4402
  const val UPCOMING_TASK_NOTIFICATION_ID = 4403
  const val DAILY_REPORT_NOTIFICATION_ID = 4404

  fun ensureChannels(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    val alarmChannel = NotificationChannel(
      CHANNEL_ALARMS,
      "VISION Alarms",
      NotificationManager.IMPORTANCE_HIGH,
    ).apply {
      description = "Scheduled wake-up and daily alarms"
      enableVibration(true)
      setSound(
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
        AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_ALARM)
          .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
          .build(),
      )
      lockscreenVisibility = Notification.VISIBILITY_PUBLIC
    }

    val reminderChannel = NotificationChannel(
      CHANNEL_REMINDERS,
      "VISION Reminders & Schedule",
      NotificationManager.IMPORTANCE_HIGH,
    ).apply {
      description = "Custom reminders and daily schedule alerts"
      enableVibration(true)
    }

    val focusChannel = NotificationChannel(
      CHANNEL_FOCUS,
      "VISION Focus Monitor",
      NotificationManager.IMPORTANCE_LOW,
    ).apply {
      description = "Active Focus Session timer and Study Mode monitor"
      setShowBadge(false)
    }

    val warningChannel = NotificationChannel(
      CHANNEL_WARNINGS,
      "VISION Distraction Warnings",
      NotificationManager.IMPORTANCE_HIGH,
    ).apply {
      description = "Study Mode grace period and distracting app alerts"
      enableVibration(true)
    }

    manager.createNotificationChannels(
      listOf(alarmChannel, reminderChannel, focusChannel, warningChannel),
    )
  }

  fun hasNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
      ) == PackageManager.PERMISSION_GRANTED
    } else {
      true
    }
  }

  fun hasRecordAudioPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED
  }

  fun buildAlarmRingingNotification(
    context: Context,
    alarmId: Long,
    label: String,
    timeLabel: String,
  ): Notification {
    ensureChannels(context)

    val fullScreenIntent = Intent(context, AlarmRingActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
      putExtra(AlarmReceiver.EXTRA_ALARM_LABEL, label)
      putExtra(AlarmReceiver.EXTRA_ALARM_TIME, timeLabel)
    }
    val fullScreenPendingIntent = PendingIntent.getActivity(
      context,
      (alarmId.toInt() + 10000),
      fullScreenIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    val dismissIntent = Intent(context, AlarmReceiver::class.java).apply {
      action = AlarmReceiver.ACTION_DISMISS_ALARM
      putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
    }
    val dismissPendingIntent = PendingIntent.getBroadcast(
      context,
      (alarmId.toInt() + 20000),
      dismissIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    val snoozeIntent = Intent(context, AlarmReceiver::class.java).apply {
      action = AlarmReceiver.ACTION_SNOOZE_ALARM
      putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
      putExtra(AlarmReceiver.EXTRA_ALARM_LABEL, label)
    }
    val snoozePendingIntent = PendingIntent.getBroadcast(
      context,
      (alarmId.toInt() + 30000),
      snoozeIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    return NotificationCompat.Builder(context, CHANNEL_ALARMS)
      .setSmallIcon(R.drawable.ic_vision_logo)
      .setContentTitle("VISION Alarm · $timeLabel")
      .setContentText(label)
      .setCategory(NotificationCompat.CATEGORY_ALARM)
      .setPriority(NotificationCompat.PRIORITY_MAX)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .setOngoing(true)
      .setAutoCancel(false)
      .setFullScreenIntent(fullScreenPendingIntent, true)
      .setContentIntent(fullScreenPendingIntent)
      .addAction(0, "Dismiss", dismissPendingIntent)
      .addAction(0, "Snooze 5m", snoozePendingIntent)
      .build()
  }

  fun postReminderNotification(
    context: Context,
    notificationId: Int,
    title: String,
    body: String,
  ) {
    if (!hasNotificationPermission(context)) return
    ensureChannels(context)
    val openIntent = PendingIntent.getActivity(
      context,
      notificationId,
      Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
      },
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
      .setSmallIcon(R.drawable.ic_vision_logo)
      .setContentTitle(title)
      .setContentText(body)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setAutoCancel(true)
      .setContentIntent(openIntent)
      .build()
    NotificationManagerCompat.from(context).notify(notificationId, notification)
  }

  fun buildFocusForegroundNotification(
    context: Context,
    title: String,
    body: String,
  ): Notification {
    ensureChannels(context)
    val openIntent = PendingIntent.getActivity(
      context,
      FOCUS_NOTIFICATION_ID,
      Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
      },
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    return NotificationCompat.Builder(context, CHANNEL_FOCUS)
      .setSmallIcon(R.drawable.ic_vision_logo)
      .setContentTitle(title)
      .setContentText(body)
      .setOngoing(true)
      .setSilent(true)
      .setContentIntent(openIntent)
      .build()
  }

  fun postWarningNotification(
    context: Context,
    notificationId: Int,
    title: String,
    body: String,
  ) {
    if (!hasNotificationPermission(context)) return
    ensureChannels(context)
    val openIntent = PendingIntent.getActivity(
      context,
      notificationId,
      Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
      },
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val notification = NotificationCompat.Builder(context, CHANNEL_WARNINGS)
      .setSmallIcon(R.drawable.ic_vision_logo)
      .setContentTitle(title)
      .setContentText(body)
      .setStyle(NotificationCompat.BigTextStyle().bigText(body))
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setAutoCancel(true)
      .setContentIntent(openIntent)
      .build()
    NotificationManagerCompat.from(context).notify(notificationId, notification)
  }
}
