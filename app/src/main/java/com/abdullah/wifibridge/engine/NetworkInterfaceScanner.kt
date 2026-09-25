package com.abdullah.wifibridge.engine

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

object NetworkInterfaceScanner {
    private const val TAG = "InterfaceScanner"

    /**
     * استشعار وفحص البطاقة المسؤولة عن الخروج للإنترنت (مثل rmnet_data0 أو wlan0)
     */
    fun findActiveWanInterface(): String? {
        try {
            val process = Runtime.getRuntime().exec("su -c ip route show")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                // البحث عن السطر الذي يحتوي على كلمة "default via" أو "dev"
                if (line!!.contains("default")) {
                    val parts = line!!.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    for (i in parts.indices) {
                        if (parts[i] == "dev" && i + 1 < parts.size) {
                            Log.d(TAG, "Found active WAN interface: ${parts[i + 1]}")
                            return parts[i + 1]
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning WAN interface: ${e.message}")
        }
        // قيمة افتراضية احتياطية لمعالجات كوالكوم في حال لم ينجح الاستشعار التلقائي
        return "rmnet_data0"
    }

    /**
     * تحديد بطاقة نقطة البث الداخلية (Hotspot Interface) في أندرويد 8
     */
    fun findHotspotInterface(): String {
        // عادة في أندرويد 8-9 تكون واجهة البث ap0 أو wlan1
        val possibleInterfaces = listOf("ap0", "swlan0", "wlan1", "softap0")
        try {
            val process = Runtime.getRuntime().exec("su -c ip link show")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                for (iface in possibleInterfaces) {
                    if (line!!.contains(iface)) {
                        Log.d(TAG, "Found active Hotspot interface: $iface")
                        return iface
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning Hotspot interface: ${e.message}")
        }
        return "ap0" // القيمة الأكثر شيوعاً
    }
}
