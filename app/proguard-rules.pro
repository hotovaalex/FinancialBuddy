# Financial Buddy ProGuard Rules

# Keep WebView JavaScript interface
-keepclassmembers class com.financialbuddy.app.MainActivity$AndroidBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep NotificationListenerService
-keep class com.financialbuddy.app.PaymentNotificationListener { *; }

# Keep all Activity classes
-keep class com.financialbuddy.app.** { *; }

# AndroidX
-keep class androidx.** { *; }
-dontwarn androidx.**

# Material
-keep class com.google.android.material.** { *; }
