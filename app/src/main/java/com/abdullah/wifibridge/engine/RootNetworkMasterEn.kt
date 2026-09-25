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

    /**
     * التحقق من توفر صلاحيات الجذر (Root Access) على الجهاز
     */
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

    /**
     * 🛡️ بروتوكول التنظيف العميق (Deep Environment Sanitization Protocol)
     */
    fun performDeepEnvironmentSanitization(hotspotInterface: String): Boolean {
        Log.i(TAG, "=== Initiating Deep Environment Sanitization Protocol ===")
        val sanitizationCommands = listOf(
            "iptables -F",
            "iptables -t nat -F",
            "iptables -t mangle -F",
            "iptables -X",
            "iptables -t nat -X",
            "iptables -P INPUT ACCEPT",
            "iptables -P FORWARD ACCEPT",
            "iptables -P OUTPUT ACCEPT",
            "ip6tables -F",
            "ip6tables -t nat -F",
            "ip6tables -t mangle -F",
            "ip6tables -X",
            "ip6tables -P INPUT ACCEPT",
            "ip6tables -P FORWARD ACCEPT",
            "ip6tables -P OUTPUT ACCEPT",
            "ip neigh flush all",
            "ip link set $hotspotInterface down 2>/dev/null || true",
            "ip addr flush dev $hotspotInterface 2>/dev/null || true",
            "rm -rf /data/misc/dhcp/dnsmasq.leases",
            "rm -f /data/misc/apex/com.android.wifi/*",
            "echo 0 > /proc/sys/net/ipv4/ip_forward"
        )

        return executeRootCommandsBatch(sanitizationCommands)
    }

    /**
     * تفعيل خاصية الـ IP Forwarding في نواة اللينكس
     */
    fun enableIpForwarding(): Boolean {
        return executeRootCommand("echo 1 > /proc/sys/net/ipv4/ip_forward")
    }

    /**
     * 🚀 ضبط وتشغيل نقطة الاتصال (SoftAP) إجبارياً وتجاوز عشوائية النظام (Hard Enforcement)
     */
    fun forceConfigureAndStartSoftAp(ssid: String, password: String): Boolean {
        Log.i(TAG, "Enforcing custom SoftAP configuration: SSID=$ssid")
        val commands = listOf(
            "svc wifi disable",
            "cmd wifi set-softap-enabled false",
            "killall hostapd 2>/dev/null || true",
            "killall dnsmasq 2>/dev/null || true",
            "cmd wifi set-softap-configuration ssid \"$ssid\" passphrase \"$password\" security WPA2_PSK",
            "cmd wifi set-softap-enabled true"
        )
        return executeRootCommandsBatch(commands)
    }

    /**
     * 🧠 الدالة الشاملة المتقدمة: تشغيل الهوتسبت مع استخدام آليات بديلة
     */
    fun startHotspotWithManager(context: Context, ssid: String, password: String): Boolean {
        try {
            Log.i(TAG, "Starting hotspot via primary hard enforcement...")
            if (forceConfigureAndStartSoftAp(ssid, password)) {
                return true
            }

            Log.w(TAG, "Primary method failed. Executing advanced recovery & fallback mechanisms...")

            executeRootCommand("settings put global tether_supported 1")

            val fallbackCommands = listOf(
                "svc wifi disable",
                "am force-stop com.android.settings",
                "cmd wifi set-softap-enabled false",
                "sleep 1",
                "cmd wifi set-softap-configuration ssid \"$ssid\" passphrase \"$password\" security WPA2_PSK",
                "cmd wifi set-softap-enabled true"
            )
            
            val fallbackSuccess = executeRootCommandsBatch(fallbackCommands)
            if (fallbackSuccess) {
                Log.i(TAG, "Fallback hotspot activation succeeded.")
                return true
            }

            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifiManager?.let {
                @Suppress("DEPRECATION")
                it.isWifiEnabled = false
            }

            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error in startHotspotWithManager execution", e)
            return false
        }
    }

    /**
     * تعديل اسم الشبكة (SSID) وكلمة المرور (Password) عبر الروت حصرياً
     */
    fun updateSoftApConfiguration(ssid: String, password: String): Boolean {
        val commands = listOf(
            "cmd wifi set-softap-enabled false",
            "cmd wifi set-softap-configuration ssid \"$ssid\" passphrase \"$password\" security WPA2_PSK",
            "cmd wifi set-softap-enabled true"
        )
        return executeRootCommandsBatch(commands)
    }

    /**
     * تشغيل نقطة الاتصال عبر النظام
     */
    fun startSystemSoftAp(): Boolean {
        val commands = listOf(
            "svc wifi disable",
            "cmd wifi set-softap-enabled true"
        )
        return executeRootCommandsBatch(commands)
    }

    /**
     * إيقاف نقطة الاتصال عبر النظام
     */
    fun stopSystemSoftAp(): Boolean {
        return executeRootCommand("cmd wifi set-softap-enabled false")
    }

    /**
     * 🔍 التحقق الفوري من إعدادات الـ SoftAP النشطة في النظام
     */
    fun verifyActiveSoftApConfig(): String? {
        var process: Process? = null
        var os: DataOutputStream? = null
        return try {
            process = Runtime.getRuntime().exec("su")
            os = DataOutputStream(process.outputStream)
            os.writeBytes("cmd wifi get-softap-configuration\n")
            os.writeBytes("exit\n")
            os.flush()
            
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            output
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying active softap config", e)
            null
        } finally {
            try {
                os?.close()
                process?.destroy()
            } catch (_: IOException) {}
        }
    }

    /**
     * توليد وتطبيق عنوان MAC عشوائي (MAC Spoofing) لواجهة البث
     */
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
        } else {
            Log.w(TAG, "Failed to spoof MAC address for $hotspotInterface")
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

    /**
     * توليد أرقام نطاق Subnet عشوائية ديناميكية (Dynamic Subnet Rotation)
     */
    fun generateDynamicSubnet(): Pair<Int, Int> {
        val random = Random()
        val subnetX = random.nextInt(191) + 10 
        val gatewayY = 1
        return Pair(subnetX, gatewayY)
    }

    /**
     * 🌐 اكتشاف واجهة الخروج النشطة (Active WAN Interface) ديناميكياً لتجنب الاعتماد على rmnet_data0 فقط
     */
    fun detectActiveWanInterface(): String {
        var process: Process? = null
        try {
            process = Runtime.getRuntime().exec(arrayOf("su", "-c", "ip route show | grep default"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val line = reader.readLine() ?: ""
            process.waitFor()
            
            // مثال على السطر الناتج: default via 10.0.2.2 dev wlan0 table 0
            if (line.isNotEmpty()) {
                val parts = line.split(" ")
                for (i in 0 until parts.size) {
                    if (parts[i] == "dev" && i + 1 < parts.size) {
                        val detectedInterface = parts[i + 1]
                        Log.i(TAG, "Successfully detected active WAN interface: $detectedInterface")
                        return detectedInterface
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to detect active WAN interface dynamically, falling back to rmnet_data0", e)
        }
        return "rmnet_data0" // القيمة الافتراضية الاحتياطية
    }

    /**
     * إعداد Subnet مخصص ديناميكي وتفعيل قواعد الـ NAT للتوجيه والخروج (WAN)
     */
    fun setupCustomSubnetAndNat(subnetX: Int, gatewayY: Int, outboundInterface: String, hotspotInterface: String): Boolean {
        // التحقق الذكي واكتشاف الواجهة إذا كانت فارغة أو افتراضية
        val activeWan = if (outboundInterface.isBlank()) detectActiveWanInterface() else outboundInterface
        val customGatewayIp = "192.168.$subnetX.$gatewayY"
        
        val commands = listOf(
            "iptables -F",
            "iptables -t nat -F",
            "ip addr flush dev $hotspotInterface",
            "ip addr add $customGatewayIp/24 dev $hotspotInterface",
            "ip link set $hotspotInterface up",
            "iptables -A FORWARD -i $hotspotInterface -o $activeWan -j ACCEPT",
            "iptables -A FORWARD -i $activeWan -o $hotspotInterface -m state --state RELATED,ESTABLISHED -j ACCEPT",
            "iptables -t nat -A POSTROUTING -o $activeWan -j MASQUERADE"
        )
        return executeRootCommandsBatch(commands)
    }

    /**
     * تطبيق قواعد اعتراض وتوجيه الـ DNS قسرياً (DNS Hijacking)
     */
    fun applyForcedDnsRedirection(hotspotInterface: String, targetDnsIp: String): Boolean {
        val commands = listOf(
            "iptables -t nat -A PREROUTING -i $hotspotInterface -p udp --dport 53 -j DNAT --to-destination $targetDnsIp:53",
            "iptables -t nat -A PREROUTING -i $hotspotInterface -p tcp --dport 53 -j DNAT --to-destination $targetDnsIp:53"
        )
        return executeRootCommandsBatch(commands)
    }

    /**
     * تنظيف كافة القواعد وإعادة تعيين حالة الشبكة عند إيقاف الخدمة
     */
    fun flushAllRules(): Boolean {
        val commands = listOf(
            "iptables -F",
            "iptables -t nat -F",
            "iptables -X",
            "iptables -t nat -X",
            "ip6tables -F",
            "ip6tables -t nat -F",
            "cmd wifi set-softap-enabled false",
            "echo 0 > /proc/sys/net/ipv4/ip_forward"
        )
        return executeRootCommandsBatch(commands)
    }

    /**
     * تنفيذ أمر روت منفرد
     */
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

    /**
     * تنفيذ مجموعة أوامر روت بشكل متسلسل وبطريقة دفعة واحدة (Batch)
     */
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
