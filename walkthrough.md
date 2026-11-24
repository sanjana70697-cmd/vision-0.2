# Security Summary

**Reviewed Files**: 38
**Issues Resolved**: 7

### Issues Resolved
| Area | File | Line | What Was Happening | Impact | Status | How It Was Fixed |
|---|---|---|---|---|---|---|
| Accessibility Service Over-Privilege | `app/src/main/AndroidManifest.xml` | 100 | An exported Accessibility Service (`BIND_ACCESSIBILITY_SERVICE`) could observe window state transitions across every app on the device. | High | Fixed | Removed `FocusAccessibilityService`, its XML config, and the manifest declaration; Focus & Study monitoring runs strictly via local `UsageStatsManager` inside `FocusMonitorService`. |
| Broad Installed-App Enumeration | `app/src/main/AndroidManifest.xml` | 27 | A wildcard `ACTION_MAIN` / `CATEGORY_LAUNCHER` query combined with an init-time scan inventoried all installed apps on startup. | High | Fixed | Replaced the wildcard `<queries>` entry with an explicit list of 12 curated apps plus Speech/TTS services, moved discovery to lazy picker open (`loadCuratedDistractingApps`), and added manual package entry. |
| Debug App Check Provider in Release | `app/build.gradle.kts` | 123 | `firebase-appcheck-debug` was compiled into all build variants and could accept a debug token extra from the launch `Intent` in release APKs. | High | Fixed | Switched the dependency to `debugImplementation` and split `AppCheckProviderInstaller.kt` into `src/debug/` (debug provider) and `src/release/` (`RecaptchaAppCheckProviderFactory` only, ignoring `Intent` extras). |
| Unminified Release Build & Debug Logs | `app/build.gradle.kts` | 46 | Release builds had `isMinifyEnabled = false` and `isShrinkResources = false` with an empty ProGuard rules file. | Medium | Fixed | Enabled R8 code minification and resource shrinking for `release`, configured keep rules for Room/WorkManager/Firebase AI, and stripped `Log.v` / `Log.d` calls in release builds. |
| Unencrypted Traffic & App Data Backups | `app/src/main/AndroidManifest.xml` | 49 | App data backup (`allowBackup="true"`) lacked database/preference exclusions, and cleartext traffic was not explicitly blocked. | Medium | Fixed | Set `android:allowBackup="false"`, `android:usesCleartextTraffic="false"`, added `network_security_config.xml` (`cleartextTrafficPermitted="false"`), and excluded `database`, `sharedpref`, and `file` in backup rules XMLs. |
| Foreground Service Types & Lifecycle | `app/src/main/java/com/example/system/FocusMonitorService.kt` | 86 | `FocusMonitorService` declared `dataSync` instead of `specialUse` and risked running indefinitely on null-intent `START_STICKY` restarts; audio services lacked lifecycle-cancelled coroutine scopes. | Medium | Fixed | Switched to `FOREGROUND_SERVICE_SPECIAL_USE` with `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`, added a null-intent state check returning `START_NOT_STICKY` when idle, and bounded service scopes to `onDestroy()`. |
| In-Context Permission Rationales | `app/src/main/java/com/example/ui/components/VisionCommonComponents.kt` | 1086 | Microphone (`RECORD_AUDIO`) and notification (`POST_NOTIFICATIONS`) prompts launched directly without an in-app explanation dialog. | Low | Fixed | Added clear pre-request `AlertDialog` rationales in `CentralAiAssistantSheet` and `AlarmScreen`, plus an Android 14+ full-screen alarm intent readiness control. |

### Safeguards Verified
| Area | File | How It's Protected |
|---|---|---|
| Sensitive Permissions Audit | `app/src/main/AndroidManifest.xml` | Verified absent: `READ_CONTACTS`, `READ_SMS`, `READ_CALL_LOG`, `ACCESS_FINE_LOCATION`, `MANAGE_EXTERNAL_STORAGE`, `QUERY_ALL_PACKAGES`, `SYSTEM_ALERT_WINDOW`, and `REQUEST_INSTALL_PACKAGES`. |
| Exported Component Surface | `app/src/main/AndroidManifest.xml` | `AlarmRingActivity`, `AlarmReceiver`, `AlarmRingingService`, `FocusMonitorService`, and `AlfredAnnouncementService` are all `android:exported="false"`. Only `MainActivity` (launcher) and `BootCompletedReceiver` (validating 4 exact system actions) are exported. |
| PendingIntent Flags | `app/src/main/java/com/example/system/VisionNotificationHelper.kt` | Every `PendingIntent` across alarms, notifications, and announcements sets `PendingIntent.FLAG_IMMUTABLE`, preventing external intent mutation. |
| API Keys & Release Signing | `app/build.gradle.kts` | No hardcoded API keys or signing secrets exist in source; Gemini requests route through Firebase AI Logic with App Check, and release signing reads `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD` from environment variables. |

---

## PoC Verification

### 1. Debug App Check Token Injection in Release Builds

