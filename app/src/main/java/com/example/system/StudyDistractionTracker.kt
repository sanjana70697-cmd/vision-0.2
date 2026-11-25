package com.example.system

/**
 * Stateful tracker for real foreground app-switch detection during Study/Focus sessions.
 * Enforces per-package notification cooldowns and transition-only alerts so notifications
 * are never spammed repeatedly while the user remains in an app or switches briefly.
 */
class StudyDistractionTracker(
  private val cooldownMillis: Long = 120_000L,
) {
  private var lastObservedPackage: String? = null
  private val lastWarnedAtMillis = mutableMapOf<String, Long>()

  data class DetectionDecision(
    val shouldNotify: Boolean,
    val triggeredPackage: String? = null,
  )

  fun onForegroundObserved(
    ownPackage: String,
    foregroundPackage: String?,
    monitoredPackages: Set<String>,
    nowMillis: Long = System.currentTimeMillis(),
  ): DetectionDecision {
    if (foregroundPackage.isNullOrBlank()) {
      return DetectionDecision(shouldNotify = false)
    }

    if (foregroundPackage == ownPackage) {
      lastObservedPackage = foregroundPackage
      return DetectionDecision(shouldNotify = false)
    }

    val transitionedToPackage = foregroundPackage != lastObservedPackage
    lastObservedPackage = foregroundPackage

    if (!transitionedToPackage || foregroundPackage !in monitoredPackages) {
      return DetectionDecision(shouldNotify = false)
    }

    val lastWarned = lastWarnedAtMillis[foregroundPackage]
    if (lastWarned != null && nowMillis - lastWarned < cooldownMillis) {
      return DetectionDecision(shouldNotify = false)
    }

    lastWarnedAtMillis[foregroundPackage] = nowMillis
    return DetectionDecision(shouldNotify = true, triggeredPackage = foregroundPackage)
  }

  fun reset() {
    lastObservedPackage = null
    lastWarnedAtMillis.clear()
  }
}
