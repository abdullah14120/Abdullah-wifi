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
            
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
