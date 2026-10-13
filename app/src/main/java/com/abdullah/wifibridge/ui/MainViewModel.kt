package com.abdullah.wifibridge.ui

import android.content.Context
import android.content.pm.PackageManager
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
import rikka.shizuku.Shizuku
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
                    
                    val shizukuStatus = shizukuManager.checkShizukuStatus()
                    
                    when (shizukuStatus) {
                        -1 -> {
                            proxyManager.addLog("❌ Shizuku لا يستجيب (الاتصال مقطوع).")
                        }
                        0 -> {
                            proxyManager.addLog("⚠️ جاري طلب صلاحية Shizuku...")
                            val listener = object : Shizuku.OnRequestPermissionResultListener {
                                override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                                    if (grantResult == PackageManager.PERMISSION_GRANTED) {
                                        proxyManager.addLog("✅ تم منح الصلاحية بنجاح.")
                                        executeShizukuCommands()
                                    } else {
                                        proxyManager.addLog("❌ تم رفض الصلاحية من قبل المستخدم.")
                                    }
                                    Shizuku.removeRequestPermissionResultListener(this)
                                }
                            }
                            Shizuku.addRequestPermissionResultListener(listener)
                            shizukuManager.requestPermission(100)
                        }
                        1 -> {
                            proxyManager.addLog("✅ Shizuku متصل ومصرح به مسبقاً.")
                            executeShizukuCommands()
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

    private fun executeShizukuCommands() {
        viewModelScope.launch(Dispatchers.IO) {
            proxyManager.addLog("⏳ جاري توجيه طلبات DNS وتشفيرها...")
            
            // إعطاء مهلة ثانيتين لضمان إنشاء واجهة p2p0 بالكامل قبل إرسال الأوامر
            delay(2000)
            
            val resultMessage = shizukuManager.setupCleanSessionNetwork(
                dnsIp = _currentDns.value
            )
            
            if (resultMessage == "SUCCESS") {
                proxyManager.addLog("✅ نجاح: تم توجيه طلبات DNS إجبارياً إلى ${_currentDns.value}")
            } else {
                // سيتم هنا طباعة الخطأ الحقيقي القادم من نواة النظام
                proxyManager.addLog("❌ فشل الأوامر - $resultMessage")
            }
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
