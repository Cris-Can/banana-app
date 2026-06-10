package com.eventos.banana.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventos.banana.domain.model.ExactLocation
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject

data class ExternalEventsUiState(
    val snackbarMessage: String? = null,
    val externalBotReady: Boolean = false,
    val externalSources: List<Map<String, Any>> = emptyList(),
    val pendingExternalEvents: List<Map<String, Any>> = emptyList(),
    val newSourceUrl: String = "",
    val newSourceName: String = "",
    val isLoadingSources: Boolean = false,
    val isLoadingPending: Boolean = false,
    val isPublishing: Boolean = false,
    val isRunningScheduler: Boolean = false,
    val selectedModel: String = "gemini-flash",
    val instagramUrl: String = "",
    val isProcessingInstagram: Boolean = false,
    val mapResult: ExactLocation? = null,
    val showApprovalDialog: Boolean = false,
    val approvalPendingId: String = "",
    val approvalTitle: String = "",
    val approvalEventUrl: String = "",
    val approvalDescription: String = "",
    val approvalCategory: String = "OTRO",
    val approvalScrapedLocation: String = "",
    val approvalStartAt: String = "",
    val approvalEndAt: String = "",
    val approvalExpiresAt: String = "",
    val approvalIsAdultContent: Boolean = false,
    val approvalRegion: String = "",
    val approvalCommune: String = "",
    val approvalLat: String = "",
    val approvalLng: String = "",
    val approvalAddress: String = "",
    val createdExternalEvents: List<Map<String, Any>> = emptyList(),
    val isLoadingCreated: Boolean = false,
    val showDeleteConfirmDialog: Boolean = false,
    val eventToDeleteId: String? = null,
    val eventToDeleteTitle: String? = null,
    val isDeletingEvent: Boolean = false
)

