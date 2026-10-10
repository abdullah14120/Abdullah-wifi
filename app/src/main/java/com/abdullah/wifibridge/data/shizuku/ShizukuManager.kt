package com.abdullah.wifibridge.data.shizuku

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

class ShizukuManager {

    fun isShizukuAvailable(): Boolean = Shizuku.pingBinder()

    fun hasPermission(): Boolean {
        return isShizukuAvailable() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }

    fun requestPermission(requestCode: Int) {
        if (isShizukuAvailable() && !hasPermission()) {
            Shizuku.requestPermission(requestCode)
        }
    }

    /**
     * تغيير اسم الجهاز برمجياً لتغيير اسم شبكة Wi-Fi Direct الناتج.
     */
    fun randomizeDeviceName(newName: String): Boolean {
        if (!hasPermission()) return false

        return try {
            // تغيير اسم الجهاز العام واسم شبكة P2P في قاعدة بيانات الإعدادات
            val command = """
                settings put global device_name "$newName"
                settings put global wifi_p2p_device_name "$newName"
            """.trimIndent()

            val commandArray = arrayOf("sh", "-c", command)
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

    /**
     * إعداد الجلسة النظيفة.
     * تم إيقاف أوامر ifconfig لأنها تفصل اتصال الواي فاي الأساسي في أجهزة MediaTek وما شابهها.
     */
    fun setupCleanSessionNetwork(newIp: String): Boolean {
        if (!hasPermission()) return false
        
        // بما أننا ثبتنا الـ IP على 192.168.49.1 في ViewModel
        // وبما أن الإنترنت يأتي من اتصال واي فاي آخر، سنتخطى أوامر ifconfig لتجنب فصل الشريحة.
        // نظام أندرويد 10 وما فوق سيقوم تلقائياً بتوليد MAC عشوائي جديد لـ P2P في كل مرة يتم فيها البث.
        
        println("تم تخطي تعديل MAC/IP يدوياً للحفاظ على استقرار شبكة الواي فاي الأساسية.")
        return true
    }
}
