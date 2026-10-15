package com.abdullah.wifibridge.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abdullah.wifibridge.data.network.KeepAliveManager
import com.abdullah.wifibridge.data.network.ProxyServerManager
import com.abdullah.wifibridge.data.network.WifiDirectManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

class MainViewModel : ViewModel() {
    
    // إعدادات البوابة والمنفذ
    val currentIp = MutableStateFlow("192.168.49.1")
    val currentPort = MutableStateFlow(8080)

    // إعدادات التخفي والـ Anti-DPI
    val selectedDnsProvider = MutableStateFlow("Cloudflare")
    val customDohUrl = MutableStateFlow("")
    val vlessNodeUri = MutableStateFlow("")
    val isSniFragmentationEnabled = MutableStateFlow(false)

    // حالة الجلسة
    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive

    // بيانات شبكة Wi-Fi Direct
    val networkName = MutableStateFlow("")
    val networkPassword = MutableStateFlow("")

    private var wifiDirectManager: WifiDirectManager? = null
    private val proxyManager = ProxyServerManager()
    private var keepAliveManager: KeepAliveManager? = null

    // سجلات النظام (Proxy Logs)
    val proxyLogs: StateFlow<List<String>> = proxyManager.serverLogs

    init {
        // تعيين بورت عشوائي مبدئي
        currentPort.value = Random.nextInt(10000, 60000)
    }

    // توليد بيانات جديدة
    fun generateCleanSessionParams() {
        if (_isSessionActive.value) return
        currentIp.value = "192.168.49.1"
        currentPort.value = Random.nextInt(10000, 60000)
        proxyManager.addLog("♻️ تم تعيين منفذ وكيل (Proxy Port) جديد عشوائياً: ${currentPort.value}")
    }

    fun startSession(context: Context) {
        wifiDirectManager = WifiDirectManager(context)
        keepAliveManager = KeepAliveManager(context)
        keepAliveManager?.acquireWakeLock()
        
        viewModelScope.launch {
            proxyManager.addLog("⏳ جاري تهيئة شبكة P2P...")
            delay(500)
            
            // تمرير الاسم وكلمة المرور إذا كتبهما المستخدم
            wifiDirectManager?.startBroadcast(
                customSsid = networkName.value,
                customPassword = networkPassword.value,
                onSuccess = { name, password ->
                    networkName.value = name
                    networkPassword.value = password
                    _isSessionActive.value = true 
                    
                    proxyManager.addLog("✅ تم بث الشبكة بنجاح.")
                    
                    // تمرير الإعدادات المتقدمة إلى ProxyServerManager وتشغيله
                    proxyManager.startServer(
                        port = currentPort.value,
                        dnsProvider = selectedDnsProvider.value,
                        customDohUrl = customDohUrl.value,
                        enableSniFragmentation = isSniFragmentationEnabled.value,
                        vlessUri = vlessNodeUri.value // تمهيداً للإصدارات القادمة
                    )
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
        keepAliveManager?.releaseWakeLock()
        
        _isSessionActive.value = false
        proxyManager.addLog("🛑 تم إيقاف الجلسة والشبكة بنجاح.")
    }
}
