package ru.vsm.mobile.data.local

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Наблюдатель за наличием интернета (не просто Wi-Fi/сотовой сети — именно валидированного
 * доступа в интернет) поверх [ConnectivityManager]. Нужен, чтобы [ru.vsm.mobile.data.sync.OfflineQueueSyncer]
 * пробовал отправить накопленную офлайн-очередь сразу при восстановлении связи в поезде.
 */
class NetworkMonitor(context: Context) {

    private val connectivityManager =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)

    val isOnline: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(hasValidatedInternet())
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(capabilities.hasInternet())
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        if (connectivityManager != null) {
            connectivityManager.registerNetworkCallback(request, callback)
            trySend(hasValidatedInternet())
        } else {
            // Сервис недоступен (не должно случаться на реальном устройстве/эмуляторе) — считаем
            // сеть доступной, чтобы не блокировать отправку очереди навсегда.
            trySend(true)
        }

        awaitClose {
            connectivityManager?.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()

    private fun hasValidatedInternet(): Boolean {
        val active = connectivityManager?.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(active) ?: return false
        return capabilities.hasInternet()
    }

    private fun NetworkCapabilities.hasInternet(): Boolean =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
