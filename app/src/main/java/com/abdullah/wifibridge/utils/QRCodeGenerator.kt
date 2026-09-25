package com.abdullah.wifibridge.utils

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

object QRCodeGenerator {

    /**
     * توليد نص مطبق للمواصفات العالمية لربط الـ Wi-Fi عبر الـ QR Code
     * الصيغة المطلوبة: WIFI:S:SSID;T:WPA;P:PASSWORD;H:false;;
     */
    fun generateWifiQrCode(
        ssid: String,
        password: String,
        securityType: String = "WPA", // WPA أو WPA2 أو WEP أو nopass
        isHidden: Boolean = false,    // تحديد ما إذا كانت الشبكة مخفية
        size: Int = 512
    ): Bitmap? {
        
        val type = if (password.isEmpty()) "nopass" else securityType
        val pass = if (password.isEmpty()) "" else password
        val hiddenFlag = if (isHidden) "true" else "false"

        // التنسيق القياسي بدعم الشبكات المخفية للتأكد من ربط الكاميرا
        val qrContent = "WIFI:S:$ssid;T:$type;P:$pass;H:$hiddenFlag;;"

        return try {
            val hints = HashMap<EncodeHintType, Any>()
            hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
            hints[EncodeHintType.MARGIN] = 1

            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(qrContent, BarcodeFormat.QR_CODE, size, size, hints)

            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
