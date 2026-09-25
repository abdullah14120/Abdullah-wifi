package com.abdullah.wifibridge.engine

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * NetworkInterfaceScanner - كاشف واجهات الشبكة الذكي والمستقل (الإصدار المحسن).
 * مُعد خصيصاً لدعم أجهزة كوالكوم وإصدارات أندرويد المختلفة (مثل Redmi 5A - Android 8.1).
 */
object NetworkInterfaceScanner {

    private const val TAG = "InterfaceScanner"

    /**
     * استشعار بطاقة الإنترنت الخارجي النشطة حالياً (WAN)
     * سواء كانت Wi-Fi أو بيانات جوال، بناءً على جدول التوجيه الافتراضي (ip route)
     */
    fun getActiveWanInterface(): String {
        var wanInterface: String? = null
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "ip route show"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            
            while (reader.readLine().also { line = it } != null) {
                // البحث عن السطر الافتراضي المعتمد للخروج (default via ... dev ...)
                if (line!!.contains("default")) {
                    val tokens = line!!.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    for (i in tokens.indices) {
                        if (tokens[i] == "dev" && i + 1 < tokens.size) {
                            wanInterface = tokens[i + 1]
                            break
                        }
                    }
                }
            }
            reader.close()
            process.waitFor()
        } catch (e: Exception) {
            Log.e(TAG, "خطأ أثناء استشعار واجهة WAN: ${e.message}")
        }

        // إذا كان التطبيق يعتمد على استقبال الإنترنت عبر الواي فاي، فالقيمة الافتراضية الآمنة هي wlan0
        if (wanInterface.isNullOrEmpty()) {
            wanInterface = "wlan0"
            Log.w(TAG, "تعذر تحديد WAN، الاعتماد على القيمة الافتراضية للواي فاي: $wanInterface")
        } else {
            Log.i(TAG, "تم بنجاح رصد واجهة الإنترنت الخارجي (WAN): $wanInterface")
        }
        
        return wanInterface
    }

    /**
     * تحديد بطاقة نقطة البث الداخلية (Hotspot Interface)
     * تم تحسينها لتتوافق مع معالجات كوالكوم (Snapdragon) وأندرويد 8.1 حيث تكون wlan0 هي الأساس
     */
    fun getHotspotInterface(): String {
        // القوائم المحتملة لأسماء واجهات البث حسب الأردنرويد والمعالج
        val possibleHotspots = arrayOf("ap0", "wlan0", "swlan0", "wlan1", "softap0", "ap-wlan0")
        val activeInterfaces = mutableListOf<String>()

        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "ip link show"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            
            while (reader.readLine().also { line = it } != null) {
                for (iface in possibleHotspots) {
                    // التحقق من الواجهة التي تحتوي على اسم البطاقة وتكون حالتها نشطة أو قابلة للبث
                    if (line!!.contains(iface)) {
                        activeInterfaces.add(iface)
                    }
                }
            }
            reader.close()
            process.waitFor()
        } catch (e: Exception) {
            Log.e(TAG, "خطأ أثناء استشعار واجهة Hotspot: ${e.message}")
        }
        
        // هندسة خاصة لجهاز Redmi 5A وأندرويد 8.1:
        // غالبًا في أندرويد 8.1 معالجات Snapdragon، تكون wlan0 مسؤولة عن استقبال البث وإرساله معاً عند تفعيل الـ Routing.
        val selectedHotspot = if (activeInterfaces.contains("ap0")) {
            "ap0"
        } else {
            // القيمة المضمونة لهواتف كوالكوم في هذا الإصدار
            "wlan0"
        }

        Log.i(TAG, "تم اعتماد واجهة البث (Hotspot): $selectedHotspot (الواجهات المكتشفة: $activeInterfaces)")
        return selectedHotspot
    }
}
