package com.abdullah.wifibridge.data.shizuku

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

class ShizukuManager {

    fun checkShizukuStatus(): Int {
        return try {
            if (Shizuku.getBinder() != null) {
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) 1 else 0
            } else {
                -1
            }
        } catch (e: Exception) {
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

    fun setupCleanSessionNetwork(dnsIp: String): String {
        if (checkShizukuStatus() != 1) return "Shizuku غير متصل أو لا يملك صلاحية."

        return try {
            val command = "iptables -t nat -A PREROUTING -i p2p0 -p udp --dport 53 -j DNAT --to-destination $dnsIp:53 && iptables -t nat -A PREROUTING -i p2p0 -p tcp --dport 53 -j DNAT --to-destination $dnsIp:53"
            val commandArray = arrayOf("sh", "-c", command)
            
            // 🚀 استخدام الانعكاس (Reflection) المتقدم للوصول للدالة المخفية
            val shizukuClass = Shizuku::class.java
            val processMethod = shizukuClass.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            // 🔓 كسر الحماية (Private) لجعل الدالة قابلة للاستخدام
            processMethod.isAccessible = true 
            
            val process = processMethod.invoke(null, commandArray, null, null) as? Process
            
            if (process == null) return "فشل إنشاء عملية Shizuku (Process = null)."

            val exitCode = process.waitFor()

            if (exitCode == 0) {
                "SUCCESS"
            } else {
                // قراءة الخطأ الحقيقي من النواة
                val errorReader = BufferedReader(InputStreamReader(process.errorStream))
                val errorOutput = errorReader.readText().trim()

                if (errorOutput.contains("Permission denied", ignoreCase = true) || errorOutput.contains("root", ignoreCase = true)) {
                    "أمر iptables يطلب صلاحية Root! (Shizuku يعمل بوضع ADB ولن يقبل هذا الأمر)"
                } else if (errorOutput.contains("No such device", ignoreCase = true)) {
                    "واجهة p2p0 غير جاهزة بعد، أو غير موجودة."
                } else {
                    "السبب: $errorOutput"
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "خطأ برمجي: ${e.message}"
        }
    }
}
