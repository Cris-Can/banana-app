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
    }
}
