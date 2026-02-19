package com.eventos.banana.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest

data class PlacePredictionItem(
    val placeId: String,
    val primaryText: String,
    val secondaryText: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GooglePlacesSearchDialog(
    onDismiss: () -> Unit,
    onPlaceSelected: (String, String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var predictions by remember { mutableStateOf<List<PlacePredictionItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val context = LocalContext.current
    val placesClient = remember { Places.createClient(context) }
    val token = remember { AutocompleteSessionToken.newInstance() }

    LaunchedEffect(query) {
        if (query.length > 2) {
            isLoading = true
            errorMessage = null
            
            val request = FindAutocompletePredictionsRequest.builder()
                .setSessionToken(token)
                .setQuery(query)
                .setTypesFilter(listOf("(cities)"))
                .build()

            placesClient.findAutocompletePredictions(request)
                .addOnSuccessListener { response ->
                    isLoading = false
                    predictions = response.autocompletePredictions.map {
                        PlacePredictionItem(
                            it.placeId,
                            it.getPrimaryText(null).toString(),
                            it.getSecondaryText(null).toString()
                        )
                    }
                }
                .addOnFailureListener { exception ->
                    isLoading = false
                    predictions = emptyList()
                    errorMessage = "Error: ${exception.localizedMessage}"
                }
        } else {
            predictions = emptyList()
            isLoading = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(com.eventos.banana.R.string.places_search_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, stringResource(com.eventos.banana.R.string.common_close))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Search Field
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(com.eventos.banana.R.string.places_search_hint)) },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp)
                    )
                }

                // Predictions List
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(predictions) { prediction ->
                        ListItem(
                            headlineContent = { Text(prediction.primaryText) },
                            supportingContent = { Text(prediction.secondaryText) },
                            modifier = Modifier.clickable {
                                onPlaceSelected(prediction.placeId, "${prediction.primaryText}, ${prediction.secondaryText}")
                            }
                        )
                        HorizontalDivider()
                    }
                    
                    if (query.length > 2 && predictions.isEmpty() && !isLoading) {
                        item {
                            Text(
                                stringResource(com.eventos.banana.R.string.places_no_results),
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
