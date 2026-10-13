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

    // تم تغيير نوع الإرجاع إلى String لطباعة سبب الخطأ بدقة
    fun setupCleanSessionNetwork(dnsIp: String): String {
        if (checkShizukuStatus() != 1) return "Shizuku غير متصل أو لا يملك صلاحية."

        return try {
            // دمج الأمرين لضمان التنفيذ المتسلسل
            val command = "iptables -t nat -A PREROUTING -i p2p0 -p udp --dport 53 -j DNAT --to-destination $dnsIp:53 && iptables -t nat -A PREROUTING -i p2p0 -p tcp --dport 53 -j DNAT --to-destination $dnsIp:53"

            // استخدام دالة Shizuku مباشرة
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            val exitCode = process.waitFor()

            if (exitCode == 0) {
                "SUCCESS"
            } else {
                // قراءة الخطأ الحقيقي من النظام
                val errorReader = BufferedReader(InputStreamReader(process.errorStream))
                val errorOutput = errorReader.readText().trim()

                if (errorOutput.contains("Permission denied", ignoreCase = true) || errorOutput.contains("root", ignoreCase = true)) {
                    "iptables يطلب صلاحية Root. (Shizuku يعمل بوضع ADB فقط)"
                } else if (errorOutput.contains("No such device", ignoreCase = true)) {
                    "واجهة p2p0 غير جاهزة أو غير موجودة في هذا الهاتف."
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
