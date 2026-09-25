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

    /**
     * إنشاء إعدادات Static IP متوافقة مع أندرويد 10+ وتتجاوز قيود المترجم
     */
    @RequiresApi(Build.VERSION_CODES.S)
    fun createStaticIpConfiguration(customIp: String, gatewayIp: String): StaticIpConfiguration {
        val gatewayInet = InetAddress.getByName(gatewayIp)
        
        // إنشاء LinkAddress عبر الـ Dynamic Reflection التام لمنع الـ Compiler من فحص الـ Constructor
        val linkAddress = createLinkAddressViaReflection(customIp, 24)

        return StaticIpConfiguration.Builder()
            .setIpAddress(linkAddress)
            .setGateway(gatewayInet)
            .setDnsServers(listOf(gatewayInet, InetAddress.getByName("8.8.8.8")))
            .build()
    }

    /**
     * تفادي فحص الـ Compiler تماماً عبر استخدام Class.forName والـ Reflection
     */
    private fun createLinkAddressViaReflection(ipAddress: String, prefixLength: Int): LinkAddress {
        val inetAddress = InetAddress.getByName(ipAddress)
        
        return try {
            // المحاولة الأولى: استخدام Dynamic Constructor Reflection المباشر
            val clazz = Class.forName("android.net.LinkAddress")
            val constructor = clazz.getConstructor(InetAddress::class.java, Int::class.javaPrimitiveType)
            constructor.newInstance(inetAddress, prefixLength) as LinkAddress
        } catch (e: Exception) {
            try {
                // المحاولة الثانية: استخدام String CIDR Constructor عبر الـ Reflection
                val clazz = Class.forName("android.net.LinkAddress")
                val constructor = clazz.getConstructor(String::class.java)
                constructor.newInstance("$ipAddress/$prefixLength") as LinkAddress
            } catch (ex: Exception) {
                throw RuntimeException("فشل إنشاء LinkAddress عبر Reflection: ${ex.message}")
            }
        }
    }

    /**
     * الاتصال بشبكة Wi-Fi محددة برمجياً
     */
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
