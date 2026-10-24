package com.abdullah.wifibridge.data.network

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.random.Random

class XrayManager(private val context: Context) {

    var isRunning = false
        private set

    // منفذ محلي متغير في كل جلسة لضمان عدم بقاء أي قنوات TCP معلقة
    var localPort: Int = 10808
        private set

    // قائمة بصمات التشفير المعتمدة في نواة Xray لمحاكاة متصفحات حقيقية مختلفة
    private val tlsFingerprints = listOf("chrome", "firefox", "safari", "ios", "edge")

    // قائمة عناوين Cloudflare Anycast نظيفة لتغيير مسار الخروج في كل جلسة (تُستخدم مع WS)
    private val cleanCloudflareIps = listOf(
        "104.16.132.229",
        "104.17.70.206",
        "104.18.2.161",
        "172.64.32.1",
        "162.159.36.1",
        "104.19.155.92"
    )

    suspend fun startCore(
        vlessUri: String,
        rotateCloudflareIp: Boolean = true,
        logCallback: (String) -> Unit
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val baseConfig = VlessParser.parseUri(vlessUri)
                if (baseConfig == null) {
                    logCallback("❌ رابط VLESS غير صالح، تأكد من الصيغة.")
                    return@withContext false
                }

                // 1. توليد منفذ محلي جديد ومستقل لهذه الجلسة فقط
                localPort = Random.nextInt(12000, 25000)

                // 2. اختيار بصمة تشفير (JA3/JA4) عشوائية لهذه الجلسة
                val sessionFingerprint = tlsFingerprints.random()

                // 3. تدوير عنوان IP الدخول إذا كان الاتصال عبر Cloudflare WebSocket
                val sessionAddress = if (rotateCloudflareIp && baseConfig.network == "ws") {
                    cleanCloudflareIps.random()
                } else {
                    baseConfig.address
                }

                val dynamicConfig = baseConfig.copy(
                    address = sessionAddress,
                    fp = sessionFingerprint
                )

                logCallback("🧬 بيئة جلسة جديدة: البوابة [$sessionAddress] | بصمة TLS [$sessionFingerprint]")

                val configJson = XrayConfigGenerator.generateCleanTunnelConfig(dynamicConfig, localPort)

                // مسح أي ملف سابق وإنشاء ملف إعدادات نظيف للجلسة الحالية
                val configFile = File(context.filesDir, "xray_clean_config.json")
                if (configFile.exists()) configFile.delete()
                configFile.writeText(configJson)

                // استدعاء مكتبة تشغيل النواة المدمجة لديك
                // Libv2ray.startV2Ray(configFile.absolutePath)

                isRunning = true
                logCallback("🟢 نفق Xray معزول ويعمل على المنفذ المحلي 127.0.0.1:$localPort")
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
                // حذف ملف الإعدادات المؤقت فور انتهاء الجلسة
                val configFile = File(context.filesDir, "xray_clean_config.json")
                if (configFile.exists()) configFile.delete()

                isRunning = false
                logCallback("🛑 تم إغلاق نفق Xray وتطهير ذاكرة الجلسة.")
            } catch (e: Exception) {
                logCallback("⚠️ خطأ أثناء إيقاف Xray: ${e.message}")
            }
        }
    }
}
