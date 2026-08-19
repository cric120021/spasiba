# ProGuard & R8 Optimization Rules for Spasiba Music

# Media3 & ExoPlayer
-keep class androidx.media3.common.** { *; }
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.session.** { *; }
-keep class androidx.media3.ui.** { *; }
-dontwarn androidx.media3.**

# Kotlin Coroutines & Flow
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler { *; }
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# OkHttp & Okio
-keepattributes Signature
-keepattributes *Annotation*
-keepclassmembers class okhttp3.internal.publicsuffix.PublicSuffixDatabase {
    private byte[] *;
}
-dontwarn okhttp3.**
-dontwarn okio.**

# Gson & Model Reflection
-keepattributes EnclosingMethod
-keep class com.google.gson.** { *; }
-keep class app.spasiba.nativeapp2.data.model.** { *; }
-keep class app.spasiba.nativeapp2.data.api.** { *; }

# Room Database
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# WebRTC & JNI Libs
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**
