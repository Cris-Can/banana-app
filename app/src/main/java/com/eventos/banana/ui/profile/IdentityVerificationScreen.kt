package com.eventos.banana.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import com.google.firebase.storage.FirebaseStorage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentityVerificationScreen(
    userId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedImageUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var isUploading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> selectedImageUri = uri }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Verificar Identidad") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Sube una foto de tu cédula de identidad (frente)",
                style = MaterialTheme.typography.titleMedium
            )

            Card(
                modifier = Modifier.fillMaxWidth().height(250.dp),
                onClick = { photoPicker.launch("image/*") },
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedImageUri != null) {
                        AsyncImage(
                            model = selectedImageUri,
                            contentDescription = "Foto de cédula",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Add,
                                null,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("Toca para seleccionar foto")
                        }
                    }
                }
            }

            if (selectedImageUri != null) {
                Button(
                    onClick = {
                        isUploading = true
                        val storageRef = FirebaseStorage.getInstance()
                            .reference.child("identityDocs/$userId/front.jpg")
                        selectedImageUri?.let { uri ->
                            val bytes = com.eventos.banana.util.ImageCompressor.compressFromUri(
                                context,
                                uri,
                                maxWidth = 1920,
                                maxHeight = 1920,
                                quality = 70
                            ) ?: context.contentResolver.openInputStream(uri)?.use { stream ->
                                stream.readBytes()
                            }
                            if (bytes != null) {
                                val metadata = com.google.firebase.storage.StorageMetadata.Builder()
                                    .setContentType("image/jpeg")
                                    .build()
                                storageRef.putBytes(bytes, metadata)
                                    .addOnSuccessListener {
                                        // Marcar como pendiente de verificación
                                        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                            .collection("users")
                                            .document(userId)
                                            .update("identityVerificationStatus", "pending")
                                            .addOnSuccessListener {
                                                android.util.Log.d("IdentityVerification", "identityVerificationStatus=pending written for $userId")
                                            }
                                            .addOnFailureListener { e ->
                                                android.util.Log.e("IdentityVerification", "Failed to write status: ${e.message}")
                                            }
                                        statusMessage = "Foto subida correctamente. Un administrador revisará tu documentación."
                                        isUploading = false
                                    }
                                    .addOnFailureListener { e ->
                                        statusMessage = "Error al subir: ${e.message}"
                                        isUploading = false
                                    }
                            }
                        }
                    },
                    enabled = !isUploading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isUploading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Subir foto")
                    }
                }
            }

            statusMessage?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (it.startsWith("Error")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.weight(1f))
            Text(
                "Tu documento será revisado manualmente. Una vez aprobado, podrás crear y ver eventos +18.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
