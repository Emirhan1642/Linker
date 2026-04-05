# ============================================================================
# Linker ProGuard / R8 Configuration
# ============================================================================
#
# Not: Bazı kurallar kasıtlı olarak geniş tutuldu (kotlin.** / firebase.** /
# compose.**). Kütüphane consumer kurallarına ek güvence sağlar; daraltmadan
# önce mutlaka `assembleRelease` + smoke test çalıştırın.
# ============================================================================

# ── General Android ──────────────────────────────────────────────────────
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose

# R8 varsayılan olarak optimize + shrink uygular (proguard-android-optimize).
# İleride gizli R8 hatası çıkarsa geçici olarak aşağıdaki satırı açabilirsiniz:
# -dontoptimize

# Keep line numbers for debugging stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Kotlin ───────────────────────────────────────────────────────────────
-keepattributes *Annotation*
-dontwarn kotlin.**
# Geniş tutuluyor: reflection / Metadata ile uyumluluk (daraltma riski yüksek).
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# ── Kotlin Coroutines ──────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.** {
    volatile <fields>;
}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ── Kotlin Serialization ─────────────────────────────────────────────────
# Uygulama paketi: generated $$serializer ve Companion
-keepattributes InnerClasses
-keep,includedescriptorclasses class com.linker.app.**$$serializer { *; }
-keepclassmembers class com.linker.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.linker.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Serializable sınıfların Companion alanı
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# serializer() yalnızca **$Companion üzerinde — tüm sınıflara yaymayın (eski hata).
-if @kotlinx.serialization.Serializable class ** {
    static **$Companion Companion;
}
-keepclassmembers class **$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── Hilt / Dagger 2 ─────────────────────────────────────────────────────
-dontwarn com.google.errorprone.annotations.**
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }
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

# ── Firebase / Play Services ───────────────────────────────────────────────
# Geniş tutuluyor: Firestore/Auth/FCM reflection; daraltma önce release test şart.
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# @PropertyName ile işaretlenmiş alanlar
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
}

# Firestore toObject() için: iç repository DTO'larında @androidx.annotation.Keep kullanın.

# ── Compose ──────────────────────────────────────────────────────────────
# Geniş tutuluyor: compiler/runtime iç sınıfları; kaldırmadan önce release doğrula.
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

# ── Logging (release sertleştirme) ───────────────────────────────────────
# d/v/i kaldırır; w/e ve Throwable’lı overload’lar kalır (hata ayıklama için).
# İhtiyaç yoksa yorumda bırakın.
# -assumenosideeffects class android.util.Log {
#     public static *** d(...);
#     public static *** v(...);
#     public static *** i(...);
# }

# ============================================================================
# End of ProGuard Configuration
# ============================================================================
