package com.eventos.banana.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.eventos.banana.R
import com.eventos.banana.domain.model.AppNotification
import com.eventos.banana.domain.model.DateFilter
import com.eventos.banana.domain.model.EventType
import com.eventos.banana.domain.model.NotificationType
import com.eventos.banana.domain.model.UserProfile

// ─────────────────────────────────────────────
// EventType  →  Localized display name
// ─────────────────────────────────────────────
@Composable
fun EventType.localizedName(): String = when (this) {
    EventType.DEPORTES     -> stringResource(R.string.event_type_deportes)
    EventType.SOCIAL       -> stringResource(R.string.event_type_social)
    EventType.CULTURAL     -> stringResource(R.string.event_type_cultural)
    EventType.EDUCATIVO    -> stringResource(R.string.event_type_educativo)
    EventType.JUEGOS       -> stringResource(R.string.event_type_juegos)
    EventType.GASTRONOMIA  -> stringResource(R.string.event_type_gastronomia)
    EventType.AIRE_LIBRE   -> stringResource(R.string.event_type_aire_libre)
    EventType.OTRO         -> stringResource(R.string.event_type_otro)
}

// ─────────────────────────────────────────────
// EventType  →  Localized subcategories
// ─────────────────────────────────────────────
@Composable
fun EventType.localizedSubcategories(): List<String> = when (this) {
    EventType.DEPORTES -> listOf(
        stringResource(R.string.sub_futbol), stringResource(R.string.sub_calistenia),
        stringResource(R.string.sub_gym), stringResource(R.string.sub_tenis),
        stringResource(R.string.sub_padel), stringResource(R.string.sub_running),
        stringResource(R.string.sub_ciclismo), stringResource(R.string.sub_trekking),
        stringResource(R.string.sub_basket), stringResource(R.string.sub_voleibol),
        stringResource(R.string.sub_yoga), stringResource(R.string.sub_crossfit)
    ).also { check(it.size == EventType.DEPORTES.subcategories.size) { "Subcategory count mismatch for DEPORTES" } }
    EventType.SOCIAL -> listOf(
        stringResource(R.string.sub_fiesta), stringResource(R.string.sub_previa),
        stringResource(R.string.sub_bar), stringResource(R.string.sub_boliche),
        stringResource(R.string.sub_cita), stringResource(R.string.sub_reunion),
        stringResource(R.string.sub_cumpleanos), stringResource(R.string.sub_networking)
    ).also { check(it.size == EventType.SOCIAL.subcategories.size) { "Subcategory count mismatch for SOCIAL" } }
    EventType.CULTURAL -> listOf(
        stringResource(R.string.sub_cine), stringResource(R.string.sub_teatro),
        stringResource(R.string.sub_museo), stringResource(R.string.sub_concierto),
        stringResource(R.string.sub_arte), stringResource(R.string.sub_standup),
        stringResource(R.string.sub_lectura), stringResource(R.string.sub_musica_vivo)
    ).also { check(it.size == EventType.CULTURAL.subcategories.size) { "Subcategory count mismatch for CULTURAL" } }
    EventType.EDUCATIVO -> listOf(
        stringResource(R.string.sub_taller), stringResource(R.string.sub_curso),
        stringResource(R.string.sub_idiomas), stringResource(R.string.sub_estudio),
        stringResource(R.string.sub_hackathon), stringResource(R.string.sub_charla),
        stringResource(R.string.sub_tutoria)
    ).also { check(it.size == EventType.EDUCATIVO.subcategories.size) { "Subcategory count mismatch for EDUCATIVO" } }
    EventType.JUEGOS -> listOf(
        stringResource(R.string.sub_videojuegos), stringResource(R.string.sub_juegos_mesa),
        stringResource(R.string.sub_rol), stringResource(R.string.sub_cartas),
        stringResource(R.string.sub_escape_room), stringResource(R.string.sub_trivia),
        stringResource(R.string.sub_casino)
    ).also { check(it.size == EventType.JUEGOS.subcategories.size) { "Subcategory count mismatch for JUEGOS" } }
    EventType.GASTRONOMIA -> listOf(
        stringResource(R.string.sub_cena), stringResource(R.string.sub_almuerzo),
        stringResource(R.string.sub_cafe), stringResource(R.string.sub_asado),
        stringResource(R.string.sub_foodtrucks), stringResource(R.string.sub_cata),
        stringResource(R.string.sub_cocina)
    ).also { check(it.size == EventType.GASTRONOMIA.subcategories.size) { "Subcategory count mismatch for GASTRONOMIA" } }
    EventType.AIRE_LIBRE -> listOf(
        stringResource(R.string.sub_parque), stringResource(R.string.sub_playa),
        stringResource(R.string.sub_camping), stringResource(R.string.sub_pesca),
        stringResource(R.string.sub_picnic), stringResource(R.string.sub_caminata),
        stringResource(R.string.sub_escalada)
    ).also { check(it.size == EventType.AIRE_LIBRE.subcategories.size) { "Subcategory count mismatch for AIRE_LIBRE" } }
    EventType.OTRO -> emptyList()
}

