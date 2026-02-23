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

# Firestore model classes
-keep class com.eventos.banana.domain.model.** { *; }

# Google Maps
-keep class com.google.android.gms.maps.** { *; }
-dontwarn com.google.android.gms.maps.**

# Google Places
-keep class com.google.android.libraries.places.** { *; }
-dontwarn com.google.android.libraries.places.**

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