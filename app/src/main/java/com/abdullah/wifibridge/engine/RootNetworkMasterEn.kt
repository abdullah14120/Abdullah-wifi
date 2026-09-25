package com.abdullah.wifibridge.engine

import android.util.Log
import java.io.DataOutputStream
import java.io.File
import java.io.IOException

/**
 * RootNetworkMasterEngine - المحرك السيادي المتقدم لإدارة الشبكات وحزم البيانات عبر الروت.
 * مصمم خصيصاً للتحكم الكامل بـ iptables، تفعيل الـ IP Forwarding، وإدارة الجسور الشبكية بدقة فائقة.
 */
object RootNetworkMasterEngine {

    private const val TAG = "RootNetworkEngine"

    /**
     * التحقق من توفر صلاحيات الروت وصلاحية الوصول الفعلي لطبقة النظام.
     */
    fun checkRootAccess(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val outputStream = DataOutputStream(process.outputStream)
            outputStream.writeBytes("id\n")
            outputStream.writeBytes("exit\n")
            outputStream.flush()
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            Log.e(TAG, "خطأ في التحقق من صلاحيات الروت: ${e.message}")
            false
        }
    }

    /**
     * تفعيل التوجيه الشبكي (IP Forwarding) على مستوى نواة النظام (Kernel).
     */
    fun enableIpForwarding(): Boolean {
        val command = "echo 1 > /proc/sys/net/ipv4/ip_forward"
        val success = executeRootCommand(command)
        if (success) {
            Log.i(TAG, "تم تفعيل IP Forwarding بنجاح.")
        } else {
            Log.e(TAG, "فشل في تفعيل IP Forwarding.")
        }
        return success
    }

    /**
     * إعداد قواعد NAT و iptables الأساسية لتوجيه حزم البيانات بين واجهات الشبكة (مثلاً من rmnet_data0 إلى ap0).
     * @param outboundInterface واجهة الاتصال الخارجية (البيانات الخلوية أو الواي فاي الأساسي مثل rmnet_data0 أو wlan0)
     * @param hotspotInterface واجهة نقطة الاتصال الوهمية أو المحلية (مثل ap0 أو wlan1)
     */
    fun setupNatRules(outboundInterface: String, hotspotInterface: String): Boolean {
        val commands = listOf(
            // تنظيف القواعد القديمة لمنع التداخل وتراكم القواعد
            "iptables -F",
            "iptables -t nat -F",
            "iptables -X",
            
            // تفعيل التوجيه عبر الواجهات المحددة
            "iptables -A FORWARD -i $hotspotInterface -o$outboundInterface -j ACCEPT",
            "iptables -A FORWARD -i $outboundInterface -o$hotspotInterface -m state --state RELATED,ESTABLISHED -j ACCEPT",
            
            // تفعيل تقنية Masquerading لإخفاء العناوين وخروج البيانات بالواجهة الخارجية
            "iptables -t nat -A POSTROUTING -o $outboundInterface -j MASQUERADE"
        ]

        return executeRootCommandsBatch(commands)
    }

    /**
     * إعداد إعادة توجيه DNS (DNS Hijacking) لتوجيه طلبات الDNS إلى خادم محلي أو خادم موثوق (مثل 8.8.8.8).
     * مفيد جداً لحجب الإعلانات وتسريع التصفح.
     */
    fun setupDnsHijacking(dnsPort: Int = 5353): Boolean {
        val commands = listOf(
            "iptables -t nat -A PREROUTING -p udp --dport 53 -j REDIRECT --to-ports $dnsPort",
            "iptables -t nat -A PREROUTING -p tcp --dport 53 -j REDIRECT --to-ports $dnsPort"
        ]
        return executeRootCommandsBatch(commands)
    }

    /**
     * مسح وإزالة جميع قواعد iptables وإعادة تعيين الشبكة للوضع الافتراضي.
     */
    fun flushAllRules(): Boolean {
        val commands = listOf(
            "iptables -F",
            "iptables -t nat -F",
            "iptables -X",
            "iptables -t nat -X"
        )
        return executeRootCommandsBatch(commands)
    }

    /**
     * تنفيذ أمر روت واحد بصلاحيات مطلقة.
     */
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
            Log.e(TAG, "خطأ أثناء تنفيذ أمر الروت: $command", e)
            false
        } finally {
            try {
                os?.close()
                process?.destroy()
            } catch (e: IOException) {
                // تجاهل خطأ الإغلاق
            }
        }
    }

    /**
     * تنفيذ مجموعة من أوامر الروت دفعة واحدة ضمن جلسة `su` واحدة (أكثر كفاءة وأسرع).
     */
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
            Log.e(TAG, "خطأ أثناء تنفيذ حزمة أوامر الروت", e)
            false
        } finally {
            try {
                os?.close()
                process?.destroy()
            } catch (e: IOException) {
                // تجاهل خطأ الإغلاق
            }
        }
    }
}
