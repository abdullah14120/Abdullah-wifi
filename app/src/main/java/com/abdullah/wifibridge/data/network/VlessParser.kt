package com.abdullah.wifibridge.data.network

import java.net.URI

data class VlessConfig(
    val uuid: String,
    val address: String,
    val port: Int,
    val encryption: String,
    val network: String, // tcp, ws, grpc
    val type: String,
    val sni: String, // الموقع الوهمي للتمويه (مثلاً samsung.com)
    val flow: String, // نوع التدفق (مثلاً xtls-rprx-vision لجعله يبدو كمتصفح حقيقي)
    val pbk: String, // المفتاح العام لـ REALITY
    val sid: String, // Session ID
    val fp: String, // بصمة المتصفح (مثلاً chrome)
    val remark: String // اسم العقدة
)

class VlessParser {
    companion object {
        fun parseUri(vlessUri: String): VlessConfig? {
            if (!vlessUri.startsWith("vless://")) {
                return null
            }

            try {
                // تقسيم الرابط الأساسي
                val uri = URI(vlessUri)
                
                // استخراج الـ UUID (معلومات المستخدم)
                val uuid = uri.userInfo ?: return null
                
                // استخراج الآي بي والمنفذ
                val address = uri.host ?: return null
                val port = uri.port.takeIf { it != -1 } ?: 443
                
                // استخراج الإعدادات المتقدمة (التمويه، SNI، البصمة)
                val queryParams = uri.query?.split("&")?.associate {
                    val (key, value) = it.split("=", limit = 2)
                    key to value
                } ?: emptyMap()

                // استخراج اسم العقدة (ما بعد علامة #)
                val remark = uri.fragment ?: "VLESS Node"

                return VlessConfig(
                    uuid = uuid,
                    address = address,
                    port = port,
                    encryption = queryParams["encryption"] ?: "none",
                    network = queryParams["type"] ?: "tcp",
                    type = queryParams["security"] ?: "tls",
                    sni = queryParams["sni"] ?: "",
                    flow = queryParams["flow"] ?: "",
                    pbk = queryParams["pbk"] ?: "",
                    sid = queryParams["sid"] ?: "",
                    fp = queryParams["fp"] ?: "chrome", // بصمة كروم الافتراضية للثقة
                    remark = remark
                )
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        }
    }
}
