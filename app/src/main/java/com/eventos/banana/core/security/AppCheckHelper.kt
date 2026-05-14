package com.eventos.banana.core.security

import android.content.Context
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import com.eventos.banana.BuildConfig

/**
 * Firebase App Check Integration Helper
 *
 * App Check protege los backends de Firebase contra accesos no autorizados,
 * atestando que las peticiones vienen únicamente de la app legítima.
 */
@Singleton
class AppCheckHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "AppCheckHelper"
    }

    /**
     * Inicializa App Check. Debe llamarse desde Application.onCreate()
     * antes de usar cualquier otro servicio de Firebase.
     *
     * @param isDebug Pasa BuildConfig.DEBUG aquí
     */
    fun initialize(isDebug: Boolean) {
        val provider = if (isDebug) {
            Timber.d("$TAG: Inicializando Debug App Check Provider")
            DebugAppCheckProviderFactory.getInstance()
        } else {
            Timber.d("$TAG: Inicializando Play Integrity App Check Provider")
            PlayIntegrityAppCheckProviderFactory.getInstance()
        }

        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(provider)
        Timber.d("$TAG: App Check inicializado correctamente")
    }

    /**
     * Obtiene el token actual de App Check.
     * Útil para adjuntarlo manualmente a llamadas a APIs externas.
     */
    suspend fun getCurrentAppCheckToken(): Result<String> {
        return try {
            val tokenResult = FirebaseAppCheck.getInstance()
                .getAppCheckToken(false) // false = no forzar actualización
                .await()
            Result.success(tokenResult.token)
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error obteniendo token de App Check")
            Result.failure(e)
        }
    }

    /**
     * Fuerza la renovación del token de App Check.
     * Úsalo cuando el servidor rechace el token actual.
     */
    suspend fun forceRefreshAppCheckToken(): Result<String> {
        return try {
            val tokenResult = FirebaseAppCheck.getInstance()
                .getAppCheckToken(true) // true = forzar actualización
                .await()
            Timber.d("$TAG: Token de App Check renovado correctamente")
            Result.success(tokenResult.token)
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error renovando token de App Check")
            Result.failure(e)
        }
    }

    /**
     * Verifica que App Check esté correctamente configurado.
     */
    fun isAppCheckReady(): Boolean {
        return try {
            FirebaseAppCheck.getInstance() != null
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Información de estado para debugging.
     */
    fun getDebugStatus(): String {
        return buildString {
            appendLine("App Check Status:")
            appendLine("  Ready: ${isAppCheckReady()}")
            appendLine("  Build Type: ${if (BuildConfig.DEBUG) "DEBUG" else "RELEASE"}")
            appendLine("  Provider: ${if (BuildConfig.DEBUG) "Debug" else "Play Integrity"}")
        }
    }
}