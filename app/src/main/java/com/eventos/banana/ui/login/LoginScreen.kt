package com.eventos.banana.ui.login

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.eventos.banana.domain.model.LoginUiState
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    loginUiState: LoginUiState,
    registerUiState: com.eventos.banana.domain.model.RegisterUiState,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String, String, Long, String) -> Unit  // email, password, nickname, birthDate, commune
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var commune by remember { mutableStateOf("") }
    var communeExpanded by remember { mutableStateOf(false) }
    var birthDate by remember { mutableStateOf<Long?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var isRegistering by remember { mutableStateOf(false) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var ageError by remember { mutableStateOf<String?>(null) }

    // Use current state based on mode
    val isLoading = if (isRegistering) registerUiState.isLoading else loginUiState.isLoading
    val errorMessage = if (isRegistering) registerUiState.errorMessage else loginUiState.errorMessage

    val snackbarHostState = remember { SnackbarHostState() }

    // Show errors in Snackbar
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    // Reset password error when switching modes
    LaunchedEffect(isRegistering) {
        passwordError = null
    }

    // Handle successful registration
    LaunchedEffect(registerUiState.isSuccess) {
        if (registerUiState.isSuccess) {
            // Switch to login mode
            isRegistering = false
            // Show success message
            snackbarHostState.showSnackbar(
                "✅ Cuenta creada exitosamente! Revisa tu email para verificar tu cuenta antes de iniciar sesión.",
                duration = SnackbarDuration.Long
            )
        }
    }

    fun validateEmail(mail: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(mail).matches()
    }

    // Password validation function
    fun validatePassword(pass: String): String? {
        if (pass.length < 8) return "Mínimo 8 caracteres"
        if (!pass.any { it.isUpperCase() }) return "Debe tener 1 mayúscula"
        if (!pass.any { it.isDigit() }) return "Debe tener 1 número"
        return null
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingVals ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingVals),
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
                    onValueChange = { 
                        password = it
                        // Only validate during registration
                        if (isRegistering) {
                            passwordError = validatePassword(it)
                        }
                    },
                    label = { Text("Contraseña") },
                    visualTransformation = PasswordVisualTransformation(),
                    isError = passwordError != null,
                    supportingText = {
                        if (isRegistering) {
                            Text(
                                passwordError ?: "Mínimo 8 caracteres, 1 mayúscula, 1 número",
                                color = if (passwordError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
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
                    
                // 🎂 FECHA DE NACIMIENTO
                    val dateText = if (birthDate != null) {
                        val calendar = java.util.Calendar.getInstance()
                        calendar.timeInMillis = birthDate!!
                        "📅 ${calendar.get(java.util.Calendar.DAY_OF_MONTH)}/${calendar.get(java.util.Calendar.MONTH) + 1}/${calendar.get(java.util.Calendar.YEAR)}"
                    } else {
                        "📅 Seleccionar Fecha de Nacimiento"
                    }
                    
                    com.eventos.banana.ui.components.BananaOutlinedButton(
                        onClick = { showDatePicker = true },
                        text = dateText,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    if (ageError != null) {
                        Text(
                            ageError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    
                    // 📍 COMUNA (Click para seleccionar)
                // Selector de Comuna (con box para capturar click correctamente)
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = commune,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Comuna") },
                        trailingIcon = {
                            Icon(Icons.Default.ArrowDropDown, "Seleccionar")
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Overlay transparente para capturar el click
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { communeExpanded = true }
                    )
                }
            }

                // Dialog para seleccionar comuna con BUSCADOR
                if (communeExpanded) {
                    var searchQuery by remember { mutableStateOf("") }
                    val filteredCommunes = remember(searchQuery) {
                        com.eventos.banana.data.ChileCommunesList.search(searchQuery)
                    }

                    androidx.compose.ui.window.Dialog(
                        onDismissRequest = { communeExpanded = false }
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(600.dp),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                // Header
                                Text(
                                    "Selecciona tu Comuna",
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.padding(16.dp)
                                )

                                // 🔍 BUSCADOR
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    label = { Text("Buscar comuna...") },
                                    leadingIcon = { Text("🔍") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                )

                                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                                
                                // Lista scrollable FILTRADA
                                androidx.compose.foundation.lazy.LazyColumn(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    items(filteredCommunes.size) { index ->
                                        val selectedCommune = filteredCommunes[index]
                                        TextButton(
                                            onClick = {
                                                commune = selectedCommune
                                                communeExpanded = false
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                selectedCommune,
                                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                                            )
                                        }
                                        HorizontalDivider()
                                    }
                                }
                                
                                // Footer button
                                com.eventos.banana.ui.components.BananaTextButton(
                                    onClick = { communeExpanded = false },
                                    text = "Cerrar",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp)
                                )
                            }
                        }
                    }
                }

                // DatePicker Dialog
                if (showDatePicker) {
                    val datePickerState = androidx.compose.material3.rememberDatePickerState(
                        initialSelectedDateMillis = birthDate ?: com.eventos.banana.util.AgeCalculator.getMinimumBirthDate()
                    )
                    
                    androidx.compose.material3.DatePickerDialog(
                        onDismissRequest = { showDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                datePickerState.selectedDateMillis?.let { selectedDate ->
                                    if (com.eventos.banana.util.AgeCalculator.isAdult(selectedDate)) {
                                        birthDate = selectedDate
                                        ageError = null
                                    } else {
                                        ageError = "❌ Debes ser mayor de 18 años para usar esta app"
                                    }
                                }
                                showDatePicker = false
                            }) {
                                Text("Confirmar")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDatePicker = false }) {
                                Text("Cancelar")
                            }
                        }
                    ) {
                        androidx.compose.material3.DatePicker(state = datePickerState)
                    }
                }

                com.eventos.banana.ui.components.BananaButton(
                    onClick = { 
                        if (validateEmail(email)) {
                            if (isRegistering) {
                                // Validación final de edad
                                if (birthDate == null) {
                                    ageError = "Debes seleccionar tu fecha de nacimiento"
                                    return@BananaButton
                                }
                                if (!com.eventos.banana.util.AgeCalculator.isAdult(birthDate!!)) {
                                    ageError = "❌ Debes ser mayor de 18 años"
                                    return@BananaButton
                                }
                                onRegister(email, password, nickname, birthDate!!, commune)
                            } else {
                                onLogin(email, password)
                            }
                        }
                    },
                    text = if (isRegistering) "Registrarse" else "Entrar",
                    enabled = !isLoading && email.isNotBlank() && password.isNotBlank() && 
                              emailError == null && passwordError == null && ageError == null &&
                              (!isRegistering || (nickname.isNotBlank() && birthDate != null && commune.isNotBlank())),
                    isLoading = isLoading,
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Toggle Button
                com.eventos.banana.ui.components.BananaTextButton(
                    onClick = { isRegistering = !isRegistering },
                    text = if (isRegistering) "¿Ya tienes cuenta? Inicia Sesión" 
                           else "¿No tienes cuenta? Regístrate aquí",
                    enabled = !isLoading
                )
            }
        }
    }
}
