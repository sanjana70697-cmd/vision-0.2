package com.example.worker

import android.content.Context
import android.util.Log
import androidx.work.Configuration
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.local.VisionDatabase
import com.example.data.local.VisionRepository
import com.example.system.AlfredAnnouncementScheduler
import java.util.concurrent.TimeUnit

class ProductivitySyncWorker(
  appContext: Context,
  params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

  override suspend fun doWork(): Result {
    val db = VisionDatabase.getInstance(applicationContext)
    val repo = VisionRepository(db.visionDao())
    runCatching { AlfredAnnouncementScheduler.rescheduleSmartAlerts(applicationContext) }
    return repo.syncTodaySnapshot().fold(
      onSuccess = { Result.success() },
      onFailure = { Result.retry() },
    )
  }

  companion object {
    private const val PERIODIC_NAME = "vision_periodic_productivity_sync"
    private const val IMMEDIATE_NAME = "vision_immediate_productivity_sync"

    fun enqueuePeriodicAndImmediate(context: Context) {
      runCatching {
        val appCtx = context.applicationContext
        if (!WorkManager.isInitialized()) {
          WorkManager.initialize(appCtx, Configuration.Builder().build())
        }
        val wm = WorkManager.getInstance(appCtx)
        val periodic = PeriodicWorkRequestBuilder<ProductivitySyncWorker>(15, TimeUnit.MINUTES)
          .build()
        wm.enqueueUniquePeriodicWork(
          PERIODIC_NAME,
          ExistingPeriodicWorkPolicy.KEEP,
          periodic,
        )
        val immediate = OneTimeWorkRequestBuilder<ProductivitySyncWorker>().build()
        wm.enqueueUniqueWork(
          IMMEDIATE_NAME,
          ExistingWorkPolicy.REPLACE,
          immediate,
        )
      }.onFailure {
        Log.w("ProductivitySyncWorker", "WorkManager enqueue skipped", it)
      }
    }
  }
}
