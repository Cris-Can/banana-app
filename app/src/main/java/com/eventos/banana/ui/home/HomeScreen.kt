package com.eventos.banana.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eventos.banana.domain.model.ProfileUiState

@Composable
fun HomeScreen(
    profileUiState: ProfileUiState,
    onLogout: () -> Unit,
    onCreateEvent: () -> Unit
)
 {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when {
            profileUiState.isLoading -> {
                CircularProgressIndicator()
            }

            profileUiState.errorMessage != null -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = profileUiState.errorMessage,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onLogout) {
                        Text("Cerrar sesión")
                    }
                }
            }

            profileUiState.profile != null -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Bienvenido ${profileUiState.profile.nickname}",
                        style = MaterialTheme.typography.headlineSmall
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(text = "Email: ${profileUiState.profile.email}")

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(onClick = onCreateEvent) {
                        Text("Crear evento")
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(onClick = onLogout) {
                        Text("Cerrar sesión")
                    }
                }
            }

        }
    }
}
