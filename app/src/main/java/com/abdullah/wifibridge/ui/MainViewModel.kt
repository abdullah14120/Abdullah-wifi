package com.abdullah.wifibridge.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abdullah.wifibridge.data.network.KeepAliveManager
import com.abdullah.wifibridge.data.network.WifiDirectManager
import com.abdullah.wifibridge.data.network.RootSuperManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

class MainViewModel : ViewModel() {
    // 🚀 يمكنك هنا السماح للمستخدم بتعديل הـ IP والمنفذ بحرية
    private val _currentIp = MutableStateFlow("10.0.0.1") // استخدمنا 10.0.0.1 لتمييز الشبكة المروتة
    val currentIp: StateFlow<String> = _currentIp

    private val _currentPort = MutableStateFlow(8080) // لم نعد بحاجة إليه كبروكسي ولكن يمكن استخدامه كمنفذ مخصص
    val currentPort: StateFlow<Int> = _currentPort

    private val _currentMac = MutableStateFlow("")
    
    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive

    private val _networkName = MutableStateFlow("")
    val networkName: StateFlow<String> = _networkName
    
    private val _networkPassword = MutableStateFlow("")
    val networkPassword: StateFlow<String> = _networkPassword

    // سجلات النظام لعرض حالة الروت والاتصال للمستخدم
    private val _systemLogs = MutableStateFlow<List<String>>(emptyList())
    val proxyLogs: StateFlow<List<String>> = _systemLogs

    private var wifiDirectManager: WifiDirectManager? = null
    // قمنا باستبدال البروكسي العادي بالمدير الخارق (Root)
    private val rootSuperManager = RootSuperManager()
    private var keepAliveManager: KeepAliveManager? = null

    init {
        // توليد ماك أدرس عشوائي أولي عند فتح التطبيق
        generateRandomMac()
    }

    private fun generateRandomMac() {
        val macBytes = ByteArray(5)
        Random.nextBytes(macBytes)
        _currentMac.value = String.format("02:%02x:%02x:%02x:%02x:%02x", 
            macBytes[0], macBytes[1], macBytes[2], macBytes[3], macBytes[4])
    }

    fun generateCleanSessionParams() {
        // يمكنك هنا جعل الآي بي عشوائي إذا أردت، أو تركه 10.0.0.1
        _currentIp.value = "10.0.0.1" 
        _currentPort.value = Random.nextInt(10000, 60000)
        generateRandomMac()
        addLog("♻️ تم توليد إعدادات جلسة جديدة.")
    }

    fun startSession(context: Context) {
        wifiDirectManager = WifiDirectManager(context)
        keepAliveManager = KeepAliveManager(context)
        keepAliveManager?.acquireWakeLock()
        
        viewModelScope.launch {
            addLog("⏳ جاري تهيئة شبكة P2P...")
            delay(500)
            
            wifiDirectManager?.startBroadcast(
                onSuccess = { name, password ->
                    _networkName.value = name
                    _networkPassword.value = password
                    _isSessionActive.value = true 
                    
                    addLog("✅ تم بث الشبكة بنجاح.")
                    
                    // 🚀 تفعيل سحر الروت بعد بث الشبكة
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
                addLog("✅ تم العثور على صلاحيات الروت. جاري بناء الجلسة الخارقة...")
                
                // إضافة مهلة صغيرة للسماح لشبكة p2p0 بالاستقرار قبل حقنها
                delay(1500)
                
                val isSuccess = rootSuperManager.enableSuperSession(
                    dnsIp = "1.1.1.1", // سيرفر كلاودفلير
                    newMac = _currentMac.value,
                    newGatewayIp = _currentIp.value
                )
                
                if (isSuccess) {
                    addLog("🚀 تفعيل ناجح! تم فرض IP: ${_currentIp.value} و MAC: ${_currentMac.value}")
                    addLog("🛡️ الشبكة الآن مشفرة (DNS) ومخفية (TTL) بالكامل.")
                    addLog("💡 أطلب من المتصل تعيين IP هاتفه إلى 10.0.0.2 والبوابة 10.0.0.1 (ثابت).")
                } else {
                    addLog("❌ فشل تطبيق قواعد الروت. تأكد من منح الصلاحية (SU).")
                }
            } else {
                addLog("❌ خطأ: جهازك لا يحتوي على صلاحيات الروت (SU). لا يمكن تفعيل الميزات الخارقة.")
            }
        }
    }

    fun stopSession(context: Context) {
        wifiDirectManager?.stopBroadcast()
        
        // تنظيف نواة لينكس من أوامرنا
        viewModelScope.launch(Dispatchers.IO) {
            rootSuperManager.disableSuperSession(dnsIp = "1.1.1.1")
        }
        
        keepAliveManager?.releaseWakeLock()
        
        _isSessionActive.value = false
        _networkName.value = ""
        _networkPassword.value = ""
        
        addLog("🛑 تم إيقاف الشبكة وتنظيف النظام.")
    }

    private fun addLog(message: String) {
        val logs = _systemLogs.value.toMutableList()
        logs.add(0, message)
        if (logs.size > 100) logs.removeLast()
        _systemLogs.value = logs
    }
}
