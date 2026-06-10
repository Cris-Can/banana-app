package com.eventos.banana.util

/**
 * 🍌 Banana App - Constantes de la Aplicación
 * 
 * Centraliza todas las constantes utilizadas en la aplicación
 * para facilitar el mantenimiento y la consistencia.
 * 
 * Convención de lenguaje: INGLÉS (para consistencia con el código base)
 */
object AppConstants {

    // ============================================
    // 📺 ADVERTISING CONFIGURATION
    // ============================================
    
    /** Número máximo de anuncios que se pueden desbloquear por evento */
    const val ADS_MAX_UNLOCKS_PER_EVENT = 3
    
    /** Duración del ciclo de anuncios (24 horas en milisegundos) */
    const val ADS_CYCLE_DURATION_MS = 24 * 60 * 60 * 1000L
    
    /** Número máximo de eventos que se pueden desbloquear con anuncios por ciclo */
    const val ADS_MAX_EVENTS_PER_CYCLE = 2
    
    /** ID de anuncios de prueba (para desarrollo) */
    const val AD_UNIT_ID_INTERSTITIAL_TEST = "ca-app-pub-3940256099942544/1033173712"
    
    /** ID de anuncios de prueba - Rewarded */
    const val AD_UNIT_ID_REWARDED_TEST = "ca-app-pub-3940256099942544/5224354917"

    // ============================================
    // ⏱️ TIMEOUTS (Tiempos de espera)
    // ============================================
    
    /** Timeout para operaciones de red (30 segundos) */
    const val NETWORK_TIMEOUT_MS = 30_000L
    
    /** Timeout para operaciones de Firestore (15 segundos) */
    const val FIRESTORE_TIMEOUT_MS = 15_000L
    
    /** Timeout para operaciones de autenticación (20 segundos) */
    const val AUTH_TIMEOUT_MS = 20_000L
    
    /** Timeout para operaciones de almacenamiento (60 segundos) */
    const val STORAGE_TIMEOUT_MS = 60_000L
    
    /** Timeout para llamadas a Cloud Functions (30 segundos) */
    const val CLOUD_FUNCTION_TIMEOUT_MS = 30_000L

    // ============================================
    // 🕐 TIME WINDOWS (Ventanas de tiempo)
    // ============================================
    
    /** Ventana de tiempo para rate limiting de login (15 minutos) */
    const val RATE_LIMIT_WINDOW_LOGIN_MS = 15 * 60 * 1000L
    
    /** Ventana de tiempo para rate limiting de registro (60 minutos) */
    const val RATE_LIMIT_WINDOW_REGISTER_MS = 60 * 60 * 1000L
    
    /** Ventana de tiempo para rate limiting de vistas de perfil (60 minutos) */
    const val RATE_LIMIT_WINDOW_PROFILE_VIEW_MS = 60 * 60 * 1000L
    
    /** Ventana de tiempo para rate limiting de mensajes (60 minutos) */
    const val RATE_LIMIT_WINDOW_MESSAGE_MS = 60 * 60 * 1000L
    
    /** Ventana de tiempo para rate limiting de creación de eventos (60 minutos) */
    const val RATE_LIMIT_WINDOW_EVENT_CREATE_MS = 60 * 60 * 1000L

    /** Ventana de tiempo para rate limiting de calificaciones (5 minutos) */
    const val RATE_LIMIT_WINDOW_RATING_MS = 5 * 60 * 1000L
    
    /** Ciclo de suscripción (30 días) */
    const val SUBSCRIPTION_CYCLE_DAYS = 30
    
    /** Duración del boost de evento (24 horas) */
    const val EVENT_BOOST_DURATION_MS = 24 * 60 * 60 * 1000L
    
    /** Deadline para calificar evento después de finalizado (5 días) */
    const val RATING_DEADLINE_MS = 5 * 24 * 60 * 60 * 1000L

    /** Ventana de tiempo para el filtro de eventos de fin de semana (5 días) */
    const val WEEKEND_FILTER_WINDOW_MS = 5 * 24 * 60 * 60 * 1000L

    // ============================================
    // 📊 LIMITS (Límites)
    // ============================================
    
    /** Límite de eventos que puede crear un usuario FREE por ciclo */
    const val FREE_USER_EVENT_LIMIT = 1
    
    /** Límite de solicitudes de unión por ciclo para usuarios FREE */
    const val FREE_USER_JOIN_REQUEST_LIMIT = 5
    
    /** Máximo de participantes por evento */
    const val MAX_PARTICIPANTS_PER_EVENT = 50
    
