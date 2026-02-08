package com.eventos.banana.ui.login

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
    onRegister: (String, String, String, Long, String, String, Double?, Double?) -> Unit, // 🌍 Added Region, Lat, Lng
    onForgotPassword: (String, (Boolean, String?) -> Unit) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    
    // 🌍 LOCATION STATE
    var commune by remember { mutableStateOf("") } // City Name
    var selectedRegion by remember { mutableStateOf("") } // Secondary Text (Region/Country)
    var selectedLat by remember { mutableStateOf<Double?>(null) }
    var selectedLng by remember { mutableStateOf<Double?>(null) }
    
    var birthDate by remember { mutableStateOf<Long?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var isRegistering by remember { mutableStateOf(false) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var ageError by remember { mutableStateOf<String?>(null) }
    
    // Forgot Password State
    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var forgotPasswordEmail by remember { mutableStateOf("") }
    var isSendingReset by remember { mutableStateOf(false) }
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val placesClient = remember { com.google.android.libraries.places.api.Places.createClient(context) }

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
    
    // 📍 AUTO-DETECT LOCATION (Round 6)
    LaunchedEffect(isRegistering) {
        if (isRegistering && commune.isEmpty()) {
             if (com.eventos.banana.util.LocationHelper.hasLocationPermissions(context)) {
                val helper = com.eventos.banana.util.LocationHelper(context)
                val result = helper.detectLocationFull()
                if (result != null) {
                    commune = result.commune
                    selectedRegion = result.region ?: ""
                    selectedLat = result.latitude
                    selectedLng = result.longitude
                }
            }
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
        // MAIN CONTAINER
        Box(modifier = Modifier.fillMaxSize()) {
            
            // 🎥 VIDEO BACKGROUND
            val videoResId = remember {
                context.resources.getIdentifier("login_video", "raw", context.packageName)
            }
            
            com.eventos.banana.ui.components.VideoBackground(
                rawResId = if (videoResId != 0) videoResId else null
            )
            
            // 🌑 DARK OVERLAY (Gradient or Solid)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.3f),
                                androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f)
                            )
                        )
                    )
            )

            // 🔐 CONTENT
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingVals)
                    .verticalScroll(androidx.compose.foundation.rememberScrollState()), // Ensure scrollable on small screens
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

                // 🔑 Forgot Password Button (Only in Login Mode)
                if (!isRegistering) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        TextButton(onClick = { showForgotPasswordDialog = true }) {
                            Text(
                                "¿Olvidaste tu contraseña?",
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // 📧 Forgot Password Dialog
                if (showForgotPasswordDialog) {
                    androidx.compose.ui.window.Dialog(onDismissRequest = { showForgotPasswordDialog = false }) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text("Recuperar Contraseña", style = MaterialTheme.typography.titleLarge)
                                Text(
                                    "Ingresa tu email y te enviaremos un enlace para restablecerla.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                
                                OutlinedTextField(
                                    value = forgotPasswordEmail,
                                    onValueChange = { forgotPasswordEmail = it },
                                    label = { Text("Email") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(onClick = { showForgotPasswordDialog = false }) {
                                        Text("Cancelar")
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            if (validateEmail(forgotPasswordEmail)) {
                                                isSendingReset = true
                                                onForgotPassword(forgotPasswordEmail) { success, error ->
                                                    isSendingReset = false
                                                    showForgotPasswordDialog = false
                                                    if (success) {
                                                        snackbarHostState.currentSnackbarData?.dismiss()
                                                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                                             snackbarHostState.showSnackbar("✅ Correo enviado. Revisa tu bandeja de entrada.")
                                                        }
                                                    } else {
                                                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                                            snackbarHostState.showSnackbar("❌ Error: $error")
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        enabled = !isSendingReset && forgotPasswordEmail.isNotBlank()
                                    ) {
                                        if (isSendingReset) {
                                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                        } else {
                                            Text("Enviar")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

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
                    
                    // 🌍 LOCATION SELECTION (Dialog Mode)
                    var showPlaceSearchDialog by remember { mutableStateOf(false) }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = commune,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Ciudad / Comuna (Auto)") },
                            placeholder = { Text("Toca para buscar...") },
                            trailingIcon = { Text("📍") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                disabledBorderColor = MaterialTheme.colorScheme.outline,
                                disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                // For ReadOnly look
                            )
                        )
                        // Invisible overlay to catch clicks
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { showPlaceSearchDialog = true }
                        )
                    }

                    if (showPlaceSearchDialog) {
                        com.eventos.banana.ui.components.GooglePlacesSearchDialog(
                            onDismiss = { showPlaceSearchDialog = false },
                            onPlaceSelected = { placeId, fullText ->
                                showPlaceSearchDialog = false // Close dialog
                                
                                // 1. Update text
                                val parts = fullText.split(",")
                                commune = parts.firstOrNull()?.trim() ?: fullText
                                selectedRegion = if (parts.size > 1) parts[1].trim() else fullText
                                
                                // 2. Fetch Coordinates
                                val fields = listOf(com.google.android.libraries.places.api.model.Place.Field.LAT_LNG)
                                val request = com.google.android.libraries.places.api.net.FetchPlaceRequest.builder(placeId, fields).build()
                                
                                placesClient.fetchPlace(request).addOnSuccessListener { response ->
                                    val place = response.place
                                    selectedLat = place.latLng?.latitude
                                    selectedLng = place.latLng?.longitude
                                }.addOnFailureListener {
                                    selectedLat = null
                                    selectedLng = null
                                }
                            }
                        )
                    }
                    
                    if (commune.isNotBlank()) {
                         Text("📍 Ubicación seleccionada: $commune ($selectedRegion)", style = MaterialTheme.typography.bodySmall)
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
                                if (commune.isBlank()) {
                                    // Show error toast or similar? For now rely on button disabled
                                    return@BananaButton
                                }
                                onRegister(email, password, nickname, birthDate!!, commune, selectedRegion, selectedLat, selectedLng)
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
}
