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
import kotlinx.coroutines.withContext
import kotlin.random.Random

class MainViewModel : ViewModel() {
    private val _currentIp = MutableStateFlow("192.168.49.1")
    val currentIp: StateFlow<String> = _currentIp

    private val _currentPort = MutableStateFlow(8080)
    val currentPort: StateFlow<Int> = _currentPort

    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive

    private val _networkName = MutableStateFlow("")
    val networkName: StateFlow<String> = _networkName
    
    private val _networkPassword = MutableStateFlow("")
    val networkPassword: StateFlow<String> = _networkPassword

    private val _targetDeviceName = MutableStateFlow("WiFiBridge-0000")
    val targetDeviceName: StateFlow<String> = _targetDeviceName

    private var wifiDirectManager: WifiDirectManager? = null
    private val proxyManager = ProxyServerManager()
    private val shizukuManager = ShizukuManager()

    val proxyLogs: StateFlow<List<String>> = proxyManager.serverLogs

    fun generateCleanSessionParams() {
        _currentIp.value = "192.168.49.1"
        _currentPort.value = Random.nextInt(10000, 60000)
        
        val randomString = (1..4)
            .map { "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".random() }
            .joinToString("")
        _targetDeviceName.value = "Bridge-$randomString"
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
                    
                    // التعديل الجذري: تشغيل البروكسي فوراً دون انتظار شروط Shizuku
                    proxyManager.startServer(_currentPort.value)
                    
                    // استدعاء Shizuku في الخلفية (في حال احتجناه لاحقاً) دون أن يوقف البروكسي
                    viewModelScope.launch(Dispatchers.IO) {
                        shizukuManager.setupCleanSessionNetwork(_currentIp.value)
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
        // الاستدعاء هنا أيضاً يتم بدون context
        proxyManager.stopServer()
        _isSessionActive.value = false
        _networkName.value = ""
        _networkPassword.value = ""
    }
}
