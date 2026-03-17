# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep NotificationHelper to prevent stripping of channel constants
-keep class com.eventos.banana.util.NotificationHelper { *; }

# Firebase
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Firebase App Check
-keep class com.google.firebase.appcheck.** { *; }

# Firestore model classes (CRITICAL: prevents R8 from renaming fields used by CustomClassMapper)
-keep class com.eventos.banana.domain.model.** { *; }
-keepclassmembers class com.eventos.banana.domain.model.** { *; }

# ⚠️ DTOs in data.remote.model MUST also be kept — Firestore uses toObject() on these
-keep class com.eventos.banana.data.remote.model.** { *; }
-keepclassmembers class com.eventos.banana.data.remote.model.** { *; }

# Keep all Kotlin synthetic getter methods for boolean properties (isXxx)
# These are used by Firestore CustomClassMapper to detect properties
-keepclassmembers class com.eventos.banana.** {
    boolean is*();
    void set*(boolean);
    ** get*();
    void set**(**);
}

# Google Maps
-keep class com.google.android.gms.maps.** { *; }
-dontwarn com.google.android.gms.maps.**

# Google Places
-keep class com.google.android.libraries.places.** { *; }
-keepclassmembers class com.google.android.libraries.places.** { *; }
-keepclasseswithmembers class com.google.android.libraries.places.** { *; }
-dontwarn com.google.android.libraries.places.**
-keep class com.google.maps.places.** { *; }
-dontwarn com.google.maps.places.**

# Coil (Image Loading)
-dontwarn coil.**

# Keep Compose
-dontwarn androidx.compose.**

# Billing
-keep class com.android.vending.billing.** { *; }

# AdMob
-keep class com.google.android.gms.ads.** { *; }

# Timber
-keep class timber.log.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class com.google.dagger.hilt.** { *; }

# BuildConfig (Secrets)
-keep class com.eventos.banana.BuildConfig { *; }

# Keep Kotlin serialization (if used)
-keepattributes *Annotation*
-keep class kotlin.Metadata { *; }

# 🔒 Strip debug logs in production (security + performance)
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}