package com.example.system

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.MainActivity
import com.example.data.local.AlarmEntity
import com.example.data.local.DateKeyHelper
import java.util.Calendar

object AlarmScheduler {

  private const val TAG = "AlarmScheduler"

  fun canScheduleExactAlarms(context: Context): Boolean {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      alarmManager.canScheduleExactAlarms()
    } else {
      true
    }
  }

  fun computeNextTriggerMillis(
    alarm: AlarmEntity,
    fromMillis: Long = System.currentTimeMillis(),
  ): Long {
    val repeatDays = alarm.repeatDaysCsv
      .split(",")
      .mapNotNull { it.trim().toIntOrNull() }
      .toSet()

    val candidate = Calendar.getInstance().apply {
      timeInMillis = fromMillis
      set(Calendar.HOUR_OF_DAY, alarm.hour24)
      set(Calendar.MINUTE, alarm.minute)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }

    if (repeatDays.isEmpty()) {
      if (candidate.timeInMillis <= fromMillis + 1000L) {
        candidate.add(Calendar.DAY_OF_YEAR, 1)
      }
      return candidate.timeInMillis
    }

    for (offset in 0..7) {
      val probe = (candidate.clone() as Calendar).apply {
        add(Calendar.DAY_OF_YEAR, offset)
      }
      val isoDay = DateKeyHelper.isoDayOfWeek(probe)
      if (isoDay in repeatDays && probe.timeInMillis > fromMillis + 1000L) {
        return probe.timeInMillis
      }
    }
    candidate.add(Calendar.DAY_OF_YEAR, 1)
    return candidate.timeInMillis
  }

  fun schedule(context: Context, alarm: AlarmEntity): Long {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val triggerAtMillis = computeNextTriggerMillis(alarm)
    val triggerPendingIntent = buildTriggerPendingIntent(context, alarm)

    try {
      if (alarm.kind == "ALARM" && canScheduleExactAlarms(context)) {
        val showIntent = PendingIntent.getActivity(
          context,
          alarm.id.toInt(),
          Intent(context, MainActivity::class.java),
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val info = AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent)
        alarmManager.setAlarmClock(info, triggerPendingIntent)
      } else if (canScheduleExactAlarms(context)) {
        alarmManager.setExactAndAllowWhileIdle(
          AlarmManager.RTC_WAKEUP,
          triggerAtMillis,
          triggerPendingIntent,
        )
      } else {
        alarmManager.setAndAllowWhileIdle(
          AlarmManager.RTC_WAKEUP,
          triggerAtMillis,
          triggerPendingIntent,
        )
      }
    } catch (se: SecurityException) {
      Log.w(TAG, "Exact alarm permission denied; falling back to setAndAllowWhileIdle", se)
      alarmManager.setAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP,
        triggerAtMillis,
        triggerPendingIntent,
      )
    }
    return triggerAtMillis
  }

  fun scheduleSnooze(
    context: Context,
    alarmId: Long,
    label: String,
    snoozeMinutes: Int = 5,
  ) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val triggerAt = System.currentTimeMillis() + snoozeMinutes * 60_000L
    val intent = Intent(context, AlarmReceiver::class.java).apply {
      action = AlarmReceiver.ACTION_TRIGGER_ALARM
      putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
      putExtra(AlarmReceiver.EXTRA_ALARM_LABEL, "$label (Snoozed)")
      putExtra(AlarmReceiver.EXTRA_ALARM_KIND, "ALARM")
      putExtra(AlarmReceiver.EXTRA_ALARM_TIME, "Snooze")
    }
    val pi = PendingIntent.getBroadcast(
      context,
      alarmId.toInt() + 50000,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    try {
      if (canScheduleExactAlarms(context)) {
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
      } else {
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
      }
    } catch (se: SecurityException) {
      Log.w(TAG, "Snooze exact alarm permission denied", se)
      alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
    }
  }

  fun cancel(context: Context, alarmId: Long) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, AlarmReceiver::class.java).apply {
      action = AlarmReceiver.ACTION_TRIGGER_ALARM
    }
    val pi = PendingIntent.getBroadcast(
      context,
      alarmId.toInt(),
      intent,
      PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    )
    if (pi != null) {
      alarmManager.cancel(pi)
      pi.cancel()
    }
  }

  private fun buildTriggerPendingIntent(context: Context, alarm: AlarmEntity): PendingIntent {
    val intent = Intent(context, AlarmReceiver::class.java).apply {
      action = AlarmReceiver.ACTION_TRIGGER_ALARM
      putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarm.id)
      putExtra(AlarmReceiver.EXTRA_ALARM_LABEL, alarm.label)
      putExtra(AlarmReceiver.EXTRA_ALARM_KIND, alarm.kind)
      putExtra(
        AlarmReceiver.EXTRA_ALARM_TIME,
        DateKeyHelper.formatFullTime12h(alarm.hour24, alarm.minute),
      )
    }
    return PendingIntent.getBroadcast(
      context,
      alarm.id.toInt(),
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }
}
