package com.eventos.banana.ui.event

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.eventos.banana.data.ChileCommunesList
import com.eventos.banana.domain.model.CreateEventUiState
import com.eventos.banana.domain.model.Event
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEventScreen(
    creatorId: String,
    viewModel: com.eventos.banana.viewmodel.CreateEventViewModel,
    onSuccess: () -> Unit,
    onSelectExactLocation: () -> Unit
) {
    // ================= CONTEXTO =================
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locations = remember {
        ChileCommunesList.getRegionsWithCommunes()
    }

    // ================= ESTADO (ViewModel) =================
    val uiState by viewModel.uiState.collectAsState()
    val formState by viewModel.formState.collectAsState()

    val regionExpanded = remember { mutableStateOf(false) }
    val communeExpanded = remember { mutableStateOf(false) }

    val communes = locations[formState.region] ?: emptyList()
    val scrollState = rememberScrollState()
    var timeError by remember { mutableStateOf<String?>(null) }

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
                        lat = result.latitude,
                        lng = result.longitude
                    )
                    // Resetear communeExpanded para que no muestre el menú
                    communeExpanded.value = false
                }
            }
        }
    }

    // Auto-fill address from Map Picker
    // Auto-fill address handled in ViewModel now

    // ================= ÉXITO =================
    LaunchedEffect(uiState.success) {
        if (uiState.success) onSuccess()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Crear evento", style = MaterialTheme.typography.headlineSmall)

        // Helper Text
        Text(
            "* Campos obligatorios",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // ---------- FOTO DE PORTADA ----------
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clickable {
                    photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                if (formState.selectedImageUri != null) {
                    AsyncImage(
                        model = formState.selectedImageUri,
                        contentDescription = "Portada",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        Text("📷", style = MaterialTheme.typography.displayMedium)
                        Text("Agregar Foto de Portada")
                    }
                }
            }
        }

        // ---------- DATOS BÁSICOS ----------
        OutlinedTextField(formState.title, { viewModel.updateTitle(it) }, label = { Text("Título *") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(formState.description, { viewModel.updateDescription(it) }, label = { Text("Descripción *") }, modifier = Modifier.fillMaxWidth())

        // ---------- TIPO DE EVENTO ----------
        val eventTypeExpanded = remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = eventTypeExpanded.value,
            onExpandedChange = { eventTypeExpanded.value = !eventTypeExpanded.value }
        ) {
            OutlinedTextField(
                value = "${formState.eventType.emoji} ${formState.eventType.displayName}",
                onValueChange = {},
                readOnly = true,
                label = { Text("Tipo de Evento *") },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = eventTypeExpanded.value,
                onDismissRequest = { eventTypeExpanded.value = false }
            ) {
                com.eventos.banana.domain.model.EventType.values().forEach { type ->
                    DropdownMenuItem(
                        text = { Text("${type.emoji} ${type.displayName}") },
                        onClick = {
                            viewModel.updateEventType(type)
                            eventTypeExpanded.value = false
                        }
                    )
                }
            }
        }

        // Event Type Description
        Text(
            text = when (formState.eventType) {
                com.eventos.banana.domain.model.EventType.DEPORTES -> "Actividades físicas y deportivas"
                com.eventos.banana.domain.model.EventType.SOCIAL -> "Reuniones sociales y celebraciones"
                com.eventos.banana.domain.model.EventType.CULTURAL -> "Arte, teatro, música, cine"
                com.eventos.banana.domain.model.EventType.EDUCATIVO -> "Talleres, cursos, charlas"
                com.eventos.banana.domain.model.EventType.JUEGOS -> "Videojuegos, juegos de mesa"
                com.eventos.banana.domain.model.EventType.GASTRONOMIA -> "Comida, cocina, restaurantes"
                com.eventos.banana.domain.model.EventType.AIRE_LIBRE -> "Camping, senderismo, naturaleza"
                com.eventos.banana.domain.model.EventType.OTRO -> "Otros eventos"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp)
        )

        // ---------- SCORE MÍNIMO (OPCIONAL) ----------
        val scoreOptions = mapOf(
            null to "Sin restricción",
            3.0 to "⭐ 3.0+",
            3.5 to "⭐ 3.5+",
            4.0 to "🥇 4.0+",
            4.5 to "🏆 4.5+"
        )
        val scoreExpanded = remember { mutableStateOf(false) }
        
        ExposedDropdownMenuBox(
            expanded = scoreExpanded.value,
            onExpandedChange = { scoreExpanded.value = !scoreExpanded.value }
        ) {
            OutlinedTextField(
                value = scoreOptions[formState.minimumScore] ?: "Sin restricción",
                onValueChange = {},
                readOnly = true,
                label = { Text("Reputación Mínima (Opcional)") },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = scoreExpanded.value) }
            )

            ExposedDropdownMenu(
                expanded = scoreExpanded.value,
                onDismissRequest = { scoreExpanded.value = false }
            ) {
                scoreOptions.forEach { (score, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            viewModel.updateMinimumScore(score)
                            scoreExpanded.value = false
                        }
                    )
                }
            }
        }
        
        Text(
            text = if (formState.minimumScore != null) {
                "⚠️ Solo usuarios con reputación ${formState.minimumScore}+ podrán unirse"
            } else {
                "Todos los usuarios podrán solicitar unirse"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (formState.minimumScore != null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp)
        )

        // ---------- REGIÓN ----------
        // Validación de datos: Si la región seleccionada no existe en la lista (legacy data), resetear
        LaunchedEffect(formState.region, locations) {
            if (formState.region.isNotEmpty() && !locations.containsKey(formState.region)) {
                viewModel.updateRegion("")
                viewModel.updateCommune("")
            }
        }

        ExposedDropdownMenuBox(
            expanded = regionExpanded.value,
            onExpandedChange = { regionExpanded.value = !regionExpanded.value }
        ) {
            Box(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = formState.region,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Región *") },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                )
                // Overlay para capturar click en dispositivos mañosos (MIUI)
                Box(
                    Modifier
                        .matchParentSize()
                        .clickable { regionExpanded.value = true }
                )
            }

            ExposedDropdownMenu(
                expanded = regionExpanded.value,
                onDismissRequest = { regionExpanded.value = false },
                modifier = Modifier.heightIn(max = 250.dp) // Limitar altura para scroll
            ) {
                locations.keys.forEach {
                    DropdownMenuItem(
                        text = { Text(it) },
                        onClick = {
                            viewModel.updateRegion(it)
                            viewModel.updateCommune("")
                            regionExpanded.value = false
                        }
                    )
                }
            }
        }

        // ---------- COMUNA ----------
        ExposedDropdownMenuBox(
            expanded = communeExpanded.value,
            onExpandedChange = {
                if (communes.isNotEmpty()) communeExpanded.value = !communeExpanded.value
            }
        ) {
            Box(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = formState.commune,
                    onValueChange = {},
                    readOnly = true,
                    enabled = communes.isNotEmpty(),
                    label = { Text("Comuna * (${communes.size})") }, // Debug hint visual
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                )
                // Overlay para capturar click
                Box(
                    Modifier
                        .matchParentSize()
                        .clickable(enabled = communes.isNotEmpty()) { 
                            communeExpanded.value = true 
                        }
                )
            }

            ExposedDropdownMenu(
                expanded = communeExpanded.value,
                onDismissRequest = { communeExpanded.value = false },
                modifier = Modifier.heightIn(max = 250.dp)
            ) {
                communes.forEach {
                    DropdownMenuItem(
                        text = { Text(it) },
                        onClick = {
                            viewModel.updateCommune(it)
                            communeExpanded.value = false
                        }
                    )
                }
            }
        }

        // ---------- UBICACIÓN AUTOMÁTICA ----------
        Button(
            onClick = {
                locationPermissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("📍 Usar mi ubicación (Región/Comuna)")
        }
        
        // ---------- DIRECCIÓN EXACTA (PRIVADA) ----------
        OutlinedTextField(
            value = formState.address,
            onValueChange = { viewModel.updateAddress(it) },
            label = { Text("Dirección Exacta (Solo para aceptados) *") },
            placeholder = { Text("Ej: Calle Falsa 123, Depto 401") },
            modifier = Modifier.fillMaxWidth(),
            supportingText = { Text("Esta información NO es pública.") }
        )

        // Exact Location Picker
        OutlinedCard(
            onClick = onSelectExactLocation,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    text = if (formState.exactLocation != null) "📍 Ubicación exacta seleccionada" else "🗺️ Seleccionar ubicación exacta en Mapa",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (formState.exactLocation != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // ---------- CUPOS ----------
        OutlinedTextField(
            value = formState.maxParticipants,
            onValueChange = { if (it.all(Char::isDigit)) viewModel.updateMaxParticipants(it) },
            label = { Text("Cupos máximos *") },
            modifier = Modifier.fillMaxWidth()
        )

        // ---------- FECHA INICIO ----------
        Text("Inicio", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Button Date
            Button(
                modifier = Modifier.weight(1f),
                onClick = {
                    val cal = Calendar.getInstance()
                    formState.startAt?.let { cal.timeInMillis = it }
                    DatePickerDialog(
                        context,
                        { _, y, m, d ->
                            val newCal = Calendar.getInstance()
                            formState.startAt?.let { newCal.timeInMillis = it }
                            newCal.set(y, m, d)
                            viewModel.updateStartAt(newCal.timeInMillis)
                        },
                        cal.get(Calendar.YEAR),
                        cal.get(Calendar.MONTH),
                        cal.get(Calendar.DAY_OF_MONTH)
                    ).show()
                }
            ) {
                Text(formState.startAt?.let { SimpleDateFormat("dd/MM/yyyy").format(Date(it)) } ?: "📅 Fecha")
            }

            // Button Time
            Button(
                modifier = Modifier.weight(1f),
                onClick = {
                    val cal = Calendar.getInstance()
                    formState.startAt?.let { cal.timeInMillis = it }
                    TimePickerDialog(
                        context,
                        { _, h, min ->
                            val newCal = Calendar.getInstance()
                            formState.startAt?.let { newCal.timeInMillis = it }
                            newCal.set(Calendar.HOUR_OF_DAY, h)
                            newCal.set(Calendar.MINUTE, min)
                            viewModel.updateStartAt(newCal.timeInMillis)
                        },
                        cal.get(Calendar.HOUR_OF_DAY),
                        cal.get(Calendar.MINUTE),
                        true
                    ).show()
                }
            ) {
                Text(formState.startAt?.let { SimpleDateFormat("HH:mm").format(Date(it)) } ?: "⏰ Hora")
            }
        }

        // ---------- FECHA TÉRMINO ----------
        Text("Término", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Button Date
            Button(
                modifier = Modifier.weight(1f),
                onClick = {
                    val cal = Calendar.getInstance()
                    formState.endAt?.let { cal.timeInMillis = it }
                    DatePickerDialog(
                        context,
                        { _, y, m, d ->
                            val newCal = Calendar.getInstance()
                            formState.endAt?.let { newCal.timeInMillis = it }
                            newCal.set(y, m, d)
                            
                            // Simple client-side check
                            val start = formState.startAt
                            if (start != null && newCal.timeInMillis <= start) {
                                timeError = "El término debe ser posterior al inicio"
                            } else {
                                timeError = null
                            }
                            viewModel.updateEndAt(newCal.timeInMillis)
                        },
                        cal.get(Calendar.YEAR),
                        cal.get(Calendar.MONTH),
                        cal.get(Calendar.DAY_OF_MONTH)
                    ).show()
                }
            ) {
                Text(formState.endAt?.let { SimpleDateFormat("dd/MM/yyyy").format(Date(it)) } ?: "📅 Fecha")
            }

            // Button Time
            Button(
                modifier = Modifier.weight(1f),
                onClick = {
                    val cal = Calendar.getInstance()
                    formState.endAt?.let { cal.timeInMillis = it }
                    TimePickerDialog(
                        context,
                        { _, h, min ->
                            val newCal = Calendar.getInstance()
                            formState.endAt?.let { newCal.timeInMillis = it }
                            newCal.set(Calendar.HOUR_OF_DAY, h)
                            newCal.set(Calendar.MINUTE, min)

                            // Simple client-side check
                            val startAt = formState.startAt
                            if (startAt != null && newCal.timeInMillis <= startAt) {
                                timeError = "El término debe ser posterior al inicio"
                            } else {
                                timeError = null
                            }
                            viewModel.updateEndAt(newCal.timeInMillis)
                        },
                        cal.get(Calendar.HOUR_OF_DAY),
                        cal.get(Calendar.MINUTE),
                        true
                    ).show()
                }
            ) {
                Text(formState.endAt?.let { SimpleDateFormat("HH:mm").format(Date(it)) } ?: "⏰ Hora")
            }
        }

        timeError?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 4.dp)
            )
        }


        Divider()

        // ---------- CREAR EVENTO ----------
        Button(
            enabled =
                !uiState.isLoading &&
                formState.startAt != null &&
                        formState.endAt != null &&
                        formState.startAt!! < formState.endAt!! &&
                        formState.title.isNotBlank() &&
                        formState.description.isNotBlank() &&
                        formState.region.isNotBlank() &&
                        formState.commune.isNotBlank() &&
                        formState.address.isNotBlank() &&
                        formState.maxParticipants.toIntOrNull()?.let { it > 0 } == true,
            onClick = {
                scope.launch {
                    val imageBytes = formState.selectedImageUri?.let { uri ->
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
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
                            address = formState.address,
                             exactLatitude = formState.exactLocation?.latitude ?: formState.currentLatitude,
                             exactLongitude = formState.exactLocation?.longitude ?: formState.currentLongitude,
                             exactAddress = formState.exactLocation?.address ?: formState.address,
                             maxParticipants = formState.maxParticipants.toInt(),
                             startAt = formState.startAt!!,
                             endAt = formState.endAt!!,
                             eventTimestamp = formState.startAt!!,
                             joinQuestions = formState.questions
                        ),
                        imageBytes
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Crear evento")
            }
        }

        uiState.errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}
