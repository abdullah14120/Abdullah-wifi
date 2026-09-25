package com.abdullah.wifibridge.server

import com.abdullah.wifibridge.model.NetworkConfig
import kotlinx.coroutines.*
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

class DualProxyEngine(private val config: NetworkConfig) {

    private var httpServerSocket: ServerSocket? = null
    private var socksServerSocket: ServerSocket? = null
    private var isRunning = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val threadPool = Executors.newCachedThreadPool()

    fun start() {
        isRunning = true
        
        // 1. تشغيل سيرفر HTTP/HTTPS Proxy
        scope.launch {
            try {
                val bindAddr = InetAddress.getByName("0.0.0.0")
                httpServerSocket = ServerSocket(config.httpPort, 100, bindAddr)
                while (isRunning) {
                    val client = httpServerSocket?.accept() ?: break
                    threadPool.execute { handleHttpClient(client) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. تشغيل سيرفر SOCKS5 Proxy (للألعاب وحركة الترافيك الشاملة)
        scope.launch {
            try {
                val bindAddr = InetAddress.getByName("0.0.0.0")
                socksServerSocket = ServerSocket(config.socksPort, 100, bindAddr)
                while (isRunning) {
                    val client = socksServerSocket?.accept() ?: break
                    threadPool.execute { handleSocks5Client(client) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun handleHttpClient(clientSocket: Socket) {
        try {
            val input = clientSocket.getInputStream()
            val output = clientSocket.getOutputStream()
            val header = readLine(input)

            if (header.startsWith("CONNECT")) {
                // معالجة نفق HTTPS
                val parts = header.split(" ")
                if (parts.size >= 2) {
                    val targetHostPort = parts[1].split(":")
                    val targetHost = targetHostPort[0]
                    val targetPort = if (targetHostPort.size > 1) targetHostPort[1].toInt() else 443

                    val targetSocket = Socket(targetHost, targetPort)
                    output.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
                    output.flush()

                    relayData(clientSocket, targetSocket)
                }
            }
        } catch (_: Exception) {
            runCatching { clientSocket.close() }
        }
    }

    private fun handleSocks5Client(clientSocket: Socket) {
        try {
            val input = clientSocket.getInputStream()
            val output = clientSocket.getOutputStream()

            // SOCKS5 Handshake Initializing
            val version = input.read()
            if (version == 5) {
                val numMethods = input.read()
                val methods = ByteArray(numMethods)
                input.read(methods)

                // الرد بـ No Authentication Required (0x00)
                output.write(byteArrayOf(0x05, 0x00))
                output.flush()

                // قراءة الطلب التالِي
                val ver = input.read()
                val cmd = input.read() // 0x01 = CONNECT
                input.read() // Reserved
                val addrType = input.read()

                var targetHost = ""
                if (addrType == 0x01) { // IPv4
                    val ipBytes = ByteArray(4)
                    input.read(ipBytes)
                    targetHost = InetAddress.getByAddress(ipBytes).hostAddress ?: ""
                } else if (addrType == 0x03) { // Domain Name
                    val domainLen = input.read()
                    val domainBytes = ByteArray(domainLen)
                    input.read(domainBytes)
                    targetHost = String(domainBytes)
                }

                val portByte1 = input.read()
                val portByte2 = input.read()
                val targetPort = (portByte1 shl 8) or portByte2

                if (cmd == 1) { // CONNECT Command
                    val targetSocket = Socket(targetHost, targetPort)
                    // الرد بأسلوب SOCKS5 Success
                    val response = byteArrayOf(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0)
                    output.write(response)
                    output.flush()

                    relayData(clientSocket, targetSocket)
                }
            }
        } catch (_: Exception) {
            runCatching { clientSocket.close() }
        }
    }

    private fun relayData(socketA: Socket, socketB: Socket) {
        val t1 = Thread { pipeStream(socketA.getInputStream(), socketB.getOutputStream()) }
        val t2 = Thread { pipeStream(socketB.getInputStream(), socketA.getOutputStream()) }
        t1.start()
        t2.start()
    }

    private fun pipeStream(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(16384)
        var read: Int
        try {
            while (input.read(buffer).also { read = it } != -1) {
                output.write(buffer, 0, read)
                output.flush()
            }
        } catch (_: Exception) {}
    }

    private fun readLine(input: InputStream): String {
        val sb = StringBuilder()
        var c: Int
        while (input.read().also { c = it } != -1) {
            if (c == '\n'.code) break
            if (c != '\r'.code) sb.append(c.toChar())
        }
        return sb.toString()
    }

    fun stop() {
        isRunning = false
        runCatching { httpServerSocket?.close() }
        runCatching { socksServerSocket?.close() }
        threadPool.shutdown()
        scope.cancel()
    }
}
