# ProGuard rules for hr-automation-android

# Keep Room entities
-keep class com.hiringai.mobile.data.** { *; }

# Keep Retrofit interfaces
-keep,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Kotlin
-keepattributes *Annotation*
-keep class kotlin.Metadata { *; }
