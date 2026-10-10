package com.abdullah.wifibridge.data.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.littleshoot.proxy.HttpProxyServer
import org.littleshoot.proxy.impl.DefaultHttpProxyServer
import org.littleshoot.proxy.HttpFilters
import org.littleshoot.proxy.HttpFiltersAdapter
import org.littleshoot.proxy.HttpFiltersSourceAdapter
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponse
import io.netty.channel.ChannelHandlerContext
import java.net.InetSocketAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ProxyServerManager {
    private var proxyServer: HttpProxyServer? = null
    private val _isServerRunning = MutableStateFlow(false)
    val isServerRunning: StateFlow<Boolean> = _isServerRunning

    private val _serverLogs = MutableStateFlow<List<String>>(emptyList())
    val serverLogs: StateFlow<List<String>> = _serverLogs

    fun startServer(port: Int) {
        // نقل عملية تشغيل الخادم إلى مسار خلفي (IO Thread) لتجنب انهيار التطبيق
        CoroutineScope(Dispatchers.IO).launch {
            try {
                proxyServer = DefaultHttpProxyServer.bootstrap()
                    .withPort(port)
                    .withAddress(InetSocketAddress("0.0.0.0", port))
                    .withAllowLocalOnly(false) // السماح للهواتف الأخرى بالاتصال
                    .withFiltersSource(object : HttpFiltersSourceAdapter() {
                        override fun filterRequest(originalRequest: HttpRequest, ctx: ChannelHandlerContext): HttpFilters {
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
                
                // تحديث حالة الواجهة بعد نجاح التشغيل
                _isServerRunning.value = true
                addLog("Proxy started on port: $port in Stealth Mode")
            } catch (e: Exception) {
                e.printStackTrace()
                addLog("Error starting proxy: ${e.message}")
                _isServerRunning.value = false
            }
        }
    }

    fun stopServer() {
        proxyServer?.stop()
        proxyServer = null
        _isServerRunning.value = false
        addLog("Proxy Server Stopped")
    }

    private fun addLog(message: String) {
        val logs = _serverLogs.value.toMutableList()
        logs.add(message)
        _serverLogs.value = logs
    }
}
