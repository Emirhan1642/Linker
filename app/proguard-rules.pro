# ============================================================================
# Linker ProGuard Configuration
# ============================================================================

# ── General Android ──────────────────────────────────────────────────────
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose
-dontoptimize
-dontpreverify

# Keep line numbers for debugging stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Kotlin ───────────────────────────────────────────────────────────────
-keepattributes *Annotation*
-dontwarn kotlin.**
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# ── Kotlin Coroutines ────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.** {
    volatile <fields>;
}

# ServiceLoader support
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Most of volatile fields are updated with AFU and should not be mangled
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ── Kotlin Serialization ─────────────────────────────────────────────────
-keepattributes InnerClasses
-keep,includedescriptorclasses class com.linker.app.**$$serializer { *; }
-keepclassmembers class com.linker.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.linker.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep `Companion` object fields of serializable classes
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# Keep `serializer()` on companion objects
-if @kotlinx.serialization.Serializable class ** {
    static **$Companion Companion;
}
-keepclassmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── Hilt/Dagger ──────────────────────────────────────────────────────────
-dontwarn com.google.errorprone.annotations.**
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.internal.Binding
-keep class * extends dagger.internal.ModuleAdapter
-keep class * extends dagger.internal.StaticInjection
-keepclasseswithmembers class * {
    @dagger.* <methods>;
}
-keep @dagger.Module class *
-keep @javax.inject.Singleton class *

# Hilt AndroidEntryPoint
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * extends androidx.lifecycle.ViewModel
-keep class * extends androidx.hilt.** { *; }

# ── Room Database ────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Keep DAO classes and methods
-keep interface * extends androidx.room.Dao {
    *;
}

# Keep entity constructors
-keepclassmembers @androidx.room.Entity class * {
    <init>(...);
}

# ── Retrofit & OkHttp ────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepattributes Signature
-keepattributes Exceptions
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# OkHttp platform used only on JVM and when Conscrypt and other security providers are available
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ── Firebase ─────────────────────────────────────────────────────────────
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Firebase Firestore models
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
}

# ── Compose ──────────────────────────────────────────────────────────────
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ── Data Models (Domain & Entity) ────────────────────────────────────────
-keep class com.linker.app.domain.model.** { *; }
-keep class com.linker.app.data.local.entity.** { *; }

# Keep data class copy methods
-keepclassmembers class com.linker.app.domain.model.** {
    public ** component1();
    public ** copy(...);
}

# ── Parcelable ───────────────────────────────────────────────────────────
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# ── Enum ─────────────────────────────────────────────────────────────────
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── Security (EncryptedSharedPreferences) ────────────────────────────────
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# ── Remove Logging (Optional - for extra security) ──────────────────────
# Uncomment to remove all Log calls in release
# -assumenosideeffects class android.util.Log {
#     public static *** d(...);
#     public static *** v(...);
#     public static *** i(...);
# }

# ============================================================================
# End of ProGuard Configuration
# ============================================================================
