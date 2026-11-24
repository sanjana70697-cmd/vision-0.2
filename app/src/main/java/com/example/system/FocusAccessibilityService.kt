package com.example.system

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * User-consented AccessibilityService that enforces configured Focus Mode app restrictions
 * when a blocked distracting app enters the foreground.
 * Does not retrieve window text/content (canRetrieveWindowContent=false).
 */
class FocusAccessibilityService : AccessibilityService() {

  private var lastRedirectMillis = 0L

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
    val pkg = event.packageName?.toString() ?: return

    if (FocusEnforcementPrefs.shouldBlockPackage(this, pkg)) {
      val now = System.currentTimeMillis()
      if (now - lastRedirectMillis < 2000L) return
      lastRedirectMillis = now

      VisionNotificationHelper.postWarningNotification(
        context = this,
        notificationId = VisionNotificationHelper.STUDY_WARNING_NOTIFICATION_ID + 1,
        title = "VISION Focus Shield Active",
        body = "Returned home because $pkg is restricted during your active Focus session.",
      )
      performGlobalAction(GLOBAL_ACTION_HOME)
    }
  }

  override fun onInterrupt() {
    // No persistent resources to release
  }
}
