package com.abdullah.wifibridge.data.network

import org.json.JSONObject
import org.littleshoot.proxy.HostResolver
import java.net.InetSocketAddress
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class CloudflareDnsResolver(private val logCallback: (String) -> Unit) : HostResolver {
    
    override fun resolve(host: String, port: Int): InetSocketAddress {
        try {
            // تنظيف الرابط وإزالة المنفذ إن وُجد
            val cleanHost = host.substringBefore(":")
            
            // إذا كان آي بي مباشر لا نشفره
            if (cleanHost.matches(Regex("^\\d{1,3}(\\.\\d{1,3}){3}$"))) {
                return InetSocketAddress(cleanHost, port)
            }

            logCallback("🔍 جاري تشفير DNS للموقع: $cleanHost")

            // 1. المحاولة عبر Cloudflare
            var ip = fetchDoh("https://cloudflare-dns.com/dns-query?name=$cleanHost&type=A", "Cloudflare")
            
            // 2. المحاولة عبر Google DNS
            if (ip == null) {
                ip = fetchDoh("https://dns.google/resolve?name=$cleanHost&type=A", "Google")
            }

            // 3. المحاولة عبر AliDNS (خادم سريع وممتاز جداً لتجاوز الحجب)
            if (ip == null) {
                ip = fetchDoh("https://dns.alidns.com/resolve?name=$cleanHost&type=1", "AliDNS")
            }

            if (ip != null) {
                logCallback("✅ نجاح التشفير ($cleanHost -> $ip)")
                return InetSocketAddress(ip, port)
            } else {
                logCallback("❌ فشلت جميع سيرفرات التشفير. سيتم استخدام DNS الهاتف العادي.")
            }
        } catch (e: Exception) {
            logCallback("❌ خطأ عام في المحلل: ${e.message}")
        }
        
        // الخطة البديلة: العودة لنظام أندرويد العادي
        return InetSocketAddress(host, port)
    }

    private fun fetchDoh(urlString: String, providerName: String): String? {
        return try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpsURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("accept", "application/dns-json")
            
            // 🚀 رفع المهلة إلى 10 ثوانٍ ليتناسب مع بطء استجابة الشبكات
            connection.connectTimeout = 10000 
            connection.readTimeout = 10000

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
                logCallback("⚠️ $providerName: لم يتم العثور على IP في الرد.")
                null
            } else {
                logCallback("⚠️ $providerName: رفض الاتصال (كود ${connection.responseCode})")
                null
            }
        } catch (e: java.net.SocketTimeoutException) {
            logCallback("⚠️ $providerName: انتهى وقت الاتصال (بطء إنترنت).")
            null
        } catch (e: java.net.UnknownHostException) {
            logCallback("⚠️ $providerName: محجوب في شبكتك الرئيسية.")
            null
        } catch (e: javax.net.ssl.SSLHandshakeException) {
            logCallback("⚠️ $providerName: خطأ في شهادة الأمان (احتمال تدخل مزود الخدمة).")
            null
        } catch (e: Exception) {
            logCallback("⚠️ $providerName خطأ: ${e.message}")
            null
        }
    }
}
