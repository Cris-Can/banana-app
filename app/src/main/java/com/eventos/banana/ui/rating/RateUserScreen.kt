package com.eventos.banana.ui.rating

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eventos.banana.viewmodel.RateUserUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RateUserScreen(
    uiState: RateUserUiState,
    onSubmit: (Int, String) -> Unit,
    onBack: () -> Unit
) {
    var score by remember { mutableStateOf(0) }
    var comment by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(com.eventos.banana.R.string.rating_title)) }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator()
            } else if (uiState.success) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(stringResource(com.eventos.banana.R.string.rating_sent), style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onBack) {
                        Text(stringResource(com.eventos.banana.R.string.common_back))
                    }
                }
            } else if (uiState.alreadyRated) {
                 Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(stringResource(com.eventos.banana.R.string.rating_already_rated), style = MaterialTheme.typography.titleMedium)
                     Spacer(modifier = Modifier.height(16.dp))
                     Button(onClick = onBack) {
                         Text(stringResource(com.eventos.banana.R.string.common_back))
                     }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Calificar a ${uiState.targetNickname}",
                        style = MaterialTheme.typography.headlineSmall
                    )

                    // STARS
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        (1..5).forEach { star ->
                            Icon(
                                imageVector = if (star <= score) Icons.Filled.Star else Icons.Outlined.Star,
                                contentDescription = "$star estrellas",
                                tint = if (star <= score) Color(0xFFFFC107) else Color.Gray,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clickable { score = star }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = comment,
                        onValueChange = { comment = it },
                        label = { Text(stringResource(com.eventos.banana.R.string.rating_comment_label)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                    )

                    uiState.errorMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }

                    Button(
                        onClick = { onSubmit(score, comment) },
                        enabled = score > 0,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(com.eventos.banana.R.string.common_send))
                    }
                    
                    TextButton(onClick = onBack) {
                        Text(stringResource(com.eventos.banana.R.string.common_cancel))
                    }
                }
            }
        }
    }
}
