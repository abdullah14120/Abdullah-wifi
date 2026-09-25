package com.abdullah.wifibridge.engine

import android.util.Log
import java.io.DataOutputStream

object WiFiBridgeController {

    private const val TAG = "BridgeController"

    /**
     * تشغيل جسر مشاركة الواي فاي (Wi-Fi Repeater / Bridge)
     * @param wanInterface واجهة استقبال الإنترنت (مثل wlan0)
     * @param hotspotInterface واجهة البث (مثل wlan0 أو ap0)
     */
    fun startBridge(wanInterface: String, hotspotInterface: String): Boolean {
        var success = false
        try {
            // تجهيز قائمة الأوامر الهندسية للروت
            val commands = listOf(
                // 1. السماح بنظام أندرويد بتمرير وتوجيه الحزم بين الشبكات (IP Forwarding)
                "echo 1 > /proc/sys/net/ipv4/ip_forward",
                
                // 2. تنظيف قواعد iptables القديمة لتجنب تداخل القواعد السابقة
                "iptables -F",
                "iptables -t nat -F",
                "iptables -X",
                
                // 3. السماح بمرور الحزم في اتجاهي الوارد والصادر بين الواجهتين
                "iptables -A FORWARD -i $wanInterface -o $hotspotInterface -j ACCEPT",
                "iptables -A FORWARD -i $hotspotInterface -o $wanInterface -j ACCEPT",
                
                // 4. تفعيل خاصية الـ NAT (Network Address Translation) وإخفاء العناوين (Masquerading)
                // هذه الخطوة هي السر في السماح للأجهزة المتصلة بنقطة البث بالوصول للإنترنت القادم من الـ Wi-Fi
                "iptables -t nat -A POSTROUTING -o $wanInterface -j MASQUERADE"
            )

            // تنفيذ الأوامر دفعة واحدة عبر جلسة su واحدة لضمان الاستقرار
            val process = Runtime.getRuntime().exec("su")
            val outputStream = DataOutputStream(process.outputStream)
            
            for (cmd in commands) {
                outputStream.writeBytes("$cmd\n")
                Log.d(TAG, "تنفيذ أمر الروت: $cmd")
            }
            
            outputStream.writeBytes("exit\n")
            outputStream.flush()
            outputStream.close()
            
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                Log.i(TAG, "تم تفعيل جسر الشبكة بنجاح تام بين $wanInterface و $hotspotInterface")
                success = true
            } else {
                Log.e(TAG, "فشل تنفيذ بعض أوامر الروت، رمز الخروج: $exitCode")
            }

        } catch (e: Exception) {
            Log.e(TAG, "استثناء أثناء تشغيل الجسر: ${e.message}", e)
        }
        return success
    }

    /**
     * إيقاف الجسر وإعادة تعيين إعدادات الشبكة لوضعها الطبيعي
     */
    fun stopBridge(): Boolean {
        return try {
            val commands = listOf(
                "echo 0 > /proc/sys/net/ipv4/ip_forward",
                "iptables -F",
                "iptables -t nat -F"
            )
            val process = Runtime.getRuntime().exec("su")
            val outputStream = DataOutputStream(process.outputStream)
            for (cmd in commands) {
                outputStream.writeBytes("$cmd\n")
            }
            outputStream.writeBytes("exit\n")
            outputStream.flush()
            outputStream.close()
            process.waitFor()
            Log.i(TAG, "تم إيقاف الجسر وتنظيف قواعد الـ iptables بنجاح.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "خطأ أثناء إيقاف الجسر: ${e.message}")
            false
        }
    }
}
