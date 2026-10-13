package com.abdullah.wifibridge.data.network

import org.json.JSONObject
import org.littleshoot.proxy.HostResolver
import java.net.InetSocketAddress
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class CloudflareDnsResolver : HostResolver {
    override fun resolve(host: String, port: Int): InetSocketAddress {
        try {
            // 1. إذا كان الطلب عبارة عن IP أرقام مباشرة، لا حاجة لتحليله
            if (host.matches(Regex("^\\d{1,3}(\\.\\d{1,3}){3}$"))) {
                return InetSocketAddress(host, port)
            }

            // 2. طلب الـ IP بشكل مشفر من Cloudflare (DNS over HTTPS)
            val url = URL("https://cloudflare-dns.com/dns-query?name=$host&type=A")
            val connection = url.openConnection() as HttpsURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("accept", "application/dns-json")
            connection.connectTimeout = 3000 // مهلة 3 ثوانٍ
            connection.readTimeout = 3000

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                val jsonObject = JSONObject(response)
                
                // 3. استخراج أول آي بي (IP) متاح من الرد
                if (jsonObject.has("Answer")) {
                    val answers = jsonObject.getJSONArray("Answer")
                    for (i in 0 until answers.length()) {
                        val answer = answers.getJSONObject(i)
                        // Type 1 يعني IPv4
                        if (answer.getInt("type") == 1) { 
                            val ip = answer.getString("data")
                            return InetSocketAddress(ip, port)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // 4. خطة بديلة (Fallback): إذا فشل الاتصال بكلاودفلير لأي سبب، استخدم المحلل العادي
        return InetSocketAddress(host, port)
    }
}
