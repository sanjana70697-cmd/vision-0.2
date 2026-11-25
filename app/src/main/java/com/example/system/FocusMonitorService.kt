package com.example.system

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.data.local.ActivitySessionEntity
import com.example.data.local.DateKeyHelper
import com.example.data.local.VisionDatabase
import com.example.data.local.VisionRepository
import com.example.domain.alfred.AlfredAnnouncementEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FocusMonitorService : Service() {

  companion object {
    const val ACTION_START_FOCUS = "com.example.vision.ACTION_START_FOCUS"
    const val ACTION_PAUSE_FOCUS = "com.example.vision.ACTION_PAUSE_FOCUS"
    const val ACTION_RESET_FOCUS = "com.example.vision.ACTION_RESET_FOCUS"
    const val ACTION_START_STUDY_SESSION = "com.example.vision.ACTION_START_STUDY_SESSION"
    const val ACTION_STOP_STUDY_SESSION = "com.example.vision.ACTION_STOP_STUDY_SESSION"
    const val ACTION_CANCEL_STUDY_WARNING = "com.example.vision.ACTION_CANCEL_STUDY_WARNING"
    const val EXTRA_PLANNED_MINUTES = "extra_planned_minutes"

    fun startFocus(context: Context, plannedMinutes: Int) {
      val intent = Intent(context, FocusMonitorService::class.java).apply {
        action = ACTION_START_FOCUS
        putExtra(EXTRA_PLANNED_MINUTES, plannedMinutes)
      }
      ContextCompat.startForegroundService(context, intent)
    }

    fun pauseFocus(context: Context) {
      val intent = Intent(context, FocusMonitorService::class.java).apply {
        action = ACTION_PAUSE_FOCUS
      }
      context.startService(intent)
    }

    fun resetFocus(context: Context, plannedMinutes: Int) {
      val intent = Intent(context, FocusMonitorService::class.java).apply {
        action = ACTION_RESET_FOCUS
        putExtra(EXTRA_PLANNED_MINUTES, plannedMinutes)
      }
      context.startService(intent)
    }

    fun startStudySession(context: Context, plannedMinutes: Int) {
      val intent = Intent(context, FocusMonitorService::class.java).apply {
        action = ACTION_START_STUDY_SESSION
        putExtra(EXTRA_PLANNED_MINUTES, plannedMinutes)
      }
      ContextCompat.startForegroundService(context, intent)
    }

    fun stopStudySession(context: Context) {
      val intent = Intent(context, FocusMonitorService::class.java).apply {
        action = ACTION_STOP_STUDY_SESSION
      }
      context.startService(intent)
    }

    fun cancelStudyWarning(context: Context) {
      val intent = Intent(context, FocusMonitorService::class.java).apply {
        action = ACTION_CANCEL_STUDY_WARNING
      }
      context.startService(intent)
    }
  }

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var tickerJob: Job? = null
  private val distractionTracker = StudyDistractionTracker(cooldownMillis = 120_000L)

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val db = VisionDatabase.getInstance(this)
    val repo = VisionRepository(db.visionDao())

    startFocusForeground(
      title = "VISION Focus Monitor",
      body = "Study & Focus monitoring active",
    )

    if (intent?.action == null) {
      serviceScope.launch {
        val cfg = repo.getFocusConfigSync()
        val persisted = ActiveTimerStore.get(this@FocusMonitorService, TimerActivityKind.FOCUS)
        if (persisted.isRunning || cfg.activeTimerRunning || cfg.studyWarningActive) {
          ensureLoopRunning(repo)
        } else {
          stopForegroundAndSelf()
        }
      }
      return START_NOT_STICKY
    }

    when (intent.action) {
      ACTION_START_FOCUS, ACTION_START_STUDY_SESSION -> {
        val isStudyExplicit = intent.action == ACTION_START_STUDY_SESSION
        val plannedMins = intent.getIntExtra(EXTRA_PLANNED_MINUTES, 25).coerceIn(5, 180)
        distractionTracker.reset()
        serviceScope.launch {
          val cfg = repo.getFocusConfigSync()
          val studyOn = isStudyExplicit || cfg.studyModeEnabled
          val existingTimer = ActiveTimerStore.get(this@FocusMonitorService, TimerActivityKind.FOCUS)
          val activeTimer = if (existingTimer.isActive && !existingTimer.isRunning && !isStudyExplicit) {
            ActiveTimerStore.resume(this@FocusMonitorService, TimerActivityKind.FOCUS)
          } else {
            ActiveTimerStore.start(
              context = this@FocusMonitorService,
              kind = TimerActivityKind.FOCUS,
              titleOrSkillName = if (studyOn) "Study Focus Session" else "Focus Session",
              plannedMinutes = plannedMins,
              studyMode = studyOn,
            )
          }

          val sessionId = if (cfg.activeSessionId > 0L && existingTimer.isActive) {
            cfg.activeSessionId
          } else {
            repo.startFocusSession(activeTimer.plannedMinutes, studyOn).getOrDefault(0L)
          }

          val initialRem = activeTimer.remainingCountdownSeconds()
          val hasUsage = UsageStatsHelper.isUsageAccessGranted(this@FocusMonitorService)
          val statusMsg = if (hasUsage) {
            "Study Session active: monitoring app switches via UsageStatsManager."
          } else {
            "Study timer active. Grant Usage Access in Settings to detect distracting app switches."
          }

          val nextCfg = repo.updateFocusConfig {
            it.copy(
              focusModeEnabled = true,
              studyModeEnabled = studyOn,
              activeTimerRunning = true,
              activeTimerRemainingSeconds = initialRem,
              activeSessionId = sessionId,
              studyWarningActive = false,
              studyGraceRemainingSeconds = 0,
              studyWarningMessage = if (studyOn) statusMsg else "",
              blockUntilMillis = System.currentTimeMillis() + it.blockDurationMinutes * 60_000L,
            )
          }.getOrNull() ?: cfg

          FocusEnforcementPrefs.syncFromRoom(this@FocusMonitorService, nextCfg, repo.getDistractingAppsSync())
          if (!existingTimer.isActive || isStudyExplicit) {
            val studyAnn = AlfredAnnouncementEngine.buildStudyStartAnnouncement(
              plannedMinutes = activeTimer.plannedMinutes,
              studyModeEnabled = nextCfg.studyModeEnabled,
            )
            if (nextCfg.aiMorningBriefing) {
              AlfredAnnouncementService.announceInBackground(
                context = this@FocusMonitorService,
                title = studyAnn.title,
                message = studyAnn.spokenMessage,
                notificationId = VisionNotificationHelper.FOCUS_NOTIFICATION_ID + 1,
                speakAloud = true,
              )
            }
          }
          ensureLoopRunning(repo)
        }
      }

      ACTION_PAUSE_FOCUS -> {
        serviceScope.launch {
          val pausedTimer = ActiveTimerStore.pause(this@FocusMonitorService, TimerActivityKind.FOCUS)
          val nextCfg = repo.updateFocusConfig {
            it.copy(
              activeTimerRunning = false,
              activeTimerRemainingSeconds = if (pausedTimer.isActive) pausedTimer.remainingCountdownSeconds() else it.activeTimerRemainingSeconds,
            )
          }.getOrNull()
          if (nextCfg != null) {
            FocusEnforcementPrefs.syncFromRoom(this@FocusMonitorService, nextCfg, repo.getDistractingAppsSync())
          }
          if (nextCfg?.focusModeEnabled != true && nextCfg?.studyWarningActive != true) {
            stopForegroundAndSelf()
          }
        }
      }

      ACTION_RESET_FOCUS, ACTION_STOP_STUDY_SESSION -> {
        val stopCompletely = intent.action == ACTION_STOP_STUDY_SESSION
        val plannedMins = intent.getIntExtra(EXTRA_PLANNED_MINUTES, 25).coerceIn(5, 180)
        distractionTracker.reset()
        serviceScope.launch {
          val cfg = repo.getFocusConfigSync()
          val completed = ActiveTimerStore.finishAndClear(this@FocusMonitorService, TimerActivityKind.FOCUS)
          val savedMins = completed?.durationMinutes ?: 0
          if (completed != null && savedMins > 0) {
            repo.saveActivitySession(
              ActivitySessionEntity(
                activityType = "FOCUS",
                dateKey = DateKeyHelper.todayKey(completed.startTimeMillis),
                titleOrSkillName = completed.titleOrSkillName.ifBlank { "Focus Session" },
                notes = if (cfg.studyModeEnabled) "Study Mode session" else "Focus timer session",
                startTimeMillis = completed.startTimeMillis,
                endTimeMillis = completed.endTimeMillis,
                durationMinutes = savedMins,
                source = "TIMER",
              ),
            )
          }
          if (cfg.activeSessionId > 0L) {
            repo.updateFocusSessionProgress(
              sessionId = cfg.activeSessionId,
              actualMinutes = savedMins,
              newStatus = "STOPPED",
              endMillis = System.currentTimeMillis(),
            )
          }
          val nextCfg = repo.updateFocusConfig {
            it.copy(
              focusModeEnabled = if (stopCompletely) false else it.focusModeEnabled,
              activeTimerRunning = false,
              activeTimerRemainingSeconds = plannedMins * 60,
              activeSessionId = 0L,
              studyWarningActive = false,
              studyWarningMessage = "",
              studyGraceRemainingSeconds = 0,
            )
          }.getOrNull()
          FocusEnforcementPrefs.setYoutubeRestrictedNow(this@FocusMonitorService, false)
          if (nextCfg != null) {
            FocusEnforcementPrefs.syncFromRoom(this@FocusMonitorService, nextCfg, repo.getDistractingAppsSync())
          }
          stopForegroundAndSelf()
        }
      }

      ACTION_CANCEL_STUDY_WARNING -> {
        FocusEnforcementPrefs.setYoutubeRestrictedNow(this, false)
        serviceScope.launch {
          val cfg = repo.updateFocusConfig {
            it.copy(
              studyWarningActive = false,
              studyWarningMessage = if (it.activeTimerRunning && it.studyModeEnabled) {
                "Study Session active: monitoring app switches via UsageStatsManager."
              } else {
                ""
              },
              studyGraceRemainingSeconds = 0,
            )
          }.getOrNull()
          if (cfg?.activeTimerRunning != true) {
            stopForegroundAndSelf()
          }
        }
      }
    }

    return START_STICKY
  }

  private fun ensureLoopRunning(repo: VisionRepository) {
    if (tickerJob?.isActive == true) return
    tickerJob = serviceScope.launch {
      var tickCount = 0
      while (true) {
        delay(1000L)
        tickCount++
        val cfg = repo.getFocusConfigSync()
        val apps = repo.getDistractingAppsSync()
        val timer = ActiveTimerStore.get(this@FocusMonitorService, TimerActivityKind.FOCUS)
        FocusEnforcementPrefs.syncFromRoom(this@FocusMonitorService, cfg, apps)

        var updatedCfg = cfg
        val isRunning = cfg.activeTimerRunning && (!timer.isActive || timer.isRunning)
        val shouldStop = !isRunning && !cfg.studyWarningActive

        // 1. Focus / Study Session Countdown computed accurately from persisted timestamps
        if (isRunning) {
          val now = System.currentTimeMillis()
          val nextRem = if (timer.isActive) {
            timer.remainingCountdownSeconds(now)
          } else {
            (cfg.activeTimerRemainingSeconds - 1).coerceAtLeast(0)
          }
          val elapsedMins = if (timer.isActive) {
            (timer.elapsedSeconds(now) / 60L).toInt()
          } else {
            ((25 * 60 - nextRem).coerceAtLeast(0)) / 60
          }

          if (nextRem == 0) {
            val completed = ActiveTimerStore.finishAndClear(this@FocusMonitorService, TimerActivityKind.FOCUS, now)
            val finalMins = (completed?.durationMinutes ?: elapsedMins).coerceAtLeast(1)
            val startMs = completed?.startTimeMillis ?: (now - finalMins * 60_000L)
            val endMs = completed?.endTimeMillis ?: now
            repo.saveActivitySession(
              ActivitySessionEntity(
                activityType = "FOCUS",
                dateKey = DateKeyHelper.todayKey(startMs),
                titleOrSkillName = completed?.titleOrSkillName?.ifBlank { "Focus Session" } ?: "Focus Session",
                notes = if (cfg.studyModeEnabled) "Completed Study Mode session" else "Completed Focus session",
                startTimeMillis = startMs,
                endTimeMillis = endMs,
                durationMinutes = finalMins,
                source = "TIMER",
              ),
            )
            if (cfg.activeSessionId > 0L) {
              repo.updateFocusSessionProgress(
                sessionId = cfg.activeSessionId,
                actualMinutes = finalMins,
                newStatus = "COMPLETED",
                endMillis = endMs,
              )
            }
            updatedCfg = updatedCfg.copy(
              focusModeEnabled = false,
              activeTimerRunning = false,
              activeTimerRemainingSeconds = (timer.plannedMinutes.coerceIn(5, 180)) * 60,
              activeSessionId = 0L,
              studyWarningActive = false,
              studyWarningMessage = "",
            )
            distractionTracker.reset()
            VisionNotificationHelper.postReminderNotification(
              context = this@FocusMonitorService,
              notificationId = VisionNotificationHelper.FOCUS_NOTIFICATION_ID + 2,
              title = "Study / Focus Session Complete",
              body = "Saved ${finalMins}m to your VISION Activity Breakdown.",
            )
          } else {
            updatedCfg = updatedCfg.copy(activeTimerRemainingSeconds = nextRem)
            if (tickCount % 60 == 0 && cfg.activeSessionId > 0L) {
              repo.updateFocusSessionProgress(
                sessionId = cfg.activeSessionId,
                actualMinutes = elapsedMins.coerceAtLeast(1),
              )
            }
          }
          if (tickCount % 5 == 0 && updatedCfg.activeTimerRunning) {
            val mins = nextRem / 60
            val secs = nextRem % 60
            startFocusForeground(
              title = if (cfg.studyModeEnabled) {
                "VISION Study Session · %02d:%02d".format(mins, secs)
              } else {
                "VISION Focus Active · %02d:%02d".format(mins, secs)
              },
              body = if (UsageStatsHelper.isUsageAccessGranted(this@FocusMonitorService)) {
                "Monitoring foreground app switches locally"
              } else {
                "Timer active (Usage Access not granted)"
              },
            )
          }
        }

        // 2. Real UsageStatsManager Foreground App-Switch Monitoring (every 2 seconds)
        if (
          tickCount % 2 == 0 &&
          updatedCfg.activeTimerRunning &&
          (updatedCfg.studyModeEnabled || updatedCfg.focusModeEnabled) &&
          UsageStatsHelper.isUsageAccessGranted(this@FocusMonitorService)
        ) {
          val foregroundPkg = UsageStatsHelper.getForegroundPackageName(this@FocusMonitorService)
          val monitoredSet = buildSet {
            apps.filter { it.isSelectedForMonitoring || it.isBlocked }.forEach { add(it.packageName) }
            if (updatedCfg.studyModeEnabled) {
              add(UsageStatsHelper.YOUTUBE_PACKAGE)
            }
          }

          val decision = distractionTracker.onForegroundObserved(
            ownPackage = packageName,
            foregroundPackage = foregroundPkg,
            monitoredPackages = monitoredSet,
          )

          if (decision.shouldNotify && decision.triggeredPackage != null) {
            val pkg = decision.triggeredPackage
            val matchedApp = apps.firstOrNull { it.packageName == pkg }
            val displayName = matchedApp?.appName ?: if (pkg == UsageStatsHelper.YOUTUBE_PACKAGE) "YouTube" else pkg
            val isYouTubeStudy = pkg == UsageStatsHelper.YOUTUBE_PACKAGE && updatedCfg.studyModeEnabled

            if (updatedCfg.activeSessionId > 0L) {
              repo.updateFocusSessionProgress(
                sessionId = updatedCfg.activeSessionId,
                actualMinutes = 0,
                warningDelta = 1,
                blockedDelta = if (matchedApp?.isBlocked == true && !isYouTubeStudy) 1 else 0,
              )
            }

            updatedCfg = updatedCfg.copy(
              studyWarningActive = true,
              studyGraceRemainingSeconds = 0,
              studyWarningMessage = "Switched to $displayName during your Study Session. Return to VISION to stay focused.",
            )

            val warnAnn = AlfredAnnouncementEngine.buildDistractionWarningAnnouncement(
              appName = displayName,
              isStudyModeYouTube = isYouTubeStudy,
              graceSeconds = updatedCfg.gracePeriodSeconds,
            )
            VisionNotificationHelper.postWarningNotification(
              context = this@FocusMonitorService,
              notificationId = VisionNotificationHelper.STUDY_WARNING_NOTIFICATION_ID,
              title = warnAnn.title,
              body = warnAnn.message,
            )
          }
        }

        repo.updateFocusConfig { updatedCfg }

        if (shouldStop || !updatedCfg.activeTimerRunning) {
          stopForegroundAndSelf()
          break
        }
      }
    }
  }

  private fun startFocusForeground(title: String, body: String) {
    val notif = VisionNotificationHelper.buildFocusForegroundNotification(this, title, body)
    val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
    } else {
      0
    }
    ServiceCompat.startForeground(
      this,
      VisionNotificationHelper.FOCUS_NOTIFICATION_ID,
      notif,
      fgsType,
    )
  }

  private fun stopForegroundAndSelf() {
    tickerJob?.cancel()
    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  override fun onDestroy() {
    tickerJob?.cancel()
    serviceScope.cancel()
    super.onDestroy()
  }
}
