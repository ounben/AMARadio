package com.ounben.amaradio.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.core.net.ConnectivityManagerCompat

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
    private var networkBroadcastReceiver: BroadcastReceiver? = null
    private var connectivityCallback: ConnectivityCallback? = null
    private var lastConnectionType: ConnectionType? = null

    fun startListening(context: Context, connectivityCallback: ConnectivityCallback) {
        this.connectivityCallback = connectivityCallback
        if (networkCallback != null || networkBroadcastReceiver != null) {
            return
        }
        lastConnectionType = getCurrentConnectionType(context)
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && connectivityManager != null) {
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    val connected = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    val metered = !networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                    onConnectivityChanged(connected, if (metered) ConnectionType.METERED else ConnectionType.NOT_METERED)
                }
            }
            connectivityManager!!.registerNetworkCallback(NetworkRequest.Builder().build(), networkCallback!!)
        } else {
            networkBroadcastReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val connected = !intent.hasExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY)
                    onConnectivityChanged(connected, if (ConnectivityManagerCompat.isActiveNetworkMetered(connectivityManager!!)) ConnectionType.METERED else ConnectionType.NOT_METERED)
                }
            }
            @Suppress("DEPRECATION")
            context.registerReceiver(networkBroadcastReceiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))
        }
    }

    fun stopListening(context: Context) {
        connectivityCallback = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && networkCallback != null) {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            connectivityManager.unregisterNetworkCallback(networkCallback!!)
            networkCallback = null
        } else if (networkBroadcastReceiver != null) {
            context.unregisterReceiver(networkBroadcastReceiver)
            networkBroadcastReceiver = null
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
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            return if (ConnectivityManagerCompat.isActiveNetworkMetered(connectivityManager)) ConnectionType.METERED else ConnectionType.NOT_METERED
        }
    }
}
