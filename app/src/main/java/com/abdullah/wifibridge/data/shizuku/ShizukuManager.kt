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
     * إعداد الجلسة النظيفة: تغيير MAC، IP، وتوجيه الـ DNS
     */
    fun setupCleanSessionNetwork(newIp: String, newMac: String, dnsIp: String): Boolean {
        if (!hasPermission()) return false
        
        return try {
            val commands = """
                # 1. تغيير الماك أدرس لشبكة البث (P2P)
                ip link set dev p2p0 down
                ip link set dev p2p0 address $newMac
                ip link set dev p2p0 up
                
                # 2. تم إيقاف أمر ifconfig هنا للعودة للاستقرار
                
                # 3. توجيه جميع طلبات الـ DNS القادمة من العميل إلى الـ DNS المختار (تشفير وإخفاء)
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
                println("Shizuku Clean Session Setup Exit Code: $exitCode")
                exitCode == 0
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun getActualMacAddress(): String {
        if (!hasPermission()) return "غير متاح (بدون صلاحية)"
        
        return try {
            val command = "cat /sys/class/net/p2p0/address"
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
                
                // قراءة المخرجات (الماك أدرس) من سطر الأوامر
                val reader = java.io.BufferedReader(java.io.InputStreamReader(process?.inputStream))
                val actualMac = reader.readLine()?.trim() ?: "غير معروف"
                process?.waitFor()
                
                actualMac
            } else {
                "فشل استدعاء Shizuku"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "خطأ في القراءة"
        }
    }
