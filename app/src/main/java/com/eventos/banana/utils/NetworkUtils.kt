package com.eventos.banana.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

object NetworkUtils {
    // Check if network hardware is connected
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

    // Check if we can actually reach the internet (DNS + Ping)
    fun checkRealConnectivity(context: Context, onResult: (Boolean) -> Unit) {
        if (!isNetworkAvailable(context)) {
            onResult(false)
            return
        }

        GlobalScope.launch(Dispatchers.IO) {
            try {
                val timeoutMs = 1500
                val socket = Socket()
                val socketAddress = InetSocketAddress("8.8.8.8", 53) // Google DNS
                socket.connect(socketAddress, timeoutMs)
                socket.close()

                // 🔍 Check DNS Resolution explicitly
                // Try to resolve google.com to ensure DNS is working
                val dnsCheck = java.net.InetAddress.getByName("google.com")
                if (dnsCheck.address.isNotEmpty()) {
                    Log.d("NetworkUtils", "Real connectivity confirmed (DNS + Ping success)")
                    onResult(true)
                } else {
                    Log.e("NetworkUtils", "DNS resolution returned empty")
                    onResult(false)
                }
            } catch (e: IOException) {
                Log.e("NetworkUtils", "Ping/DNS failed", e)
                onResult(false)
            }
        }
    }
}
