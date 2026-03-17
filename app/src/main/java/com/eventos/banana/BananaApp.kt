package com.eventos.banana

import android.app.Application
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import com.google.firebase.crashlytics.FirebaseCrashlytics
import androidx.work.Configuration
import androidx.hilt.work.HiltWorkerFactory
import javax.inject.Inject

@HiltAndroidApp
class BananaApp : Application(), Configuration.Provider {
    
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // 0. Timber Logging Init 🪵
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(CrashlyticsTree())
        }

        // 1. CRITICAL: Firestore Settings (Fast, keep on Main)
        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true) // ✅ CACHE OFFLINE
            .build()
        FirebaseFirestore.getInstance().firestoreSettings = settings
        
        // 2. CRITICAL: Analytics (Fast)
        com.eventos.banana.util.BananaAnalytics.init(this)

        // 3. HEAVY INITS -> Background Thread 🚀
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            // 🌍 Places API (Network/Disk IO)
            if (!com.google.android.libraries.places.api.Places.isInitialized()) {
                com.google.android.libraries.places.api.Places.initialize(applicationContext, BuildConfig.PLACES_API_KEY)
            }

            // 📺 AdMob (Heavy Reflection/IPC)
            try {
                com.google.android.gms.ads.MobileAds.initialize(this@BananaApp) { }
            } catch (e: Exception) {
                android.util.Log.e("BananaApp", "AdMob init failed", e)
            }

            // 🔔 Notification Channels (System Service IPC)
            com.eventos.banana.util.NotificationHelper.createChannels(this@BananaApp)
        }
    }
}

/**
 * 🌲 Custom Timber Tree to report non-fatal errors to Crashlytics in Production
 */
class CrashlyticsTree : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority == android.util.Log.VERBOSE || priority == android.util.Log.DEBUG) {
            return
        }

        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.setCustomKey("priority", priority)
        tag?.let { crashlytics.setCustomKey("tag", it) }
        crashlytics.log(message)

        if (t != null) {
            crashlytics.recordException(t)
        }
    }
}
