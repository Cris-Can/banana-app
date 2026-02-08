package com.eventos.banana.ui.components

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.TypeFilter
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest

data class PlacePrediction(
    val placeId: String,
    val primaryText: String, // e.g. "Santiago"
    val secondaryText: String // e.g. "Metropolitan Region, Chile"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GooglePlacesAutocomplete(
    modifier: Modifier = Modifier,
    onPlaceSelected: (String, String) -> Unit, // (PlaceId, FullAddress)
    label: String = "Buscar ciudad...",
) {
    var query by remember { mutableStateOf("") }
    var predictions by remember { mutableStateOf<List<PlacePrediction>>(emptyList()) }
    val context = LocalContext.current
    val placesClient = remember { Places.createClient(context) }
    val token = remember { AutocompleteSessionToken.newInstance() }

    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        if (query.length > 2) {
            val request = FindAutocompletePredictionsRequest.builder()
                .setSessionToken(token)
                .setQuery(query)
                .setTypeFilter(TypeFilter.CITIES) // 🏙️ Only Cities
                .build()

            placesClient.findAutocompletePredictions(request)
                .addOnSuccessListener { response ->
                    predictions = response.autocompletePredictions.map {
                        PlacePrediction(
                            it.placeId,
                            it.getPrimaryText(null).toString(),
                            it.getSecondaryText(null).toString()
                        )
                    }
                    expanded = predictions.isNotEmpty()
                }
                .addOnFailureListener {
                    predictions = emptyList()
                    expanded = false
                }
        } else {
            expanded = false
        }
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(label) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryEditable, true), // Compose 1.7+ Fix
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            predictions.forEach { prediction ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "${prediction.primaryText}, ${prediction.secondaryText}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    onClick = {
                        query = prediction.primaryText
                        expanded = false
                        onPlaceSelected(prediction.placeId, "${prediction.primaryText}, ${prediction.secondaryText}")
                    }
                )
            }
        }
    }
}
