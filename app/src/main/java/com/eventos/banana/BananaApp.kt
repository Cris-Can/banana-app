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
import com.eventos.banana.core.security.AppCheckHelper
import javax.inject.Inject
import coil.ImageLoader
import coil.ImageLoaderFactory

@HiltAndroidApp
class BananaApp : Application(), Configuration.Provider, ImageLoaderFactory {
    
    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var imageLoader: ImageLoader

    /** Coil usará el ImageLoader con disk/memory cache configurado en AppModule. */
    override fun newImageLoader(): ImageLoader = imageLoader

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    @Inject
    lateinit var appCheckHelper: AppCheckHelper

    override fun onCreate() {
        super.onCreate()

        // 0. 🔒 CRITICAL: Initialize App Check BEFORE any Firebase usage
        appCheckHelper.initialize(BuildConfig.DEBUG)

        // 1. Timber Logging Init 🪵
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

        // 🔔 CRITICAL: Notification Channels MUST be created synchronously
        // If created in IO, a push could arrive before channels exist → silently dropped
        com.eventos.banana.util.NotificationHelper.createChannels(this)

        // 3. HEAVY INITS -> Background Thread 🚀
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            // 🌍 Places API (Network/Disk IO)
            if (!com.google.android.libraries.places.api.Places.isInitialized()) {
                com.google.android.libraries.places.api.Places.initialize(applicationContext, BuildConfig.PLACES_API_KEY)
            }

            // 📺 AdMob (Inicializa SDK + Pre-carga primer anuncio recompensado)
            try {
                com.eventos.banana.util.AdMobHelper.initialize(this@BananaApp)
                android.util.Log.d("BananaApp", "AdMobHelper.initialize() completed")
            } catch (e: Exception) {
                Timber.e(e, "AdMob init failed")
            }
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
