package com.abdullah.wifibridge.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import com.abdullah.wifibridge.data.network.ProxyServerManager
import com.abdullah.wifibridge.data.network.WifiDirectManager
import com.abdullah.wifibridge.data.shizuku.ShizukuManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.random.Random

class MainViewModel : ViewModel() {
    private val _currentIp = MutableStateFlow("10.0.0.1")
    val currentIp: StateFlow<String> = _currentIp

    private val _currentPort = MutableStateFlow(8080)
    val currentPort: StateFlow<Int> = _currentPort

    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive

    // متغيرات جديدة لحفظ بيانات الشبكة
    private val _networkName = MutableStateFlow("")
    val networkName: StateFlow<String> = _networkName
    
    private val _networkPassword = MutableStateFlow("")
    val networkPassword: StateFlow<String> = _networkPassword

    private var wifiDirectManager: WifiDirectManager? = null
    private val proxyManager = ProxyServerManager()
    private val shizukuManager = ShizukuManager()

    fun generateCleanSessionParams() {
        val x = Random.nextInt(1, 255)
        val y = Random.nextInt(1, 255)
        _currentIp.value = "10.$x.$y.1"
        _currentPort.value = Random.nextInt(10000, 60000)
    }

    fun startSession(context: Context) {
        wifiDirectManager = WifiDirectManager(context)
        
        // 1. إنشاء شبكة الواي فاي
        wifiDirectManager?.startBroadcast(
            onSuccess = { name, password ->
                _networkName.value = name
                _networkPassword.value = password
                
                // 2. تطبيق Shizuku وتغيير IP/MAC
                val isShizukuReady = shizukuManager.setupCleanSessionNetwork(_currentIp.value)
                
                // 3. تشغيل البروكسي
                if (isShizukuReady) {
                    proxyManager.startServer(_currentPort.value)
                }
                _isSessionActive.value = true
            },
            onFailure = { error ->
                println("Error starting network: $error")
                _isSessionActive.value = false
            }
        )
    }

    fun stopSession() {
        wifiDirectManager?.stopBroadcast()
        proxyManager.stopServer()
        _isSessionActive.value = false
        _networkName.value = ""
        _networkPassword.value = ""
    }
}
