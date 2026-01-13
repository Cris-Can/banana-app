package com.eventos.banana.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eventos.banana.data.local.regionsWithCommunes
import com.eventos.banana.viewmodel.ProfileUiState
import com.eventos.banana.viewmodel.ProfileViewModel
import com.eventos.banana.viewmodel.SessionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    sessionViewModel: SessionViewModel,
    onBack: () -> Unit,
    profileViewModel: ProfileViewModel = viewModel()
) {
    val profileUiState by sessionViewModel.profileUiState.collectAsState()
    val uiState by profileViewModel.uiState.collectAsState()
    val profile = profileUiState.profile

    var nickname by remember(profile?.nickname) {
        mutableStateOf(profile?.nickname ?: "")
    }

    var selectedRegion by remember(profile?.region) {
        mutableStateOf<String?>(profile?.region)
    }

    var selectedCommune by remember(profile?.commune) {
        mutableStateOf<String?>(profile?.commune)
    }

    var notifyByCommune by remember(profile?.notifyEventsByCommune) {
        mutableStateOf(profile?.notifyEventsByCommune ?: false)
    }

    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Perfil") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←")
                    }
                }
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxWidth()
        ) {

            if (profileUiState.isLoading) {
                CircularProgressIndicator()
                return@Column
            }

            profile?.let {

                val canSaveNickname =
                    nickname.isNotBlank() && nickname != it.nickname

                val canSaveLocation =
                    selectedRegion != null &&
                            selectedCommune != null &&
                            (
                                    selectedRegion != it.region ||
                                            selectedCommune != it.commune
                                    )

                // EMAIL
                OutlinedTextField(
                    value = it.email,
                    onValueChange = {},
                    label = { Text("Email") },
                    enabled = false,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))

                // NICKNAME
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("Nickname") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))

                // REGIÓN
                var regionExpanded by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(
                    expanded = regionExpanded,
                    onExpandedChange = { regionExpanded = !regionExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedRegion ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Región") },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = regionExpanded,
                        onDismissRequest = { regionExpanded = false }
                    ) {
                        regionsWithCommunes.keys.forEach { region ->
                            DropdownMenuItem(
                                text = { Text(region) },
                                onClick = {
                                    selectedRegion = region
                                    selectedCommune = null
                                    regionExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // COMUNA
                var communeExpanded by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(
                    expanded = communeExpanded,
                    onExpandedChange = {
                        if (selectedRegion != null) communeExpanded = !communeExpanded
                    }
                ) {
                    OutlinedTextField(
                        value = selectedCommune ?: "",
                        onValueChange = {},
                        readOnly = true,
                        enabled = selectedRegion != null,
                        label = { Text("Comuna") },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = communeExpanded,
                        onDismissRequest = { communeExpanded = false }
                    ) {
                        selectedRegion?.let { region ->
                            regionsWithCommunes[region]?.forEach { commune ->
                                DropdownMenuItem(
                                    text = { Text(commune) },
                                    onClick = {
                                        selectedCommune = commune
                                        communeExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // 🔔 SWITCH NOTIFICACIONES
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recibir eventos de mi comuna",
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = notifyByCommune,
                        onCheckedChange = {
                            notifyByCommune = it
                            profileViewModel.updateNotifyEventsByCommune(
                                uid = it@profile.uid,
                                enabled = it
                            )
                        }
                    )
                }

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = {
                        profileViewModel.updateNickname(it.uid, nickname)
                    },
                    enabled = canSaveNickname && uiState !is ProfileUiState.Loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Guardar perfil")
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = {
                        profileViewModel.updateLocation(
                            uid = it.uid,
                            region = selectedRegion!!,
                            commune = selectedCommune!!
                        )
                    },
                    enabled = canSaveLocation && uiState !is ProfileUiState.Loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Guardar ubicación")
                }
            }

            LaunchedEffect(uiState) {
                if (uiState is ProfileUiState.Success) {
                    snackbarHostState.showSnackbar("Cambios guardados correctamente ✅")
                }
            }
        }
    }
}
