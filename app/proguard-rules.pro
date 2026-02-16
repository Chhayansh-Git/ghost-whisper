# Ghost Whisper ProGuard Rules

# Keep crypto classes (reflection used by javax.crypto)
-keep class com.ghostwhisper.crypto.** { *; }

# Keep Room entities & DAOs
-keep class com.ghostwhisper.data.model.** { *; }
-keep class com.ghostwhisper.data.db.** { *; }
-keep class * extends androidx.room.RoomDatabase

# Keep Accessibility Service
-keep class com.ghostwhisper.service.GhostWhisperService { *; }

# Keep Application class
-keep class com.ghostwhisper.GhostWhisperApp { *; }

# ZXing (QR code)
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# Room
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**

# Firebase
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler { *; }
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# AndroidX Lifecycle
-keep class androidx.lifecycle.** { *; }

# ViewModels
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Serialize/Deserialize (if used)
-keepattributes Signature
-keepattributes *Annotation*
