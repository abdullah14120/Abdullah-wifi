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
        // 1. توليد IP جديد (مثال: 192.168.75.1)
        val randomSubnet = Random.nextInt(10, 250)
        _currentIp.value = "192.168.$randomSubnet.1"
        
        // 2. توليد منفذ جديد
        _currentPort.value = Random.nextInt(10000, 60000)
        
        // 3. توليد MAC Address آمن محلياً (يبدأ بـ 02 لتجنب أخطاء النظام)
        val macBytes = ByteArray(5)
        Random.nextBytes(macBytes)
        _currentMac.value = String.format("02:%02x:%02x:%02x:%02x:%02x", 
            macBytes[0], macBytes[1], macBytes[2], macBytes[3], macBytes[4])

        // 4. اختيار DNS عشوائي بين Cloudflare و Google
        _currentDns.value = listOf("1.1.1.1", "8.8.8.8").random()
    }

    fun startSession(context: Context) {
        wifiDirectManager = WifiDirectManager(context)
        
        viewModelScope.launch {
            delay(500)
            
            wifiDirectManager?.startBroadcast(
                onSuccess = { name, password ->
                    _networkName.value = name
                    _networkPassword.value = password
                    _isSessionActive.value = true 
                    
                    // تشغيل البروكسي فوراً
                    proxyManager.startServer(_currentPort.value)
                    
                    // استدعاء أوامر Shizuku لتغيير الـ MAC، الـ IP، والـ DNS في الخلفية
                    viewModelScope.launch(Dispatchers.IO) {
                        shizukuManager.setupCleanSessionNetwork(
                            newIp = _currentIp.value,
                            newMac = _currentMac.value,
                            dnsIp = _currentDns.value
                        )
                    }
                },
                onFailure = { error ->
                    println("Error starting network: $error")
                    _isSessionActive.value = false
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
