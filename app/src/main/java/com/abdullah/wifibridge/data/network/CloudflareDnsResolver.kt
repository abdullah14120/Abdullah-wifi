package com.abdullah.wifibridge.data.network

import org.json.JSONObject
import org.littleshoot.proxy.HostResolver
import java.net.InetSocketAddress
import java.net.URL
import javax.net.ssl.HttpsURLConnection

// أضفنا logCallback لنتمكن من إرسال السجلات إلى الشاشة الرئيسية
class CloudflareDnsResolver(private val logCallback: (String) -> Unit) : HostResolver {
    
    override fun resolve(host: String, port: Int): InetSocketAddress {
        try {
            // إذا كان الطلب آي بي مباشر (أرقام)، لا حاجة لتحليله
            if (host.matches(Regex("^\\d{1,3}(\\.\\d{1,3}){3}$"))) {
                return InetSocketAddress(host, port)
            }

            // تنظيف الرابط (إزالة رقم المنفذ إذا كان موجوداً مثل example.com:443)
            val cleanHost = host.substringBefore(":")

            logCallback("🔍 جاري تحليل وتشفير DNS للموقع: $cleanHost")

            // 1. المحاولة عبر Cloudflare
            var ip = fetchDoh("https://cloudflare-dns.com/dns-query?name=$cleanHost&type=A")
            
            // 2. المحاولة عبر Google DoH إذا فشل كلاودفلير
            if (ip == null) {
                logCallback("⚠️ كلاودفلير لم يستجب، جاري المحاولة عبر Google DNS...")
                ip = fetchDoh("https://dns.google/resolve?name=$cleanHost&type=A")
            }

            if (ip != null) {
                logCallback("✅ تم التشفير: $cleanHost -> $ip")
                return InetSocketAddress(ip, port)
            } else {
                logCallback("❌ فشل جلب DNS المشفر للموقع: $cleanHost. سيتم العودة للإنترنت العادي.")
            }
        } catch (e: Exception) {
            logCallback("❌ خطأ في المحلل: ${e.message}")
        }
        
        // الخطة البديلة: العودة لنظام أندرويد العادي
        return InetSocketAddress(host, port)
    }

    private fun fetchDoh(urlString: String): String? {
        return try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpsURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("accept", "application/dns-json")
            connection.connectTimeout = 3000 // مهلة 3 ثوانٍ
            connection.readTimeout = 3000

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                val jsonObject = JSONObject(response)
                
                if (jsonObject.has("Answer")) {
                    val answers = jsonObject.getJSONArray("Answer")
                    for (i in 0 until answers.length()) {
                        val answer = answers.getJSONObject(i)
                        if (answer.getInt("type") == 1) { // IPv4
                            return answer.getString("data")
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}
