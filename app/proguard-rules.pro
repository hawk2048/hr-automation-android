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

# ONNX Runtime — JNI native methods must be preserved
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# llama.cpp Kotlin binding — JNI native methods
-keep class org.codeshipping.llamakotlin.** { *; }
-dontwarn org.codeshipping.llamakotlin.**
