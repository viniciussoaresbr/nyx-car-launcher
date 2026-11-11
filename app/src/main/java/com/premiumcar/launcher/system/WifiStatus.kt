package com.premiumcar.launcher.system

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper

/**
 * Estado do Wi-Fi (conectado ou não) para o indicador da Home.
 *
 * Escuta o sistema só enquanto a tela está visível ([start] em onStart, [stop] em onStop):
 * nenhum trabalho em segundo plano. Exige apenas `ACCESS_NETWORK_STATE` (permissão normal,
 * somente leitura).
 */
class WifiStatus(context: Context, private val onChange: (connected: Boolean) -> Unit) {

    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val networks = mutableSetOf<Network>()
    private var registered = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            main.post { if (registered && networks.add(network)) notifyChange() }
        }

        override fun onLost(network: Network) {
            main.post { if (registered && networks.remove(network)) notifyChange() }
        }
    }

    fun start() {
        if (registered) return
        if (manager == null) {
            onChange(false)
            return
        }
        networks.clear()
        networks += currentWifiNetworks()
        val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        registered = runCatching { manager.registerNetworkCallback(request, callback) }.isSuccess
        notifyChange()
    }

    fun stop() {
        if (!registered) return
        registered = false
        runCatching { manager?.unregisterNetworkCallback(callback) }
        networks.clear()
    }

    private fun notifyChange() = onChange(networks.isNotEmpty())

    @Suppress("DEPRECATION") // allNetworks: única forma de ler o estado atual no Android 6–11.
    private fun currentWifiNetworks(): List<Network> = runCatching {
        manager!!.allNetworks.filter { manager.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }
    }.getOrDefault(emptyList())

    companion object {
        /** Leitura pontual, para o diagnóstico. */
        fun isConnectedNow(context: Context): Boolean {
            val status = WifiStatus(context) {}
            return status.currentWifiNetworks().isNotEmpty()
        }
    }
}
