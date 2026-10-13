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
                    // 🚀 السحر هنا: توجيه كل طلبات المواقع عبر Cloudflare المشفر!
                    .withServerResolver(CloudflareDnsResolver()) 
                    .withFiltersSource(object : HttpFiltersSourceAdapter() {
                        override fun filterRequest(originalRequest: HttpRequest, ctx: ChannelHandlerContext): HttpFilters {
                            return object : HttpFiltersAdapter(originalRequest) {
                                override fun clientToProxyRequest(httpObject: io.netty.handler.codec.http.HttpObject): HttpResponse? {
                                    if (httpObject is HttpRequest) {
                                        // 1. مسح آثار البروكسي من الطلب
                                        httpObject.headers().remove("X-Forwarded-For")
                                        httpObject.headers().remove("Via")
                                        httpObject.headers().remove("Proxy-Connection")
                                        
                                        // 2. إخفاء هوية الجهاز (User-Agent Spoofing) للطلبات غير المشفرة
                                        // نجعل الطلب يبدو وكأنه قادم من متصفح هاتف أندرويد قياسي وليس جهازا متصلا بالشبكة
                                        if (httpObject.headers().contains("User-Agent")) {
                                            httpObject.headers().set("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
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
                addLog("✅ البروكسي جاهز (DNS مشفر عبر Cloudflare 1.1.1.1)")
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
        _serverLogs.value = logs
    }
}
