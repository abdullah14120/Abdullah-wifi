package com.abdullah.wifibridge.data.network

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.HttpObject
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.littleshoot.proxy.ChainedProxy
import org.littleshoot.proxy.ChainedProxyAdapter
import org.littleshoot.proxy.ChainedProxyManager
import org.littleshoot.proxy.HttpFilters
import org.littleshoot.proxy.HttpFiltersAdapter
import org.littleshoot.proxy.HttpFiltersSourceAdapter
import org.littleshoot.proxy.HttpProxyServer
import org.littleshoot.proxy.impl.DefaultHttpProxyServer
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.util.Queue
import kotlin.random.Random

class ProxyServerManager {
    private var proxyServer: HttpProxyServer? = null
    private val _isServerRunning = MutableStateFlow(false)
    val isServerRunning: StateFlow<Boolean> = _isServerRunning

    private val _serverLogs = MutableStateFlow<List<String>>(emptyList())
    val serverLogs: StateFlow<List<String>> = _serverLogs

    // قائمة الترويسات التي تكشف وجود البروكسي ليتم إزالتها بالكامل
    private val headersToStrip = listOf(
        "X-Forwarded-For",
        "X-Forwarded-Host",
        "X-Forwarded-Proto",
        "Forwarded",
        "Via",
        "X-Real-IP",
        "Client-IP",
        "Proxy-Connection",
        "Proxy-Authorization"
    )

