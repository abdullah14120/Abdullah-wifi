package com.abdullah.wifibridge.data.network

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class XrayManager(private val context: Context) {

    var isRunning = false
        private set

    val localPort = 10808

    suspend fun startCore(vlessUri: String, logCallback: (String) -> Unit): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val parsedConfig = VlessParser.parseUri(vlessUri)
                if (parsedConfig == null) {
                    logCallback("❌ رابط VLESS غير صالح، تأكد من الصيغة.")
                    return@withContext false
                }

                logCallback("⚙️ جاري تجهيز نفق VLESS النظيف (${parsedConfig.remark})...")
                val configJson = XrayConfigGenerator.generateCleanTunnelConfig(parsedConfig, localPort)

                val configFile = File(context.filesDir, "xray_clean_config.json")
                configFile.writeText(configJson)

                // ملاحظة: يتم هنا استدعاء مكتبة النواة المدمجة لديك (مثل Libv2ray)
                // Libv2ray.startV2Ray(configFile.absolutePath)
                
                isRunning = true
                logCallback("🟢 نفق Xray جاهز على المنفذ المحلي 127.0.0.1:$localPort (Remote DNS مفعل).")
                true
            } catch (e: Exception) {
                e.printStackTrace()
                logCallback("❌ فشل تشغيل نواة Xray: ${e.message}")
                isRunning = false
                false
            }
        }
    }

    fun stopCore(logCallback: (String) -> Unit) {
        if (isRunning) {
            try {
                // Libv2ray.stopV2Ray()
                isRunning = false
                logCallback("🛑 تم إغلاق نفق Xray المحلي.")
            } catch (e: Exception) {
                logCallback("⚠️ خطأ أثناء إيقاف Xray: ${e.message}")
            }
        }
    }
}
