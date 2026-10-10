package com.abdullah.wifibridge.data.shizuku

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import kotlin.random.Random

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
     * Generates a random Locally Administered Unicast MAC address.
     * Starts with 02 to ensure it is valid and locally administered.
     */
    private fun generateRandomMacAddress(): String {
        val macBytes = ByteArray(6)
        Random.nextBytes(macBytes)
        macBytes[0] = 0x02.toByte()
        return macBytes.joinToString(":") { String.format("%02x", it) }
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
     * Executes shell commands to change p2p0 MAC and IP.
     * Achieves the "Clean Session" requirement.
     */
    fun setupCleanSessionNetwork(newIp: String): Boolean {
        if (!hasPermission()) return false
        
        val newMac = generateRandomMacAddress()
        
        return try {
            val command = """
                ifconfig p2p0 down
                ifconfig p2p0 hw ether $newMac
                ifconfig p2p0 $newIp netmask 255.255.255.0 up
            """.trimIndent()
            
            // الحل الجذري: استخدام Reflection لتجاوز خلل مترجم Kotlin
            // هذا الكود سيبحث عن الدالة العامة (Public) ويشغلها ديناميكياً
            val commandArray = arrayOf("sh", "-c", command)
            val shizukuClass = Shizuku::class.java
            
            val processMethod = shizukuClass.methods.firstOrNull { it.name == "newProcess" }
            
            if (processMethod != null) {
                // تنفيذ الدالة بناءً على عدد المعاملات التي تطلبها النسخة الحالية من Shizuku
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
