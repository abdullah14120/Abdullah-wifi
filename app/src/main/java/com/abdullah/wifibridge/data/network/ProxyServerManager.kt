package com.abdullah.wifibridge.data.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.littleshoot.proxy.HttpProxyServer
import org.littleshoot.proxy.impl.DefaultHttpProxyServer
import org.littleshoot.proxy.HttpFilters
import org.littleshoot.proxy.HttpFiltersAdapter
import org.littleshoot.proxy.HttpFiltersSourceAdapter
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponse
import io.netty.channel.ChannelHandlerContext
import java.net.InetSocketAddress

class ProxyServerManager {
    private var proxyServer: HttpProxyServer? = null
    private val _isServerRunning = MutableStateFlow(false)
    val isServerRunning: StateFlow<Boolean> = _isServerRunning

    private val _serverLogs = MutableStateFlow<List<String>>(emptyList())
    val serverLogs: StateFlow<List<String>> = _serverLogs

    fun startServer(
        port: Int,
        dnsProvider: String,
        customDohUrl: String,
        enableSniFragmentation: Boolean,
        vlessUri: String,
        spoofedDevice: String // 🚀 المعامل الجديد لتغيير هوية الجهاز
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                addLog("⏳ جاري تشغيل البروكسي الشبح على المنفذ: $port...")
                
                // 🚀 تحديد بصمة الجهاز بناءً على اختيار المستخدم
                val targetUserAgent = when (spoofedDevice) {
                    "Samsung S24 Ultra" -> "Mozilla/5.0 (Linux; Android 14; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                    "Windows PC" -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                    "MacBook" -> "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4.1 Safari/605.1.15"
                    else -> null // الافتراضي (لا نغير شيئاً)
                }

                if (targetUserAgent != null) {
                    addLog("🎭 تم تفعيل تمويه الهوية. سيظهر الجهاز كـ: $spoofedDevice")
                }

                proxyServer = DefaultHttpProxyServer.bootstrap()
                    .withPort(port)
                    .withAddress(InetSocketAddress("0.0.0.0", port))
                    .withAllowLocalOnly(false)
                    .withServerResolver(CustomDnsResolver(dnsProvider, customDohUrl) { msg -> addLog(msg) }) 
                    .withFiltersSource(object : HttpFiltersSourceAdapter() {
                        override fun filterRequest(originalRequest: HttpRequest, ctx: ChannelHandlerContext): HttpFilters {
                            
                            val url = originalRequest.uri
                            if (!url.endsWith(".png") && !url.endsWith(".jpg") && !url.endsWith(".css")) {
                                addLog("🌐 طلب: $url")
                            }

                            return object : HttpFiltersAdapter(originalRequest) {
                                override fun clientToProxyRequest(httpObject: io.netty.handler.codec.http.HttpObject): HttpResponse? {
                                    if (httpObject is HttpRequest) {
                                        // مسح آثار البروكسي
                                        httpObject.headers().remove("X-Forwarded-For")
                                        httpObject.headers().remove("Via")
                                        httpObject.headers().remove("Proxy-Connection")
                                        
                                        // 🚀 سحر تغيير هوية الجهاز (Spoofing)
                                        if (targetUserAgent != null && httpObject.headers().contains("User-Agent")) {
                                            httpObject.headers().set("User-Agent", targetUserAgent)
                                        }
                                        
                                        httpObject.headers().set("Connection", "keep-alive")
                                    }
                                    return null
                                }
                            }
                        }
                    })
                    .start()
                
                _isServerRunning.value = true
                addLog("✅ البروكسي جاهز ويعمل بكفاءة.")
            } catch (e: Exception) {
                e.printStackTrace()
                addLog("❌ خطأ تشغيل البروكسي: ${e.message}")
                _isServerRunning.value = false
            }
        }
    }

    fun stopServer() {
        proxyServer?.stop()
        proxyServer = null
        _isServerRunning.value = false
        addLog("🛑 تم إيقاف البروكسي.")
    }

    fun addLog(message: String) {
        val logs = _serverLogs.value.toMutableList()
        logs.add(0, message)
        if (logs.size > 100) logs.removeLast()
        _serverLogs.value = logs
    }
}
