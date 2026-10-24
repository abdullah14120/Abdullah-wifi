package com.abdullah.wifibridge.data.network

import java.net.URI
import java.net.URLDecoder

data class VlessConfig(
    val uuid: String,
    val address: String,
    val port: Int,
    val encryption: String,
    val network: String,   // tcp, ws, grpc
    val security: String,  // tls, reality, none
    val sni: String,       // Server Name Indication
    val host: String,      // Host header (مهم لـ Cloudflare Workers)
    val path: String,      // WebSocket path
    val flow: String,      // xtls-rprx-vision
    val pbk: String,       // Reality Public Key
    val sid: String,       // Reality Short ID
    val fp: String,        // TLS Fingerprint (chrome, safari, ios)
    val remark: String
)

class VlessParser {
    companion object {
        fun parseUri(vlessUri: String): VlessConfig? {
            val cleanUri = vlessUri.trim()
            if (!cleanUri.startsWith("vless://")) return null

            return try {
                val uri = URI(cleanUri)
                val uuid = uri.userInfo ?: return null
                val address = uri.host ?: return null
                val port = if (uri.port != -1) uri.port else 443

                val queryParams = uri.rawQuery?.split("&")?.mapNotNull { param ->
                    val parts = param.split("=", limit = 2)
                    if (parts.size == 2) {
                        val key = URLDecoder.decode(parts[0], "UTF-8")
                        val value = URLDecoder.decode(parts[1], "UTF-8")
                        key to value
                    } else null
                }?.toMap() ?: emptyMap()

                val remark = uri.rawFragment?.let { URLDecoder.decode(it, "UTF-8") } ?: "VLESS_Clean_Node"
                val hostHeader = queryParams["host"] ?: ""
                val sniValue = queryParams["sni"]?.ifEmpty { hostHeader } ?: hostHeader.ifEmpty { address }

                VlessConfig(
                    uuid = uuid,
                    address = address,
                    port = port,
                    encryption = queryParams["encryption"] ?: "none",
                    network = queryParams["type"] ?: "tcp",
                    security = queryParams["security"] ?: "tls",
                    sni = sniValue,
                    host = hostHeader.ifEmpty { sniValue },
                    path = queryParams["path"]?.ifEmpty { "/" } ?: "/",
                    flow = queryParams["flow"] ?: "",
                    pbk = queryParams["pbk"] ?: "",
                    sid = queryParams["sid"] ?: "",
                    fp = queryParams["fp"]?.ifEmpty { "chrome" } ?: "chrome",
                    remark = remark
                )
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}
