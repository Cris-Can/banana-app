package com.eventos.banana.ui.guide

import android.content.Context
import android.content.SharedPreferences

/**
 * Gestiona todos los flags de guías de inicio.
 * Cada guía se muestra UNA SOLA VEZ.
 */
class GuidePreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("banana_prefs", Context.MODE_PRIVATE)

    companion object {
        const val KEY_ONBOARDING = "onboarding_seen_v2"
        const val KEY_HOME = "home_guide_seen"
        const val KEY_PROFILE = "profile_guide_seen"
        const val KEY_CREATE_EVENT = "create_event_guide_seen"
        const val KEY_EVENT_DETAIL = "event_detail_guide_seen"
        const val KEY_CHAT = "chat_guide_seen"
        const val KEY_RANKING = "ranking_guide_seen"
        const val KEY_SEARCH = "search_guide_seen"
    }

    fun isGuideSeen(key: String): Boolean = prefs.getBoolean(key, false)
    fun markGuideSeen(key: String) { prefs.edit().putBoolean(key, true).apply() }
    fun resetAllGuides() {
        prefs.edit()
            .putBoolean(KEY_ONBOARDING, false)
            .putBoolean(KEY_HOME, false)
            .putBoolean(KEY_PROFILE, false)
            .putBoolean(KEY_CREATE_EVENT, false)
            .putBoolean(KEY_EVENT_DETAIL, false)
            .putBoolean(KEY_CHAT, false)
            .putBoolean(KEY_RANKING, false)
            .putBoolean(KEY_SEARCH, false)
            .apply()
    }
}
