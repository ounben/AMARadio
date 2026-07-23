package com.ounben.amaradio.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.ounben.amaradio.Utils

class ConnectivityChecker {
    enum class ConnectionType {
        NOT_METERED,
        METERED
    }

    fun interface ConnectivityCallback {
        fun onConnectivityChanged(connected: Boolean, connectionType: ConnectionType)
    }

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var connectivityCallback: ConnectivityCallback? = null
    private var lastConnectionType: ConnectionType? = null

    fun startListening(context: Context, connectivityCallback: ConnectivityCallback) {
        this.connectivityCallback = connectivityCallback
        if (networkCallback != null) {
            return
        }
        lastConnectionType = getCurrentConnectionType(context)
        val attributedContext = Utils.getAttributedContext(context)
        connectivityManager = attributedContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (connectivityManager != null) {
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    val connected = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    val metered = !networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                    onConnectivityChanged(connected, if (metered) ConnectionType.METERED else ConnectionType.NOT_METERED)
                }
            }
            connectivityManager!!.registerNetworkCallback(NetworkRequest.Builder().build(), networkCallback!!)
        }
    }

    fun stopListening(context: Context) {
        connectivityCallback = null
        if (networkCallback != null) {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            connectivityManager.unregisterNetworkCallback(networkCallback!!)
            networkCallback = null
        }
    }

    private fun onConnectivityChanged(connected: Boolean, connectionType: ConnectionType) {
        if (lastConnectionType == connectionType) {
            return
        }
        lastConnectionType = connectionType
        connectivityCallback?.onConnectivityChanged(connected, connectionType)
    }

    companion object {
        @JvmStatic
        fun getCurrentConnectionType(context: Context): ConnectionType {
            val attributedContext = Utils.getAttributedContext(context)
            val connectivityManager = attributedContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            return if (connectivityManager.isActiveNetworkMetered) ConnectionType.METERED else ConnectionType.NOT_METERED
        }
    }
}
