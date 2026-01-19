package com.eventos.banana.ui.login

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.eventos.banana.domain.model.LoginUiState

@Composable
fun LoginScreen(
    uiState: LoginUiState,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String, String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var isRegistering by remember { mutableStateOf(false) }
    var emailError by remember { mutableStateOf<String?>(null) }

    fun validateEmail(mail: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(mail).matches()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (isRegistering) "Crear Cuenta" else "Iniciar Sesión",
                style = MaterialTheme.typography.headlineMedium
            )

            TextField(
                value = email,
                onValueChange = { 
                    email = it
                    emailError = if (validateEmail(it)) null else "Email inválido"
                },
                label = { Text("Email") },
                isError = emailError != null,
                supportingText = {
                    if (emailError != null) Text(emailError!!)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            TextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Contraseña") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (isRegistering) {
                TextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("Nickname (Nombre visible)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Button(
                onClick = { 
                    if (validateEmail(email)) {
                        if (isRegistering) {
                            onRegister(email, password, nickname)
                        } else {
                            onLogin(email, password)
                        }
                    }
                },
                enabled = !uiState.isLoading && email.isNotBlank() && password.isNotBlank() && emailError == null && (!isRegistering || nickname.isNotBlank()),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(if (isRegistering) "Registrarse" else "Entrar")
                }
            }
            
            // Toggle Button
            TextButton(onClick = { isRegistering = !isRegistering }) {
                Text(
                    if (isRegistering) "¿Ya tienes cuenta? Inicia Sesión" 
                    else "¿No tienes cuenta? Regístrate aquí"
                )
            }

            uiState.errorMessage?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
