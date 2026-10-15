package com.united.digitaldispatch.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Same idea as the NetworkMonitor of the purchase app:
 *   isConnected   -> some network exists
 *   isHighQuality -> the network is strong AND the server can really be reached
 *
 * Change: the real-internet test connects to OUR server (probeHost:probePort)
 * instead of 8.8.8.8:53, so networks that block Google DNS don't look "offline".
 *
 * Needs the permissions ACCESS_NETWORK_STATE and ACCESS_WIFI_STATE.
 */
object NetworkMonitor {

    private val _isConnected = MutableLiveData(false)
    val isConnected: LiveData<Boolean> get() = _isConnected

    private val _isHighQuality = MutableLiveData(false)
    val isHighQuality: LiveData<Boolean> get() = _isHighQuality

    /** server used for the "can we really reach it" test */
    var probeHost: String = "103.165.4.170"
    var probePort: Int = 81

    private var started = false

    @Volatile
    private var checking = false

    fun start(context: Context) {

        if (started) return
        started = true

        val appContext = context.applicationContext

        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {

            override fun onAvailable(network: Network) {
                _isConnected.postValue(true)
                checkQuality(appContext)
            }

            override fun onLost(network: Network) {
                _isConnected.postValue(false)
                _isHighQuality.postValue(false)
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                checkQuality(appContext)
            }
        })
    }

    private fun checkQuality(context: Context) {

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val network = cm.activeNetwork ?: return _isHighQuality.postValue(false)
        val caps = cm.getNetworkCapabilities(network) ?: return _isHighQuality.postValue(false)

        var strong = false

        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {

            // WIFI: good signal OR enough bandwidth
            val rssi = try {
                @Suppress("DEPRECATION")
                (context.getSystemService(Context.WIFI_SERVICE) as WifiManager).connectionInfo.rssi
            } catch (e: Exception) {
                -100
            }

            val downKbps = caps.linkDownstreamBandwidthKbps

            strong = rssi > -70 || downKbps >= 5000

        } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {

            // MOBILE: bandwidth only
            val downKbps = caps.linkDownstreamBandwidthKbps
            val upKbps = caps.linkUpstreamBandwidthKbps

            strong = downKbps >= 3000 && upKbps >= 1000
        }

        if (!strong) {
            _isHighQuality.postValue(false)
            return
        }

        if (checking) return
        checking = true

        // real test: can we reach the server?
        Thread {
            val reachable = canReachServer()
            _isHighQuality.postValue(reachable)
            checking = false
        }.start()
    }

    private fun canReachServer(): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(probeHost, probePort), 2000)
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}