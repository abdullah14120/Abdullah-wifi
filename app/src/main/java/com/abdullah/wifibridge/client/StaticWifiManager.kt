package com.abdullah.wifibridge.client

import android.content.Context
import android.net.IpConfiguration
import android.net.LinkAddress
import android.net.StaticIpConfiguration
import android.net.wifi.WifiNetworkSpecifier
import android.net.NetworkRequest
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import androidx.annotation.RequiresApi
import java.net.InetAddress

class StaticWifiManager(private val context: Context) {

    // تطبيق ضبط الـ Static IP الحقيقي بدون VPN
    fun createStaticIpConfiguration(customIp: String, gatewayIp: String): StaticIpConfiguration {
        val staticIpConfig = StaticIpConfiguration()
        
        val ipInet = InetAddress.getByName(customIp)
        val gatewayInet = InetAddress.getByName(gatewayIp)

        // تعيين IP جهاز العميل وقناع الشبكة Prefix Length 24 (/255.255.255.0)
        staticIpConfig.ipAddress = LinkAddress(ipInet, 24)
        staticIpConfig.gateway = gatewayInet

        // تعيين الـ Gateway والـ DNS المخصص
        staticIpConfig.dnsServers.add(gatewayInet)
        staticIpConfig.dnsServers.add(InetAddress.getByName("8.8.8.8"))

        return staticIpConfig
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun connectToBridgeNetwork(ssid: String, passphrase: String, clientIp: String, gatewayIp: String) {
        val specifier = WifiNetworkSpecifier.Builder()
            .setSsid(ssid)
            .setWpa2Passphrase(passphrase)
            .build()

        val request = NetworkRequest.Builder()
            .addTransportType(android.net.NetworkCapabilities.TRANSPORT_WIFI) // تحديد صريح لواي فاي حقيقي
            .setNetworkSpecifier(specifier)
            .build()

        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        connectivityManager.requestNetwork(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // تم الاتصال بنجاح بشبكة الواي فاي الحقيقية!
                connectivityManager.bindProcessToNetwork(network)
            }
        })
    }
}
