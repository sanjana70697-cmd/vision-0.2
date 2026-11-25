package com.example.system

import android.content.Context
import kotlin.math.roundToInt

enum class TimerActivityKind(val key: String) {
  YOGA("YOGA"),
  SKILL("SKILL"),
  FOCUS("FOCUS"),
}

data class PersistedActivityTimer(
  val kind: TimerActivityKind,
  val isActive: Boolean = false,
  val isRunning: Boolean = false,
  val sessionStartEpochMillis: Long = 0L,
  val lastResumedEpochMillis: Long = 0L,
  val accumulatedActiveMillis: Long = 0L,
  val plannedMinutes: Int = 25,
  val titleOrSkillName: String = "",
  val notes: String = "",
  val studyMode: Boolean = false,
) {
  fun elapsedActiveMillis(nowMillis: Long = System.currentTimeMillis()): Long {
    if (!isActive) return 0L
    val currentStretch = if (isRunning && lastResumedEpochMillis > 0L) {
      (nowMillis - lastResumedEpochMillis).coerceAtLeast(0L)
    } else {
      0L
    }
    return accumulatedActiveMillis + currentStretch
  }

  fun elapsedSeconds(nowMillis: Long = System.currentTimeMillis()): Long =
    elapsedActiveMillis(nowMillis) / 1000L

  fun remainingCountdownSeconds(nowMillis: Long = System.currentTimeMillis()): Int {
    if (!isActive) return plannedMinutes * 60
    val rem = (plannedMinutes * 60L) - elapsedSeconds(nowMillis)
    return rem.coerceAtLeast(0L).toInt()
  }
}

data class CompletedTimerSnapshot(
  val kind: TimerActivityKind,
  val startTimeMillis: Long,
  val endTimeMillis: Long,
  val activeSeconds: Long,
  val durationMinutes: Int,
  val titleOrSkillName: String,
  val notes: String,
)

/**
 * Persistent wall-clock timer store backed by SharedPreferences.
 * Ensures Yoga, Skill Development, and Focus timers recover accurately across app closes/restarts
 * and pause/resume cycles never lose or double-count elapsed time.
 */
object ActiveTimerStore {
  private const val PREF_NAME = "vision_active_activity_timers"

  private fun k(type: TimerActivityKind, suffix: String) = "${type.key}_$suffix"

  fun get(context: Context, kind: TimerActivityKind): PersistedActivityTimer {
    val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    return PersistedActivityTimer(
      kind = kind,
      isActive = prefs.getBoolean(k(kind, "active"), false),
      isRunning = prefs.getBoolean(k(kind, "running"), false),
      sessionStartEpochMillis = prefs.getLong(k(kind, "start_ms"), 0L),
      lastResumedEpochMillis = prefs.getLong(k(kind, "resumed_ms"), 0L),
      accumulatedActiveMillis = prefs.getLong(k(kind, "acc_ms"), 0L),
      plannedMinutes = prefs.getInt(k(kind, "planned_min"), 25).coerceIn(1, 720),
      titleOrSkillName = prefs.getString(k(kind, "title"), "").orEmpty(),
      notes = prefs.getString(k(kind, "notes"), "").orEmpty(),
      studyMode = prefs.getBoolean(k(kind, "study"), false),
    )
  }

  private fun save(context: Context, timer: PersistedActivityTimer) {
    val kKind = timer.kind
    context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
      .edit()
      .putBoolean(k(kKind, "active"), timer.isActive)
      .putBoolean(k(kKind, "running"), timer.isRunning)
      .putLong(k(kKind, "start_ms"), timer.sessionStartEpochMillis)
      .putLong(k(kKind, "resumed_ms"), timer.lastResumedEpochMillis)
      .putLong(k(kKind, "acc_ms"), timer.accumulatedActiveMillis)
      .putInt(k(kKind, "planned_min"), timer.plannedMinutes)
      .putString(k(kKind, "title"), timer.titleOrSkillName)
      .putString(k(kKind, "notes"), timer.notes)
      .putBoolean(k(kKind, "study"), timer.studyMode)
      .commit()
  }

  fun start(
    context: Context,
    kind: TimerActivityKind,
    titleOrSkillName: String,
    notes: String = "",
    plannedMinutes: Int = 25,
    studyMode: Boolean = false,
    nowMillis: Long = System.currentTimeMillis(),
  ): PersistedActivityTimer {
    val timer = PersistedActivityTimer(
      kind = kind,
      isActive = true,
      isRunning = true,
      sessionStartEpochMillis = nowMillis,
      lastResumedEpochMillis = nowMillis,
      accumulatedActiveMillis = 0L,
      plannedMinutes = plannedMinutes.coerceIn(1, 720),
      titleOrSkillName = titleOrSkillName.trim(),
      notes = notes.trim(),
      studyMode = studyMode,
    )
    save(context, timer)
    return timer
  }

  fun pause(
    context: Context,
    kind: TimerActivityKind,
    nowMillis: Long = System.currentTimeMillis(),
  ): PersistedActivityTimer {
    val current = get(context, kind)
    if (!current.isActive || !current.isRunning) return current
    val totalAcc = current.elapsedActiveMillis(nowMillis)
    val paused = current.copy(
      isRunning = false,
      lastResumedEpochMillis = 0L,
      accumulatedActiveMillis = totalAcc,
    )
    save(context, paused)
    return paused
  }

  fun resume(
    context: Context,
    kind: TimerActivityKind,
    nowMillis: Long = System.currentTimeMillis(),
  ): PersistedActivityTimer {
    val current = get(context, kind)
    if (!current.isActive) return current
    if (current.isRunning) return current
    val resumed = current.copy(
      isRunning = true,
      lastResumedEpochMillis = nowMillis,
    )
    save(context, resumed)
    return resumed
  }

  fun updateNotes(context: Context, kind: TimerActivityKind, notes: String) {
    val current = get(context, kind)
    if (!current.isActive) return
    save(context, current.copy(notes = notes.trim()))
  }

  fun finishAndClear(
    context: Context,
    kind: TimerActivityKind,
    nowMillis: Long = System.currentTimeMillis(),
  ): CompletedTimerSnapshot? {
    val current = get(context, kind)
    if (!current.isActive) return null
    val activeMs = current.elapsedActiveMillis(nowMillis)
    val activeSecs = (activeMs / 1000L).coerceAtLeast(0L)
    val startMs = if (current.sessionStartEpochMillis > 0L) current.sessionStartEpochMillis else (nowMillis - activeMs)
    val mins = if (activeSecs <= 0L) {
      0
    } else {
      (activeSecs / 60.0).roundToInt().coerceIn(1, 720)
    }
    val safeEndMs = maxOf(startMs + (mins.coerceAtLeast(1) * 60_000L), nowMillis)
    clear(context, kind)
    return CompletedTimerSnapshot(
      kind = kind,
      startTimeMillis = startMs,
      endTimeMillis = safeEndMs,
      activeSeconds = activeSecs,
      durationMinutes = mins,
      titleOrSkillName = current.titleOrSkillName,
      notes = current.notes,
    )
  }

  fun clear(context: Context, kind: TimerActivityKind) {
    save(context, PersistedActivityTimer(kind = kind))
  }
}
