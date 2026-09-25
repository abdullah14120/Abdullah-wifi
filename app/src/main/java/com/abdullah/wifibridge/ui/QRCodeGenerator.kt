package com.abdullah.wifibridge.ui

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

object QRCodeGenerator {

    /**
     * يقوم بتوليد نص خاص للاتصال بشبكة الواي فاي حسب المعايير القياسية
     */
    fun generateWifiQrCode(ssid: String, password: String, securityType: String = "WPA", size: Int = 512): Bitmap? {
        // نص التنسيق القياسي للواي فاي
        val qrContent = if (password.isEmpty()) {
            "WIFI:S:$ssid;T:nopass;;"
        } else {
            "WIFI:S:$ssid;T:$securityType;P:$password;;"
        }

        return generateBitmap(qrContent, size)
    }

    private fun generateBitmap(content: String, size: Int): Bitmap? {
        return try {
            val hints = HashMap<EncodeHintType, Any>()
            hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
            hints[EncodeHintType.MARGIN] = 1 // تقليل الحواف الميتة حول الكود

            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)

            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

            for (x in 0 until width) {
                for (y in 0 until height) {
                    // لون الكود أسود والخلفية بيضاء لضمان سهولة القراءة عبر الكاميرا
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
