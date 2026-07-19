# ProGuard rules for LazyBrowser
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Room entities and DAOs
-keep class com.lazybrowser.app.data.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

# WebView JavaScript interface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep Application class
-keep class com.lazybrowser.app.LazyApp { *; }

# Keep Activities
-keep class com.lazybrowser.app.MainActivity { *; }
-keep class com.lazybrowser.app.reader.NovelReaderActivity { *; }
-keep class com.lazybrowser.app.settings.SettingsActivity { *; }

# AndroidX
-keep class androidx.** { *; }
-keep interface androidx.** { *; }

# Material Design
-keep class com.google.android.material.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
