package com.example.system

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
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
    const val ACTION_TRIGGER_STUDY_WARNING_CHECK = "com.example.vision.ACTION_TRIGGER_STUDY_WARNING_CHECK"
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

    fun triggerStudyWarningCheck(context: Context) {
      val intent = Intent(context, FocusMonitorService::class.java).apply {
        action = ACTION_TRIGGER_STUDY_WARNING_CHECK
      }
      ContextCompat.startForegroundService(context, intent)
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
  private var youtubeContinuousSeconds = 0
  private var lastNotifiedBlockedPkg: String? = null

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val db = VisionDatabase.getInstance(this)
    val repo = VisionRepository(db.visionDao())

    startForeground(
      VisionNotificationHelper.FOCUS_NOTIFICATION_ID,
      VisionNotificationHelper.buildFocusForegroundNotification(
        this,
        "VISION Focus Monitor",
        "Focus & Study Mode monitoring active",
      ),
    )

    when (intent?.action) {
      ACTION_START_FOCUS -> {
        val plannedMins = intent.getIntExtra(EXTRA_PLANNED_MINUTES, 25)
        serviceScope.launch {
          val cfg = repo.getFocusConfigSync()
          val initialRem = if (cfg.activeTimerRemainingSeconds > 0) cfg.activeTimerRemainingSeconds else plannedMins * 60
          val sessionId = if (cfg.activeSessionId > 0L) cfg.activeSessionId
          else repo.startFocusSession(plannedMins, cfg.studyModeEnabled).getOrDefault(0L)

          val nextCfg = repo.updateFocusConfig {
            it.copy(
              focusModeEnabled = true,
              activeTimerRunning = true,
              activeTimerRemainingSeconds = initialRem,
              activeSessionId = sessionId,
              blockUntilMillis = System.currentTimeMillis() + it.blockDurationMinutes * 60_000L,
            )
          }.getOrNull() ?: cfg
          FocusEnforcementPrefs.syncFromRoom(this@FocusMonitorService, nextCfg, repo.getDistractingAppsSync())
          val studyAnn = AlfredAnnouncementEngine.buildStudyStartAnnouncement(
            plannedMinutes = plannedMins,
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
          ensureLoopRunning(repo)
        }
      }

      ACTION_PAUSE_FOCUS -> {
        serviceScope.launch {
          val nextCfg = repo.updateFocusConfig {
            it.copy(activeTimerRunning = false)
          }.getOrNull()
          if (nextCfg != null) {
            FocusEnforcementPrefs.syncFromRoom(this@FocusMonitorService, nextCfg, repo.getDistractingAppsSync())
          }
          if (nextCfg?.focusModeEnabled != true && nextCfg?.studyWarningActive != true) {
            stopForegroundAndSelf()
          }
        }
      }

      ACTION_RESET_FOCUS -> {
        val plannedMins = intent.getIntExtra(EXTRA_PLANNED_MINUTES, 25)
        serviceScope.launch {
          val cfg = repo.getFocusConfigSync()
          if (cfg.activeSessionId > 0L) {
            repo.updateFocusSessionProgress(
              sessionId = cfg.activeSessionId,
              actualMinutes = ((plannedMins * 60 - cfg.activeTimerRemainingSeconds).coerceAtLeast(0)) / 60,
              newStatus = "STOPPED",
              endMillis = System.currentTimeMillis(),
            )
          }
          val nextCfg = repo.updateFocusConfig {
            it.copy(
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

      ACTION_TRIGGER_STUDY_WARNING_CHECK -> {
        serviceScope.launch {
          val cfg = repo.getFocusConfigSync()
          val grace = cfg.gracePeriodSeconds.coerceAtLeast(5)
          repo.updateFocusConfig {
            it.copy(
              studyWarningActive = true,
              studyGraceRemainingSeconds = grace,
              studyWarningMessage = "Extended YouTube usage flagged in Study Mode. Return to study within ${grace}s or restriction applies.",
            )
          }
          val studyWarn = AlfredAnnouncementEngine.buildDistractionWarningAnnouncement(
            appName = "YouTube",
            isStudyModeYouTube = true,
            graceSeconds = grace,
          )
          AlfredAnnouncementService.announceInBackground(
            context = this@FocusMonitorService,
            title = studyWarn.title,
            message = studyWarn.spokenMessage,
            notificationId = VisionNotificationHelper.STUDY_WARNING_NOTIFICATION_ID,
            speakAloud = cfg.aiMorningBriefing,
            isWarning = true,
          )
          ensureLoopRunning(repo)
        }
      }

      ACTION_CANCEL_STUDY_WARNING -> {
        youtubeContinuousSeconds = 0
        FocusEnforcementPrefs.setYoutubeRestrictedNow(this, false)
        serviceScope.launch {
          val cfg = repo.updateFocusConfig {
            it.copy(
              studyWarningActive = false,
              studyWarningMessage = "",
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
        FocusEnforcementPrefs.syncFromRoom(this@FocusMonitorService, cfg, apps)

        var updatedCfg = cfg
        var shouldStop = !cfg.activeTimerRunning && !cfg.studyWarningActive

        // 1. Focus Session Countdown
        if (cfg.activeTimerRunning) {
          val nextRem = (cfg.activeTimerRemainingSeconds - 1).coerceAtLeast(0)
          val elapsedMins = ((25 * 60 - nextRem).coerceAtLeast(0)) / 60
          if (nextRem == 0) {
            if (cfg.activeSessionId > 0L) {
              repo.updateFocusSessionProgress(
                sessionId = cfg.activeSessionId,
                actualMinutes = elapsedMins.coerceAtLeast(1),
                newStatus = "COMPLETED",
                endMillis = System.currentTimeMillis(),
              )
            }
            updatedCfg = updatedCfg.copy(
              activeTimerRunning = false,
              activeTimerRemainingSeconds = 25 * 60,
              activeSessionId = 0L,
            )
            VisionNotificationHelper.postReminderNotification(
              context = this@FocusMonitorService,
              notificationId = VisionNotificationHelper.FOCUS_NOTIFICATION_ID + 2,
              title = "Focus Session Complete",
              body = "Great focus! Your session has been recorded to VISION Reports.",
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
          if (tickCount % 5 == 0) {
            val mins = nextRem / 60
            val secs = nextRem % 60
            val notif = VisionNotificationHelper.buildFocusForegroundNotification(
              this@FocusMonitorService,
              "VISION Focus Active · %02d:%02d".format(mins, secs),
              if (cfg.studyModeEnabled) "Study Mode enabled (YouTube educational allowance)"
              else "Blocking selected distracting apps",
            )
            startForeground(VisionNotificationHelper.FOCUS_NOTIFICATION_ID, notif)
          }
        }

        // 2. Study Mode Grace Period Countdown
        if (updatedCfg.studyWarningActive) {
          val nextGrace = (updatedCfg.studyGraceRemainingSeconds - 1).coerceAtLeast(0)
          if (nextGrace == 0) {
            FocusEnforcementPrefs.setYoutubeRestrictedNow(this@FocusMonitorService, true)
            if (updatedCfg.activeSessionId > 0L) {
              repo.updateFocusSessionProgress(
                sessionId = updatedCfg.activeSessionId,
                actualMinutes = 1,
                warningDelta = 1,
                blockedDelta = 1,
              )
            }
            updatedCfg = updatedCfg.copy(
              studyWarningActive = false,
              studyGraceRemainingSeconds = 0,
              studyWarningMessage = "Grace period ended: YouTube restricted for this Focus window.",
            )
            VisionNotificationHelper.postWarningNotification(
              context = this@FocusMonitorService,
              notificationId = VisionNotificationHelper.STUDY_WARNING_NOTIFICATION_ID,
              title = "Study Mode Restriction Applied",
              body = "Grace period elapsed. YouTube is now restricted until Study Mode resets.",
            )
          } else {
            updatedCfg = updatedCfg.copy(
              studyGraceRemainingSeconds = nextGrace,
              studyWarningMessage = "Study Mode Warning: return to study within ${nextGrace}s or restriction applies.",
            )
          }
        }

        // 3. Real UsageStatsManager Foreground Polling every 3 seconds
        if (tickCount % 3 == 0 && UsageStatsHelper.isUsageAccessGranted(this@FocusMonitorService)) {
          val pkg = UsageStatsHelper.getForegroundPackageName(this@FocusMonitorService)
          if (pkg != null && pkg != packageName) {
            val matchedApp = apps.firstOrNull { it.packageName == pkg }
            if (pkg == UsageStatsHelper.YOUTUBE_PACKAGE && updatedCfg.studyModeEnabled) {
              youtubeContinuousSeconds += 3
              // After 15 minutes continuous YouTube session during Focus, warn unless user dismisses
              if (youtubeContinuousSeconds >= 15 * 60 && !updatedCfg.studyWarningActive) {
                val grace = updatedCfg.gracePeriodSeconds.coerceAtLeast(10)
                updatedCfg = updatedCfg.copy(
                  studyWarningActive = true,
                  studyGraceRemainingSeconds = grace,
                  studyWarningMessage = "15m continuous YouTube detected in Study Mode. Confirm educational study or return within ${grace}s.",
                )
                val youtubeWarn = AlfredAnnouncementEngine.buildDistractionWarningAnnouncement(
                  appName = "YouTube",
                  isStudyModeYouTube = true,
                  graceSeconds = grace,
                )
                AlfredAnnouncementService.announceInBackground(
                  context = this@FocusMonitorService,
                  title = youtubeWarn.title,
                  message = youtubeWarn.spokenMessage,
                  notificationId = VisionNotificationHelper.STUDY_WARNING_NOTIFICATION_ID,
                  speakAloud = updatedCfg.aiMorningBriefing,
                  isWarning = true,
                )
              }
            } else {
              youtubeContinuousSeconds = 0
              if (matchedApp != null && matchedApp.isBlocked && updatedCfg.focusModeEnabled) {
                if (lastNotifiedBlockedPkg != pkg) {
                  lastNotifiedBlockedPkg = pkg
                  if (updatedCfg.activeSessionId > 0L) {
                    repo.updateFocusSessionProgress(
                      sessionId = updatedCfg.activeSessionId,
                      actualMinutes = 0,
                      blockedDelta = 1,
                    )
                  }
                  val appWarn = AlfredAnnouncementEngine.buildDistractionWarningAnnouncement(
                    appName = matchedApp.appName,
                    isStudyModeYouTube = false,
                  )
                  AlfredAnnouncementService.announceInBackground(
                    context = this@FocusMonitorService,
                    title = appWarn.title,
                    message = appWarn.spokenMessage,
                    notificationId = VisionNotificationHelper.STUDY_WARNING_NOTIFICATION_ID + 2,
                    speakAloud = updatedCfg.aiMorningBriefing,
                    isWarning = true,
                  )
                }
              } else {
                lastNotifiedBlockedPkg = null
              }
            }
          }
        }

        repo.updateFocusConfig { updatedCfg }

        if (shouldStop) {
          stopForegroundAndSelf()
          break
        }
      }
    }
  }

  private fun stopForegroundAndSelf() {
    tickerJob?.cancel()
    stopForeground(STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  override fun onDestroy() {
    tickerJob?.cancel()
    serviceScope.cancel()
    super.onDestroy()
  }
}
