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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import com.eventos.banana.ui.components.shimmerEffect

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

    // Load initial stats for premium features/limits
    LaunchedEffect(creatorId) {
        viewModel.loadUserStats(creatorId)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(com.eventos.banana.R.string.create_event_title), style = MaterialTheme.typography.headlineSmall)

        // Helper Text
        Text(
            "* Campos obligatorios",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // ---------- FOTO DE PORTADA ----------
        // ---------- FOTO DE PORTADA ----------
        val stroke = remember { androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f) }
        val borderColor = MaterialTheme.colorScheme.outlineVariant
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clickable {
                    photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            border = if (formState.selectedImageUri == null) {
                androidx.compose.foundation.BorderStroke(1.dp, borderColor) // Fallback if dash not easy, but let's try drawBehind instead
            } else null
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .then(
                        if (formState.selectedImageUri == null) {
                            Modifier.drawBehind {
                                drawRoundRect(
                                    color = borderColor,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                                        width = 4f,
                                        pathEffect = stroke
                                    ),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx())
                                )
                            }
                        } else Modifier
                    ),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                if (formState.selectedImageUri != null) {
                    Box(Modifier.fillMaxSize()) {
                        coil.compose.SubcomposeAsyncImage(
                            model = formState.selectedImageUri,
                            contentDescription = stringResource(com.eventos.banana.R.string.create_event_cd_cover),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            loading = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .shimmerEffect()
                                )
                            }
                        )
                        
                        // "Change" Badge
                        Surface(
                            modifier = Modifier
                                .align(androidx.compose.ui.Alignment.BottomEnd)
                                .padding(12.dp),
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                        ) {
                            Text(
                                "Cambiar foto",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                } else {
                    Column(
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Filled.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            "Agregar Foto de Portada",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // ---------- DATOS BÁSICOS ----------
        OutlinedTextField(formState.title, { viewModel.updateTitle(it) }, label = { Text(stringResource(com.eventos.banana.R.string.create_event_field_title)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(formState.description, { viewModel.updateDescription(it) }, label = { Text(stringResource(com.eventos.banana.R.string.create_event_field_description)) }, modifier = Modifier.fillMaxWidth())

        // ---------- TIPO DE EVENTO ----------
        val eventTypeExpanded = remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = eventTypeExpanded.value,
            onExpandedChange = { eventTypeExpanded.value = !eventTypeExpanded.value }
        ) {
            OutlinedTextField(
                value = "${formState.eventType.emoji} ${formState.eventType.localizedName()}",
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(com.eventos.banana.R.string.create_event_field_type)) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = eventTypeExpanded.value,
                onDismissRequest = { eventTypeExpanded.value = false }
            ) {
                com.eventos.banana.domain.model.EventType.values().forEach { type ->
                    DropdownMenuItem(
                        text = { Text("${type.emoji} ${type.localizedName()}") },
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
                label = { Text(stringResource(com.eventos.banana.R.string.create_event_field_min_reputation)) },
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
                    label = { Text(stringResource(com.eventos.banana.R.string.create_event_field_region)) },
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
                    label = { Text(stringResource(com.eventos.banana.R.string.create_event_field_commune, communes.size)) }, // Debug hint visual
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
            label = { Text(stringResource(com.eventos.banana.R.string.create_event_field_address)) },
            placeholder = { Text(stringResource(com.eventos.banana.R.string.create_event_field_address_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            supportingText = { Text(stringResource(com.eventos.banana.R.string.create_event_field_address_note)) },
            singleLine = true
        )

        // Exact Location Picker (PREMIUM CARD)
        Card(
            onClick = onSelectExactLocation,
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Map Icon / Mini Map placeholder
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                         Icon(
                            imageVector = androidx.compose.material.icons.Icons.Filled.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (formState.exactLocation != null) "Ubicación exacta definida" else "Definir ubicación en mapa",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                         text = if (formState.exactLocation != null) "Tocá para editar" else "Obligatorio para el check-in",
                         style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // icon removed from here, import moved to top

// ... inside the file ...

                Icon(
                    imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = stringResource(com.eventos.banana.R.string.create_event_cd_go),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
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
                            .clickable(enabled = !isRestricted) {
                                viewModel.updateNotificationRange(range)
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
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

        // ---------- FECHA INICIO ----------
        Text("Cuándo empieza", style = MaterialTheme.typography.titleMedium)

        
        // Let's rewrite the Row approach to be cleaner:
        
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // DATE PICKER
            Box(Modifier.weight(1f)) {
                OutlinedTextField(
                    value = formState.startAt?.let { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(it)) } ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Fecha") },
                    trailingIcon = { Icon(androidx.compose.material.icons.Icons.Default.DateRange, null) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledContainerColor = Color.Transparent
                    ),
                    enabled = false
                )
                Box(
                    Modifier
                        .matchParentSize()
                        .clickable {
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
                )
            }

            // TIME PICKER
            Box(Modifier.weight(1f)) {
                OutlinedTextField(
                    value = formState.startAt?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it)) } ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Hora") },
                    trailingIcon = { Icon(androidx.compose.material.icons.Icons.Default.DateRange, null) }, // Use Watch/Clock icon if available, else Date
                    modifier = Modifier.fillMaxWidth(),
                     colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                         disabledContainerColor = Color.Transparent
                    ),
                    enabled = false
                )
                Box(
                    Modifier
                        .matchParentSize()
                        .clickable {
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
                )
            }
        }

        // ---------- FECHA TÉRMINO ----------
        Text("Cuándo termina", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
             // DATE PICKER END
            Box(Modifier.weight(1f)) {
                OutlinedTextField(
                    value = formState.endAt?.let { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(it)) } ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Fecha") },
                    trailingIcon = { Icon(androidx.compose.material.icons.Icons.Default.DateRange, null) },
                    modifier = Modifier.fillMaxWidth(),
                     colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                         disabledContainerColor = Color.Transparent
                    ),
                    enabled = false
                )
                Box(
                    Modifier
                        .matchParentSize()
                        .clickable {
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
                )
            }

            // TIME PICKER END
            Box(Modifier.weight(1f)) {
                OutlinedTextField(
                    value = formState.endAt?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it)) } ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Hora") },
                    trailingIcon = { Icon(androidx.compose.material.icons.Icons.Default.DateRange, null) }, // Clock icon if avail
                    modifier = Modifier.fillMaxWidth(),
                     colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                         disabledContainerColor = Color.Transparent
                    ),
                    enabled = false
                )
                Box(
                    Modifier
                        .matchParentSize()
                        .clickable {
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
                )
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


        HorizontalDivider()

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
                val safetyError = com.eventos.banana.util.TextSafetyUtils.validateEventContent(formState.title, formState.description)
                if (safetyError != null) {
                    viewModel.updateErrorMessage(safetyError) // Reuse existing error mechanism
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
                                address = formState.address,
                                exactLatitude = formState.exactLocation?.latitude ?: formState.currentLatitude,
                                exactLongitude = formState.exactLocation?.longitude ?: formState.currentLongitude,
                                // ⚠️ CRITICAL FIX: Populate main lat/lng fields too!
                                latitude = formState.exactLocation?.latitude ?: formState.currentLatitude,
                                longitude = formState.exactLocation?.longitude ?: formState.currentLongitude,
                                exactAddress = formState.exactLocation?.address ?: formState.address,
                                maxParticipants = formState.maxParticipants.toInt(),
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
            if (it == "LIMIT_REACHED") {
               // Handled by Effect below
            } else {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
        
        // 🚀 Espacio para evitar que el teclado o la barra de navegación tapen el botón final
        Spacer(modifier = Modifier.height(32.dp))
        Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }

    // ================= 📺 AD UNLOCK DIALOG =================
    // Definitions moved to top

    // Intercept Limit Error
    LaunchedEffect(uiState.errorMessage) {
        if (uiState.errorMessage == "LIMIT_REACHED") {
            showAdDialog = true
        }
    }

    if (showAdDialog) {
        // Robust check via Structured Data
        val isCapReached = (userLimitStats?.adsUnlocked ?: 0) >= 1
        
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
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
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
}
