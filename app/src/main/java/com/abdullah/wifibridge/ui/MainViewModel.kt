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
    
    val currentIp = MutableStateFlow("192.168.49.1")
    val currentPort = MutableStateFlow(8080)

    val selectedDnsProvider = MutableStateFlow("Cloudflare")
    val customDohUrl = MutableStateFlow("")
    val vlessNodeUri = MutableStateFlow("")
    val isSniFragmentationEnabled = MutableStateFlow(false)
    
    // 🚀 المتغير الجديد لتغيير هوية الجهاز
    val spoofedDevice = MutableStateFlow("الافتراضي")

    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive

    val networkName = MutableStateFlow("")
    val networkPassword = MutableStateFlow("")

    private var wifiDirectManager: WifiDirectManager? = null
    private val proxyManager = ProxyServerManager()
    private var keepAliveManager: KeepAliveManager? = null

    val proxyLogs: StateFlow<List<String>> = proxyManager.serverLogs

    init {
        currentPort.value = Random.nextInt(10000, 60000)
    }

    fun generateCleanSessionParams() {
        if (_isSessionActive.value) return
        currentIp.value = "192.168.49.1"
        currentPort.value = Random.nextInt(10000, 60000)
        proxyManager.addLog("♻️ تم تعيين منفذ وكيل جديد: ${currentPort.value}")
    }

    fun startSession(context: Context) {
        wifiDirectManager = WifiDirectManager(context)
        keepAliveManager = KeepAliveManager(context)
        keepAliveManager?.acquireWakeLock()
        
        viewModelScope.launch {
            proxyManager.addLog("⏳ جاري تهيئة شبكة P2P...")
            delay(500)
            
            wifiDirectManager?.startBroadcast(
                onSuccess = { name, password ->
                    networkName.value = name
                    networkPassword.value = password
                    _isSessionActive.value = true 
                    
                    proxyManager.addLog("✅ تم بث الشبكة بنجاح.")
                    
                    proxyManager.startServer(
                        port = currentPort.value,
                        dnsProvider = selectedDnsProvider.value,
                        customDohUrl = customDohUrl.value,
                        enableSniFragmentation = isSniFragmentationEnabled.value,
                        vlessUri = vlessNodeUri.value,
                        spoofedDevice = spoofedDevice.value // 🚀 تمرير الهوية للبروكسي
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