// ─────────────────────────────────────────────
// DateFilter  →  Localized display name
// ─────────────────────────────────────────────
@Composable
fun DateFilter.localizedName(): String = when (this) {
    DateFilter.ALL      -> stringResource(R.string.date_filter_all)
    DateFilter.TODAY     -> stringResource(R.string.date_filter_today)
    DateFilter.TOMORROW  -> stringResource(R.string.date_filter_tomorrow)
    DateFilter.WEEKEND   -> stringResource(R.string.date_filter_weekend)
}

// ─────────────────────────────────────────────
// UserProfile  →  Localized badge text
// ─────────────────────────────────────────────
@Composable
fun UserProfile.localizedBadgeText(): String = when {
    isPerfectAttendee()    -> stringResource(R.string.badge_perfect_attendee)
    ratingCount == 0       -> stringResource(R.string.badge_new_user)
    averageRating >= 4.5   -> stringResource(R.string.badge_top_banana)
    averageRating >= 4.0   -> stringResource(R.string.badge_reliable)
    averageRating >= 3.0   -> stringResource(R.string.badge_good)
    else                   -> stringResource(R.string.badge_developing)
}

// ─────────────────────────────────────────────
// AppNotification  →  Localized title & message
// Resolves stored Firestore text to the user's locale
// ─────────────────────────────────────────────
@Composable
fun AppNotification.localizedTitle(): String = when (type) {
    NotificationType.EVENT_CREATED      -> stringResource(R.string.notif_new_event_title, extractParam())
    NotificationType.JOIN_REQUEST_SENT  -> stringResource(R.string.notif_join_request_title)
    NotificationType.JOIN_APPROVED      -> stringResource(R.string.notif_join_approved_title)
    NotificationType.JOIN_REJECTED      -> stringResource(R.string.notif_join_rejected_title)
    NotificationType.FRIEND_REQUEST     -> stringResource(R.string.notif_friend_request_title)
    NotificationType.FRIEND_ACCEPTED    -> stringResource(R.string.notif_friend_accepted_title)
    NotificationType.NEW_MESSAGE        -> stringResource(R.string.notif_new_message_title, extractParam())
    NotificationType.PROFILE_VIEW       -> stringResource(R.string.notif_profile_view_title)
    NotificationType.RATING             -> title.ifBlank { "¡Nueva calificación! ⭐" }
    NotificationType.RATING_REMINDER    -> title.ifBlank { "¡Evento finalizado! ⭐" }
    NotificationType.EVENT_CANCELLED,
    NotificationType.EVENT_CLOSED,
    NotificationType.EVENT_UPDATE,
    NotificationType.EVENT_WALL_POST,
    NotificationType.REMOVED_FROM_EVENT -> title.ifBlank { stringResource(R.string.notif_generic_title) }
    NotificationType.GENERIC            -> title.ifBlank { stringResource(R.string.notif_generic_title) }
}

@Composable
fun AppNotification.localizedMessage(): String = when (type) {
    NotificationType.JOIN_REQUEST_SENT  -> stringResource(R.string.notif_join_request_msg, extractParam())
    NotificationType.JOIN_APPROVED      -> stringResource(R.string.notif_join_approved_msg, extractParam())
    NotificationType.JOIN_REJECTED      -> stringResource(R.string.notif_join_rejected_msg, extractParam())
    NotificationType.FRIEND_REQUEST     -> stringResource(R.string.notif_friend_request_msg, extractParam())
    NotificationType.FRIEND_ACCEPTED    -> stringResource(R.string.notif_friend_accepted_msg, extractParam())
    NotificationType.PROFILE_VIEW       -> stringResource(R.string.notif_profile_view_msg)
    else                                -> message // fallback to stored text
}

/**
 * Extracts a dynamic parameter from the stored message.
 * For notifications like "Nuevo evento en Santiago" → extracts "Santiago"
 * For "username quiere ser tu amigo" → extracts "username"
 * Falls back to the raw message if no pattern matches.
 */
private fun AppNotification.extractParam(): String {
    // Try to extract meaningful param from the stored message
    return when (type) {
        NotificationType.EVENT_CREATED -> {
            // "Nuevo evento en <commune>" → extract commune from title
            title.substringAfterLast(" en ", title)
                .substringAfterLast(" in ", title)
        }
        NotificationType.JOIN_REQUEST_SENT,
        NotificationType.JOIN_APPROVED,
        NotificationType.JOIN_REJECTED -> {
            // Extract event name between quotes
            val quoted = message.substringAfter("\"", "").substringBefore("\"", "")
            quoted.ifBlank { message }
        }
        NotificationType.FRIEND_REQUEST -> {
            // "Username quiere ser tu amigo" → extract username
            message.substringBefore(" quiere", "")
                .substringBefore(" wants", "")
                .ifBlank { message }
        }
        NotificationType.FRIEND_ACCEPTED -> {
            // "Username ahora es tu amigo"
            message.substringBefore(" ahora", "")
                .substringBefore(" is now", "")
                .ifBlank { message }
        }
        NotificationType.NEW_MESSAGE -> {
            // title: "Nuevo mensaje de Username"
            title.substringAfterLast(" de ", "")
                .substringAfterLast(" from ", "")
                .ifBlank { title }
        }
        else -> message
    }
}
