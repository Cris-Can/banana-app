package com.eventos.banana.ui.register

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eventos.banana.domain.model.RegisterUiState
import androidx.compose.material3.SnackbarDuration

@Composable
fun RegisterScreen(
    uiState: RegisterUiState,
    onRegister: (String, String, String, String, String?, Double?, Double?) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    
    // 🌍 Location State
    var commune by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf<Double?>(null) }
    var longitude by remember { mutableStateOf<Double?>(null) }
    var isLocationLoading by remember { mutableStateOf(false) }
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    
    // 📍 Auto-Detect Location
    LaunchedEffect(Unit) {
        if (com.eventos.banana.util.LocationHelper.hasLocationPermissions(context)) {
            isLocationLoading = true
            val helper = com.eventos.banana.util.LocationHelper(context)
            val result = helper.detectLocationFull()
            if (result != null) {
                commune = result.commune
                region = result.region
                latitude = result.latitude
                longitude = result.longitude
            }
            isLocationLoading = false
        }
    }
    
    LaunchedEffect(uiState) {
        if (uiState.errorMessage != null) {
            snackbarHostState.showSnackbar("❌ ${uiState.errorMessage}")
        }
        if (uiState.isSuccess) {
            snackbarHostState.showSnackbar(
                message = "✅ Cuenta creada. ¡Verifica tu email para acceder!",
                duration = SnackbarDuration.Long
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ... (Email/Pass/Nickname fields remain) ...
            TextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(com.eventos.banana.R.string.auth_email_hint)) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            TextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(com.eventos.banana.R.string.auth_password_hint)) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            TextField(
                value = nickname,
                onValueChange = { nickname = it },
                label = { Text(stringResource(com.eventos.banana.R.string.auth_apodo_label)) },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))

            // 📍 Comuna Field (Auto-filled)
            OutlinedTextField(
                value = if (isLocationLoading) stringResource(com.eventos.banana.R.string.auth_detecting_location) else commune.ifBlank { stringResource(com.eventos.banana.R.string.auth_location_not_detected) },
                onValueChange = { }, // Read-only
                label = { Text(stringResource(com.eventos.banana.R.string.auth_your_commune)) },
                enabled = false, // User cannot edit manually (for now)
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { onRegister(email, password, nickname, commune, region, latitude, longitude) },
                enabled = !uiState.isLoading && email.isNotBlank() && password.isNotBlank() && nickname.isNotBlank() && commune.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(com.eventos.banana.R.string.auth_create_account_button))
                }
            }

            uiState.errorMessage?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
    }
}
