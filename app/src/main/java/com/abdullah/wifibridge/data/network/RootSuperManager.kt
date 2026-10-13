package com.abdullah.wifibridge.data.network

import java.io.DataOutputStream

class RootSuperManager {

    fun checkRootAccess(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * تفعيل الميزات الخارقة مع دعم تغيير IP والماك
     * @param dnsIp الآي بي الخاص بسيرفر التشفير
     * @param newMac الماك أدرس الوهمي (اختياري)
     * @param newGatewayIp الآي بي الجديد للبوابة (اختياري - يغير 192.168.49.1)
     */
    fun enableSuperSession(
        dnsIp: String = "1.1.1.1", 
        newMac: String? = null, 
        newGatewayIp: String? = null
    ): Boolean {
        if (!checkRootAccess()) return false

        try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)

            // 1. تجاوز فحص الـ Tethering
            os.writeBytes("settings put global tether_dun_required 0\n")

            // 2. قفل طاقة النواة (Kernel Wakelock)
            os.writeBytes("echo 'WiFiBridgePro_Lock' > /sys/power/wake_lock\n")

            // 3. التوجيه الشفاف (NAT)
            os.writeBytes("echo 1 > /proc/sys/net/ipv4/ip_forward\n")
            os.writeBytes("iptables -t nat -I POSTROUTING -o rmnet+ -j MASQUERADE\n")
            os.writeBytes("iptables -t nat -I POSTROUTING -o wlan0 -j MASQUERADE\n")

            // 4. خداع مزود الخدمة (TTL Modification)
            os.writeBytes("iptables -t mangle -I POSTROUTING -j TTL --ttl-set 64\n")

            // 5. تشفير الـ DNS الإجباري (صيد جميع الطلبات)
            os.writeBytes("iptables -t nat -I PREROUTING -i p2p0 -p udp --dport 53 -j DNAT --to-destination $dnsIp:53\n")
            os.writeBytes("iptables -t nat -I PREROUTING -i p2p0 -p tcp --dport 53 -j DNAT --to-destination $dnsIp:53\n")

            // 🚀 6. تغيير الهوية الفيزيائية والشبكية (MAC & IP Spoofing)
            if (newMac != null || newGatewayIp != null) {
                // إغلاق الواجهة مؤقتاً لتغيير الهوية
                os.writeBytes("ip link set dev p2p0 down\n")
                
                if (newMac != null) {
                    os.writeBytes("ip link set dev p2p0 address $newMac\n")
                }
                
                // إعادة تشغيل الواجهة
                os.writeBytes("ip link set dev p2p0 up\n")
                
                if (newGatewayIp != null) {
                    // مسح الآي بي القديم (192.168.49.1) وفرض الآي بي الجديد مع قناع الشبكة
                    os.writeBytes("ifconfig p2p0 $newGatewayIp netmask 255.255.255.0 up\n")
                }
            }

            os.writeBytes("exit\n")
            os.flush()
            
            val exitCode = process.waitFor()
            return exitCode == 0

        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun disableSuperSession(dnsIp: String = "1.1.1.1") {
        try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)

            os.writeBytes("echo 'WiFiBridgePro_Lock' > /sys/power/wake_unlock\n")
            os.writeBytes("echo 0 > /proc/sys/net/ipv4/ip_forward\n")
            
            os.writeBytes("iptables -t nat -D POSTROUTING -o rmnet+ -j MASQUERADE\n")
            os.writeBytes("iptables -t nat -D POSTROUTING -o wlan0 -j MASQUERADE\n")
            
            os.writeBytes("iptables -t mangle -D POSTROUTING -j TTL --ttl-set 64\n")
            
            os.writeBytes("iptables -t nat -D PREROUTING -i p2p0 -p udp --dport 53 -j DNAT --to-destination $dnsIp:53\n")
            os.writeBytes("iptables -t nat -D PREROUTING -i p2p0 -p tcp --dport 53 -j DNAT --to-destination $dnsIp:53\n")

            os.writeBytes("exit\n")
            os.flush()
            process.waitFor()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
