package com.eventos.banana.ui.login

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.animation.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource // ➕
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.eventos.banana.domain.model.LoginUiState
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    loginUiState: LoginUiState,
    registerUiState: com.eventos.banana.domain.model.RegisterUiState,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String, String, Long, String, String, String?, Double?, Double?, String?) -> Unit, 
    onForgotPassword: (String, (Boolean, String?) -> Unit) -> Unit,
    hasBiometricCredentials: Boolean = false,
    onBiometricLogin: () -> Unit = {},
    onEnableBiometric: () -> Unit = {}
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    
    // 🌍 LOCATION STATE
    var commune by remember { mutableStateOf("") } // City Name
    var selectedRegion by remember { mutableStateOf("") } // Secondary Text (Region)
    var selectedCountry by remember { mutableStateOf("") } // Country Name
    var selectedLat by remember { mutableStateOf<Double?>(null) }
    var selectedLng by remember { mutableStateOf<Double?>(null) }
    
    var birthDate by remember { mutableStateOf<Long?>(null) }
    var invitationCode by remember { mutableStateOf("") }
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
    val placesClient = remember { com.eventos.banana.util.LocationHelper(context).getPlacesClient() }

    // Use current state based on mode
    val isLoading = if (isRegistering) registerUiState.isLoading else loginUiState.isLoading
    val errorMessage = if (isRegistering) registerUiState.errorMessage else loginUiState.errorMessage

    val snackbarHostState = remember { SnackbarHostState() }
    var showBiometricDialog by remember { mutableStateOf(false) }

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
                    selectedCountry = result.country ?: ""
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
                context.getString(com.eventos.banana.R.string.auth_register_success),
                duration = SnackbarDuration.Long
            )
        }
    }

    fun validateEmail(mail: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(mail).matches()
    }

    // Password validation function
    fun validatePassword(pass: String): String? {
        if (pass.length < 8) return context.getString(com.eventos.banana.R.string.auth_password_error_short)
        if (!pass.any { it.isUpperCase() }) return context.getString(com.eventos.banana.R.string.auth_password_error_upper)
        if (!pass.any { it.isDigit() }) return context.getString(com.eventos.banana.R.string.auth_password_error_digit)
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
                    text = if (isRegistering) stringResource(com.eventos.banana.R.string.auth_register_title) else stringResource(com.eventos.banana.R.string.auth_login_title),
                    style = MaterialTheme.typography.headlineMedium
                )

                TextField(
                    value = email,
                    onValueChange = { 
                        email = it
                        emailError = if (validateEmail(it)) null else context.getString(com.eventos.banana.R.string.auth_invalid_email)
                    },
                    label = { Text(stringResource(com.eventos.banana.R.string.auth_email_hint)) },
                    isError = emailError != null,
                    supportingText = {
                        if (emailError != null) Text(emailError!!)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
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
                    label = { Text(stringResource(com.eventos.banana.R.string.auth_password_hint)) },
                    visualTransformation = PasswordVisualTransformation(),
                    isError = passwordError != null,
                    supportingText = {
                        if (isRegistering) {
                            Text(
                                passwordError ?: stringResource(com.eventos.banana.R.string.auth_password_requirements),
                                color = if (passwordError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // 🔑 Forgot Password Button (Only in Login Mode)
                if (!isRegistering) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        TextButton(onClick = { showForgotPasswordDialog = true }) {
                            Text(
                                stringResource(com.eventos.banana.R.string.auth_forgot_password),
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
                                Text(stringResource(com.eventos.banana.R.string.auth_recover_title), style = MaterialTheme.typography.titleLarge)
                                Text(
                                    stringResource(com.eventos.banana.R.string.auth_recover_body),
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                
                                OutlinedTextField(
                                    value = forgotPasswordEmail,
                                    onValueChange = { forgotPasswordEmail = it },
                                    label = { Text(stringResource(com.eventos.banana.R.string.auth_email_hint)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(onClick = { showForgotPasswordDialog = false }) {
                                        Text(stringResource(com.eventos.banana.R.string.common_cancel))
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
                                                             snackbarHostState.showSnackbar(context.getString(com.eventos.banana.R.string.auth_recover_success))
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
                                            Text(stringResource(com.eventos.banana.R.string.common_send))
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
                        label = { Text(stringResource(com.eventos.banana.R.string.auth_nickname_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                // 🎂 FECHA DE NACIMIENTO
                    val dateText = if (birthDate != null) {
                        // Fix: Material3 DatePicker returns UTC millis — format in UTC to avoid -1 day bug
                        val instant = java.time.Instant.ofEpochMilli(birthDate!!)
                        val utcDate = instant.atZone(java.time.ZoneOffset.UTC).toLocalDate()
                        "📅 ${utcDate.dayOfMonth}/${utcDate.monthValue}/${utcDate.year}"
                    } else {
                        stringResource(com.eventos.banana.R.string.auth_select_birthdate)
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
                    var isFetchingCoordinates by remember { mutableStateOf(false) }

                    Box(modifier = Modifier.fillMaxWidth().animateContentSize()) {
                        OutlinedTextField(
                            value = if (isFetchingCoordinates) "Obteniendo coordenadas..." else commune,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(com.eventos.banana.R.string.auth_city_label)) },
                            placeholder = { Text(stringResource(com.eventos.banana.R.string.auth_city_placeholder)) },
                            trailingIcon = { 
                                if (isFetchingCoordinates) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("📍") 
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                disabledBorderColor = MaterialTheme.colorScheme.outline,
                                disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            enabled = !isFetchingCoordinates
                        )
                        // Invisible overlay to catch clicks
                        if (!isFetchingCoordinates) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { showPlaceSearchDialog = true }
                            )
                        }
                    }

                    if (showPlaceSearchDialog) {
                        com.eventos.banana.ui.components.GooglePlacesSearchDialog(
                            onDismiss = { showPlaceSearchDialog = false },
                            onPlaceSelected = { placeId, fullText ->
                                showPlaceSearchDialog = false // Close dialog
                                isFetchingCoordinates = true
                                
                                // 1. Update text
                                val parts = fullText.split(",")
                                commune = parts.firstOrNull()?.trim() ?: fullText
                                selectedRegion = if (parts.size > 1) parts[1].trim() else ""
                                selectedCountry = if (parts.size > 2) parts.last().trim() else selectedRegion
                                
                                // 2. Fetch Coordinates
                                val fields = listOf(com.google.android.libraries.places.api.model.Place.Field.LAT_LNG)
                                val request = com.google.android.libraries.places.api.net.FetchPlaceRequest.builder(placeId, fields).build()
                                
                                placesClient.fetchPlace(request).addOnSuccessListener { response ->
                                    val place = response.place
                                    selectedLat = place.latLng?.latitude
                                    selectedLng = place.latLng?.longitude
                                    isFetchingCoordinates = false
                                }.addOnFailureListener {
                                    selectedLat = null
                                    selectedLng = null
                                    isFetchingCoordinates = false
                                }
                            }
                        )
                    }
                    
                    androidx.compose.animation.AnimatedVisibility(
                        visible = commune.isNotBlank() && !isFetchingCoordinates,
                        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
                        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically()
                    ) {
                         Text(
                             stringResource(com.eventos.banana.R.string.auth_location_selected, commune, selectedRegion), 
                             style = MaterialTheme.typography.bodySmall,
                             color = MaterialTheme.colorScheme.primary,
                             modifier = Modifier.padding(start = 4.dp)
                         )
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
                                        ageError = context.getString(com.eventos.banana.R.string.auth_age_error)
                                    }
                                }
                                showDatePicker = false
                            }) {
                                Text(stringResource(com.eventos.banana.R.string.common_confirm))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDatePicker = false }) {
                                Text(stringResource(com.eventos.banana.R.string.common_cancel))
                            }
                        }
                    ) {
                        androidx.compose.material3.DatePicker(state = datePickerState)
                    }
                }

                if (isRegistering) {
                    OutlinedTextField(
                        value = invitationCode,
                        onValueChange = { invitationCode = it.uppercase().trim() },
                        label = { Text("Código de Invitación (Opcional)") },
                        placeholder = { Text("Ej: FOUNDER-XXXX") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = com.eventos.banana.ui.theme.BananaGold,
                            focusedLabelColor = com.eventos.banana.ui.theme.BananaGold
                        )
                    )
                }

                // 🔐 BIOMETRIC LOGIN BUTTON
                if (hasBiometricCredentials) {
                    com.eventos.banana.ui.components.BananaOutlinedButton(
                        onClick = { onBiometricLogin() },
                        text = "🔓 Entrar con huella digital",
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                    
                    Text(
                        text = "— o usa email y contraseña —",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                com.eventos.banana.ui.components.BananaButton(
                    onClick = { 
                        if (validateEmail(email)) {
                            if (isRegistering) {
                                // Validación final de edad
                                if (birthDate == null) {
                                    ageError = context.getString(com.eventos.banana.R.string.auth_birthdate_required)
                                    return@BananaButton
                                }
                                if (!com.eventos.banana.util.AgeCalculator.isAdult(birthDate!!)) {
                                    ageError = context.getString(com.eventos.banana.R.string.auth_age_error)
                                    return@BananaButton
                                }
                                if (commune.isBlank()) {
                                    // Show error toast or similar? For now rely on button disabled
                                    return@BananaButton
                                }
                                onRegister(
                                    email, password, nickname, birthDate!!,
                                    commune, selectedRegion, selectedCountry,
                                    selectedLat, selectedLng, invitationCode.ifBlank { null }
                                )
                            } else {
                                onLogin(email, password)
                            }
                        }
                    },
                    text = if (isRegistering) stringResource(com.eventos.banana.R.string.auth_register_button) else stringResource(com.eventos.banana.R.string.auth_login_button),
                    enabled = !isLoading && email.isNotBlank() && password.isNotBlank() && 
                              emailError == null && passwordError == null && ageError == null &&
                              (!isRegistering || (nickname.isNotBlank() && birthDate != null && commune.isNotBlank())),
                    isLoading = isLoading,
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Toggle Button
                com.eventos.banana.ui.components.BananaTextButton(
                    onClick = { isRegistering = !isRegistering },
                    text = if (isRegistering) stringResource(com.eventos.banana.R.string.auth_has_account) 
                           else stringResource(com.eventos.banana.R.string.auth_no_account),
                    enabled = !isLoading
                )

                // 🧬 ENABLE BIOMETRIC LOGIN (Solo en modo login, NO en registro)
                if (!isRegistering && email.isNotBlank() && password.isNotBlank()) {
                    var enableBiometric by remember { mutableStateOf(false) }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = enableBiometric,
                            onCheckedChange = { enabled ->
                                enableBiometric = enabled
                                if (enabled) onEnableBiometric()
                            }
                        )
                        Text(
                            text = "Recordar acceso con huella digital",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable { 
                                enableBiometric = !enableBiometric
                                if (enableBiometric) onEnableBiometric()
                            }
                        )
                    }
                }
            }
        }
        }
    }
}
