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
-keepattributes SourceFile,LineNumberTable

# Gson
-keepattributes Signature
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ONNX Runtime — JNI native methods must be preserved
-keep class ai.onnxruntime.** { *; }
-keep class com.microsoft.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**
-dontwarn com.microsoft.onnxruntime.**

# llama.cpp Kotlin binding — JNI native methods
-keep class org.codeshipping.llamakotlin.** { *; }
-dontwarn org.codeshipping.llamakotlin.**

# Native methods (JNI)
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep models and serializable classes used by ML services
-keep class com.hiringai.mobile.ml.** { *; }
