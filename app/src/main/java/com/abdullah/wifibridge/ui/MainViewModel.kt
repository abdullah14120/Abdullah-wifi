package com.abdullah.wifibridge.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abdullah.wifibridge.data.network.KeepAliveManager
import com.abdullah.wifibridge.data.network.ProxyServerManager
import com.abdullah.wifibridge.data.network.WifiDirectManager
import com.abdullah.wifibridge.data.network.XrayManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

class MainViewModel : ViewModel() {

    val currentIp = MutableStateFlow("192.168.49.1")
    val currentPort = MutableStateFlow(8080)

    val selectedDnsProvider = MutableStateFlow("Cloudflare")
    val customDohUrl = MutableStateFlow("")
    val vlessNodeUri = MutableStateFlow("")
    val isSniFragmentationEnabled = MutableStateFlow(false)
    
    // القيمة المبدئية لتوليد بصمة متصفح وجهاز مختلفة تلقائياً في كل جلسة
    val spoofedDevice = MutableStateFlow("تلقائي (متغير لكل جلسة)")

    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive

    val networkName = MutableStateFlow("")
    val networkPassword = MutableStateFlow("")

    private var wifiDirectManager: WifiDirectManager? = null
    private val proxyManager = ProxyServerManager()
    private var xrayManager: XrayManager? = null
    private var keepAliveManager: KeepAliveManager? = null

    val proxyLogs: StateFlow<List<String>> = proxyManager.serverLogs

    init {
        currentPort.value = Random.nextInt(10000, 60000)
    }

    fun generateCleanSessionParams() {
        if (_isSessionActive.value) return
        currentIp.value = "192.168.49.1"
        currentPort.value = Random.nextInt(10000, 60000)
        proxyManager.addLog("♻️ تم توليد منفذ جلسة جديد ومعزول: ${currentPort.value}")
    }

    fun startSession(context: Context) {
        wifiDirectManager = WifiDirectManager(context)
        xrayManager = XrayManager(context)
        keepAliveManager = KeepAliveManager(context)
        keepAliveManager?.acquireWakeLock()

        viewModelScope.launch {
            proxyManager.addLog("⏳ جاري تهيئة شبكة P2P المعزولة...")
            delay(500)

            wifiDirectManager?.startBroadcast(
                onSuccess = { name, password ->
                    networkName.value = name
                    networkPassword.value = password
                    _isSessionActive.value = true
                    proxyManager.addLog("✅ تم بث الشبكة بنجاح.")

                    viewModelScope.launch {
                        // فحص ما إذا كان المستخدم قد أدخل عقدة VLESS
                        val hasVlessUri = vlessNodeUri.value.trim().startsWith("vless://")
                        var xrayStarted = false

                        if (hasVlessUri) {
                            xrayStarted = xrayManager?.startCore(vlessNodeUri.value) { msg ->
                                proxyManager.addLog(msg)
                            } ?: false
                        }

                        // تشغيل البروكسي وتوجيهه إلى Xray إذا نجح تشغيل النواة
                        proxyManager.startServer(
                            port = currentPort.value,
                            dnsProvider = selectedDnsProvider.value,
                            customDohUrl = customDohUrl.value,
                            enableSniFragmentation = isSniFragmentationEnabled.value,
                            useXrayUpstream = xrayStarted,
                            xrayLocalPort = xrayManager?.localPort ?: 10808,
                            spoofedDevice = spoofedDevice.value
                        )
                    }
                },
                onFailure = { error ->
                    proxyManager.addLog("❌ فشل بدء البث: $error")
                    _isSessionActive.value = false
                    keepAliveManager?.releaseWakeLock()
                }
            )
        }
    }

    fun stopSession(context: Context) {
        wifiDirectManager?.stopBroadcast()
        proxyManager.stopServer()
        xrayManager?.stopCore { msg -> proxyManager.addLog(msg) }
        keepAliveManager?.releaseWakeLock()

        _isSessionActive.value = false
        // تدوير المنفذ تلقائياً استعداداً للجلسة النظيفة القادمة
        currentPort.value = Random.nextInt(10000, 60000)
        proxyManager.addLog("🛑 تم إيقاف الجلسة وتفريغ الأنفاق وتجهيز منفذ جديد (${currentPort.value}).")
    }
}
