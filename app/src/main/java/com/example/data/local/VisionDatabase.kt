package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Database(
  entities = [
    TaskEntity::class,
    GoalEntity::class,
    HabitLogEntity::class,
    AlarmEntity::class,
    DistractingAppEntity::class,
    FocusSessionEntity::class,
    FocusConfigEntity::class,
    DailyProductivitySnapshotEntity::class,
    ActivitySessionEntity::class,
    AlfredChatMessageEntity::class,
  ],
  version = 2,
  exportSchema = false,
)
abstract class VisionDatabase : RoomDatabase() {
  abstract fun visionDao(): VisionDao

  companion object {
    @Volatile
    private var INSTANCE: VisionDatabase? = null

    val MIGRATION_1_2 = object : Migration(1, 2) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE alarms ADD COLUMN scheduledDateKey TEXT NOT NULL DEFAULT ''")
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS `activity_sessions` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `activityType` TEXT NOT NULL,
            `dateKey` TEXT NOT NULL,
            `titleOrSkillName` TEXT NOT NULL,
            `notes` TEXT NOT NULL,
            `startTimeMillis` INTEGER NOT NULL,
            `endTimeMillis` INTEGER NOT NULL,
            `durationMinutes` INTEGER NOT NULL,
            `source` TEXT NOT NULL,
            `createdAtMillis` INTEGER NOT NULL
          )
          """.trimIndent(),
        )
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS `alfred_chat_messages` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `conversationId` TEXT NOT NULL,
            `isUser` INTEGER NOT NULL,
            `text` TEXT NOT NULL,
            `sourceLabel` TEXT NOT NULL,
            `executedActionsSerialized` TEXT NOT NULL,
            `timestampMillis` INTEGER NOT NULL
          )
          """.trimIndent(),
        )
        // Carry over historical non-zero focus sessions non-destructively
        db.execSQL(
          """
          INSERT INTO activity_sessions (
            activityType, dateKey, titleOrSkillName, notes, startTimeMillis, endTimeMillis, durationMinutes, source, createdAtMillis
          )
          SELECT
            'FOCUS',
            dateKey,
            CASE WHEN studyModeEnabled = 1 THEN 'Study Focus Session' ELSE 'Focus Session' END,
            'Migrated focus session (' || status || ')',
            startTimeMillis,
            CASE WHEN endTimeMillis IS NOT NULL AND endTimeMillis > startTimeMillis THEN endTimeMillis ELSE startTimeMillis + (actualMinutes * 60000) END,
            actualMinutes,
            'TIMER',
            startTimeMillis
          FROM focus_sessions
          WHERE actualMinutes > 0
          """.trimIndent(),
        )
        // Carry over historical non-zero habit logs (Yoga & Skill Dev) non-destructively
        db.execSQL(
          """
          INSERT INTO activity_sessions (
            activityType, dateKey, titleOrSkillName, notes, startTimeMillis, endTimeMillis, durationMinutes, source, createdAtMillis
          )
          SELECT
            CASE WHEN trackType = 'YOGA_EXERCISE' THEN 'YOGA' ELSE 'SKILL' END,
            dateKey,
            label,
            notes,
            CASE WHEN trackType = 'YOGA_EXERCISE' THEN COALESCE(CAST(strftime('%s', dateKey || ' 07:00:00') AS INTEGER) * 1000, 1700000000000) ELSE COALESCE(CAST(strftime('%s', dateKey || ' 10:00:00') AS INTEGER) * 1000, 1700010800000) END,
            (CASE WHEN trackType = 'YOGA_EXERCISE' THEN COALESCE(CAST(strftime('%s', dateKey || ' 07:00:00') AS INTEGER) * 1000, 1700000000000) ELSE COALESCE(CAST(strftime('%s', dateKey || ' 10:00:00') AS INTEGER) * 1000, 1700010800000) END) + (minutesLogged * 60000),
            minutesLogged,
            'MANUAL',
            COALESCE(CAST(strftime('%s', dateKey || ' 07:00:00') AS INTEGER) * 1000, 1700000000000)
          FROM habit_logs
          WHERE minutesLogged > 0
          """.trimIndent(),
        )
      }
    }

    fun getInstance(context: Context): VisionDatabase {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
      }
    }

    private fun buildDatabase(appContext: Context): VisionDatabase {
      return Room.databaseBuilder(
        appContext,
        VisionDatabase::class.java,
        "vision_productivity.db",
      )
        .addMigrations(MIGRATION_1_2)
        .addCallback(
          object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
              super.onCreate(db)
              CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                runCatching {
                  seedInitialStructure(getInstance(appContext).visionDao())
                }
              }
            }
          },
        )
        .build()
    }

    /**
     * Seeds starter daily routines, goals, alarms, and monitored app profiles ONLY on first DB creation.
     * Every item is fully editable and deletable by the user and persists across app restarts and reboots.
     */
    suspend fun seedInitialStructure(dao: VisionDao) {
      if (dao.getFocusConfigSync() != null) return

      val today = DateKeyHelper.todayKey()
      dao.upsertFocusConfig(
        FocusConfigEntity(
          id = 1,
          focusModeEnabled = false,
          studyModeEnabled = true,
          blockDurationMinutes = 60,
          gracePeriodSeconds = 30,
          autoBlockOnSchedule = true,
          educationalYoutubeFilter = true,
          strictLockMode = false,
          gentleEscalatingChime = true,
          aiMorningBriefing = true,
          focusSafeSnoozeLimit = false,
          nextDayPlanNote = "Review pending tasks, keep Morning Yoga at 8:00 AM, and continue skill development.",
          activeTimerRemainingSeconds = 25 * 60,
          activeTimerRunning = false,
        ),
      )

      // Starter Today Tasks & Routines
      dao.insertTask(
        TaskEntity(
          title = "Morning Yoga & Exercise",
          category = "Wellness",
          scheduledTimeText = "8:00 AM",
          scheduledMinutesOfDay = 8 * 60,
          isPriority = true,
          isRoutine = true,
          isCompleted = false,
          dateKey = today,
        ),
      )
      dao.insertTask(
        TaskEntity(
          title = "Study CPPM",
          category = "Skill Dev",
          scheduledTimeText = "10:00 AM",
          scheduledMinutesOfDay = 10 * 60,
          isPriority = true,
          isRoutine = false,
          isCompleted = false,
          dateKey = today,
        ),
      )
      dao.insertTask(
        TaskEntity(
          title = "Computer Class",
          category = "Skill Dev",
          scheduledTimeText = "6:00 PM",
          scheduledMinutesOfDay = 18 * 60,
          isPriority = false,
          isRoutine = true,
          isCompleted = false,
          dateKey = today,
        ),
      )

      // Starter Personal Goals
      dao.insertGoal(
        GoalEntity(
          title = "Learn Web Development",
          subtitle = "Core modules & hands-on practice",
          currentValue = 3,
          targetValue = 5,
          unit = "modules",
          category = "Skill Development",
        ),
      )
      dao.insertGoal(
        GoalEntity(
          title = "Morning Yoga Consistency",
          subtitle = "Daily mindful movement",
          currentValue = 0,
          targetValue = 7,
          unit = "days",
          category = "Wellness",
        ),
      )

      // Starter Habit Tracks for Today
      dao.insertHabit(
        HabitLogEntity(
          dateKey = today,
          trackType = "YOGA_EXERCISE",
          label = "Morning Yoga & Exercise",
          minutesLogged = 0,
          completed = false,
          notes = "30m target session",
        ),
      )
      dao.insertHabit(
        HabitLogEntity(
          dateKey = today,
          trackType = "SKILL_DEV",
          label = "Skill Development · Web & CPPM",
          minutesLogged = 0,
          completed = false,
          notes = "Track focused learning minutes",
        ),
      )

      // Starter Alarms (Disabled until user enables/grants permissions, or ready to arm)
      dao.insertAlarm(
        AlarmEntity(
          hour24 = 6,
          minute = 0,
          label = "Wake Up Alarm",
          kind = "ALARM",
          isWakeUp = true,
          repeatDaysCsv = "1,2,3,4,5,6,7",
          enabled = false,
        ),
      )
      dao.insertAlarm(
        AlarmEntity(
          hour24 = 10,
          minute = 0,
          label = "Study CPPM",
          kind = "REMINDER",
          isWakeUp = false,
          repeatDaysCsv = "1,2,3,4,5",
          enabled = false,
        ),
      )
      dao.insertAlarm(
        AlarmEntity(
          hour24 = 18,
          minute = 0,
          label = "Computer Class",
          kind = "SCHEDULE",
          isWakeUp = false,
          repeatDaysCsv = "1,2,3,4,5,6,7",
          enabled = false,
        ),
      )

      // Starter Monitored Distracting App Candidates (usage starts at 0 until UsageStatsManager syncs real data)
      dao.insertOrUpdateApps(
        listOf(
          DistractingAppEntity(
            packageName = "com.google.android.youtube",
            appName = "YouTube",
            shortTag = "YT",
            isSelectedForMonitoring = true,
            isBlocked = false,
            allowEducational = true,
            dailyLimitMinutes = 60,
            todayUsageMinutes = 0,
          ),
          DistractingAppEntity(
            packageName = "com.instagram.android",
            appName = "Instagram",
            shortTag = "IG",
            isSelectedForMonitoring = true,
            isBlocked = true,
            allowEducational = false,
            dailyLimitMinutes = 30,
            todayUsageMinutes = 0,
          ),
          DistractingAppEntity(
            packageName = "com.zhiliaoapp.musically",
            appName = "TikTok",
            shortTag = "TT",
            isSelectedForMonitoring = true,
            isBlocked = true,
            allowEducational = false,
            dailyLimitMinutes = 20,
            todayUsageMinutes = 0,
          ),
          DistractingAppEntity(
            packageName = "com.twitter.android",
            appName = "X / Twitter",
            shortTag = "X",
            isSelectedForMonitoring = true,
            isBlocked = true,
            allowEducational = false,
            dailyLimitMinutes = 20,
            todayUsageMinutes = 0,
          ),
        ),
      )
    }
  }
}
