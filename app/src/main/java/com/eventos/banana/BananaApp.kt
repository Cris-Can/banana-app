package com.eventos.banana

import android.app.Application
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

class BananaApp : Application() {

    override fun onCreate() {
        super.onCreate()

        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true) // ✅ CACHE OFFLINE
            .build()

        FirebaseFirestore.getInstance().firestoreSettings = settings
        
        com.eventos.banana.util.BananaAnalytics.init(this)

        // 🌍 Places API Initialization (Global Expansion)
        if (!com.google.android.libraries.places.api.Places.isInitialized()) {
            com.google.android.libraries.places.api.Places.initialize(applicationContext, "AIzaSyCoCzzjj6ZIO6a-RH-9c-5JlYm2VlzRKCY")
        }
    }
}
