package com.abdullah.wifibridge.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abdullah.wifibridge.data.network.KeepAliveManager
import com.abdullah.wifibridge.data.network.ProxyServerManager
import com.abdullah.wifibridge.data.network.WifiDirectManager
import com.abdullah.wifibridge.data.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

class MainViewModel : ViewModel() {
    private val _currentIp = MutableStateFlow("192.168.49.1")
    val currentIp: StateFlow<String> = _currentIp

    private val _currentPort = MutableStateFlow(8080)
    val currentPort: StateFlow<Int> = _currentPort

    private val _currentMac = MutableStateFlow("")
    private val _currentDns = MutableStateFlow("1.1.1.1")

    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive

    private val _networkName = MutableStateFlow("")
    val networkName: StateFlow<String> = _networkName
    
    private val _networkPassword = MutableStateFlow("")
    val networkPassword: StateFlow<String> = _networkPassword

    private var wifiDirectManager: WifiDirectManager? = null
    private val proxyManager = ProxyServerManager()
    private val shizukuManager = ShizukuManager()
    
    private var keepAliveManager: KeepAliveManager? = null

    val proxyLogs: StateFlow<List<String>> = proxyManager.serverLogs

    fun generateCleanSessionParams() {
        _currentIp.value = "192.168.49.1"
        _currentPort.value = Random.nextInt(10000, 60000)
        
        val macBytes = ByteArray(5)
        Random.nextBytes(macBytes)
        _currentMac.value = String.format("02:%02x:%02x:%02x:%02x:%02x", 
            macBytes[0], macBytes[1], macBytes[2], macBytes[3], macBytes[4])

        _currentDns.value = listOf("1.1.1.1", "8.8.8.8").random()
    }

    fun startSession(context: Context) {
        wifiDirectManager = WifiDirectManager(context)
        keepAliveManager = KeepAliveManager(context)
        keepAliveManager?.acquireWakeLock()
        
        viewModelScope.launch {
            delay(500)
            
            wifiDirectManager?.startBroadcast(
                onSuccess = { name, password ->
                    _networkName.value = name
                    _networkPassword.value = password
                    _isSessionActive.value = true 
                    
                    proxyManager.startServer(_currentPort.value)
                    
                    viewModelScope.launch(Dispatchers.IO) {
                        // 1. الفحص الاستباقي لحالة Shizuku
                        proxyManager.addLog("🔍 جاري فحص اتصال Shizuku...")
                        
                        val isShizukuRunning = shizukuManager.isShizukuAvailable()
                        val hasShizukuPermission = shizukuManager.hasPermission()
                        
                        if (!isShizukuRunning) {
                            proxyManager.addLog("❌ خطأ: خدمة Shizuku غير تعمل في هاتفك. يرجى تشغيلها من تطبيق Shizuku أولاً.")
                            return@launch // إيقاف تنفيذ أوامر التخفي
                        }
                        
                        if (!hasShizukuPermission) {
                            proxyManager.addLog("⚠️ تنبيه: التطبيق لا يملك صلاحية Shizuku. جاري طلب الصلاحية...")
                            // هذا سيظهر نافذة منبثقة للمستخدم تطلب الصلاحية
                            shizukuManager.requestPermission(100) 
                            
                            // الانتظار قليلاً لعل المستخدم يوافق
                            delay(3000)
                            
                            if (!shizukuManager.hasPermission()) {
                                proxyManager.addLog("❌ خطأ: لم يتم منح صلاحية Shizuku. لا يمكن تطبيق قواعد التخفي.")
                                return@launch
                            }
                        }
                        
                        // 2. إذا وصلنا إلى هنا، فهذا يعني أن Shizuku يعمل والصلاحية ممنوحة
                        proxyManager.addLog("✅ Shizuku متصل ومصرح به.")
                        proxyManager.addLog("⏳ جاري تطبيق قواعد التخفي (تشفير DNS)...")
                        
                        val isShizukuSuccess = shizukuManager.setupCleanSessionNetwork(
                            newIp = _currentIp.value, 
                            newMac = _currentMac.value,
                            dnsIp = _currentDns.value
                        )
                        
                        delay(1000)
                        
                        if (isShizukuSuccess) {
                            proxyManager.addLog("✅ نجاح: تم توجيه طلبات DNS إجبارياً إلى ${_currentDns.value}")
                        } else {
                            proxyManager.addLog("❌ فشل في تطبيق أوامر iptables (قد يكون النظام يمنع هذا الأمر).")
                        }
                    }
                },
                onFailure = { error ->
                    println("Error starting network: $error")
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
        _networkName.value = ""
        _networkPassword.value = ""
    }
}
