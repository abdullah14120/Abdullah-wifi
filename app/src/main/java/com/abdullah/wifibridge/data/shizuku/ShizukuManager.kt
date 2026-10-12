package com.abdullah.wifibridge.data.shizuku

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

class ShizukuManager {

    /**
     * 1: Shizuku يعمل والصلاحية ممنوحة
     * 0: Shizuku يعمل ولكن يحتاج صلاحية
     * -1: Shizuku لا يعمل أو غير متصل
     */
    fun checkShizukuStatus(): Int {
        return try {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                1
            } else {
                0
            }
        } catch (e: Exception) {
            // إذا أرجع النظام IllegalStateException فهذا يعني أن الـ Provider لم يلتقط الخدمة
            -1 
        }
    }

    fun requestPermission(requestCode: Int) {
        try {
            Shizuku.requestPermission(requestCode)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * إعداد الجلسة النظيفة: توجيه الـ DNS 
     */
    fun setupCleanSessionNetwork(newIp: String, newMac: String, dnsIp: String): Boolean {
        // فحص سريع للتأكد أن الصلاحية موجودة
        if (checkShizukuStatus() != 1) return false
        
        return try {
            val commands = """
                iptables -t nat -A PREROUTING -i p2p0 -p udp --dport 53 -j DNAT --to-destination $dnsIp:53
                iptables -t nat -A PREROUTING -i p2p0 -p tcp --dport 53 -j DNAT --to-destination $dnsIp:53
            """.trimIndent()

            val commandArray = arrayOf("sh", "-c", commands)
            val shizukuClass = Shizuku::class.java
            val processMethod = shizukuClass.methods.firstOrNull { it.name == "newProcess" }
            
            if (processMethod != null) {
                val process = when (processMethod.parameterTypes.size) {
                    1 -> processMethod.invoke(null, commandArray)
                    2 -> processMethod.invoke(null, commandArray, null)
                    3 -> processMethod.invoke(null, commandArray, null, null)
                    else -> null
                } as? Process
                
                val exitCode = process?.waitFor() ?: -1
                exitCode == 0
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
