package com.abdullah.wifibridge.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abdullah.wifibridge.data.network.KeepAliveManager
import com.abdullah.wifibridge.data.network.RootSuperManager
import com.abdullah.wifibridge.data.network.WifiDirectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

class MainViewModel : ViewModel() {
    
    // جعلنا المتغيرات عامة وقابلة للتعديل (Mutable) من واجهة المستخدم
    val currentIp = MutableStateFlow("10.0.0.1")
    val networkName = MutableStateFlow("")
    val networkPassword = MutableStateFlow("")
    
    // إخفاء الماك أدرس حالياً لأنه يسبب مشكلة في المصادقة (Password Rejected)
    // val currentMac = MutableStateFlow("")

    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive

    private val _systemLogs = MutableStateFlow<List<String>>(emptyList())
    val proxyLogs: StateFlow<List<String>> = _systemLogs

    private var wifiDirectManager: WifiDirectManager? = null
    private val rootSuperManager = RootSuperManager()
    private var keepAliveManager: KeepAliveManager? = null

    init {
        generateCleanSessionParams()
    }

    // توليد نصوص عشوائية للاسم وكلمة المرور
    private fun generateRandomString(length: Int): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..length).map { chars.random() }.joinToString("")
    }

    fun generateCleanSessionParams() {
        if (_isSessionActive.value) return
        
        // توليد IP عشوائي في نطاق 10.0.X.1
        val randomSubnet = Random.nextInt(1, 250)
        currentIp.value = "192.168.$randomSubnet.1"
        
        // توليد اسم وكلمة مرور عشوائية
        networkName.value = "DIRECT-${Random.nextInt(10, 99)}-Bridge${generateRandomString(4)}"
        networkPassword.value = generateRandomString(10)
        
        addLog("♻️ تم توليد إعدادات جلسة عشوائية جديدة. يمكنك تعديلها يدوياً.")
    }

    fun startSession(context: Context) {
        wifiDirectManager = WifiDirectManager(context)
        keepAliveManager = KeepAliveManager(context)
        keepAliveManager?.acquireWakeLock()
        
        viewModelScope.launch {
            addLog("⏳ جاري تهيئة شبكة P2P بالبيانات المخصصة...")
            delay(500)
            
            // تمرير البيانات المخصصة التي كتبها المستخدم أو تم توليدها
            wifiDirectManager?.startBroadcast(
                customSsid = networkName.value,
                customPassword = networkPassword.value,
                onSuccess = { name, password ->
                    // تحديث الواجهة بما تم اعتماده فعلياً
                    networkName.value = name
                    networkPassword.value = password
                    _isSessionActive.value = true 
                    
                    addLog("✅ تم بث الشبكة بنجاح.")
                    activateRootFeatures()
                },
                onFailure = { error ->
                    addLog("❌ فشل بث الشبكة: $error")
                    _isSessionActive.value = false
                    keepAliveManager?.releaseWakeLock()
                }
            )
        }
    }

    private fun activateRootFeatures() {
        viewModelScope.launch(Dispatchers.IO) {
            addLog("🔍 جاري التحقق من صلاحيات الروت...")
            
            if (rootSuperManager.checkRootAccess()) {
                delay(1500)
                
                // تم إيقاف تمرير الماك أدرس لتفادي مشكلة رفض كلمة المرور
                val isSuccess = rootSuperManager.enableSuperSession(
                    dnsIp = "1.1.1.1", 
                    newMac = null, 
                    newGatewayIp = currentIp.value
                )
                
                if (isSuccess) {
                    val clientIp = currentIp.value.dropLast(1) + "2" // مثلا 10.0.X.2
                    addLog("🚀 تفعيل ناجح! تم فرض IP: ${currentIp.value}")
                    addLog("💡 أطلب من المتصل تعيين IP هاتفه إلى $clientIp والبوابة ${currentIp.value} (ثابت).")
                } else {
                    addLog("❌ فشل تطبيق قواعد الروت.")
                }
            } else {
                addLog("❌ خطأ: جهازك لا يحتوي على صلاحيات الروت.")
            }
        }
    }

    fun stopSession(context: Context) {
        wifiDirectManager?.stopBroadcast()
        viewModelScope.launch(Dispatchers.IO) {
            rootSuperManager.disableSuperSession(dnsIp = "1.1.1.1")
        }
        keepAliveManager?.releaseWakeLock()
        _isSessionActive.value = false
        addLog("🛑 تم إيقاف الشبكة وتنظيف النظام.")
    }

    private fun addLog(message: String) {
        val logs = _systemLogs.value.toMutableList()
        logs.add(0, message)
        if (logs.size > 100) logs.removeLast()
        _systemLogs.value = logs
    }
}
