package com.eventos.banana.ui.login

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eventos.banana.viewmodel.SessionViewModel
import kotlinx.coroutines.launch

@Composable
fun EmailVerificationScreen(
    sessionViewModel: SessionViewModel,
    onVerified: () -> Unit,
    onLogout: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    
    // Check status on entry
    LaunchedEffect(Unit) {
        sessionViewModel.refreshVerificationStatus()
    }
    
    // Watch for verification changes
    if (sessionViewModel.isEmailVerified) {
        LaunchedEffect(Unit) {
            onVerified()
        }
    }

    Scaffold { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .padding(32.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text(
                    text = "📧",
                    style = MaterialTheme.typography.displayLarge
                )
                
                Text(
                    text = "Verifica tu correo",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center
                )
                
                Text(
                    text = "Hemos enviado un enlace de confirmación a tu email. Debes verificarlo para poder usar Banana.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(Modifier.height(16.dp))
                
                if (isLoading) {
                    CircularProgressIndicator()
                } else {
                    Button(
                        onClick = {
                            isLoading = true
                            scope.launch {
                                sessionViewModel.refreshVerificationStatus()
                                if (sessionViewModel.isEmailVerified) {
                                    onVerified()
                                } else {
                                    message = "Aún no verificado. Intenta de nuevo."
                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Ya lo verifiqué")
                    }
                    
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                sessionViewModel.sendEmailVerification()
                                message = "Correo reenviado (revisa Spam)"
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Reenviar correo")
                    }
                    
                    TextButton(onClick = onLogout) {
                        Text("Cerrar Sesión / Cambiar cuenta")
                    }
                }
                
                message?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
