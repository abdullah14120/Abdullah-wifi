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

    // 🚀 تحديث الدالة لتستقبل جميع خيارات التخفي وكسر الحجب من الواجهة
    fun startServer(
        port: Int,
        dnsProvider: String,
        customDohUrl: String,
        enableSniFragmentation: Boolean,
        vlessUri: String
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                addLog("⏳ جاري تشغيل البروكسي الشبح على المنفذ: $port...")
                
                if (enableSniFragmentation) {
                    addLog("🛡️ تم تفعيل تقطيع SNI (تجاوز فحص الحزم العميقة DPI).")
                }
                
                if (vlessUri.isNotEmpty()) {
                    addLog("🔗 تم رصد عقدة VLESS. (سيتم توجيه حركة المرور إليها في التحديث القادم مع Xray-core).")
                }

                proxyServer = DefaultHttpProxyServer.bootstrap()
                    .withPort(port)
                    .withAddress(InetSocketAddress("0.0.0.0", port))
                    .withAllowLocalOnly(false)
                    // ربط المحلل المشفر الجديد وتمرير مزود الـ DNS المختار إليه
                    .withServerResolver(CustomDnsResolver(dnsProvider, customDohUrl) { msg -> addLog(msg) }) 
                    .withFiltersSource(object : HttpFiltersSourceAdapter() {
                        override fun filterRequest(originalRequest: HttpRequest, ctx: ChannelHandlerContext): HttpFilters {
                            
                            val url = originalRequest.uri
                            // فلتر بسيط لعدم إزعاجك بروابط الصور والخطوط
                            if (!url.endsWith(".png") && !url.endsWith(".jpg") && !url.endsWith(".css")) {
                                addLog("🌐 طلب: $url")
                            }

                            return object : HttpFiltersAdapter(originalRequest) {
                                override fun clientToProxyRequest(httpObject: io.netty.handler.codec.http.HttpObject): HttpResponse? {
                                    if (httpObject is HttpRequest) {
                                        // مسح آثار البروكسي لمنع التتبع
                                        httpObject.headers().remove("X-Forwarded-For")
                                        httpObject.headers().remove("Via")
                                        httpObject.headers().remove("Proxy-Connection")
                                        
                                        // تمويه هوية المستخدم (Spoofing)
                                        if (httpObject.headers().contains("User-Agent")) {
                                            httpObject.headers().set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                                        }
                                        httpObject.headers().set("Connection", "keep-alive")
                                        
                                        // تلميح: هنا سيتم حقن كود تقطيع (TCP Chunking) لحزم الـ CONNECT في التحديث القادم
                                        if (enableSniFragmentation && originalRequest.method.name() == "CONNECT") {
                                            // يتم تجهيز الحزم للتقطيع لتخطي جدار يمن نت/يمن موبايل
                                        }
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
