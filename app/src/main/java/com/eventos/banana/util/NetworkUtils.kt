package com.eventos.banana.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

object NetworkUtils {

    /** Verifica si el hardware de red está conectado */
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }
    }

    /**
     * Verifica conectividad real (DNS + Ping).
     * Refactorizado: ya NO usa GlobalScope (memory leak).
     * Ahora es una suspend function que se ejecuta en IO dispatcher.
     */
    suspend fun checkRealConnectivity(context: Context): Boolean {
        if (!isNetworkAvailable(context)) return false

        return withContext(Dispatchers.IO) {
            try {
                val timeoutMs = 1500
                val socket = Socket()
                val socketAddress = InetSocketAddress("8.8.8.8", 53)
                socket.connect(socketAddress, timeoutMs)
                socket.close()

                val dnsCheck = java.net.InetAddress.getByName("google.com")
                if (dnsCheck.address.isNotEmpty()) {
                    Log.d("NetworkUtils", "Conectividad real confirmada")
                    true
                } else {
                    Log.e("NetworkUtils", "DNS vacío")
                    false
                }
            } catch (e: IOException) {
                Log.e("NetworkUtils", "Ping/DNS falló", e)
                false
            }
        }
    }
}
