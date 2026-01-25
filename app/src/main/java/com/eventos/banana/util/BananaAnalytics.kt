package com.eventos.banana.util

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

object BananaAnalytics {
    private var firebaseAnalytics: FirebaseAnalytics? = null

    fun init(context: Context) {
        if (firebaseAnalytics == null) {
            firebaseAnalytics = FirebaseAnalytics.getInstance(context)
        }
    }

    private fun logEvent(eventName: String, params: Bundle?) {
        // Ensure initialization (safe fallback if init wasn't called, though usually it is in App or Activity)
        // Note: FirebaseAnalytics.getInstance needs Context. If not manually init, we might rely on it being initialized elsewhere or pass context.
        // For a clean singleton without context holding, we can use a weak reference or rely on the caller to pass context if needed, 
        // OR better: Just use a centralized "log" function that takes context or initialize in Application class.
        // Let's assume initialized in Application or allow lazy init if we change design.
        // Actually, let's keep it simple: getInstance requires context. 
        // We will make logEvent take context or rely on a stored instance (ApplicationContext safe).
        firebaseAnalytics?.logEvent(eventName, params)
    }

    fun logNfcEncounter(eventId: String, otherUserId: String) {
        val analytics = firebaseAnalytics ?: return
        
        val bundle = Bundle().apply {
            putString("event_id", eventId)
            putString("partner_user_id", otherUserId)
            putString("interaction_type", "nfc_tap")
        }
        analytics.logEvent("nfc_encounter_success", bundle)
    }
}
