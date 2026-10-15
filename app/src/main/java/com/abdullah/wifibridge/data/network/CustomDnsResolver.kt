package com.abdullah.wifibridge.data.network

import org.json.JSONObject
import org.littleshoot.proxy.HostResolver
import java.net.InetSocketAddress
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class CustomDnsResolver(
    private val provider: String,
    private val customDohUrl: String,
    private val logCallback: (String) -> Unit
) : HostResolver {
    
    override fun resolve(host: String, port: Int): InetSocketAddress {
        try {
            val cleanHost = host.substringBefore(":")
            
            // إذا كان آي بي مباشر أو تم اختيار "بدون تشفير"
            if (cleanHost.matches(Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")) || provider == "بدون تشفير") {
                if (provider == "بدون تشفير") logCallback("⚠️ تمرير $cleanHost (بدون تشفير)")
                return InetSocketAddress(cleanHost, port)
            }

            logCallback("🔍 تشفير DNS لـ $cleanHost عبر [$provider]")

            // اختيار رابط الـ DoH بناءً على اختيار المستخدم
            val dohUrl = when (provider) {
                "Cloudflare" -> "https://cloudflare-dns.com/dns-query?name=$cleanHost&type=A"
                "Google" -> "https://dns.google/resolve?name=$cleanHost&type=A"
                "AliDNS" -> "https://dns.alidns.com/resolve?name=$cleanHost&type=1"
                "Custom DoH" -> {
                    if (customDohUrl.isNotEmpty()) {
                        // إضافة الاستعلام للرابط المخصص (يفترض أنه Cloudflare Worker)
                        "$customDohUrl?name=$cleanHost&type=A"
                    } else {
                        null
                    }
                }
                else -> "https://cloudflare-dns.com/dns-query?name=$cleanHost&type=A"
            }

            if (dohUrl == null) {
                logCallback("❌ رابط Custom DoH فارغ. سيتم العودة للإنترنت العادي.")
                return InetSocketAddress(cleanHost, port)
            }

            // تنفيذ الطلب المشفر
            val ip = fetchDoh(dohUrl, provider)

            if (ip != null) {
                logCallback("✅ تشفير ناجح ($cleanHost -> $ip)")
                return InetSocketAddress(ip, port)
            } else {
                logCallback("❌ فشل تشفير $provider. سيتم استخدام DNS الهاتف العادي.")
            }
        } catch (e: Exception) {
            logCallback("❌ خطأ عام في المحلل: ${e.message}")
        }
        
        return InetSocketAddress(host, port)
    }

    private fun fetchDoh(urlString: String, providerName: String): String? {
        return try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpsURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("accept", "application/dns-json")
            
            // مهلة طويلة نسبياً لتخطي بطء شبكات الـ 4G المحلية
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
                logCallback("⚠️ $providerName: لا يوجد IP في الرد.")
                null
            } else {
                logCallback("⚠️ $providerName: رفض الاتصال (كود ${connection.responseCode})")
                null
            }
        } catch (e: java.net.SocketTimeoutException) {
            logCallback("⚠️ $providerName: انتهى وقت الاتصال (بطء إنترنت).")
            null
        } catch (e: java.net.UnknownHostException) {
            logCallback("⚠️ $providerName: محجوب في شبكتك الرئيسية (حجب DPI).")
            null
        } catch (e: javax.net.ssl.SSLHandshakeException) {
            logCallback("⚠️ $providerName: خطأ في شهادة الأمان (تدخل من مزود الخدمة).")
            null
        } catch (e: Exception) {
            logCallback("⚠️ $providerName خطأ: ${e.message}")
            null
        }
    }
}
