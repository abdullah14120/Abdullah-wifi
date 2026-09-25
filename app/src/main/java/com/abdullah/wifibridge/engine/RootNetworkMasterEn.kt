package com.abdullah.wifibridge.engine

import android.util.Log
import java.io.DataOutputStream
import java.io.IOException

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
     * إعداد Subnet مخصص، وتثبيت الآي بي على الواجهة، وتفعيل قواعد الـ NAT للتوجيه والخروج (WAN)
     */
    fun setupCustomSubnetAndNat(subnetX: Int, gatewayY: Int, outboundInterface: String, hotspotInterface: String): Boolean {
        val customGatewayIp = "192.168.$subnetX.$gatewayY"
        val commands = listOf(
            // 1. تنظيف جداول iptables الأساسية وnat لمنع التداخل مع القواعد القديمة
            "iptables -F",
            "iptables -t nat -F",
            "iptables -X",
            "iptables -t nat -X",
            
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
            "cmd wifi set-softap-enabled false"
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
