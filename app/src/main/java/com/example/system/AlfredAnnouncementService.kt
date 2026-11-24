package com.example.system

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.data.local.DateKeyHelper
import com.example.data.local.TaskEntity
import com.example.data.local.VisionDatabase
import com.example.data.local.VisionRepository
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "AlfredAnnouncementSvc"

/**
 * Foreground service used when ALFRED speaks personalized contextual announcements while
 * the app UI is closed, removed from recents, or the device screen is locked.
 */
class AlfredAnnouncementService : Service() {

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var tts: TextToSpeech? = null
  private var safetyTimeoutJob: Job? = null

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val title = intent?.getStringExtra(EXTRA_TITLE) ?: "ALFRED Announcement"
    val spokenText = intent?.getStringExtra(EXTRA_SPOKEN_TEXT).orEmpty()
    val notificationId = intent?.getIntExtra(EXTRA_NOTIFICATION_ID, 7301) ?: 7301
    val isWarning = intent?.getBooleanExtra(EXTRA_IS_WARNING, false) ?: false

    val fgNotif = VisionNotificationHelper.buildFocusForegroundNotification(
      this,
      title,
      spokenText.ifBlank { "ALFRED voice announcement active" },
    )
    val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
    } else {
      0
    }
    ServiceCompat.startForeground(
      this,
      VisionNotificationHelper.ANNOUNCEMENT_FG_NOTIFICATION_ID,
      fgNotif,
      fgsType,
    )

    if (spokenText.isBlank()) {
      stopSelf()
      return START_NOT_STICKY
    }

    if (isWarning) {
      VisionNotificationHelper.postWarningNotification(this, notificationId, title, spokenText)
    } else {
      VisionNotificationHelper.postReminderNotification(this, notificationId, title, spokenText)
    }

    safetyTimeoutJob?.cancel()
    safetyTimeoutJob = serviceScope.launch {
      delay(20_000L)
      stopSelf()
    }

    runCatching {
      tts?.shutdown()
      tts = TextToSpeech(this) { status ->
        if (status == TextToSpeech.SUCCESS) {
          val res = tts?.setLanguage(Locale.getDefault()) ?: TextToSpeech.LANG_NOT_SUPPORTED
          if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts?.setLanguage(Locale.US)
          }
          tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
              stopSelf()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
              stopSelf()
            }
          })
          tts?.speak(spokenText, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
        } else {
          Log.w(TAG, "Background TTS init failed: $status")
          stopSelf()
        }
      }
    }.onFailure {
      Log.e(TAG, "Background TTS exception", it)
      stopSelf()
    }

    return START_NOT_STICKY
  }

  override fun onDestroy() {
    safetyTimeoutJob?.cancel()
    serviceScope.cancel()
    runCatching {
      tts?.stop()
      tts?.shutdown()
    }
    tts = null
    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    super.onDestroy()
  }

  companion object {
    const val EXTRA_TITLE = "extra_alfred_title"
    const val EXTRA_SPOKEN_TEXT = "extra_alfred_spoken_text"
    const val EXTRA_NOTIFICATION_ID = "extra_alfred_notif_id"
    const val EXTRA_IS_WARNING = "extra_alfred_is_warning"

    fun announceInBackground(
      context: Context,
      title: String,
      message: String,
      notificationId: Int,
      speakAloud: Boolean,
      isWarning: Boolean = false,
    ) {
      if (isWarning) {
        VisionNotificationHelper.postWarningNotification(context, notificationId, title, message)
      } else {
        VisionNotificationHelper.postReminderNotification(context, notificationId, title, message)
      }
      if (!speakAloud) return

      runCatching {
        val intent = Intent(context, AlfredAnnouncementService::class.java).apply {
          putExtra(EXTRA_TITLE, title)
          putExtra(EXTRA_SPOKEN_TEXT, message)
          putExtra(EXTRA_NOTIFICATION_ID, notificationId)
          putExtra(EXTRA_IS_WARNING, isWarning)
        }
        ContextCompat.startForegroundService(context, intent)
      }.onFailure { e ->
        Log.w(TAG, "Could not start AlfredAnnouncementService in background", e)
      }
    }
  }
}

/**
 * Schedules background ALFRED announcements via AlarmManager using real stored Room tasks and daily schedule.
 */
object AlfredAnnouncementScheduler {

