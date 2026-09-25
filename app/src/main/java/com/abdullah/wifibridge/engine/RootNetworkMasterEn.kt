package com.abdullah.wifibridge.engine

import android.util.Log
import java.io.DataOutputStream
import java.io.IOException
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
     * يقوم بتطهير النواة تماماً، مسح وتصفية جداول الـ iptables و ip6tables، تفريغ جدول الـ ARP،
     * ومسح مخلفات ملفات التأجير لضمان سِجل نظيف تماماً (Clean Slate) وبصمة معدومة.
     */
    fun performDeepEnvironmentSanitization(hotspotInterface: String): Boolean {
        Log.i(TAG, "=== Initiating Deep Environment Sanitization Protocol ===")
        val sanitizationCommands = listOf(
            // 1. تصفية وإلغاء كافة قواعد الجدار الناري IPv4 بالكامل وإعادة ضبط سياساتها الافتراضية
            "iptables -F",
            "iptables -t nat -F",
            "iptables -t mangle -F",
            "iptables -X",
            "iptables -t nat -X",
            "iptables -P INPUT ACCEPT",
            "iptables -P FORWARD ACCEPT",
            "iptables -P OUTPUT ACCEPT",

            // 2. تصفية قواعد IPv6 لمنع أي تسريب خفي للحزم عبر بروتوكول النسخة السادسة
            "ip6tables -F",
            "ip6tables -t nat -F",
            "ip6tables -t mangle -F",
            "ip6tables -X",
            "ip6tables -P INPUT ACCEPT",
            "ip6tables -P FORWARD ACCEPT",
            "ip6tables -P OUTPUT ACCEPT",

            // 3. تفريغ جدول الـ ARP والـ Neighbor Cache لمسح سجلات الأجهزة المتصلة سابقاً بالفيزيائي
            "ip neigh flush all",

            // 4. إنزال واجهة البث مؤقتاً لتفريغ أي إعدادات عالقة من الجلسة السابقة
            "ip link set $hotspotInterface down",
            "ip addr flush dev $hotspotInterface",

            // 5. مسح ملفات تأجير العناوين المؤقتة (DHCP Leases) لتجنب تكرار الـ IPs السابقة
            "rm -rf /data/misc/dhcp/dnsmasq.leases",
            "rm -f /data/misc/apex/com.android.wifi/*",

            // 6. إعادة تعيين توجيه الحزم (IP Forwarding) للصفر مؤقتاً أثناء إعادة التطهير
            "echo 0 > /proc/sys/net/ipv4/ip_forward"
        )

        val success = executeRootCommandsBatch(sanitizationCommands)
        if (success) {
            Log.i(TAG, "=== Deep Environment Sanitization Completed Successfully ===")
        } else {
            Log.w(TAG, "Some commands during deep sanitization encountered non-zero exit codes.")
        }
        return success
    }

    /**
     * تفعيل خاصية الـ IP Forwarding في نواة اللينكس للسماح بتمرير الحزم بين الشبكات
     */
    fun enableIpForwarding(): Boolean {
        return executeRootCommand("echo 1 > /proc/sys/net/ipv4/ip_forward")
    }

    /**
     * ضبط إعدادات نقطة الاتصال (SoftAP) بالاسم وكلمة المرور وتفعيلها إجبارياً دفعة واحدة
     */
    fun configureAndStartSoftAp(ssid: String, password: String): Boolean {
        val commands = listOf(
            "svc wifi disable", // تعطيل الواي فاي العادي لمنع التداخل الهاردويري
            "cmd wifi set-softap-enabled false",
            "cmd wifi set-softap-configuration ssid \"$ssid\" passphrase \"$password\"",
            "cmd wifi set-softap-enabled true"
        )
        return executeRootCommandsBatch(commands)
    }

    /**
     * تعديل اسم الشبكة (SSID) وكلمة المرور (Password) عبر صلاحيات الروت فقط
     */
    fun updateSoftApConfiguration(ssid: String, password: String): Boolean {
        val commands = listOf(
            "cmd wifi set-softap-enabled false",
            "cmd wifi set-softap-configuration ssid \"$ssid\" passphrase \"$password\"",
            "cmd wifi set-softap-enabled true"
        )
        return executeRootCommandsBatch(commands)
    }

    /**
     * تشغيل نقطة الاتصال (SoftAP) إجبارياً عبر خدمة النظام
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
     * توليد وتطبيق عنوان MAC عشوائي (MAC Spoofing) لواجهة البث لضمان بصمة شبكية جديدة كلياً على مستوى الطبقة الثانية (Layer 2)
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
            Log.w(TAG, "Failed to spoof MAC address for $hotspotInterface (Interface might still be initializing)")
        }
        return success
    }

    /**
     * دالة مساعدة لتوليد عنوان MAC عشوائي محلي الصنع صالح للشبكات اللاسلكية
     */
    private fun generateRandomLocallyAdministeredMac(): String {
        val random = Random()
        val macBytes = ByteArray(6)
        random.nextBytes(macBytes)
        // تعيين البت الثاني في البايت الأول ليكون العنوان محلياً (Locally Administered) ومنع التعارض العالمي
        macBytes[0] = ((macBytes[0].toInt() and 0xfe) or 0x02).toByte()
        return macBytes.joinToString(":") { "%02x".format(it) }
    }

    /**
     * توليد أرقام نطاق Subnet عشوائية ديناميكية (Dynamic Subnet Rotation) 
     * لتغيير الآي بي الافتراضي والـ Gateway في كل دورة اتصال (لتجنب الثبات والتتبع).
     * @return مصفوفة تتكون من [SubnetX, GatewayY]
     */
    fun generateDynamicSubnet(): Pair<Int, Int> {
        val random = Random()
        // اختيار رقم عشوائي للنطاق الثالث بين 10 و 200 لتجنب التصادم مع شبكات الراوترات المنزلية الشائعة (مثل 0 أو 1 أو 8)
        val subnetX = random.nextInt(191) + 10 
        val gatewayY = 1 // عادة يكون الجيتواي هو .1
        return Pair(subnetX, gatewayY)
    }

    /**
     * إعداد Subnet مخصص ديناميكي، وتثبيت الآي بي على الواجهة، وتفعيل قواعد الـ NAT للتوجيه والخروج (WAN)
     */
    fun setupCustomSubnetAndNat(subnetX: Int, gatewayY: Int, outboundInterface: String, hotspotInterface: String): Boolean {
        val customGatewayIp = "192.168.$subnetX.$gatewayY"
        val commands = listOf(
            // 1. تنظيف القواعد المحلية للواجهة والتأكد من خلوها
            "iptables -F",
            "iptables -t nat -F",
            
            // 2. إعطاء الآي بي المخصص لواجهة البث ورفع حالة الواجهة (UP)
            "ip addr flush dev $hotspotInterface",
            "ip addr add $customGatewayIp/24 dev $hotspotInterface",
            "ip link set $hotspotInterface up",

            // 3. تفعيل قواعد الـ NAT والتوجيه للخارج (Forwarding & Masquerading)
            "iptables -A FORWARD -i $hotspotInterface -o $outboundInterface -j ACCEPT",
            "iptables -A FORWARD -i $outboundInterface -o $hotspotInterface -m state --state RELATED,ESTABLISHED -j ACCEPT",
            "iptables -t nat -A POSTROUTING -o $outboundInterface -j MASQUERADE"
        )
        return executeRootCommandsBatch(commands)
    }

    /**
     * تطبيق قواعد اعتراض وتوجيه الـ DNS قسرياً (DNS Hijacking / Redirection) لمنع التسريب وضمان أمان الشبكة
     */
    fun applyForcedDnsRedirection(hotspotInterface: String, targetDnsIp: String): Boolean {
        val commands = listOf(
            // اعتراض طلبات UDP على المنفذ 53 القادمة من الأجهزة المتصلة وتحويلها للخادم الموثوق
            "iptables -t nat -A PREROUTING -i $hotspotInterface -p udp --dport 53 -j DNAT --to-destination $targetDnsIp:53",
            
            // اعتراض طلبات TCP على المنفذ 53 القادمة من الأجهزة المتصلة وتحويلها للخادم الموثوق
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
            return process.waitFor() == 0
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
            return process.waitFor() == 0
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
