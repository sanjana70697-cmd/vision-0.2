package com.example.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.local.VisionDatabase
import com.example.data.local.VisionRepository
import com.example.worker.ProductivitySyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    val action = intent.action ?: return
    if (
      action != Intent.ACTION_BOOT_COMPLETED &&
      action != Intent.ACTION_TIME_CHANGED &&
      action != Intent.ACTION_TIMEZONE_CHANGED &&
      action != Intent.ACTION_MY_PACKAGE_REPLACED
    ) {
      return
    }

    val pendingResult: PendingResult? = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
      try {
        VisionNotificationHelper.ensureChannels(context)
        val db = VisionDatabase.getInstance(context)
        val repo = VisionRepository(db.visionDao())
        val enabledAlarms = repo.getEnabledAlarms()
        enabledAlarms.forEach { alarm ->
          val nextTrigger = AlarmScheduler.schedule(context, alarm)
          repo.saveAlarm(alarm.copy(nextTriggerMillis = nextTrigger))
        }
        AlfredAnnouncementScheduler.rescheduleSmartAlerts(context)
        ProductivitySyncWorker.enqueuePeriodicAndImmediate(context)
      } finally {
        pendingResult?.finish()
      }
    }
  }
}
