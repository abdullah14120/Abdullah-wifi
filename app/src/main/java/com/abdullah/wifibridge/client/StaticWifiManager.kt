package com.abdullah.wifibridge.client

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.Network
import android.net.NetworkRequest
import android.net.StaticIpConfiguration
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import androidx.annotation.RequiresApi
import java.net.InetAddress

class StaticWifiManager(private val context: Context) {

    @RequiresApi(Build.VERSION_CODES.S)
    fun createStaticIpConfiguration(customIp: String, gatewayIp: String): StaticIpConfiguration {
        val ipInet = InetAddress.getByName(customIp)
        val gatewayInet = InetAddress.getByName(gatewayIp)

        val linkAddress = LinkAddress(ipInet, 24)

        // استخدام Builder لتجنب مشكلة Package-Private Constructor
        return StaticIpConfiguration.Builder()
            .setIpAddress(linkAddress)
            .setGateway(gatewayInet)
            .setDnsServers(listOf(gatewayInet, InetAddress.getByName("8.8.8.8")))
            .build()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun connectToBridgeNetwork(ssid: String, passphrase: String) {
        val specifier = WifiNetworkSpecifier.Builder()
            .setSsid(ssid)
            .setWpa2Passphrase(passphrase)
            .build()

        val request = NetworkRequest.Builder()
            .addTransportType(android.net.NetworkCapabilities.TRANSPORT_WIFI)
            .setNetworkSpecifier(specifier)
            .build()

        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        connectivityManager.requestNetwork(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                connectivityManager.bindProcessToNetwork(network)
            }
        })
    }
}
