package com.eventos.banana.data.repository

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BiometricRepository @Inject constructor(
    context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "biometric_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_SAVED_EMAIL = "saved_email"
        private const val KEY_SAVED_PASSWORD = "saved_password"
    }

    fun isBiometricLoginEnabled(): Boolean {
        return prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
    }

    fun setBiometricLoginEnabled(enabled: Boolean, email: String? = null, password: String? = null) {
        prefs.edit()
            .putBoolean(KEY_BIOMETRIC_ENABLED, enabled)
            .apply {
                if (enabled && email != null && password != null) {
                    putString(KEY_SAVED_EMAIL, email)
                    putString(KEY_SAVED_PASSWORD, password)
                } else {
                    remove(KEY_SAVED_EMAIL)
                    remove(KEY_SAVED_PASSWORD)
                }
            }
            .apply()
    }

    fun getSavedCredentials(): Pair<String, String>? {
        val email = prefs.getString(KEY_SAVED_EMAIL, null) ?: return null
        val password = prefs.getString(KEY_SAVED_PASSWORD, null) ?: return null
        return Pair(email, password)
    }

    fun clearCredentials() {
        prefs.edit()
            .putBoolean(KEY_BIOMETRIC_ENABLED, false)
            .remove(KEY_SAVED_EMAIL)
            .remove(KEY_SAVED_PASSWORD)
            .apply()
    }
}
