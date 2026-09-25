package com.abdullah.wifibridge.engine

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * NetworkInterfaceScanner - كاشف واجهات الشبكة الذكي والمستقل.
 * يقوم بفحص بيئة لينكس الداخلية عبر الروت لتحديد بطاقة الإنترنت الخارجي (WAN) وبطاقة البث (LAN/Hotspot) ديناميكياً.
 */
object NetworkInterfaceScanner {

    private const val TAG = "InterfaceScanner"

    /**
     * استشعار بطاقة الإنترنت الخارجي النشطة حالياً (Cellular Data أو Wi-Fi الأساسي)
     */
    fun getActiveWanInterface(): String? {
        var wanInterface: String? = null
        try {
            val process = Runtime.getRuntime().exec("su -c ip route show")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            
            while (reader.readLine().also { line = it } != null) {
                // البحث عن السطر الذي يحتوي على مسار الخروج الافتراضي (default via ... dev ...)
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

        // إذا لم يتم العثور عليها تلقائياً، نعود للقيم الشائعة كخيار احتياطي
        if (wanInterface.isNullOrEmpty()) {
            wanInterface = "rmnet_data0"
            Log.w(TAG, "تعذر تحديد WAN بدقة، استخدام القيمة الاحتياطية: $wanInterface")
        } else {
            Log.i(TAG, "تم بنجاح رصد واجهة الإنترنت الخارجي: $wanInterface")
        }
        
        return wanInterface
    }

    /**
     * تحديد بطاقة نقطة البث الداخلية (Hotspot Interface) حسب إصدار أندرويد
     */
    fun getHotspotInterface(): String {
        val possibleHotspots = arrayOf("ap0", "swlan0", "wlan1", "softap0", "ap-wlan0")
        try {
            val process = Runtime.getRuntime().exec("su -c ip link show")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            
            while (reader.readLine().also { line = it } != null) {
                for (iface in possibleHotspots) {
                    if (line!!.contains(iface)) {
                        Log.i(TAG, "تم رصد واجهة البث النشطة: $iface")
                        return iface
                    }
                }
            }
            reader.close()
            process.waitFor()
        } catch (e: Exception) {
            Log.e(TAG, "خطأ أثناء استشعار واجهة Hotspot: ${e.message}")
        }
        
        // القيمة الافتراضية الأكثر اعتماداً في أندرويد
        return "ap0"
    }
}
