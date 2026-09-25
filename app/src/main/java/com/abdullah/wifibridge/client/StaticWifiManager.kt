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
     * إنشاء إعدادات Static IP بطريقة آمنة تتوافق مع قيود Kotlin Compiler و Android SDK
     */
    @RequiresApi(Build.VERSION_CODES.S)
    fun createStaticIpConfiguration(customIp: String, gatewayIp: String): StaticIpConfiguration {
        val gatewayInet = InetAddress.getByName(gatewayIp)
        
        //  إنشاء LinkAddress بشكل مضاعف الأمان لتفادي أخطاء Access/Constructor restricted
        val linkAddress = createLinkAddressSafe(customIp, 24)

        return StaticIpConfiguration.Builder()
            .setIpAddress(linkAddress)
            .setGateway(gatewayInet)
            .setDnsServers(listOf(gatewayInet, InetAddress.getByName("8.8.8.8")))
            .build()
    }

    /**
     * دالة مساعدة لإنشاء LinkAddress بشكل متوافق مع كافة المستويات دون التعارض مع القيود
     */
    private fun createLinkAddressSafe(ipAddress: String, prefixLength: Int): LinkAddress {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // الاستدعاء المباشر عن طريق CIDR Notation القياسي (مثال: "192.168.10.1/24")
            LinkAddress("$ipAddress/$prefixLength")
        } else {
            // للأجهزة القديمة باستخدام InetAddress
            val inetAddress = InetAddress.getByName(ipAddress)
            val constructor = LinkAddress::class.java.getConstructor(InetAddress::class.java, Int::class.javaPrimitiveType)
            constructor.newInstance(inetAddress, prefixLength)
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
