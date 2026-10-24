package com.abdullah.wifibridge.data.network

import org.json.JSONArray
import org.json.JSONObject

class XrayConfigGenerator {
    companion object {
        fun generateCleanTunnelConfig(config: VlessConfig, localPort: Int = 10808): String {
            val root = JSONObject()

            // 1. إعدادات السجلات (تقليل الضوضاء لزيادة الأداء)
            root.put("log", JSONObject().apply {
                put("loglevel", "warning")
            })

            // 2. البوابة المحلية (HTTP Inbound) لاستقبال الحزم من LittleProxy
            val httpInbound = JSONObject().apply {
                put("tag", "http-in")
                put("port", localPort)
                put("listen", "127.0.0.1")
                put("protocol", "http")
                put("settings", JSONObject().apply {
                    put("allowTransparent", false)
                })
                // فحص النطاق للتوجيه فقط دون استبداله بـ IP محلي (منع تسرب DNS)
                put("sniffing", JSONObject().apply {
                    put("enabled", true)
                    put("destOverride", JSONArray().put("http").put("tls"))
                    put("routeOnly", true)
                })
            }
            root.put("inbounds", JSONArray().put(httpInbound))

            // 3. البوابة الخارجية الرئيسية (VLESS Outbound)
            val vlessOutbound = JSONObject().apply {
                put("tag", "proxy")
                put("protocol", "vless")
                put("settings", JSONObject().apply {
                    val vnext = JSONObject().apply {
                        put("address", config.address)
                        put("port", config.port)
                        val user = JSONObject().apply {
                            put("id", config.uuid)
                            put("encryption", config.encryption)
                            if (config.flow.isNotEmpty() && config.security == "reality") {
                                put("flow", config.flow)
                            }
                        }
                        put("users", JSONArray().put(user))
                    }
                    put("vnext", JSONArray().put(vnext))
                })

                // إعدادات النقل والتخفي (دعم تلقائي لـ Cloudflare WS و Reality)
                put("streamSettings", JSONObject().apply {
                    put("network", config.network)
                    put("security", config.security)

                    if (config.security == "tls") {
                        put("tlsSettings", JSONObject().apply {
                            put("serverName", config.sni)
                            put("fingerprint", config.fp) // محاكاة بصمة متصفح حقيقي
                            put("allowInsecure", false)
                        })
                    } else if (config.security == "reality") {
                        put("realitySettings", JSONObject().apply {
                            put("serverName", config.sni)
                            put("publicKey", config.pbk)
                            put("shortId", config.sid)
                            put("fingerprint", config.fp)
                        })
                    }

                    if (config.network == "ws") {
                        put("wsSettings", JSONObject().apply {
                            put("path", config.path)
                            put("headers", JSONObject().apply {
                                put("Host", config.host)
                            })
                        })
                    }
                })
            }

            // 4. بوابة الحجب (Blackhole) لمنع تسرب WebRTC/STUN
            val blockOutbound = JSONObject().apply {
                put("tag", "block")
                put("protocol", "blackhole")
            }

            root.put("outbounds", JSONArray().put(vlessOutbound).put(blockOutbound))

            // 5. قواعد التوجيه (Routing) لتوحيد الـ DNS وإغلاق منافذ STUN
            root.put("routing", JSONObject().apply {
                // AsIs: إرسال اسم النطاق كما هو للسيرفر الخارجي ليتم حله هناك (Zero DNS Leak)
                put("domainStrategy", "AsIs")
                val rules = JSONArray()

                // القاعدة 1: حجب منافذ STUN/TURN المسؤولة عن تسرب WebRTC
                rules.put(JSONObject().apply {
                    put("type", "field")
                    put("port", "3478,5349,19302-19309")
                    put("outboundTag", "block")
                })

                // القاعدة 2: توجيه كافة الاتصالات الأخرى عبر نفق VLESS
                rules.put(JSONObject().apply {
                    put("type", "field")
                    put("network", "tcp,udp")
                    put("outboundTag", "proxy")
                })

                put("rules", rules)
            })

            return root.toString(4)
        }
    }
}
