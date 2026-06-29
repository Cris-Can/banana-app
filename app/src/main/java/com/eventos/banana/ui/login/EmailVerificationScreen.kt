package com.eventos.banana.ui.login

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.eventos.banana.ui.auth.SessionViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun EmailVerificationScreen(
    sessionViewModel: SessionViewModel,
    onVerified: () -> Unit,
    onLogout: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isPolling by remember { mutableStateOf(false) }
    
    // Watch for verification result feedback
    val sendVerificationMessage by sessionViewModel.sendVerificationMessage.collectAsState()
    LaunchedEffect(sendVerificationMessage) {
        sendVerificationMessage?.let {
            message = it
            sessionViewModel.clearSendVerificationMessage()
        }
    }
    
    // Check status on entry
    LaunchedEffect(Unit) {
        sessionViewModel.refreshVerificationStatus()
    }
    
    // Auto-polling every 5s for up to 2 minutes
    LaunchedEffect(Unit) {
        isPolling = true
        var attempts = 0
        while (attempts < 24) {
            delay(5000)
            if (!sessionViewModel.isEmailVerified) {
                sessionViewModel.refreshVerificationStatus()
                attempts++
            }
        }
        isPolling = false
        if (!sessionViewModel.isEmailVerified) {
            message = "📧 ¿No recibes el correo? Revisa tu bandeja de spam o reenvíalo."
        }
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
                    text = stringResource(com.eventos.banana.R.string.email_verify_title),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center
                )
                
                Text(
                    text = stringResource(com.eventos.banana.R.string.email_verify_body),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                if (isPolling) {
                    Text(
                        text = "⏳ Esperando verificación...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
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
                                    message = context.getString(com.eventos.banana.R.string.email_verify_not_yet)
                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(com.eventos.banana.R.string.email_verify_already))
                    }
                    
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                sessionViewModel.sendEmailVerification()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(com.eventos.banana.R.string.email_verify_resend))
                    }
                    
                    TextButton(onClick = onLogout) {
                        Text(stringResource(com.eventos.banana.R.string.email_verify_logout))
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
