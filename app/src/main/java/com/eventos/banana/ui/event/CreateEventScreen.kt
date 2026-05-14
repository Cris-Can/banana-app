package com.eventos.banana.ui.event

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.res.stringResource
import com.eventos.banana.ui.util.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.eventos.banana.domain.model.CreateEventUiState
import com.eventos.banana.domain.model.Event
import com.eventos.banana.domain.model.EventStatus
import com.eventos.banana.domain.model.JoinQuestion
import com.eventos.banana.util.LocationHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import com.eventos.banana.ui.components.shimmerEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEventScreen(
    creatorId: String,
    viewModel: com.eventos.banana.ui.event.CreateEventViewModel,
    onSuccess: () -> Unit,
    onSelectExactLocation: () -> Unit,
    onNavigateToPremium: () -> Unit
) {
    // ================= CONTEXTO =================
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 🎯 Guide overlay (first visit only)
    val sharedPrefs = androidx.compose.ui.platform.LocalContext.current
        .getSharedPreferences("banana_prefs", android.content.Context.MODE_PRIVATE)
    val hasSeenCreateGuide = remember { mutableStateOf(
        sharedPrefs.getBoolean("create_event_guide_seen", false)
    ) }
    // ================= ESTADO (ViewModel) =================
    val uiState by viewModel.uiState.collectAsState()
    val formState by viewModel.formState.collectAsState()

    val scrollState = rememberScrollState()
    var timeError by remember { mutableStateOf<String?>(null) }

    // ================= ESTADO DE LÍMITES / ADS =================
    val adUnlockState by viewModel.adUnlockState.collectAsState()
    val limitDebugInfo by viewModel.limitDebugInfo.collectAsState()
    val userLimitStats by viewModel.userLimitStats.collectAsState()
    var showAdDialog by remember { mutableStateOf(false) }

    // Image Picker
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> viewModel.updateSelectedImageUri(uri) }
    )

    // Location Permission
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            scope.launch {
                val result = LocationHelper(context).detectLocationFull()
                if (result != null) {
                    viewModel.updateLocationResult(
                        region = result.region,
                        commune = result.commune,
                        country = result.country,
                        lat = result.latitude,
                        lng = result.longitude
                    )
                }
            }
        }
    }

    LaunchedEffect(uiState.success) {
        if (uiState.success) {
            onSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(com.eventos.banana.R.string.create_event_title)) },
                navigationIcon = {
                    IconButton(onClick = { /* Handle back if needed */ }) {
                        // Icon(...)
                    }
                }
            )
        }
    ) { paddingVals ->
        Column(
            modifier = Modifier
                .padding(paddingVals)
                .padding(16.dp)
                .verticalScroll(scrollState)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ---------- IMAGEN DEL EVENTO ----------
            Text(stringResource(com.eventos.banana.R.string.create_event_image_title), style = MaterialTheme.typography.titleMedium)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                contentAlignment = Alignment.Center
            ) {
                if (formState.selectedImageUri != null) {
                    AsyncImage(
                        model = formState.selectedImageUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(48.dp))
                        Text(stringResource(com.eventos.banana.R.string.create_event_image_hint))
                    }
                }
            }

            // ---------- TÍTULO Y DESCRIPCIÓN ----------
            OutlinedTextField(
                value = formState.title,
                onValueChange = { viewModel.updateTitle(it) },
                label = { Text(stringResource(com.eventos.banana.R.string.create_event_field_title)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = formState.description,
                onValueChange = { viewModel.updateDescription(it) },
                label = { Text(stringResource(com.eventos.banana.R.string.create_event_field_description)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            // ---------- TIPO DE EVENTO ----------
            var showTypeMenu by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = showTypeMenu,
                onExpandedChange = { showTypeMenu = it }
            ) {
                OutlinedTextField(
                    value = formState.eventType.localizedName(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(com.eventos.banana.R.string.create_event_field_type)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showTypeMenu) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = showTypeMenu,
                    onDismissRequest = { showTypeMenu = false }
                ) {
                    com.eventos.banana.domain.model.EventType.values().forEach { type ->
                        DropdownMenuItem(
                            text = { Text("${type.emoji} ${type.localizedName()}") },
                            onClick = {
                                viewModel.updateEventType(type)
                                showTypeMenu = false
                            }
                        )
                    }
                }
            }

            HorizontalDivider()

            // ---------- UBICACIÓN ----------
            Text(stringResource(com.eventos.banana.R.string.create_event_location_title), style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = formState.region,
                onValueChange = { viewModel.updateRegion(it) },
                label = { Text("Estado / Región *") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Ej: Región Metropolitana, Madrid, Florida...") },
                singleLine = true
            )

            OutlinedTextField(
                value = formState.commune,
                onValueChange = { viewModel.updateCommune(it) },
                label = { Text("Ciudad / Comuna *") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Ej: Santiago, Barcelona, Miami...") },
                singleLine = true
            )

            OutlinedTextField(
                value = formState.country,
                onValueChange = { viewModel.updateCountry(it) },
                label = { Text("País *") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Ej: Chile, España, USA...") },
                singleLine = true
            )

            Button(
                onClick = {
                    locationPermissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Icon(Icons.Default.LocationOn, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("📍 Detectar mi ubicación")
            }

            if (formState.currentLatitude == null && formState.exactLocation == null) {
                Text(
                    "⚠️ Coordenadas no detectadas. Usa el botón de arriba o el mapa para definir la ubicación.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            } else {
                Text(
                    if (formState.exactLocation != null) "✅ Ubicación definida en el mapa" else "✅ Ubicación de red detectada",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            
            OutlinedTextField(
                value = formState.address,
                onValueChange = { viewModel.updateAddress(it) },
                label = { Text(stringResource(com.eventos.banana.R.string.create_event_field_address)) },
                placeholder = { Text(stringResource(com.eventos.banana.R.string.create_event_field_address_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text(stringResource(com.eventos.banana.R.string.create_event_field_address_note)) },
                singleLine = true
            )

            Card(
                onClick = onSelectExactLocation,
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                             Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (formState.exactLocation != null) "Ubicación exacta definida" else "Definir ubicación en mapa",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                             text = if (formState.exactLocation != null) "Tocá para editar" else "Obligatorio para el check-in",
                             style = MaterialTheme.typography.bodySmall,
                             color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            
            Spacer(Modifier.height(8.dp))
            
            // 🌍 PUBLIC EVENT SWITCH
            Text(stringResource(com.eventos.banana.R.string.create_event_visibility), style = MaterialTheme.typography.titleMedium)
            Card(
                 modifier = Modifier.fillMaxWidth(),
                 colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                 border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(com.eventos.banana.R.string.create_event_public), style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Cualquiera puede ver la ubicación y asistir sin aprobación.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = formState.isPublic,
                        onCheckedChange = { viewModel.updateIsPublic(it) }
                    )
                }
            }

            // ---------- RANGO DE NOTIFICACIONES ----------
            Text("Alcance de Notificaciones", style = MaterialTheme.typography.titleMedium)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val isGold = userLimitStats?.subscriptionType == "GOLD" || 
                               userLimitStats?.subscriptionType == "FOUNDER" || 
                               userLimitStats?.isFounder == true
                    
                    val rangeOptions = listOf(
                        Triple("COMMUNE", "Solo Comuna", "Notifica a usuarios de tu misma comuna"),
                        Triple("REGION", "Región", "Notifica a toda la región (Solo Gold 🍌)"),
                        Triple("NATIONAL", "Nacional", "Notifica a todo el país (Solo Gold 🍌)")
                    )

                    rangeOptions.forEach { (range, title, desc) ->
                        val isRestricted = range != "COMMUNE" && !isGold
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isRestricted) { viewModel.updateNotificationRange(range) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = formState.notificationRange == range,
                                onClick = { viewModel.updateNotificationRange(range) },
                                enabled = !isRestricted
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isRestricted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isRestricted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // ---------- CUPOS ----------
            OutlinedTextField(
                value = formState.maxParticipants,
                onValueChange = { if (it.all(Char::isDigit)) viewModel.updateMaxParticipants(it) },
                label = { Text("Cupos máximos *") },
                modifier = Modifier.fillMaxWidth()
            )

            // ---------- FECHA Y HORA ----------
            Text("Cuándo empieza", style = MaterialTheme.typography.titleMedium)
            
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = formState.startAt?.let { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(it)) } ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Fecha") },
                        trailingIcon = { Icon(Icons.Default.DateRange, null) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledContainerColor = Color.Transparent
                        )
                    )
                    Box(Modifier.matchParentSize().clickable {
                        val cal = Calendar.getInstance()
                        formState.startAt?.let { cal.timeInMillis = it }
                        DatePickerDialog(context, { _, y, m, d ->
                            val newCal = Calendar.getInstance()
                            formState.startAt?.let { newCal.timeInMillis = it }
                            newCal.set(y, m, d)
                            viewModel.updateStartAt(newCal.timeInMillis)
                        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                    })
                }

                Box(Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = formState.startAt?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it)) } ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Hora") },
                        trailingIcon = { Icon(Icons.Default.DateRange, null) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledContainerColor = Color.Transparent
                        )
                    )
                    Box(Modifier.matchParentSize().clickable {
                        val cal = Calendar.getInstance()
                        formState.startAt?.let { cal.timeInMillis = it }
                        TimePickerDialog(context, { _, h, min ->
                            val newCal = Calendar.getInstance()
                            formState.startAt?.let { newCal.timeInMillis = it }
                            newCal.set(Calendar.HOUR_OF_DAY, h)
                            newCal.set(Calendar.MINUTE, min)
                            viewModel.updateStartAt(newCal.timeInMillis)
                        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
                    })
                }
            }

            Text("Cuándo termina", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = formState.endAt?.let { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(it)) } ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Fecha") },
                        trailingIcon = { Icon(Icons.Default.DateRange, null) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledContainerColor = Color.Transparent
                        )
                    )
                    Box(Modifier.matchParentSize().clickable {
                        val cal = Calendar.getInstance()
                        formState.endAt?.let { cal.timeInMillis = it }
                        DatePickerDialog(context, { _, y, m, d ->
                            val newCal = Calendar.getInstance()
                            formState.endAt?.let { newCal.timeInMillis = it }
                            newCal.set(y, m, d)
                            val start = formState.startAt
                            if (start != null && newCal.timeInMillis <= start) {
                                timeError = "El término debe ser posterior al inicio"
                            } else {
                                timeError = null
                            }
                            viewModel.updateEndAt(newCal.timeInMillis)
                        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                    })
                }

                Box(Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = formState.endAt?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it)) } ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Hora") },
                        trailingIcon = { Icon(Icons.Default.DateRange, null) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledContainerColor = Color.Transparent
                        )
                    )
                    Box(Modifier.matchParentSize().clickable {
                        val cal = Calendar.getInstance()
                        formState.endAt?.let { cal.timeInMillis = it }
                        TimePickerDialog(context, { _, h, min ->
                            val newCal = Calendar.getInstance()
                            formState.endAt?.let { newCal.timeInMillis = it }
                            newCal.set(Calendar.HOUR_OF_DAY, h)
                            newCal.set(Calendar.MINUTE, min)
                            val startAt = formState.startAt
                            if (startAt != null && newCal.timeInMillis <= startAt) {
                                timeError = "El término debe ser posterior al inicio"
                            } else {
                                timeError = null
                            }
                            viewModel.updateEndAt(newCal.timeInMillis)
                        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
                    })
                }
            }

            timeError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            HorizontalDivider()

            Button(
                enabled = !uiState.isLoading && formState.startAt != null && formState.endAt != null && 
                          formState.startAt!! < formState.endAt!! && formState.title.isNotBlank() && 
                          formState.description.isNotBlank() && formState.region.isNotBlank() && 
                          formState.address.isNotBlank() && 
                          formState.maxParticipants.toIntOrNull()?.let { it > 0 } == true &&
                          (formState.exactLocation != null || (formState.currentLatitude != null && formState.currentLongitude != null)),
                onClick = {
                    val safetyError = com.eventos.banana.util.TextSafetyUtils.validateEventContent(formState.title, formState.description)
                    if (safetyError != null) {
                        viewModel.updateErrorMessage(safetyError)
                    } else {
                        scope.launch {
                            val imageBytes = formState.selectedImageUri?.let { uri ->
                                com.eventos.banana.util.ImageCompressor.compressFromUri(context, uri)
                            }
                            viewModel.createEvent(
                                Event(
                                    creatorId = creatorId,
                                    title = formState.title,
                                    description = formState.description,
                                    eventType = formState.eventType,
                                    minimumScore = formState.minimumScore,
                                    region = formState.region,
                                    commune = formState.commune,
                                    country = formState.country,
                                    address = formState.address,
                                    exactLatitude = formState.exactLocation?.latitude ?: formState.currentLatitude,
                                    exactLongitude = formState.exactLocation?.longitude ?: formState.currentLongitude,
                                    latitude = formState.exactLocation?.latitude ?: formState.currentLatitude,
                                    longitude = formState.exactLocation?.longitude ?: formState.currentLongitude,
                                    exactAddress = formState.exactLocation?.address ?: formState.address,
                                    maxParticipants = formState.maxParticipants.toInt(),
                                    isPublic = formState.isPublic,
                                    startAt = formState.startAt!!,
                                    endAt = formState.endAt!!,
                                    eventTimestamp = formState.startAt!!,
                                    joinQuestions = formState.questions,
                                    notificationRange = formState.notificationRange
                                ),
                                imageBytes
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                } else {
                    Text("Crear evento")
                }
            }

            uiState.errorMessage?.let {
                if (it != "LIMIT_REACHED") {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // ================= 📺 AD UNLOCK DIALOG =================
    LaunchedEffect(uiState.errorMessage) {
        if (uiState.errorMessage == "LIMIT_REACHED") {
            showAdDialog = true
        }
    }

    if (showAdDialog) {
        val isCapReached = (userLimitStats?.adsUnlocked ?: 0) >= 5
        AlertDialog(
            onDismissRequest = { 
                if (adUnlockState !is com.eventos.banana.ui.event.CreateEventViewModel.UnlockState.LoadingAd) {
                    showAdDialog = false 
                    viewModel.resetState() 
                }
            },
            title = {
                 if (isCapReached) {
                     Text("Límite Total Alcanzado")
                 } else {
                     when (adUnlockState) {
                         is com.eventos.banana.ui.event.CreateEventViewModel.UnlockState.Unlocked -> Text("¡Evento Desbloqueado! 🎉")
                         else -> Text("Límite Mensual Alcanzado")
                     }
                 }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isCapReached) {
                        Text("Has utilizado tu desbloqueo extra mensual (2 anuncios).")
                        Text("Para crear más eventos, vuélvete Banana Gold 🍌✨.")
                    } else {
                        when (val state = adUnlockState) {
                            is com.eventos.banana.ui.event.CreateEventViewModel.UnlockState.Idle -> {
                                Text("Has usado tu cupo gratuito. Puedes desbloquear 1 evento extra viendo 2 anuncios.")
                            }
                            is com.eventos.banana.ui.event.CreateEventViewModel.UnlockState.LoadingAd -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Cargando anuncio...")
                                }
                            }
                            is com.eventos.banana.ui.event.CreateEventViewModel.UnlockState.Progress -> {
                                LinearProgressIndicator(
                                    progress = { state.watched.toFloat() / state.required.toFloat() },
                                    modifier = Modifier.fillMaxWidth().height(8.dp)
                                )
                                Text("Has visto ${state.watched} de ${state.required} anuncios. ¡Falta poco!")
                            }
                            is com.eventos.banana.ui.event.CreateEventViewModel.UnlockState.Unlocked -> {
                                Text("¡Ya tienes un cupo extra! Puedes crear tu evento ahora.")
                            }
                            is com.eventos.banana.ui.event.CreateEventViewModel.UnlockState.Error -> {
                                Text("Hubo un problema: ${state.message}", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (isCapReached) {
                    Button(onClick = {
                        showAdDialog = false
                         viewModel.resetState()
                         onNavigateToPremium()
                    }) {
                        Text("Entendido (Ir a Premium)")
                    }
                } else {
                    when (val state = adUnlockState) {
                        is com.eventos.banana.ui.event.CreateEventViewModel.UnlockState.Unlocked -> {
                            Button(onClick = { 
                                showAdDialog = false 
                                viewModel.resetState()
                            }) {
                                Text("Continuar")
                            }
                        }
                        is com.eventos.banana.ui.event.CreateEventViewModel.UnlockState.LoadingAd -> { } 
                        else -> {
                            Button(onClick = {
                                val activity = context as? android.app.Activity
                                if (activity != null) {
                                    viewModel.watchAd(activity, creatorId)
                                }
                            }) {
                                Text(if (state is com.eventos.banana.ui.event.CreateEventViewModel.UnlockState.Progress) "Ver Siguiente" else "Ver Anuncio (Gratis)")
                            }
                        }
                    }
                }
            },
            dismissButton = {
                if (adUnlockState !is com.eventos.banana.ui.event.CreateEventViewModel.UnlockState.LoadingAd && adUnlockState !is com.eventos.banana.ui.event.CreateEventViewModel.UnlockState.Unlocked) {
                    TextButton(onClick = { 
                        showAdDialog = false 
                        viewModel.resetState()
                    }) {
                        Text("Cancelar")
                    }
                }
            }
        )
    }

    // Show guide overlay on first visit (DESPUÉS del Scaffold)
    if (!hasSeenCreateGuide.value) {
        CreateEventGuideOverlay(
            onDismiss = {
                hasSeenCreateGuide.value = true
                sharedPrefs.edit().putBoolean("create_event_guide_seen", true).apply()
            }
        )
    }
}
