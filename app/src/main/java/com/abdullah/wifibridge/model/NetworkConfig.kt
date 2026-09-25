package com.abdullah.wifibridge.model

data class NetworkConfig(
    val subnetX: Int = 10,        // الرقم المحدد بعد 168 (e.g. 192.168.10.Y)
    val hostY: Int = 1,           // آي بي جهاز البث نفسه (e.g. 192.168.10.1)
    val gatewayY: Int = 254,      // آي بي الراوتر/التوجيه (e.g. 192.168.10.254)
    val httpPort: Int = 8080,     // بورت البروكسي العادي HTTP
    val socksPort: Int = 1080     // بورت Socks5 المتقدم للألعاب والـ UDP
) {
    val localIpAddress: String get() = "192.168.$subnetX.$hostY"
    val routerGatewayAddress: String get() = "192.168.$subnetX.$gatewayY"
    val clientIpPrefix: String get() = "192.168.$subnetX."
}
