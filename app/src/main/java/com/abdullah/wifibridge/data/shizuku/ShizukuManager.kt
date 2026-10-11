package com.abdullah.wifibridge.data.shizuku

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

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
     * تم التعديل: إيقاف تغيير اسم الجهاز يدوياً.
     * سيقوم النظام الآن بتوليد اسم شبكة (SSID) وكلمة مرور (Password) تلقائياً وآمناً لتجنب أي تعارض في المصادقة.
     */
    fun randomizeDeviceName(newName: String): Boolean {
        if (!hasPermission()) return false

        println("تم تخطي تغيير اسم الجهاز لتجنب تعارض المصادقة، سيتم الاعتماد على توليد النظام.")
        
        // إرجاع true مباشرة لكي يكمل التطبيق عمله بسلاسة دون تجميد أو أخطاء
        return true
    }

    /**
     * إعداد الجلسة النظيفة.
     * تم إيقاف أوامر ifconfig لأنها تفصل اتصال الواي فاي الأساسي في أجهزة MediaTek وما شابهها.
     */
    fun setupCleanSessionNetwork(newIp: String): Boolean {
        if (!hasPermission()) return false
        
        // بما أننا ثبتنا الـ IP على 192.168.49.1 في ViewModel
        // وبما أن الإنترنت يأتي من اتصال واي فاي آخر، سنتخطى أوامر ifconfig لتجنب فصل الشريحة.
        // نظام أندرويد 10 وما فوق سيقوم تلقائياً بتوليد MAC عشوائي جديد لـ P2P في كل مرة يتم فيها البث.
        
        println("تم تخطي تعديل MAC/IP يدوياً للحفاظ على استقرار شبكة الواي فاي الأساسية.")
        return true
    }
}