@HiltViewModel
class ExternalEventsViewModel @Inject constructor(
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _state = MutableStateFlow(ExternalEventsUiState())
    val state: StateFlow<ExternalEventsUiState> = _state.asStateFlow()

    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()

    fun setupExternalBot() {
        viewModelScope.launch {
            try {
                functions.getHttpsCallable("setupExternalEventBot").call().await()
                _state.update { it.copy(externalBotReady = true, snackbarMessage = "✅ Bot configurado correctamente") }
            } catch (e: Exception) {
                Timber.e(e, "setupExternalBot error")
                _state.update { it.copy(snackbarMessage = "❌ Error al configurar bot: ${e.message}") }
            }
        }
    }

    fun loadExternalSources() {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingSources = true) }
            try {
                val result = functions.getHttpsCallable("listExternalSources").call().await()
                @Suppress("UNCHECKED_CAST")
                val data = result.data as? Map<String, Any> ?: emptyMap()
                @Suppress("UNCHECKED_CAST")
                val sources = data["sources"] as? List<Map<String, Any>> ?: emptyList()
                _state.update { it.copy(externalSources = sources, isLoadingSources = false) }
            } catch (e: Exception) {
                Timber.e(e, "loadExternalSources error")
                _state.update { it.copy(isLoadingSources = false, snackbarMessage = "❌ Error al cargar fuentes: ${e.message}") }
            }
        }
    }

    fun addSource(url: String, name: String) {
        viewModelScope.launch {
            try {
                functions.getHttpsCallable("addExternalSource").call(mapOf("url" to url, "name" to name)).await()
                _state.update { it.copy(newSourceUrl = "", newSourceName = "", snackbarMessage = "✅ Fuente agregada") }
                loadExternalSources()
            } catch (e: Exception) {
                Timber.e(e, "addSource error")
                _state.update { it.copy(snackbarMessage = "❌ Error al agregar fuente: ${e.message}") }
            }
        }
    }

    fun removeSource(id: String) {
        viewModelScope.launch {
            try {
                functions.getHttpsCallable("removeExternalSource").call(mapOf("id" to id)).await()
                _state.update { it.copy(snackbarMessage = "✅ Fuente eliminada") }
                loadExternalSources()
            } catch (e: Exception) {
                Timber.e(e, "removeSource error")
                _state.update { it.copy(snackbarMessage = "❌ Error al eliminar fuente: ${e.message}") }
            }
        }
    }

    fun checkExternalBotStatus() {
        viewModelScope.launch {
            try {
                val doc = firestore.collection("users").document("external_event_bot").get().await()
                _state.update { it.copy(externalBotReady = doc.exists()) }
            } catch (e: Exception) {
                Timber.e(e, "checkExternalBotStatus error")
            }
        }
    }

    fun runScheduler(model: String) {
        viewModelScope.launch {
            _state.update { it.copy(isRunningScheduler = true) }
            try {
                val result = functions.getHttpsCallable("runSchedulerNow")
                    .call(mapOf("model" to model))
                    .await()
                @Suppress("UNCHECKED_CAST")
                val data = result.data as? Map<String, Any> ?: emptyMap()
                val checked = data["checkedCount"] as? Number ?: 0
                val newEvents = data["newEventsCount"] as? Number ?: 0
                val aiCount = data["aiEnrichments"] as? Number ?: 0
                _state.update {
                    it.copy(
                        isRunningScheduler = false,
                        snackbarMessage = "✅ Scheduler ejecutado: ${checked} fuentes, ${newEvents} eventos nuevos (IA: $aiCount)"
                    )
                }
                loadPendingEvents()
            } catch (e: Exception) {
                Timber.e(e, "runScheduler error")
                _state.update { it.copy(isRunningScheduler = false, snackbarMessage = "❌ Error al ejecutar scheduler: ${e.message}") }
            }
        }
    }

    fun loadPendingEvents() {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingPending = true) }
            try {
                val result = functions.getHttpsCallable("listPendingExternalEvents").call().await()
                @Suppress("UNCHECKED_CAST")
                val data = result.data as? Map<String, Any> ?: emptyMap()
                val success = data["success"] as? Boolean ?: false
                @Suppress("UNCHECKED_CAST")
                val events = data["events"] as? List<Map<String, Any>> ?: emptyList()
                if (success) {
                    _state.update { it.copy(pendingExternalEvents = events, isLoadingPending = false) }
                } else {
                    val errorMsg = data["error"] as? String ?: "Error desconocido"
                    _state.update { it.copy(isLoadingPending = false, snackbarMessage = "❌ Error: $errorMsg") }
                }
            } catch (e: Exception) {
                Timber.e(e, "loadPendingEvents error")
                _state.update { it.copy(isLoadingPending = false, snackbarMessage = "❌ Error al cargar eventos pendientes: ${e.message}") }
            }
        }
    }

    fun approveEvent(pendingId: String, overrides: Map<String, Any>) {
        viewModelScope.launch {
            try {
                functions.getHttpsCallable("approveExternalEvent")
                    .call(mapOf("pendingId" to pendingId, "overrides" to overrides))
                    .await()
                _state.update { it.copy(snackbarMessage = "✅ Evento aprobado y publicado") }
                loadPendingEvents()
            } catch (e: Exception) {
                Timber.e(e, "approveEvent error")
                _state.update { it.copy(snackbarMessage = "❌ Error al aprobar evento: ${e.message}") }
            }
        }
    }

    fun rejectEvent(pendingId: String, reason: String?) {
        viewModelScope.launch {
            try {
                functions.getHttpsCallable("rejectExternalEvent")
                    .call(mapOf("pendingId" to pendingId, "reason" to (reason ?: "")))
                    .await()
                _state.update { it.copy(snackbarMessage = "✅ Evento rechazado") }
                loadPendingEvents()
            } catch (e: Exception) {
                Timber.e(e, "rejectEvent error")
                _state.update { it.copy(snackbarMessage = "❌ Error al rechazar evento: ${e.message}") }
            }
        }
    }

    fun onMapResult(location: ExactLocation) {
        _state.update { it.copy(
            mapResult = location,
            approvalRegion = location.region,
            approvalCommune = location.commune,
            approvalLat = location.latitude.toString(),
            approvalLng = location.longitude.toString(),
            approvalAddress = location.address
        ) }
    }

    fun clearMapResult() {
        _state.update { it.copy(mapResult = null) }
    }

    fun showApprovalDialog(event: Map<String, Any>) {
        val currentState = _state.value
        val eventId = event["id"] as? String ?: ""
        val isReopening = eventId == currentState.approvalPendingId && currentState.showApprovalDialog
        val category = if (isReopening && currentState.approvalCategory != "OTRO") {
            currentState.approvalCategory
        } else {
            event["category"] as? String ?: "OTRO"
        }
        _state.update { it.copy(
            showApprovalDialog = true,
            approvalPendingId = eventId,
            approvalCategory = category,
            approvalTitle = event["title"] as? String ?: "",
            approvalEventUrl = event["eventUrl"] as? String ?: "",
            approvalDescription = event["description"] as? String ?: "",
            approvalScrapedLocation = event["location"] as? String ?: "",
            approvalStartAt = when (val raw = event["startAt"]) {
                is Long -> raw.toString()
                is Double -> raw.toLong().toString()
                is Int -> raw.toLong().toString()
                else -> ""
            },
            approvalEndAt = when (val raw = event["endAt"]) {
                is Long -> raw.toString()
                is Double -> raw.toLong().toString()
                is Int -> raw.toLong().toString()
                else -> ""
            },
            approvalExpiresAt = when (val raw = event["expiresAt"]) {
                is Long -> raw.toString()
                is Double -> raw.toLong().toString()
                is Int -> raw.toLong().toString()
                else -> ""
            },
            approvalRegion = "",
            approvalCommune = "",
            approvalLat = "",
            approvalLng = "",
            approvalAddress = "",
        ) }
    }

    fun updateApprovalCategory(cat: String) { _state.update { it.copy(approvalCategory = cat) } }
    fun updateApprovalTitle(t: String) { _state.update { it.copy(approvalTitle = t) } }
    fun updateApprovalDescription(d: String) { _state.update { it.copy(approvalDescription = d) } }
    fun updateApprovalStartAt(s: String) { _state.update { it.copy(approvalStartAt = s) } }
    fun updateApprovalEndAt(s: String) { _state.update { it.copy(approvalEndAt = s) } }
    fun updateApprovalExpiresAt(s: String) { _state.update { it.copy(approvalExpiresAt = s) } }
    fun updateApprovalIsAdultContent(v: Boolean) { _state.update { it.copy(approvalIsAdultContent = v) } }
    fun updateApprovalRegion(r: String) { _state.update { it.copy(approvalRegion = r) } }
    fun updateApprovalCommune(c: String) { _state.update { it.copy(approvalCommune = c) } }
    fun updateApprovalLat(l: String) { _state.update { it.copy(approvalLat = l) } }
    fun updateApprovalLng(l: String) { _state.update { it.copy(approvalLng = l) } }
    fun updateApprovalAddress(a: String) { _state.update { it.copy(approvalAddress = a) } }

    fun dismissApprovalDialog() {
        _state.update { it.copy(showApprovalDialog = false) }
        clearMapResult()
    }

    fun selectModel(model: String) {
        _state.update { it.copy(selectedModel = model) }
    }

    fun updateInstagramUrl(url: String) {
        _state.update { it.copy(instagramUrl = url) }
    }

    fun submitInstagramUrl(url: String, model: String) {
        viewModelScope.launch {
            _state.update { it.copy(isProcessingInstagram = true) }
            try {
                val result = functions.getHttpsCallable("processInstagramUrl")
                    .call(mapOf("url" to url, "model" to model))
                    .await()
                @Suppress("UNCHECKED_CAST")
                val data = result.data as? Map<String, Any> ?: emptyMap()
                val message = data["message"] as? String ?: "Post procesado."
                val isEvent = data["isEvent"] as? Boolean ?: true
                _state.update {
                    it.copy(
                        isProcessingInstagram = false,
                        instagramUrl = "",
                        snackbarMessage = if (isEvent) "✅ $message" else "ℹ️ $message"
                    )
                }
                if (isEvent) loadPendingEvents()
            } catch (e: Exception) {
                Timber.e(e, "submitInstagramUrl error")
                _state.update { it.copy(isProcessingInstagram = false, snackbarMessage = "❌ Error: ${e.message}") }
            }
        }
    }

    fun updateNewSourceUrl(url: String) {
        _state.update { it.copy(newSourceUrl = url) }
    }

    fun updateNewSourceName(name: String) {
        _state.update { it.copy(newSourceName = name) }
    }

    fun publishEventManually(
        title: String,
        region: String,
        commune: String,
        address: String,
        latitude: Double,
        longitude: Double,
        startAt: Long,
        endAt: Long? = null,
        expiresAt: Long? = null,
        isAdultContent: Boolean = false
    ) {
        viewModelScope.launch {
            _state.update { it.copy(isPublishing = true) }
            try {
                val params = mutableMapOf<String, Any>(
                    "title" to title,
                    "region" to region,
                    "commune" to commune,
                    "address" to address,
                    "latitude" to latitude,
                    "longitude" to longitude,
                    "startAt" to startAt
                )
                endAt?.let { params["endAt"] = it }
                expiresAt?.let { params["expiresAt"] = it }
                params["isAdultContent"] = isAdultContent

                functions.getHttpsCallable("publishExternalEvent").call(params).await()
                _state.update { it.copy(isPublishing = false, snackbarMessage = "✅ Evento publicado correctamente") }
            } catch (e: Exception) {
                Timber.e(e, "publishEventManually error")
                _state.update { it.copy(isPublishing = false, snackbarMessage = "❌ Error al publicar: ${e.message}") }
            }
        }
    }

    // --- Nuevas funciones para Created Events ---

    fun loadCreatedExternalEvents() {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingCreated = true) }
            try {
                val result = functions.getHttpsCallable("listCreatedExternalEvents").call().await()
                @Suppress("UNCHECKED_CAST")
                val data = result.data as? Map<String, Any> ?: emptyMap()
                val success = data["success"] as? Boolean ?: false
                @Suppress("UNCHECKED_CAST")
                val events = data["events"] as? List<Map<String, Any>> ?: emptyList()
                if (success) {
                    _state.update { it.copy(createdExternalEvents = events, isLoadingCreated = false) }
                } else {
                    val errorMsg = data["error"] as? String ?: "Error desconocido"
                    _state.update { it.copy(isLoadingCreated = false, snackbarMessage = "❌ Error al cargar eventos: $errorMsg") }
                }
            } catch (e: Exception) {
                Timber.e(e, "loadCreatedExternalEvents error")
                _state.update { it.copy(isLoadingCreated = false, snackbarMessage = "❌ Error al cargar eventos: ${e.message}") }
            }
        }
    }

    fun showDeleteConfirm(eventId: String, title: String) {
        _state.update { it.copy(showDeleteConfirmDialog = true, eventToDeleteId = eventId, eventToDeleteTitle = title) }
    }

    fun dismissDeleteConfirm() {
        _state.update { it.copy(showDeleteConfirmDialog = false, eventToDeleteId = null, eventToDeleteTitle = null) }
    }

    fun deleteEvent(eventId: String, hard: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(isDeletingEvent = true) }
            try {
                functions.getHttpsCallable("deleteExternalEvent")
                    .call(mapOf("eventId" to eventId, "hard" to hard))
                    .await()
                val msg = if (hard) "Eliminado permanentemente" else "Evento cancelado"
                _state.update { it.copy(isDeletingEvent = false, snackbarMessage = "✅ $msg") }
                dismissDeleteConfirm()
                loadCreatedExternalEvents()
            } catch (e: Exception) {
                Timber.e(e, "deleteEvent error")
                _state.update { it.copy(isDeletingEvent = false, snackbarMessage = "❌ Error: ${e.message}") }
            }
        }
    }

    fun dismissSnackbar() {
        _state.update { it.copy(snackbarMessage = null) }
    }
}
