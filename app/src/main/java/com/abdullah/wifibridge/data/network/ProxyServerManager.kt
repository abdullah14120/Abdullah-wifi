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

    fun startServer(port: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                addLog("⏳ جاري تشغيل البروكسي الشبح على المنفذ: $port...")
                
                proxyServer = DefaultHttpProxyServer.bootstrap()
                    .withPort(port)
                    .withAddress(InetSocketAddress("0.0.0.0", port))
                    .withAllowLocalOnly(false)
                    // ربط المحلل المشفر الجديد وتمرير دالة السجل إليه
                    .withServerResolver(CloudflareDnsResolver { msg -> addLog(msg) }) 
                    .withFiltersSource(object : HttpFiltersSourceAdapter() {
                        override fun filterRequest(originalRequest: HttpRequest, ctx: ChannelHandlerContext): HttpFilters {
                            
                            // 🚀 إرجاع سطر السجل الذي يطبع المواقع المطلوبة
                            val url = originalRequest.uri
                            // فلتر بسيط لعدم إزعاجك بروابط الصور والخطوط الخلفية
                            if (!url.endsWith(".png") && !url.endsWith(".jpg") && !url.endsWith(".css")) {
                                addLog("🌐 طلب: $url")
                            }

                            return object : HttpFiltersAdapter(originalRequest) {
                                override fun clientToProxyRequest(httpObject: io.netty.handler.codec.http.HttpObject): HttpResponse? {
                                    if (httpObject is HttpRequest) {
                                        httpObject.headers().remove("X-Forwarded-For")
                                        httpObject.headers().remove("Via")
                                        httpObject.headers().remove("Proxy-Connection")
                                        
                                        if (httpObject.headers().contains("User-Agent")) {
                                            httpObject.headers().set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
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
                addLog("✅ البروكسي جاهز (DNS مشفر مدعوم بخطط بديلة)")
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
        // الاحتفاظ بآخر 100 سطر فقط لتجنب امتلاء الشاشة
        if (logs.size > 100) logs.removeLast()
        _serverLogs.value = logs
    }
}
