package com.abdullah.wifibridge.engine

import android.util.Log
import java.io.DataOutputStream
import java.io.IOException

object RootNetworkMasterEngine {

    private const val TAG = "RootNetworkEngine"

    fun checkRootAccess(): Boolean {
        var process: Process? = null
        var os: DataOutputStream? = null
        return try {
            process = Runtime.getRuntime().exec("su")
            os = DataOutputStream(process.outputStream)
            os.writeBytes("id\n")
            os.writeBytes("exit\n")
            os.flush()
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            Log.e(TAG, "خطأ في التحقق من الروت: ${e.message}")
            false
        } finally {
            try {
                os?.close()
                process?.destroy()
            } catch (_: IOException) {}
        }
    }

    fun enableIpForwarding(): Boolean {
        val command = "echo 1 > /proc/sys/net/ipv4/ip_forward"
        return executeRootCommand(command)
    }

    fun setupNatRules(outboundInterface: String, hotspotInterface: String): Boolean {
        val commands = listOf(
            "iptables -F",
            "iptables -t nat -F",
            "iptables -X",
            "iptables -A FORWARD -i $hotspotInterface -o $outboundInterface -j ACCEPT",
            "iptables -A FORWARD -i $outboundInterface -o $hotspotInterface -m state --state RELATED,ESTABLISHED -j ACCEPT",
            "iptables -t nat -A POSTROUTING -o $outboundInterface -j MASQUERADE"
        )
        return executeRootCommandsBatch(commands)
    }

    fun flushAllRules(): Boolean {
        val commands = listOf(
            "iptables -F",
            "iptables -t nat -F",
            "iptables -X",
            "iptables -t nat -X"
        )
        return executeRootCommandsBatch(commands)
    }

    fun executeRootCommand(command: String): Boolean {
        var process: Process? = null
        var os: DataOutputStream? = null
        return try {
            process = Runtime.getRuntime().exec("su")
            os = DataOutputStream(process.outputStream)
            os.writeBytes("$command\n")
            os.writeBytes("exit\n")
            os.flush()
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            Log.e(TAG, "خطأ في تنفيذ أمر الروت: $command", e)
            false
        } finally {
            try {
                os?.close()
                process?.destroy()
            } catch (_: IOException) {}
        }
    }

    fun executeRootCommandsBatch(commands: List<String>): Boolean {
        var process: Process? = null
        var os: DataOutputStream? = null
        return try {
            process = Runtime.getRuntime().exec("su")
            os = DataOutputStream(process.outputStream)
            for (cmd in commands) {
                os.writeBytes("$cmd\n")
            }
            os.writeBytes("exit\n")
            os.flush()
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            Log.e(TAG, "خطأ في تنفيذ حزمة أوامر الروت", e)
            false
        } finally {
            try {
                os?.close()
                process?.destroy()
            } catch (_: IOException) {}
        }
    }
}
