package com.abdullah.wifibridge.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
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

    // تم إضافة context هنا للوصول إلى مدير الشبكات
    fun startServer(port: Int, context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // ==========================================
                // الخدعة السحرية المستخرجة من تطبيق NetShare
                // ==========================================
                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val activeNetwork = connectivityManager.activeNetwork
                
                if (activeNetwork != null) {
                    val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
                    if (capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                        // إجبار التطبيق (والبروكسي) على توجيه كل طلباته للخارج عبر الإنترنت الحقيقي فقط
                        if (Build.VERSION.SDK_INT >= Build.VERSION.SDK_INT) {
                            connectivityManager.bindProcessToNetwork(activeNetwork)
                            addLog("Network successfully bound to active internet connection.")
                        }
                    }
                } else {
                    addLog("Warning: No active internet network found to bind to.")
                }
                // ==========================================

                proxyServer = DefaultHttpProxyServer.bootstrap()
                    .withPort(port)
                    .withAddress(InetSocketAddress("0.0.0.0", port))
                    .withAllowLocalOnly(false)
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
                
                _isServerRunning.value = true
                addLog("Proxy started on port: $port in Stealth Mode")
            } catch (e: Exception) {
                e.printStackTrace()
                addLog("Error starting proxy: ${e.message}")
                _isServerRunning.value = false
            }
        }
    }

    fun stopServer(context: Context) {
        // فك ارتباط الشبكة عند الإغلاق (لتجنب تعليق النظام)
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION.SDK_INT) {
            connectivityManager.bindProcessToNetwork(null)
        }

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