    fun startServer(
        port: Int,
        dnsProvider: String,
        customDohUrl: String,
        enableSniFragmentation: Boolean,
        useXrayUpstream: Boolean,
        xrayLocalPort: Int = 10808,
        spoofedDevice: String
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                addLog("⏳ جاري تشغيل البروكسي النظيف على المنفذ: $port...")

                // توليد هوية جهاز ديناميكية تتغير مع كل تشغيل للجلسة
                val targetUserAgent = when (spoofedDevice) {
                    "تلقائي (متغير لكل جلسة)" -> {
                        val dynamicAgents = listOf(
                            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_${Random.nextInt(1, 6)} like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1",
                            "Mozilla/5.0 (Linux; Android 14; SM-S928B Build/UP1A.${Random.nextInt(230000, 239999)}.001) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/12${Random.nextInt(2, 6)}.0.0.0 Mobile Safari/537.36",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/12${Random.nextInt(2, 6)}.0.0.0 Safari/537.36",
                            "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_${Random.nextInt(1, 5)}) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Safari/605.1.15"
                        )
                        dynamicAgents.random()
                    }
                    "iPhone 15 Pro Max" -> "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4.1 Mobile/15E148 Safari/604.1"
                    "Samsung S24 Ultra" -> "Mozilla/5.0 (Linux; Android 14; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                    "Windows 11 PC" -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                    "MacBook Pro" -> "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4.1 Safari/605.1.15"
                    else -> null
                }

                if (targetUserAgent != null) {
                    addLog("🎭 تم تعيين بصمة متصفح مستقلة لهذه الجلسة: [$spoofedDevice]")
                }

                val bootstrap = DefaultHttpProxyServer.bootstrap()
                    .withPort(port)
                    .withAddress(InetSocketAddress("0.0.0.0", port))
                    .withAllowLocalOnly(false)
                    .withTransparent(true)

                // توحيد المسار: إما التوجيه الكامل لنواة Xray أو استخدام DoH المحلي
                if (useXrayUpstream) {
                    addLog("🔗 تم تفعيل الربط التسلسلي (Upstream Chaining) مع المنفذ 127.0.0.1:$xrayLocalPort")
                    addLog("🛡️ التمرير الصامت لـ TLS نشط (الحفاظ على بصمة JA3/JA4 الأصلية).")

                    val chainedProxyManager = ChainedProxyManager { _: HttpRequest, chainedProxies: Queue<ChainedProxy> ->
                        chainedProxies.add(object : ChainedProxyAdapter() {
                            override fun getChainedProxyAddress(): InetSocketAddress {
                                return InetSocketAddress("127.0.0.1", xrayLocalPort)
                            }
                        })
                    }
                    bootstrap.withChainProxyManager(chainedProxyManager)
                } else {
                    addLog("🌐 العمل بوضع البروكسي المستقل مع محلل DNS المشفر ($dnsProvider).")
                    bootstrap.withServerResolver(
                        CustomDnsResolver(dnsProvider, customDohUrl) { msg -> addLog(msg) }
                    )
                }

                // فلتر تنظيف الترويسات وحجب تسرب WebRTC/STUN
                bootstrap.withFiltersSource(object : HttpFiltersSourceAdapter() {
                    override fun filterRequest(originalRequest: HttpRequest, ctx: ChannelHandlerContext): HttpFilters {
                        val uri = originalRequest.uri.lowercase()

                        return object : HttpFiltersAdapter(originalRequest) {
                            override fun clientToProxyRequest(httpObject: HttpObject): HttpResponse? {
                                if (httpObject is HttpRequest) {
                                    val host = httpObject.headers().get("Host")?.lowercase() ?: uri

                                    // 1. إغلاق أي محاولة تسرب WebRTC / STUN عبر البروكسي
                                    if (host.contains("stun.") || host.contains("turn.") || uri.contains(":3478") || uri.contains(":19302")) {
                                        addLog("🚫 تم إحباط محاولة تسرب WebRTC/STUN: $host")
                                        return DefaultFullHttpResponse(
                                            HttpVersion.HTTP_1_1,
                                            HttpResponseStatus.FORBIDDEN
                                        )
                                    }

                                    // 2. تنظيف كافة الترويسات التي تكشف البروكسي
                                    headersToStrip.forEach { headerName ->
                                        httpObject.headers().remove(headerName)
                                    }

                                    // 3. تعديل User-Agent للطلبات غير المشفرة فقط (مع ترك نفق CONNECT صامتاً)
                                    if (targetUserAgent != null && httpObject.method().name() != "CONNECT") {
                                        if (httpObject.headers().contains("User-Agent")) {
                                            httpObject.headers().set("User-Agent", targetUserAgent)
                                        }
                                    }

                                    httpObject.headers().set("Connection", "keep-alive")
                                }
                                return null
                            }
                        }
                    }
                })

                proxyServer = bootstrap.start()
                _isServerRunning.value = true
                addLog("✅ البروكسي يعمل الآن بأعلى معايير نظافة الجلسة.")

                // انتظار قصير لاستقرار القنوات ثم إجراء الفحص التلقائي للهوية الخارجية
                delay(600)
                verifySessionIdentity(port, targetUserAgent)

            } catch (e: Exception) {
                e.printStackTrace()
                addLog("❌ خطأ تشغيل البروكسي: ${e.message}")
                _isServerRunning.value = false
            }
        }
    }

    /**
     * فحص تلقائي يمر عبر البروكسي المحلي (ومنه إلى نفق Xray إن وُجد)
     * للتحقق من الـ IP الخارجي، الدولة، مركز البيانات، وإصدار التشفير.
     */
    suspend fun verifySessionIdentity(proxyPort: Int, userAgent: String? = null) {
        withContext(Dispatchers.IO) {
            try {
                addLog("🔍 جاري فحص هوية الجلسة الخارجية ونظافة النفق...")

                val localProxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", proxyPort))
                val url = URL("https://www.cloudflare.com/cdn-cgi/trace")
                val connection = (url.openConnection(localProxy) as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 8000
                    useCaches = false
                    if (userAgent != null) {
                        setRequestProperty("User-Agent", userAgent)
                    }
                }

                if (connection.responseCode == 200) {
                    val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                    val traceMap = responseText.lines()
                        .mapNotNull { line ->
                            val parts = line.split("=", limit = 2)
                            if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
                        }.toMap()

                    val externalIp = traceMap["ip"] ?: "غير معروف"
                    val countryCode = traceMap["loc"] ?: "غير معروف"
                    val dataCenter = traceMap["colo"] ?: "غير معروف"
                    val tlsVersion = traceMap["tls"] ?: "غير معروف"

                    addLog("🌍 هوية الجلسة النشطة: IP [$externalIp] | الدولة [$countryCode] | المركز [$dataCenter] | التشفير [$tlsVersion]")
                } else {
                    addLog("⚠️ استجاب الفحص بكود غير متوقع: ${connection.responseCode}")
                }
                connection.disconnect()
            } catch (e: Exception) {
                addLog("⚠️ تعذر إكمال فحص الـ IP الخارجي (تأكد من اتصال الإنترنت أو صلاحية عقدة VLESS): ${e.message}")
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