  const val ACTION_UPCOMING_TASK_ALERT = "com.example.vision.ACTION_UPCOMING_TASK_ALERT"
  const val ACTION_DAILY_REPORT_ALERT = "com.example.vision.ACTION_DAILY_REPORT_ALERT"
  const val EXTRA_TASK_ID = "extra_task_id"
  const val EXTRA_TASK_TITLE = "extra_task_title"
  const val EXTRA_TASK_TIME = "extra_task_time"

  private const val REQ_UPCOMING_TASK = 8801
  private const val REQ_DAILY_REPORT = 8802

  suspend fun rescheduleSmartAlerts(context: Context) {
    val db = VisionDatabase.getInstance(context)
    val repo = VisionRepository(db.visionDao())
    val todayTasks = db.visionDao().getTasksForDateSync(DateKeyHelper.todayKey())
    scheduleNextUpcomingTaskAlert(context, todayTasks)
    scheduleEveningDailyReportAlert(context)
    val cfg = repo.getFocusConfigSync()
    if (!cfg.aiMorningBriefing) {
      // Keep notification ready even if voice reading is toggled off; receiver checks aiMorningBriefing.
    }
  }

  fun scheduleNextUpcomingTaskAlert(
    context: Context,
    todayTasks: List<TaskEntity>,
    nowMillis: Long = System.currentTimeMillis(),
  ) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val nowMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

    val nextTask = todayTasks
      .filter { !it.isCompleted && it.scheduledMinutesOfDay > nowMinutes }
      .minByOrNull { it.scheduledMinutesOfDay }

    if (nextTask == null) {
      cancelPending(context, REQ_UPCOMING_TASK, ACTION_UPCOMING_TASK_ALERT)
      return
    }

    // Fire 10 minutes before task if still in future, otherwise 1 minute before task start
    val alertMinutes = (nextTask.scheduledMinutesOfDay - 10).let {
      if (it > nowMinutes) it else (nextTask.scheduledMinutesOfDay - 1).coerceAtLeast(nowMinutes + 1)
    }

    val triggerCal = Calendar.getInstance().apply {
      timeInMillis = nowMillis
      set(Calendar.HOUR_OF_DAY, alertMinutes / 60)
      set(Calendar.MINUTE, alertMinutes % 60)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }

    val intent = Intent(context, AlarmReceiver::class.java).apply {
      action = ACTION_UPCOMING_TASK_ALERT
      putExtra(EXTRA_TASK_ID, nextTask.id)
      putExtra(EXTRA_TASK_TITLE, nextTask.title)
      putExtra(EXTRA_TASK_TIME, nextTask.scheduledTimeText)
    }
    val pi = PendingIntent.getBroadcast(
      context,
      REQ_UPCOMING_TASK,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    scheduleExactOrFallback(alarmManager, context, triggerCal.timeInMillis, pi)
  }

  fun scheduleEveningDailyReportAlert(
    context: Context,
    nowMillis: Long = System.currentTimeMillis(),
  ) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val triggerCal = Calendar.getInstance().apply {
      timeInMillis = nowMillis
      set(Calendar.HOUR_OF_DAY, 20)
      set(Calendar.MINUTE, 30)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
      if (timeInMillis <= nowMillis + 1000L) {
        add(Calendar.DAY_OF_YEAR, 1)
      }
    }

    val intent = Intent(context, AlarmReceiver::class.java).apply {
      action = ACTION_DAILY_REPORT_ALERT
    }
    val pi = PendingIntent.getBroadcast(
      context,
      REQ_DAILY_REPORT,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    scheduleExactOrFallback(alarmManager, context, triggerCal.timeInMillis, pi)
  }

  private fun scheduleExactOrFallback(
    alarmManager: AlarmManager,
    context: Context,
    triggerAtMillis: Long,
    pi: PendingIntent,
  ) {
    try {
      if (AlarmScheduler.canScheduleExactAlarms(context)) {
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
      } else {
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
      }
    } catch (se: SecurityException) {
      Log.w(TAG, "Exact alarm denied for smart alert; falling back", se)
      alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
    }
  }

  private fun cancelPending(context: Context, reqCode: Int, actionName: String) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, AlarmReceiver::class.java).apply { action = actionName }
    val pi = PendingIntent.getBroadcast(
      context,
      reqCode,
      intent,
      PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    )
    if (pi != null) {
      alarmManager.cancel(pi)
      pi.cancel()
    }
  }
}
