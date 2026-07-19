# ProGuard rules for LazyBrowser
-keepattributes *Annotation*

# Keep Room entities
-keep class com.lazybrowser.app.data.** { *; }

# Keep WebView JavaScript interface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep AndroidX
-keep class androidx.** { *; }
-keep interface androidx.** { *; }
