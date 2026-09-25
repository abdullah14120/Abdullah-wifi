package com.abdullah.wifibridge.server

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.*
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

class SmartProxyServer(
    private val context: Context,
    private val port: Int = 8080
) {

    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val threadPool = Executors.newCachedThreadPool()

    fun start() {
        if (isRunning) return
        isRunning = true

        scope.launch {
            try {
                // Binding على كل الواجهات للاستماع لطلبات العملاء
                serverSocket = ServerSocket(port, 100, InetAddress.getByName("0.0.0.0"))
                Log.d("WiFiBridgeProxy", "Proxy Server started on port $port")

                while (isRunning) {
                    val clientSocket = serverSocket?.accept() ?: break
                    threadPool.execute { handleClient(clientSocket) }
                }
            } catch (e: Exception) {
                Log.e("WiFiBridgeProxy", "Server error: ${e.message}")
            }
        }
    }

    private fun handleClient(clientSocket: Socket) {
        try {
            val clientIn = clientSocket.getInputStream()
            val clientOut = clientSocket.getOutputStream()

            // قراءة رأس الطلب (HTTP Request Header)
            val headerBuffer = ByteArray(8192)
            val bytesRead = clientIn.read(headerBuffer)
            if (bytesRead <= 0) {
                clientSocket.close()
                return
            }

            val requestHeader = String(headerBuffer, 0, bytesRead)
            val firstLine = requestHeader.lines().firstOrNull() ?: ""
            val parts = firstLine.split(" ")

            if (parts.size < 2) {
                clientSocket.close()
                return
            }

            val method = parts[0]
            val hostPort = parseHostAndPort(parts[1], requestHeader)

            if (hostPort != null) {
                // جلب شبكة الإنترنت النشطة بالجهاز (سواء كانت Cellular Data أو Wi-Fi خارجي)
                val internetNetwork = getInternetNetwork(context)

                // فتح Socket باتجاه السيرفر الخارجي
                val targetSocket = Socket()
                
                // 🔥 السر هنا: ربط الـ Socket بالشبكة التي توفر إنترنت حقيقي وقاطع للشك!
                internetNetwork?.bindSocket(targetSocket)
                
                targetSocket.connect(java.net.InetSocketAddress(hostPort.first, hostPort.second), 10000)

                if (method.equals("CONNECT", ignoreCase = true)) {
                    // معالجة HTTPS Tunneling
                    clientOut.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
                    clientOut.flush()
                    relayData(clientSocket, targetSocket)
                } else {
                    // معالجة HTTP Normal Request
                    val targetOut = targetSocket.getOutputStream()
                    targetOut.write(headerBuffer, 0, bytesRead)
                    targetOut.flush()
                    relayData(clientSocket, targetSocket)
                }
            } else {
                clientSocket.close()
            }
        } catch (e: Exception) {
            runCatching { clientSocket.close() }
        }
    }

    // الدالة المسؤولة عن تحديد واجهة الإنترنت الحقيقية في جهاز أندرويد
    private fun getInternetNetwork(context: Context): Network? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        for (network in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            // البحث عن شبكة تمتلك إنترنت حقيقي وليست شبكة الـ P2P/Wi-Fi Direct الخاصة بنا
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            ) {
                return network
            }
        }
        // كحل إحتياطي أخير إذا لم تكن هناك شبكة مصدقة
        return cm.activeNetwork
    }

    private fun parseHostAndPort(uri: String, fullHeader: String): Pair<String, Int>? {
        return try {
            if (uri.contains(":")) {
                val parts = uri.split(":")
                val host = parts[0].replace("http://", "").replace("https://", "")
                val port = parts[1].toIntOrNull() ?: 443
                Pair(host, port)
            } else {
                // البحث في كود Host: inside headers
                val hostLine = fullHeader.lines().firstOrNull { it.startsWith("Host:", ignoreCase = true) }
                val host = hostLine?.substringAfter(":")?.trim() ?: uri
                Pair(host, 80)
            }
        } catch (e: Exception) {
            null
        }
    }

    // نفق لنقل البيانات بالاتجاهين بسرعة عالية وبأداء ممتاز (Bidirectional Relay)
    private fun relayData(socketA: Socket, socketB: Socket) {
        val t1 = Thread { pipeStream(socketA.getInputStream(), socketB.getOutputStream()) }
        val t2 = Thread { pipeStream(socketB.getInputStream(), socketA.getOutputStream()) }
        t1.start()
        t2.start()
    }

    private fun pipeStream(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(16384) // 16KB Buffer لأداء سريع
        var read: Int
        try {
            while (input.read(buffer).also { read = it } != -1) {
                output.write(buffer, 0, read)
                output.flush()
            }
        } catch (_: Exception) {}
    }

    fun stop() {
        isRunning = false
        runCatching { serverSocket?.close() }
        threadPool.shutdown()
        scope.cancel()
    }
}
