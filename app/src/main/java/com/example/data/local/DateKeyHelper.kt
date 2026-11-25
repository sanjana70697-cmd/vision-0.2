package com.example.data.local

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object DateKeyHelper {
  private val keyFormatter: SimpleDateFormat
    get() = SimpleDateFormat("yyyy-MM-dd", Locale.US)

  private val displayDateFormatter: SimpleDateFormat
    get() = SimpleDateFormat("EEE, dd MMM yyyy", Locale.US)

  private val shortDayFormatter: SimpleDateFormat
    get() = SimpleDateFormat("EEE", Locale.US)

  fun todayKey(nowMillis: Long = System.currentTimeMillis()): String {
    val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
    return keyFormatter.format(cal.time)
  }

  fun tomorrowKey(nowMillis: Long = System.currentTimeMillis()): String {
    val cal = Calendar.getInstance().apply {
      timeInMillis = nowMillis
      add(Calendar.DAY_OF_YEAR, 1)
    }
    return keyFormatter.format(cal.time)
  }

  fun lastNDaysKeys(days: Int, nowMillis: Long = System.currentTimeMillis()): List<Pair<String, String>> {
    val result = mutableListOf<Pair<String, String>>()
    val cal = Calendar.getInstance().apply {
      timeInMillis = nowMillis
      add(Calendar.DAY_OF_YEAR, -(days - 1))
    }
    repeat(days) {
      val key = keyFormatter.format(cal.time)
      val label = shortDayFormatter.format(cal.time)
      result.add(key to label)
      cal.add(Calendar.DAY_OF_YEAR, 1)
    }
    return result
  }

  fun formatDisplayDate(nowMillis: Long = System.currentTimeMillis()): String {
    val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
    return displayDateFormatter.format(cal.time)
  }

  fun startOfTodayMillis(nowMillis: Long = System.currentTimeMillis()): Long {
    val cal = Calendar.getInstance().apply {
      timeInMillis = nowMillis
      set(Calendar.HOUR_OF_DAY, 0)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
  }

  fun formatTime12h(hour24: Int, minute: Int): Pair<String, String> {
    val period = if (hour24 < 12) "AM" else "PM"
    val hour12 = when {
      hour24 == 0 -> 12
      hour24 > 12 -> hour24 - 12
      else -> hour24
    }
    val timeStr = String.format(Locale.US, "%d:%02d", hour12, minute)
    return timeStr to period
  }

  fun formatFullTime12h(hour24: Int, minute: Int): String {
    val (t, p) = formatTime12h(hour24, minute)
    return "$t $p"
  }

  /**
   * Maps Calendar.DAY_OF_WEEK (Sun=1..Sat=7) to ISO day index (Mon=1..Sun=7).
   */
  fun isoDayOfWeek(cal: Calendar): Int = when (cal.get(Calendar.DAY_OF_WEEK)) {
    Calendar.MONDAY -> 1
    Calendar.TUESDAY -> 2
    Calendar.WEDNESDAY -> 3
    Calendar.THURSDAY -> 4
    Calendar.FRIDAY -> 5
    Calendar.SATURDAY -> 6
    Calendar.SUNDAY -> 7
    else -> 1
  }

  fun dayShortLabel(isoDay: Int): String = when (isoDay) {
    1 -> "Mon"
    2 -> "Tue"
    3 -> "Wed"
    4 -> "Thu"
    5 -> "Fri"
    6 -> "Sat"
    7 -> "Sun"
    else -> "Mon"
  }

  fun repeatSummary(repeatDaysCsv: String): String {
    if (repeatDaysCsv.isBlank()) return "Once"
    val days = repeatDaysCsv.split(",").mapNotNull { it.trim().toIntOrNull() }.sorted()
    return when {
      days == listOf(1, 2, 3, 4, 5, 6, 7) -> "Daily"
      days == listOf(1, 2, 3, 4, 5) -> "Mon – Fri"
      days == listOf(1, 2, 3, 4, 5, 6) -> "Mon – Sat"
      days == listOf(6, 7) -> "Weekends"
      else -> days.joinToString(" ") { dayShortLabel(it).take(2) }
    }
  }

  fun offsetDateKey(daysOffset: Int, nowMillis: Long = System.currentTimeMillis()): String {
    val cal = Calendar.getInstance().apply {
      timeInMillis = nowMillis
      add(Calendar.DAY_OF_YEAR, daysOffset)
    }
    return keyFormatter.format(cal.time)
  }

  fun parseDateKeyToCalendar(dateKey: String): Calendar? {
    val clean = dateKey.trim()
    if (!Regex("""^\d{4}-\d{2}-\d{2}$""").matches(clean)) return null
    return runCatching {
      val fmt = keyFormatter.apply { isLenient = false }
      val date = fmt.parse(clean) ?: return null
      Calendar.getInstance().apply {
        time = date
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
      }
    }.getOrNull()
  }

  fun formatDateKeyDisplay(dateKey: String, nowMillis: Long = System.currentTimeMillis()): String {
    if (dateKey.isBlank()) return ""
    val today = todayKey(nowMillis)
    val tomorrow = tomorrowKey(nowMillis)
    val yesterday = offsetDateKey(-1, nowMillis)
    val parsed = parseDateKeyToCalendar(dateKey)
    val shortPretty = parsed?.let {
      SimpleDateFormat("EEE, dd MMM", Locale.US).format(it.time)
    } ?: dateKey
    return when (dateKey) {
      today -> "Today · $shortPretty"
      tomorrow -> "Tomorrow · $shortPretty"
      yesterday -> "Yesterday · $shortPretty"
      else -> shortPretty
    }
  }

  fun combineDateKeyAndTime(dateKey: String, hour24: Int, minute: Int): Long {
    val base = parseDateKeyToCalendar(dateKey) ?: Calendar.getInstance()
    base.set(Calendar.HOUR_OF_DAY, hour24.coerceIn(0, 23))
    base.set(Calendar.MINUTE, minute.coerceIn(0, 59))
    base.set(Calendar.SECOND, 0)
    base.set(Calendar.MILLISECOND, 0)
    return base.timeInMillis
  }

  fun formatMillisTime12h(millis: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    return formatFullTime12h(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
  }

  fun formatMinutesSummary(totalMinutes: Int): String {
    val mins = totalMinutes.coerceAtLeast(0)
    if (mins < 60) return "${mins}m"
    val hrs = mins / 60
    val rem = mins % 60
    return if (rem == 0) "${hrs}h" else "${hrs}h ${rem}m"
  }

  fun formatElapsedClock(totalSeconds: Long): String {
    val secs = totalSeconds.coerceAtLeast(0L)
    val h = secs / 3600L
    val m = (secs % 3600L) / 60L
    val s = secs % 60L
    return if (h > 0L) String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%02d:%02d", m, s)
  }
}
