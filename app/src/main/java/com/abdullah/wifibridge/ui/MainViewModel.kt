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
                    
                    val shizukuStatus = shizukuManager.checkShizukuStatus()
                    
                    when (shizukuStatus) {
                        -1, -2 -> {
                            proxyManager.addLog("⏳ جاري انتظار إيقاظ Shizuku (قد يستغرق بضع ثوانٍ)...")
                            
                            // مستمع التقاط الاتصال
                            val binderListener = object : Shizuku.OnBinderReceivedListener {
                                override fun onBinderReceived() {
                                    proxyManager.addLog("✅ تم التقاط إشارة Shizuku بنجاح!")
                                    Shizuku.removeBinderReceivedListener(this)
                                    
                                    if (shizukuManager.checkShizukuStatus() == 1) {
                                        executeShizukuCommands()
                                    } else {
                                        requestShizukuPermissionAsync()
                                    }
                                }
                            }
                            
                            Shizuku.addBinderReceivedListener(binderListener)
                            
                            // مهلة 5 ثوانٍ للإلغاء إذا لم يتصل
                            viewModelScope.launch {
                                delay(5000)
                                if (shizukuManager.checkShizukuStatus() < 0) {
                                    proxyManager.addLog("❌ فشل الاتصال. يرجى تفعيل (التشغيل التلقائي) للتطبيق من إعدادات الهاتف.")
                                    Shizuku.removeBinderReceivedListener(binderListener)
                                }
                            }
                        }
                        0 -> {
                            requestShizukuPermissionAsync()
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

    // دالة منفصلة لطلب الصلاحية بشكل غير متزامن
    private fun requestShizukuPermissionAsync() {
        proxyManager.addLog("⚠️ التطبيق يحتاج صلاحية Shizuku. يرجى الموافقة على النافذة المنبثقة...")
        val listener = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    proxyManager.addLog("✅ تم منح الصلاحية! جاري تطبيق قواعد التخفي...")
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

    // دالة منفصلة لتنفيذ الأوامر
    private fun executeShizukuCommands() {
        viewModelScope.launch(Dispatchers.IO) {
            proxyManager.addLog("⏳ جاري توجيه طلبات DNS وتشفيرها...")
            
            val isShizukuSuccess = shizukuManager.setupCleanSessionNetwork(
                newIp = _currentIp.value, 
                newMac = _currentMac.value,
                dnsIp = _currentDns.value
            )
            
            delay(1000)
            
            if (isShizukuSuccess) {
                proxyManager.addLog("✅ نجاح: تم توجيه طلبات DNS إجبارياً إلى ${_currentDns.value}")
            } else {
                proxyManager.addLog("❌ فشل في تطبيق أوامر iptables.")
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
