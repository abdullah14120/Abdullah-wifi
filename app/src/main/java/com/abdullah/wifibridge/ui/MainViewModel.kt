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
    // تم تثبيت الـ IP على الافتراضي الخاص بـ Wi-Fi Direct لتجنب تعارض مسارات الإنترنت (DHCP)
    private val _currentIp = MutableStateFlow("192.168.49.1")
    val currentIp: StateFlow<String> = _currentIp

    private val _currentPort = MutableStateFlow(8080)
    val currentPort: StateFlow<Int> = _currentPort

    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive

    // متغيرات حفظ بيانات الشبكة
    private val _networkName = MutableStateFlow("")
    val networkName: StateFlow<String> = _networkName
    
    private val _networkPassword = MutableStateFlow("")
    val networkPassword: StateFlow<String> = _networkPassword

    // متغير حفظ الاسم العشوائي المستهدف
    private val _targetDeviceName = MutableStateFlow("WiFiBridge-0000")
    val targetDeviceName: StateFlow<String> = _targetDeviceName

    private var wifiDirectManager: WifiDirectManager? = null
    private val proxyManager = ProxyServerManager()
    private val shizukuManager = ShizukuManager()

    fun generateCleanSessionParams() {
        // 1. تأكيد تثبيت الـ IP لتجنب انقطاع التوجيه
        _currentIp.value = "192.168.49.1"
        
        // 2. الحفاظ على عشوائية المنفذ فقط (لحماية الجلسة وتجنب بصمة التطبيق الثابتة)
        _currentPort.value = Random.nextInt(10000, 60000)
        
        // 3. توليد اسم جهاز/شبكة عشوائي (مثال: Bridge-7X2A)
        val randomString = (1..4)
            .map { "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".random() }
            .joinToString("")
        _targetDeviceName.value = "Bridge-$randomString"
    }

    fun startSession(context: Context) {
        wifiDirectManager = WifiDirectManager(context)
        
        // تشغيل العمليات في الكوروتين (Coroutine) لمنع تجميد واجهة المستخدم
        viewModelScope.launch {
            // 0. تغيير اسم الجهاز عبر Shizuku (في مسار IO للخلفية لأنه أمر Shell)
            withContext(Dispatchers.IO) {
                shizukuManager.randomizeDeviceName(_targetDeviceName.value)
            }
            
            // الانتظار قليلاً باستخدام delay الآمنة ليتمكن النظام من استيعاب الاسم الجديد
            delay(500)
            
            // 1. إنشاء شبكة الواي فاي
            wifiDirectManager?.startBroadcast(
                onSuccess = { name, password ->
                    _networkName.value = name
                    _networkPassword.value = password
                    _isSessionActive.value = true // تحديث الواجهة فوراً
                    
                    // 2. تطبيق Shizuku وتشغيل البروكسي في مسار الخلفية
                    viewModelScope.launch(Dispatchers.IO) {
                        val isShizukuReady = shizukuManager.setupCleanSessionNetwork(_currentIp.value)
                        
                        // 3. تشغيل البروكسي وتمرير الـ context لربط الشبكة بنجاح
                        if (isShizukuReady) {
                            proxyManager.startServer(_currentPort.value, context)
                        }
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
        // تمرير الـ context لدالة الإيقاف
        proxyManager.stopServer(context)
        _isSessionActive.value = false
        _networkName.value = ""
        _networkPassword.value = ""
    }
}
