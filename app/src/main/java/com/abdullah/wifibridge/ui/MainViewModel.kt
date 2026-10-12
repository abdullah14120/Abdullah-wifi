package com.abdullah.wifibridge.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    val proxyLogs: StateFlow<List<String>> = proxyManager.serverLogs

    fun generateCleanSessionParams() {
        // العودة للـ IP القياسي المضمون 100% في أندرويد لشبكات P2P
        _currentIp.value = "192.168.49.1"
        
        _currentPort.value = Random.nextInt(10000, 60000)
        
        // إبقاء توليد الماك الآمن
        val macBytes = ByteArray(5)
        Random.nextBytes(macBytes)
        _currentMac.value = String.format("02:%02x:%02x:%02x:%02x:%02x", 
            macBytes[0], macBytes[1], macBytes[2], macBytes[3], macBytes[4])

        // إبقاء اختيار DNS عشوائي
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
                    
                    // تنفيذ أوامر التخفي والتحقق منها
                    viewModelScope.launch(Dispatchers.IO) {
                        proxyManager.addLog("⏳ جاري تطبيق أوامر التخفي (MAC & DNS)...")
                        
                        val isShizukuSuccess = shizukuManager.setupCleanSessionNetwork(
                            newIp = _currentIp.value, // أعدناه إلى 192.168.49.1 سابقاً
                            newMac = _currentMac.value,
                            dnsIp = _currentDns.value
                        )
                        
                        // ننتظر ثانية واحدة لكي يستوعب النظام التغييرات
                        delay(1000)
                        
                        if (isShizukuSuccess) {
                            // قراءة الماك الفعلي بعد التعديل
                            val actualMac = shizukuManager.getActualMacAddress()
                            
                            // التحقق والمطابقة
                            if (actualMac.equals(_currentMac.value, ignoreCase = true)) {
                                proxyManager.addLog("✅ نجاح: تم تغيير الماك أدرس إلى $actualMac")
                            } else {
                                proxyManager.addLog("⚠️ تحذير: الماك الحالي هو $actualMac (لم يتطابق مع المطلوب)")
                            }
                            
                            proxyManager.addLog("✅ نجاح: تم توجيه DNS إجبارياً إلى ${_currentDns.value}")
                        } else {
                            proxyManager.addLog("❌ فشل في تطبيق أوامر التخفي (تأكد من صلاحيات Shizuku).")
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
        _isSessionActive.value = false
        _networkName.value = ""
        _networkPassword.value = ""
    }
}
