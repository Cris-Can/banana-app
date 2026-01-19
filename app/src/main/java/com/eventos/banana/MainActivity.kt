package com.eventos.banana

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.eventos.banana.navigation.AppNavigation
import com.eventos.banana.ui.theme.BananaTheme
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

import android.widget.Toast
import com.eventos.banana.utils.NetworkUtils

class MainActivity : FragmentActivity() {

    private val requestLocationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🔍 DIAGNÓSTICO: GMS Security Patch
        try {
            com.google.android.gms.security.ProviderInstaller.installIfNeeded(this)
        } catch (e: Exception) {
            android.util.Log.e("BANANA_DIAG", "Failed to install GMS Provider", e)
        }

        // 🛡️ APP CHECK DEBUG (Permite writes en entornos de prueba - IMPORTANTE)
        try {
            com.google.firebase.appcheck.FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory.getInstance()
            )
        } catch (e: Exception) {
            android.util.Log.e("BANANA_DIAG", "Failed to init App Check", e)
        }

        // 🔍 DIAGNÓSTICO DE RED MEJORADO
        NetworkUtils.checkRealConnectivity(this) { isConnected ->
             android.util.Log.e("BANANA_DIAG", "Real Internet Access: $isConnected")
             if (!isConnected) {
                 runOnUiThread {
                     Toast.makeText(this, "⚠️ WiFi conectado pero SIN INTERNET real (Posible fallo DNS)", Toast.LENGTH_LONG).show()
                 }
             }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }

        requestLocationPermission.launch(
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        // 🔔 Handle Notification Intent
        var initialRoute: String? = null
        val notifType = intent.getStringExtra("type")
        if (notifType == "FRIEND_REQUEST") {
            initialRoute = "profile" // We will handle tab selection in ProfileScreen or just nav to profile
        }

        setContent {
            val sessionViewModel: com.eventos.banana.viewmodel.SessionViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            val profileUiState by sessionViewModel.profileUiState.collectAsState()
            
            // Default to BANANA if not set or loading
            val currentTheme = profileUiState.profile?.appTheme ?: "BANANA"

            BananaTheme(themeMode = currentTheme) {
                // 🔒 Fix: Surface Raíz para garantizar fondo consistente en toda la app
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(startDestination = if (initialRoute == "profile") "profile" else "splash")
                }
            }
        }
    }
}
