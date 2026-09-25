package com.abdullah.wifibridge.engine

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.util.Random

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
            process.waitFor() == 0
        } catch (e: Exception) {
            Log.e(TAG, "Error checking root access", e)
            false
        } finally {
            try {
                os?.close()
                process?.destroy()
            } catch (_: IOException) {}
        }
    }

    fun performDeepEnvironmentSanitization(hotspotInterface: String): Boolean {
        Log.i(TAG, "=== Initiating Deep Environment Sanitization Protocol ===")
        val sanitizationCommands = listOf(
            "iptables -F FORWARD",
            "iptables -t nat -F POSTROUTING",
            "ip link set $hotspotInterface down 2>/dev/null || true",
            "ip addr flush dev $hotspotInterface 2>/dev/null || true",
            "echo 0 > /proc/sys/net/ipv4/ip_forward"
        )
        return executeRootCommandsBatch(sanitizationCommands)
    }

    fun enableIpForwarding(): Boolean {
        return executeRootCommand("echo 1 > /proc/sys/net/ipv4/ip_forward")
    }

    fun randomizeHotspotMac(hotspotInterface: String): Boolean {
        val randomMac = generateRandomLocallyAdministeredMac()
        val commands = listOf(
            "ip link set $hotspotInterface down",
            "ip link set dev $hotspotInterface address $randomMac",
            "ip link set $hotspotInterface up"
        )
        val success = executeRootCommandsBatch(commands)
        if (success) {
            Log.i(TAG, "Successfully spoofed MAC address for $hotspotInterface to $randomMac")
        }
        return success
    }

    private fun generateRandomLocallyAdministeredMac(): String {
        val random = Random()
        val macBytes = ByteArray(6)
        random.nextBytes(macBytes)
        macBytes[0] = ((macBytes[0].toInt() and 0xfe) or 0x02).toByte()
        return macBytes.joinToString(":") { "%02x".format(it) }
    }

    fun generateDynamicSubnet(): Pair<Int, Int> {
        val random = Random()
        val subnetX = random.nextInt(191) + 10 
        val gatewayY = 1
        return Pair(subnetX, gatewayY)
    }

    /**
     * 🌐 اكتشاف دقيق لواجهة الواي فاي الأصلية التي تلتقط الإنترنت (WAN عبر Wi-Fi STA)
     */
    fun detectActiveWanInterface(): String {
        var process: Process? = null
        try {
            process = Runtime.getRuntime().exec(arrayOf("su", "-c", "ip route show | grep default"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val line = reader.readLine() ?: ""
            process.waitFor()
            
            if (line.isNotEmpty()) {
                val parts = line.split(" ")
                for (i in 0 until parts.size) {
                    if (parts[i] == "dev" && i + 1 < parts.size) {
                        val detectedInterface = parts[i + 1]
                        Log.i(TAG, "Successfully detected active Wi-Fi WAN interface: $detectedInterface")
                        return detectedInterface
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to detect active WAN interface, falling back to wlan0", e)
        }
        return "wlan0" // الافتراضي للواي فاي المستقبِل
    }

    /**
     * 🚀 إعداد الـ Subnet وتوجيه الـ NAT خصيصاً لسيناريو (الواي فاي إلى الـ P2P)
     */
    fun setupCustomSubnetAndNat(subnetX: Int, gatewayY: Int, outboundInterface: String, hotspotInterface: String): Boolean {
        // بما أن الإنترنت يأتي عبر الواي فاي، نحدد الواجهة بدقة (عادة wlan0)
        val activeWan = if (outboundInterface.isBlank() || outboundInterface == "rmnet_data0") detectActiveWanInterface() else outboundInterface
        val customGatewayIp = "192.168.$subnetX.$gatewayY"
        
        Log.i(TAG, "Configuring NAT: WAN (In/Out) = $activeWan | Hotspot LAN = $hotspotInterface | Gateway = $customGatewayIp")

        val commands = listOf(
            // تنظيف قواعد التوجيه السابقة فقط لتجنب التداخل
            "iptables -D FORWARD -i $hotspotInterface -o $activeWan -j ACCEPT 2>/dev/null || true",
            "iptables -D FORWARD -i $activeWan -o $hotspotInterface -m state --state RELATED,ESTABLISHED -j ACCEPT 2>/dev/null || true",
            "iptables -t nat -D POSTROUTING -o $activeWan -j MASQUERADE 2>/dev/null || true",

            // إعداد الأيبي والبوابة لواجهة البث
            "ip addr flush dev $hotspotInterface",
            "ip addr add $customGatewayIp/24 dev $hotspotInterface",
            "ip link set $hotspotInterface up",

            // تفعيل التوجيه الشفاف بين الواجهتين (Wi-Fi STA -> Wi-Fi Direct P2P)
            "iptables -A FORWARD -i $hotspotInterface -o $activeWan -j ACCEPT",
            "iptables -A FORWARD -i $activeWan -o $hotspotInterface -m state --state RELATED,ESTABLISHED -j ACCEPT",
            
            // قاعدة الترجمة الحية للعنوان (NAT Masquerade) للخروج عبر واي فاي الراوتر
            "iptables -t nat -A POSTROUTING -o $activeWan -j MASQUERADE"
        )
        return executeRootCommandsBatch(commands)
    }

    fun applyForcedDnsRedirection(hotspotInterface: String, targetDnsIp: String): Boolean {
        val commands = listOf(
            "iptables -t nat -D PREROUTING -i $hotspotInterface -p udp --dport 53 -j DNAT --to-destination $targetDnsIp:53 2>/dev/null || true",
            "iptables -t nat -D PREROUTING -i $hotspotInterface -p tcp --dport 53 -j DNAT --to-destination $targetDnsIp:53 2>/dev/null || true",
            "iptables -t nat -A PREROUTING -i $hotspotInterface -p udp --dport 53 -j DNAT --to-destination $targetDnsIp:53",
            "iptables -t nat -A PREROUTING -i $hotspotInterface -p tcp --dport 53 -j DNAT --to-destination $targetDnsIp:53"
        )
        return executeRootCommandsBatch(commands)
    }

    fun flushAllRules(): Boolean {
        val commands = listOf(
            "iptables -F FORWARD",
            "iptables -t nat -F POSTROUTING",
            "ip6tables -F",
            "echo 0 > /proc/sys/net/ipv4/ip_forward"
        )
        return executeRootCommandsBatch(commands)
    }

    private fun executeRootCommand(command: String): Boolean {
        var process: Process? = null
        var os: DataOutputStream? = null
        return try {
            process = Runtime.getRuntime().exec("su")
            os = DataOutputStream(process.outputStream)
            os.writeBytes("$command\n")
            os.writeBytes("exit\n")
            os.flush()
            process.waitFor() == 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute root command: $command", e)
            false
        } finally {
            try {
                os?.close()
                process?.destroy()
            } catch (_: IOException) {}
        }
    }

    private fun executeRootCommandsBatch(commands: List<String>): Boolean {
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
            process.waitFor() == 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute root commands batch", e)
            false
        } finally {
            try {
                os?.close()
                process?.destroy()
            } catch (_: IOException) {}
        }
    }
}
