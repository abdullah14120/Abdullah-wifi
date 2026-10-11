package com.abdullah.wifibridge.data.network

import android.content.Context
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

    fun startServer(port: Int, context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                addLog("⏳ جاري تشغيل البروكسي على المنفذ: $port...")
                
                proxyServer = DefaultHttpProxyServer.bootstrap()
                    .withPort(port)
                    // الاستماع لجميع الواجهات (wlan0 و p2p0)
                    .withAddress(InetSocketAddress("0.0.0.0", port))
                    // السماح للهاتف الثاني بالاتصال
                    .withAllowLocalOnly(false)
                    .withFiltersSource(object : HttpFiltersSourceAdapter() {
                        override fun filterRequest(originalRequest: HttpRequest, ctx: ChannelHandlerContext): HttpFilters {
                            // تسجيل أي طلب قادم من الهاتف العميل فور وصوله
                            addLog("🌐 طلب: ${originalRequest.uri}")
                            return object : HttpFiltersAdapter(originalRequest) {
                                override fun clientToProxyRequest(httpObject: io.netty.handler.codec.http.HttpObject): HttpResponse? {
                                    if (httpObject is HttpRequest) {
                                        httpObject.headers().remove("X-Forwarded-For")
                                        httpObject.headers().remove("Via")
                                        httpObject.headers().remove("Proxy-Connection")
                                        httpObject.headers().set("Connection", "keep-alive")
                                    }
                                    return null
                                }
                            }
                        }
                    })
                    .start()
                
                _isServerRunning.value = true
                addLog("✅ البروكسي جاهز ويستمع للطلبات!")
            } catch (e: Exception) {
                e.printStackTrace()
                addLog("❌ خطأ تشغيل البروكسي: ${e.message}")
                _isServerRunning.value = false
            }
        }
    }

    fun stopServer(context: Context) {
        proxyServer?.stop()
        proxyServer = null
        _isServerRunning.value = false
        addLog("🛑 تم إيقاف البروكسي.")
    }

    private fun addLog(message: String) {
        val logs = _serverLogs.value.toMutableList()
        logs.add(0, message)
        _serverLogs.value = logs
    }
}