#### Summary
| Field | Value |
|---|---|
| Issue Type | Debug SDK & Intent Extra Exposure in Release Variant |
| Impact | High |
| Affected File | `app/src/release/java/com/example/system/AppCheckProviderInstaller.kt:12` |
| Test Scenario | Launching `MainActivity` with an Intent containing extra `FIREBASE_APPCHECK_DEBUG_TOKEN="forged-token"` on a release build |

#### How It Was Fixed
Moved `DebugAppCheckProviderFactory` and `FIREBASE_APPCHECK_DEBUG_TOKEN` intent parsing exclusively into the `debug` source set (`app/src/debug/java/com/example/system/AppCheckProviderInstaller.kt`) and changed `firebase-appcheck-debug` in `app/build.gradle.kts:123` to `debugImplementation`.

#### Step-by-Step Trace
| Step | What Happens in the Code | Outcome |
|---|---|---|
| 1 | `MainActivity.onCreate` calls `configureAppCheck(this, intent)` at `MainActivity.kt:57`. | `AppCheckInitializer.kt:15` delegates to `installVariantAppCheckProvider(context, intent)`. |
| 2 | In `release` builds, Gradle compiles `src/release/.../AppCheckProviderInstaller.kt` (lines 12–21); `DebugAppCheckProviderFactory` is not on the release classpath. | Forged `intent` extra is ignored (`@Suppress("UNUSED_PARAMETER")`). |
| 3 | `installVariantAppCheckProvider` reads `R.string.recaptcha_site_key` and installs `RecaptchaAppCheckProviderFactory`. | **Handled Safely** |

#### Conclusion
**Fix Verified** - Release APKs no longer bundle the Firebase App Check debug library or honor `FIREBASE_APPCHECK_DEBUG_TOKEN` Intent extras.

---

### 2. OS Sticky Restart of Idle FocusMonitorService

#### Summary
| Field | Value |
|---|---|
| Issue Type | Unbounded Background Service Execution on Null Intent |
| Impact | Medium |
| Affected File | `app/src/main/java/com/example/system/FocusMonitorService.kt:86` |
| Test Scenario | System kills and restarts `FocusMonitorService` with `intent = null` when no focus timer or study warning is active |

#### How It Was Fixed
Added an early `intent?.action == null` check in `FocusMonitorService.onStartCommand` that promotes the service to foreground temporarily as required by Android, checks Room state (`activeTimerRunning || studyWarningActive`), calls `stopForegroundAndSelf()` if idle, and returns `START_NOT_STICKY`.

#### Step-by-Step Trace
| Step | What Happens in the Code | Outcome |
|---|---|---|
| 1 | `FocusMonitorService.onStartCommand(null, ...)` calls `startFocusForeground(...)` (`FocusMonitorService.kt:81`) to satisfy Android foreground timing rules. | No foreground start exception occurs. |
| 2 | Guard at `FocusMonitorService.kt:86` detects `intent?.action == null`, loads `FocusConfigEntity` from Room, finds `activeTimerRunning == false` and `studyWarningActive == false`. | Enters idle shutdown branch (`FocusMonitorService.kt:91-93`). |
| 3 | `stopForegroundAndSelf()` cancels jobs, removes the foreground notification, calls `stopSelf()`, and `onStartCommand` returns `START_NOT_STICKY`. | **Handled Safely** |

#### Conclusion
**Fix Verified** - `FocusMonitorService` immediately terminates when restarted by the OS without an active user session or warning.

---

### 3. Startup Full-Device Package Inventory & Backup Extraction

#### Summary
| Field | Value |
|---|---|
| Issue Type | Over-Broad Package Visibility & Unprotected Local Data Backup |
| Impact | High |
| Affected File | `app/src/main/AndroidManifest.xml:27`, `app/src/main/AndroidManifest.xml:49` |
| Test Scenario | Calling `UsageStatsHelper.curatedDistractingApps(context)` or running `adb backup` on the package |

#### How It Was Fixed
Removed the wildcard `<intent><action android:name="android.intent.action.MAIN" /><category android:name="android.intent.category.LAUNCHER" /></intent>` query, replaced startup scanning with lazy evaluation of 12 declared packages, set `android:allowBackup="false"`, and excluded `database`, `sharedpref`, and `file` in `backup_rules.xml` and `data_extraction_rules.xml`.

#### Step-by-Step Trace
| Step | What Happens in the Code | Outcome |
|---|---|---|
| 1 | `VisionViewModel.init` (`VisionViewModel.kt:157-180`) initializes Room and permissions without calling any package-enumeration API. | Zero app inventory queries on app launch. |
| 2 | Only when the user taps `+` in `DistractionScreen.kt:697`, `UsageStatsHelper.curatedDistractingApps` checks the 12 explicit `<package>` entries allowed by `AndroidManifest.xml:34-45`. | Arbitrary installed apps are invisible to `PackageManager`. |
| 3 | Android backup/cloud-transfer checks `android:allowBackup="false"` (`AndroidManifest.xml:49`) plus `<exclude domain="database/sharedpref/file" />`. | **Handled Safely** |

#### Conclusion
**Fix Verified** - Full installed app lists are never queried and local user productivity databases/preferences are excluded from device backups.
