package com.example.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.example.data.local.DateKeyHelper
import com.example.data.local.VisionDatabase
import com.example.data.local.VisionRepository
import com.example.domain.alfred.AlfredAnnouncementEngine
import com.example.domain.alfred.AlfredBrain
import com.example.domain.alfred.AlfredCloudAdvisor
import com.example.domain.alfred.AlfredCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

  companion object {
    const val ACTION_TRIGGER_ALARM = "com.example.vision.ACTION_TRIGGER_ALARM"
    const val ACTION_DISMISS_ALARM = "com.example.vision.ACTION_DISMISS_ALARM"
    const val ACTION_SNOOZE_ALARM = "com.example.vision.ACTION_SNOOZE_ALARM"
    const val EXTRA_ALARM_ID = "extra_alarm_id"
    const val EXTRA_ALARM_LABEL = "extra_alarm_label"
    const val EXTRA_ALARM_KIND = "extra_alarm_kind"
    const val EXTRA_ALARM_TIME = "extra_alarm_time"
  }

  override fun onReceive(context: Context, intent: Intent) {
    val action = intent.action ?: return
    val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
    val label = intent.getStringExtra(EXTRA_ALARM_LABEL) ?: "VISION Alarm"
    val kind = intent.getStringExtra(EXTRA_ALARM_KIND) ?: "ALARM"
    val timeLabel = intent.getStringExtra(EXTRA_ALARM_TIME) ?: ""

    when (action) {
      ACTION_DISMISS_ALARM -> {
        context.stopService(Intent(context, AlarmRingingService::class.java))
        // When user dismisses a wake-up alarm and ALFRED morning briefing is enabled, speak morning greeting
        val pendingResult: PendingResult? = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
          try {
            val db = VisionDatabase.getInstance(context)
            val repo = VisionRepository(db.visionDao())
            val storedAlarm = if (alarmId > 0L) repo.getAlarmById(alarmId) else null
            val config = repo.getFocusConfigSync()
            if (storedAlarm?.isWakeUp == true && config.aiMorningBriefing) {
              val brain = AlfredBrain(repo, AlfredCoordinator(context.applicationContext, repo), AlfredCloudAdvisor(context.applicationContext))
              val snapshot = brain.captureLiveSnapshot()
              val morning = AlfredAnnouncementEngine.buildMorningWakeUpAnnouncement(snapshot)
              AlfredAnnouncementService.announceInBackground(
                context = context,
                title = morning.title,
                message = morning.spokenMessage,
                notificationId = VisionNotificationHelper.MORNING_BRIEFING_NOTIFICATION_ID,
                speakAloud = true,
              )
            }
          } finally {
            pendingResult?.finish()
          }
        }
        return
      }

      ACTION_SNOOZE_ALARM -> {
        context.stopService(Intent(context, AlarmRingingService::class.java))
        if (alarmId > 0L) {
          AlarmScheduler.scheduleSnooze(context, alarmId, label, snoozeMinutes = 5)
        }
        return
      }

      AlfredAnnouncementScheduler.ACTION_UPCOMING_TASK_ALERT -> {
        val taskId = intent.getLongExtra(AlfredAnnouncementScheduler.EXTRA_TASK_ID, -1L)
        val pendingResult: PendingResult? = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
          try {
            runCatching {
              val db = VisionDatabase.getInstance(context)
              val repo = VisionRepository(db.visionDao())
              val config = repo.getFocusConfigSync()
              val todayTasks = db.visionDao().getTasksForDateSync(DateKeyHelper.todayKey())
              val targetTask = todayTasks.firstOrNull { it.id == taskId && !it.isCompleted }
              if (targetTask != null) {
                val announcement = AlfredAnnouncementEngine.buildUpcomingTaskAnnouncement(targetTask)
                AlfredAnnouncementService.announceInBackground(
                  context = context,
                  title = announcement.title,
                  message = announcement.spokenMessage,
                  notificationId = VisionNotificationHelper.UPCOMING_TASK_NOTIFICATION_ID,
                  speakAloud = config.aiMorningBriefing,
                )
              }
              AlfredAnnouncementScheduler.scheduleNextUpcomingTaskAlert(context, todayTasks)
            }
          } finally {
            pendingResult?.finish()
          }
        }
        return
      }

      AlfredAnnouncementScheduler.ACTION_DAILY_REPORT_ALERT -> {
        val pendingResult: PendingResult? = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
          try {
            runCatching {
              val db = VisionDatabase.getInstance(context)
              val repo = VisionRepository(db.visionDao())
              val config = repo.getFocusConfigSync()
              val summary = repo.observeTodayProductivity().first()
              if (summary.totalTasks > 0 || summary.focusMinutesToday > 0) {
                val reportAnn = AlfredAnnouncementEngine.buildDailyReportReadyAnnouncement(summary)
                AlfredAnnouncementService.announceInBackground(
                  context = context,
                  title = reportAnn.title,
                  message = reportAnn.spokenMessage,
                  notificationId = VisionNotificationHelper.DAILY_REPORT_NOTIFICATION_ID,
                  speakAloud = config.aiMorningBriefing,
                )
              }
              AlfredAnnouncementScheduler.scheduleEveningDailyReportAlert(context)
            }
          } finally {
            pendingResult?.finish()
          }
        }
        return
      }

      ACTION_TRIGGER_ALARM -> {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
          PowerManager.PARTIAL_WAKE_LOCK,
          "vision:AlarmWakeLock",
        )
        wakeLock.acquire(10_000L)

        val pendingResult: PendingResult? = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
          try {
            val db = VisionDatabase.getInstance(context)
            val repo = VisionRepository(db.visionDao())
            val storedAlarm = if (alarmId > 0L) repo.getAlarmById(alarmId) else null
            val config = repo.getFocusConfigSync()

            if (kind == "ALARM") {
              val serviceIntent = Intent(context, AlarmRingingService::class.java).apply {
                putExtra(EXTRA_ALARM_ID, alarmId)
                putExtra(EXTRA_ALARM_LABEL, storedAlarm?.label ?: label)
                putExtra(
                  EXTRA_ALARM_TIME,
                  storedAlarm?.let { DateKeyHelper.formatFullTime12h(it.hour24, it.minute) } ?: timeLabel,
                )
                putExtra("extra_gentle_chime", config.gentleEscalatingChime)
                putExtra("extra_vibrate", storedAlarm?.vibrateEnabled ?: true)
                putExtra("extra_sound", storedAlarm?.soundEnabled ?: true)
              }
              ContextCompat.startForegroundService(context, serviceIntent)

              if (config.aiMorningBriefing && storedAlarm?.isWakeUp == true) {
                val brain = AlfredBrain(repo, AlfredCoordinator(context.applicationContext, repo), AlfredCloudAdvisor(context.applicationContext))
                val morning = AlfredAnnouncementEngine.buildMorningWakeUpAnnouncement(brain.captureLiveSnapshot())
                VisionNotificationHelper.postReminderNotification(
                  context = context,
                  notificationId = VisionNotificationHelper.MORNING_BRIEFING_NOTIFICATION_ID,
                  title = morning.title,
                  body = morning.message,
                )
              }
            } else {
              val notifTitle = if (kind == "SCHEDULE") "ALFRED Schedule · $timeLabel" else "ALFRED Reminder · $timeLabel"
              val bodyText = storedAlarm?.label ?: label
              AlfredAnnouncementService.announceInBackground(
                context = context,
                title = notifTitle,
                message = bodyText,
                notificationId = (alarmId.toInt().coerceAtLeast(1)) + 6000,
                speakAloud = config.aiMorningBriefing,
              )
            }

            // Reschedule repeating alarm or disable one-shot alarm
            if (storedAlarm != null && storedAlarm.enabled) {
              if (storedAlarm.repeatDaysCsv.isBlank()) {
                repo.saveAlarm(storedAlarm.copy(enabled = false, nextTriggerMillis = 0L))
              } else {
                val nextTrigger = AlarmScheduler.schedule(context, storedAlarm)
                repo.saveAlarm(storedAlarm.copy(nextTriggerMillis = nextTrigger))
              }
            }
          } finally {
            if (wakeLock.isHeld) wakeLock.release()
            pendingResult?.finish()
          }
        }
      }
    }
  }
}
