package com.eventos.banana

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.eventos.banana.navigation.AppNavigation
import com.eventos.banana.navigation.Screen
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.LocationOn
import com.eventos.banana.ui.components.PermissionRationaleDialog

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val requestLocationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private var showNotificationRationale by mutableStateOf(false)
    private var showLocationRationale by mutableStateOf(false)

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

        // 🔔 Request Notification Permission (Android 13+) 
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notifPerm = Manifest.permission.POST_NOTIFICATIONS
            when {
                androidx.core.content.ContextCompat.checkSelfPermission(this, notifPerm) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED -> { /* Already granted */ }
                shouldShowRequestPermissionRationale(notifPerm) -> {
                    showNotificationRationale = true
                }
                else -> requestNotificationPermission.launch(notifPerm)
            }
        }

        // 📍 Request Location Permission
        val locPerm = Manifest.permission.ACCESS_FINE_LOCATION
        when {
            androidx.core.content.ContextCompat.checkSelfPermission(this, locPerm) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED -> { /* Already granted */ }
            shouldShowRequestPermissionRationale(locPerm) -> {
                showLocationRationale = true
            }
            else -> requestLocationPermission.launch(locPerm)
        }


        // 🔔 Handle Notification Intent (Deep Linking)
        var initialRoute: String? = null
        val notifType = intent?.getStringExtra("type")
        val conversationId = intent?.getStringExtra("conversationId") ?: intent?.getStringExtra("eventId")

        when (notifType) {
            "FRIEND_REQUEST" -> {
                initialRoute = Screen.Friends(tab = 1).route // Tab de solicitudes
            }
            "FRIEND_ACCEPTED" -> {
                initialRoute = Screen.Friends(tab = 0).route // Tab de amigos
            }
            "NEW_MESSAGE" -> {
                val chatId = intent?.getStringExtra("conversationId")
                if (!chatId.isNullOrBlank()) {
                    initialRoute = Screen.Chat(chatId).route
                }
            }
            "JOIN_REQUEST_SENT" -> {
                // Ir al evento para decidir si aceptar/rechazar
                val evtId = intent?.getStringExtra("eventId")
                if (!evtId.isNullOrBlank()) {
                    initialRoute = Screen.EventDetail(evtId).route
                } else {
                    initialRoute = Screen.Notifications.route
                }
            }
            "PROFILE_VIEW", "JOIN_REJECTED", "REMOVED_FROM_EVENT" -> {
                initialRoute = Screen.Notifications.route
            }
            "EVENT_UPDATE" -> {
                // Mensaje en el muro → abrir directamente el tab "Muro"
                val evtId = intent?.getStringExtra("eventId")
                if (!evtId.isNullOrBlank()) {
                    initialRoute = Screen.EventDetail(evtId, tab = 1).route
                } else {
                    initialRoute = Screen.Notifications.route
                }
            }
            "JOIN_APPROVED", "EVENT_CREATED", "EVENT_CANCELLED", "EVENT_CLOSED" -> {
                // Eventos generales → abrir tab "Detalles"
                val evtId = intent?.getStringExtra("eventId")
                if (!evtId.isNullOrBlank()) {
                    initialRoute = Screen.EventDetail(evtId).route
                } else {
                    initialRoute = Screen.Notifications.route
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
                    AppNavigation(startDestination = initialRoute ?: Screen.Splash.route)

                    if (showNotificationRationale) {
                        PermissionRationaleDialog(
                            title = "Notificaciones",
                            description = "Banana necesita notificarte sobre nuevos mensajes y cambios en tus eventos.",
                            icon = Icons.Default.Notifications,
                            onDismiss = {
                                showNotificationRationale = false
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                        )
                    }

                    if (showLocationRationale) {
                        PermissionRationaleDialog(
                            title = "Ubicación",
                            description = "Banana usa tu ubicación para encontrarte eventos cerca de ti y permitirte hacer check-in.",
                            icon = Icons.Default.LocationOn,
                            onDismiss = {
                                showLocationRationale = false
                                requestLocationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            }
                        )
                    }
                }
            }
        }
    }
}
