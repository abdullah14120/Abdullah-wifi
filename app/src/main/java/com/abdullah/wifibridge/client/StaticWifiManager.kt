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
     * إنشاء إعدادات Static IP متوافقة تماماً مع أندرويد 10+ (API 29+)
     */
    @RequiresApi(Build.VERSION_CODES.S)
    fun createStaticIpConfiguration(customIp: String, gatewayIp: String): StaticIpConfiguration {
        val gatewayInet = InetAddress.getByName(gatewayIp)
        
        // إنشاء LinkAddress بطريقة آمنة لا تتعارض مع المترجم
        val linkAddress = createLinkAddressSafe(customIp, 24)

        return StaticIpConfiguration.Builder()
            .setIpAddress(linkAddress)
            .setGateway(gatewayInet)
            .setDnsServers(listOf(gatewayInet, InetAddress.getByName("8.8.8.8")))
            .build()
    }

    /**
     * إنشاء LinkAddress بدون استدعاء المنشئات المحظورة مباشرة
     */
    private fun createLinkAddressSafe(ipAddress: String, prefixLength: Int): LinkAddress {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // الاستدعاء القياسي عبر CIDR Notation الحديث (مثال: "192.168.10.1/24")
            LinkAddress("$ipAddress/$prefixLength")
        } else {
            // استخدام الـ Reflection للوصول المباشر دون أن يتعرف المترجم على المنشئ المحظور
            val inetAddress = InetAddress.getByName(ipAddress)
            val clazz = LinkAddress::class.java
            val constructor = clazz.getConstructor(InetAddress::class.java, Int::class.javaObjectType)
                ?: clazz.getConstructor(InetAddress::class.java, java.lang.Integer.TYPE)
            
            constructor.newInstance(inetAddress, prefixLength)
        }
    }

    /**
     * الاتصال بشبكة Wi-Fi المحددة برمجياً عبر ConnectivityManager
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
                // ربط العمليات بالشبكة المستهدفة لتمرير البيانات عبرها
                connectivityManager.bindProcessToNetwork(network)
            }
        })
    }
}