    /** Máximo de caracteres para el contenido de un mensaje */
    const val MAX_MESSAGE_LENGTH = 1000
    
    /** Máximo de caracteres para la biografía del usuario */
    const val MAX_ABOUT_ME_LENGTH = 500
    
    /** Máximo de intereses que puede tener un usuario */
    const val MAX_INTERESTS = 20
    
    /** Tamaño máximo de archivo para subir (10 MB) */
    const val MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024L
    
    /** Límite de paginación para consultas de Firestore */
    const val FIRESTORE_PAGE_SIZE = 20
    
    /** Límite de paginación para mensajes */
    const val MESSAGES_PAGE_SIZE = 50
    
    /** Límite de batch de Firestore (máximo seguro) */
    const val FIRESTORE_BATCH_LIMIT = 450

    // ============================================
    // 🌍 LOCATION & GEOHASH
    // ============================================
    
    /** Radio de búsqueda predeterminado (km) */
    const val DEFAULT_SEARCH_RADIUS_KM = 20
    
    /** Radio de notificación por defecto */
    const val DEFAULT_NOTIFICATION_RANGE = "COMMUNE"

    // ============================================
    // 🔔 NOTIFICATIONS
    // ============================================
    
    /** Canal de notificación para eventos generales */
    const val NOTIFICATION_CHANNEL_GENERAL = "banana_channel_01"
    
    /** Canal de notificación para mensajes */
    const val NOTIFICATION_CHANNEL_MESSAGES = "banana_messages"
    
    /** Canal de notificación para recordatorios */
    const val NOTIFICATION_CHANNEL_REMINDERS = "banana_reminders"

    // ============================================
    // 🎨 UI CONSTANTS
    // ============================================
    
    /** Duración de animaciones de transición (ms) */
    const val ANIMATION_DURATION_MS = 300L
    
    /** Duración del debounce para búsquedas (ms) */
    const val SEARCH_DEBOUNCE_MS = 300L
    
    /** Duración del delay para typing indicator (ms) */
    const val TYPING_INDICATOR_DELAY_MS = 2000L
    
    /** Tiempo mínimo para considerar "en línea" (ms) */
    const val ONLINE_THRESHOLD_MS = 30_000L

    // ============================================
    // 📱 AUDIO RECORDING
    // ============================================
    
    /** Duración máxima de audio (60 segundos) */
    const val MAX_AUDIO_DURATION_MS = 60_000L
    
    /** Duración mínima de audio (100 ms) */
    const val MIN_AUDIO_DURATION_MS = 100L
    
    /** Formato de audio para grabación */
    const val AUDIO_OUTPUT_FORMAT = "m4a"
    
    /** Tasa de muestreo de audio */
    const val AUDIO_SAMPLE_RATE = 44100

    // ============================================
    // 🔐 SECURITY
    // ============================================
    
    /** Longitud mínima de contraseña */
    const val PASSWORD_MIN_LENGTH = 8
    
    /** Longitud máxima de contraseña */
    const val PASSWORD_MAX_LENGTH = 128
    
    /** Máximo de intentos de login antes del bloqueo */
    const val MAX_LOGIN_ATTEMPTS = 5
    
    /** Duración del bloqueo por muchos intentos (15 minutos) */
    const val LOGIN_LOCKOUT_DURATION_MS = 15 * 60 * 1000L

    // ============================================
    // 📦 CACHE
    // ============================================
    
    /** Duración de la caché para rate limiting (5 minutos) */
    const val RATE_LIMIT_CACHE_DURATION_MS = 5 * 60 * 1000L
    
    /** Duración de la caché para perfiles de usuario (10 minutos) */
    const val USER_PROFILE_CACHE_DURATION_MS = 10 * 60 * 1000L
    
    /** Duración de la caché para eventos (5 minutos) */
    const val EVENTS_CACHE_DURATION_MS = 5 * 60 * 1000L
    
    /** Tamaño máximo de la caché (MB) */
    const val MAX_CACHE_SIZE_MB = 50

    // ============================================
    // 🔄 SYNC
    // ============================================
    
    /** Intervalo de sincronización de eventos pasados (1 hora) */
    const val SYNC_PAST_EVENTS_INTERVAL_MS = 60 * 60 * 1000L
    
    /** Intervalo de verificación de suscripciones (1 hora) */
    const val SUBSCRIPTION_CHECK_INTERVAL_MS = 60 * 60 * 1000L
    
    /** Intervalo de limpieza de tokens FCM (24 horas) */
    const val FCM_TOKEN_CLEANUP_INTERVAL_MS = 24 * 60 * 60 * 1000L
}