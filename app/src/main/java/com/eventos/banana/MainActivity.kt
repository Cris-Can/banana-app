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
import com.eventos.banana.util.NetworkUtils

import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val requestLocationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 🔍 DIAGNÓSTICO: GMSS Security Patch (Background) - FIX: Usando lifecycleScope para evitar memory leaks (Paso 4 Auditoría)
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                com.google.android.gms.security.ProviderInstaller.installIfNeeded(this@MainActivity)
            } catch (e: Exception) {
                android.util.Log.e("BANANA_DIAG", "Failed to install GMS Provider", e)
            }
        }

        // 🛡️ APP CHECK — Debug uses Debug provider, Release uses Play Integrity
        try {
            val appCheck = com.google.firebase.appcheck.FirebaseAppCheck.getInstance()
            if (com.eventos.banana.BuildConfig.DEBUG) {
                // Debug builds: use debug provider (doesn't block Storage/Firestore)
                appCheck.installAppCheckProviderFactory(
                    com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory.getInstance()
                )
                android.util.Log.d("BANANA_DIAG", "App Check: Debug provider installed")
            } else {
                // Release builds: use Play Integrity
                appCheck.installAppCheckProviderFactory(
                    com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory.getInstance()
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("BANANA_DIAG", "Failed to init App Check", e)
        }
        
        setupContent(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        setupContent(intent)
    }

    private fun setupContent(intent: android.content.Intent?) {
        // 🔍 DIAGNÓSTICO DE RED MEJORADO
        lifecycleScope.launch {
            val isConnected = NetworkUtils.checkRealConnectivity(this@MainActivity)
            android.util.Log.e("BANANA_DIAG", "Real Internet Access: $isConnected")
            if (!isConnected) {
                Toast.makeText(this@MainActivity, "⚠️ WiFi conectado pero SIN INTERNET real", Toast.LENGTH_LONG).show()
            }
        }

        // 🔔 Request Notification Permission (Android 13+) — with rationale check
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notifPerm = Manifest.permission.POST_NOTIFICATIONS
            when {
                androidx.core.content.ContextCompat.checkSelfPermission(this, notifPerm) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED -> { /* Already granted */ }
                shouldShowRequestPermissionRationale(notifPerm) -> {
                    Toast.makeText(this, "Banana necesita notificaciones para avisarte de eventos y mensajes", Toast.LENGTH_LONG).show()
                    requestNotificationPermission.launch(notifPerm)
                }
                else -> requestNotificationPermission.launch(notifPerm)
            }
        }

        // 📍 Request Location Permission — with rationale check
        val locPerm = Manifest.permission.ACCESS_FINE_LOCATION
        when {
            androidx.core.content.ContextCompat.checkSelfPermission(this, locPerm) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED -> { /* Already granted */ }
            shouldShowRequestPermissionRationale(locPerm) -> {
                Toast.makeText(this, "Banana usa tu ubicación para mostrarte eventos cercanos", Toast.LENGTH_LONG).show()
                requestLocationPermission.launch(locPerm)
            }
            else -> requestLocationPermission.launch(locPerm)
        }


        // 🔔 Handle Notification Intent (Deep Linking)
        var initialRoute: String? = null
        val notifType = intent?.getStringExtra("type")
        val conversationId = intent?.getStringExtra("conversationId") ?: intent?.getStringExtra("eventId")

        when (notifType) {
            "FRIEND_REQUEST" -> {
                initialRoute = "friends?tab=1" // Tab de solicitudes
            }
            "FRIEND_ACCEPTED" -> {
                initialRoute = "friends?tab=0" // Tab de amigos
            }
            "NEW_MESSAGE" -> {
                val chatId = intent?.getStringExtra("conversationId")
                if (!chatId.isNullOrBlank()) {
                    initialRoute = "chat/$chatId"
                }
            }
            "JOIN_REQUEST_SENT" -> {
                // Ir al evento para decidir si aceptar/rechazar
                val evtId = intent?.getStringExtra("eventId")
                if (!evtId.isNullOrBlank()) {
                    initialRoute = "event_detail/$evtId"
                } else {
                    initialRoute = "notifications"
                }
            }
            "PROFILE_VIEW", "JOIN_REJECTED", "REMOVED_FROM_EVENT" -> {
                initialRoute = "notifications"
            }
            "EVENT_UPDATE" -> {
                // Mensaje en el muro → abrir directamente el tab "Muro"
                val evtId = intent?.getStringExtra("eventId")
                if (!evtId.isNullOrBlank()) {
                    initialRoute = "event_detail/$evtId?tab=1"
                } else {
                    initialRoute = "notifications"
                }
            }
            "JOIN_APPROVED", "EVENT_CREATED", "EVENT_CANCELLED", "EVENT_CLOSED" -> {
                // Eventos generales → abrir tab "Detalles"
                val evtId = intent?.getStringExtra("eventId")
                if (!evtId.isNullOrBlank()) {
                    initialRoute = "event_detail/$evtId"
                } else {
                    initialRoute = "notifications"
                }
            }
        }

        setContent {
            val sessionViewModel: com.eventos.banana.ui.auth.SessionViewModel = androidx.hilt.navigation.compose.hiltViewModel()
            val profileUiState by sessionViewModel.profileUiState.collectAsState()
            
            // Default to BANANA if not set or loading
            val currentTheme = profileUiState.profile?.appTheme ?: "BANANA"

            BananaTheme(themeMode = currentTheme) {
                // 🔒 Fix: Surface Raíz para garantizar fondo consistente en toda la app
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(startDestination = initialRoute ?: "splash")
                }
            }
        }
    }
}
