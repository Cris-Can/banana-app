package com.eventos.banana

import android.app.Application
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BananaApp : Application() {

    override fun onCreate() {
        super.onCreate()

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
                com.google.android.libraries.places.api.Places.initialize(applicationContext, "AIzaSyCoCzzjj6ZIO6a-RH-9c-5JlYm2VlzRKCY")
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
