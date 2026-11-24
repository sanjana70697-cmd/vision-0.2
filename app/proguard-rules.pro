# Production R8 / ProGuard configuration for VISION

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Preserve Room database & generated implementations
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# Preserve WorkManager workers instantiated via reflection
-keep class com.example.worker.** { *; }

# Preserve Firebase AI Logic / App Check model & schema reflection if needed
-keep class com.google.firebase.ai.** { *; }
-keepclassmembers class com.google.firebase.appcheck.** { *; }

# Strip debug-only logs in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}
