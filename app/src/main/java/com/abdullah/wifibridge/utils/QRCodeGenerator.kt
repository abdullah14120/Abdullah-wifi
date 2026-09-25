package com.abdullah.wifibridge.utils

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

object QRCodeGenerator {

    /**
     * توليد نص مطبق لمعايير الاتصال بالواي فاي القياسية (WIFI:S:ssid;T:WPA;P:password;;)
     */
    fun generateWifiQrCode(ssid: String, password: String, securityType: String = "WPA", size: Int = 512): Bitmap? {
        val qrContent = if (password.isEmpty()) {
            "WIFI:S:$ssid;T:nopass;;"
        } else {
            "WIFI:S:$ssid;T:$securityType;P:$password;;"
        }

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
