package com.chesskel.net

import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Lightweight TCP LAN session for two players on the same network.
 * Protocol: line-delimited JSON messages.
 *
 * Messages:
 *  - {"type":"hello","white":true}           // host sends side on connect
 *  - {"type":"move","fr":0,"fc":4,"tr":4,"tc":4,"promo":"q" | null}
 *  - {"type":"resign"}
 */
class LanSession(
    private val role: Role,
    private val hostIp: String?,            // required for client
    private val port: Int = DEFAULT_PORT,
    private val hostPlaysWhite: Boolean = true, // only used by host
    private val listener: Listener
) {

    enum class Role { HOST, CLIENT }

    interface Listener {
        fun onConnected(peerIp: String, iPlayWhite: Boolean)
        fun onMove(fromR: Int, fromC: Int, toR: Int, toC: Int, promotion: Char?)
        fun onPeerLeft(reason: String? = null)
        fun onError(message: String)
    }

    private val tag = "LanSession"
    private var serverSocket: ServerSocket? = null
    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null
    private val closed = AtomicBoolean(false)
    @Volatile private var connected = false
    private val writerLock = Any()
    private val sendExecutor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "LanSession-Send").apply { isDaemon = true }
    }

    fun isConnected(): Boolean = connected && !closed.get()

    fun start() {
        when (role) {
            Role.HOST -> startAsHost()
            Role.CLIENT -> startAsClient()
        }
    }

    private fun startAsHost() {
        thread(name = "LanSession-Host") {
            try {
                Log.d(tag, "HOST: Listening on port $port")
                serverSocket = ServerSocket(port)
                val s = serverSocket!!.accept()
                Log.d(tag, "HOST: Accepted ${s.inetAddress.hostAddress}")
                if (closed.get()) {
                    s.close()
                    return@thread
                }
                onSocketReady(s)
                // Mark connected now so we allow outbound writes; send hello first
                connected = true
                // Send hello as the very first line
                sendHello(hostPlaysWhite)
                listener.onConnected(s.inetAddress.hostAddress ?: "unknown", hostPlaysWhite)
                readLoop()
            } catch (e: Exception) {
                if (!closed.get()) {
                    Log.e(tag, "HOST error", e)
                    listener.onError("Host error: ${e.message}")
                }
                close()
            }
        }
    }

    private fun startAsClient() {
        val ip = hostIp ?: run {
            listener.onError("Missing host IP")
            return
        }
        thread(name = "LanSession-Client") {
            try {
                Log.d(tag, "CLIENT: Connecting to $ip:$port")
                val s = Socket(ip, port).apply {
                    try { tcpNoDelay = true; keepAlive = true } catch (_: Exception) {}
                }
                if (closed.get()) {
                    s.close()
                    return@thread
                }
                onSocketReady(s)
                // Wait for host hello
                val hello = try { reader?.readLine() } catch (_: Exception) { null }
                Log.d(tag, "CLIENT: First line: $hello")
                if (hello == null) {
                    listener.onError("Host closed before hello")
                    close()
                    return@thread
                }
                try {
                    val obj = JSONObject(hello)
                    if (obj.optString("type") != "hello") {
                        listener.onError("Unexpected first message")
                        close(); return@thread
                    }
                    val hostIsWhite = obj.optBoolean("white", true)
                    val iPlayWhite = !hostIsWhite
                    connected = true
                    listener.onConnected(s.inetAddress.hostAddress ?: "unknown", iPlayWhite)
                } catch (pe: Exception) {
                    listener.onError("Handshake parse error: ${pe.message}")
                    close(); return@thread
                }
                readLoop()
            } catch (e: Exception) {
                if (!closed.get()) {
                    Log.e(tag, "CLIENT error", e)
                    listener.onError("Client error: ${e.message}")
                }
                close()
            }
        }
    }

    private fun onSocketReady(s: Socket) {
        try { s.tcpNoDelay = true; s.keepAlive = true } catch (_: Exception) {}
        socket = s
        writer = PrintWriter(s.getOutputStream(), true) // auto-flush
        reader = BufferedReader(InputStreamReader(s.getInputStream()))
    }

    private fun readLoop() {
        try {
            while (!closed.get()) {
                val line = try { reader?.readLine() } catch (_: Exception) {
                    // transient read error; keep trying
                    if (!closed.get()) continue else null
                } ?: break // EOF
                try {
                    val obj = JSONObject(line)
                    when (obj.optString("type")) {
                        "move" -> {
                            val fr = obj.getInt("fr")
                            val fc = obj.getInt("fc")
                            val tr = obj.getInt("tr")
                            val tc = obj.getInt("tc")
                            // optString needs a non-null fallback; then map empty to null
                            val promoStr = if (obj.isNull("promo")) null else obj.optString("promo", "")
                            val promo: Char? = promoStr?.firstOrNull()
                            listener.onMove(fr, fc, tr, tc, promo)
                        }
                        "resign" -> {
                            listener.onPeerLeft("Opponent resigned")
                            close()
                            return
                        }
                        "hello" -> {
                            // ignore stray hellos
                        }
                        else -> {
                            // ignore unknown
                        }
                    }
                } catch (_: Exception) {
                    // ignore malformed JSON; keep reading
                }
            }
        } catch (_: Exception) {
        } finally {
            if (!closed.get()) {
                connected = false
                listener.onPeerLeft("Disconnected")
                close()
            }
        }
    }

    private fun sendHello(hostIsWhite: Boolean) {
        val obj = JSONObject().put("type", "hello").put("white", hostIsWhite)
        Log.d(tag, "HOST: Sending hello $obj")
        // Write immediately via executor; do not require connected gating for hello
        val payload = obj.toString()
        sendExecutor.execute {
            try {
                synchronized(writerLock) {
                    writer?.println(payload)
                    if (writer?.checkError() == true) {
                        listener.onError("Send failed (connection error)")
                    }
                }
            } catch (e: Exception) {
                if (!closed.get()) listener.onError("Send error: ${e.message}")
            }
        }
    }

    fun sendMove(fromR: Int, fromC: Int, toR: Int, toC: Int, promotion: Char?) {
        val obj = JSONObject()
            .put("type", "move")
            .put("fr", fromR).put("fc", fromC)
            .put("tr", toR).put("tc", toC)
            .put("promo", promotion?.toString())
        safeSend(obj)
    }

    fun resign() {
        val obj = JSONObject().put("type", "resign")
        safeSend(obj)
        close()
    }

    private fun safeSend(obj: JSONObject) {
        if (!isConnected()) return
        val payload = obj.toString()
        sendExecutor.execute {
            try {
                synchronized(writerLock) {
                    writer?.println(payload)
                    if (writer?.checkError() == true) {
                        listener.onError("Send failed (connection error)")
                    }
                }
            } catch (e: Exception) {
                if (!closed.get()) listener.onError("Send error: ${e.message}")
            }
        }
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        // stop sending new tasks and interrupt running
        try { sendExecutor.shutdownNow() } catch (_: Exception) {}
        connected = false
        try { reader?.close() } catch (_: Exception) {}
        try { writer?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        try { serverSocket?.close() } catch (_: Exception) {}
        reader = null
        writer = null
        socket = null
        serverSocket = null
    }

    companion object {
        const val DEFAULT_PORT = 50505

        fun getLocalIpv4(): String? {
            return try {
                val ifaces = NetworkInterface.getNetworkInterfaces()
                for (iface in ifaces) {
                    if (!iface.isUp || iface.isLoopback) continue
                    val addrs = iface.inetAddresses
                    for (addr in addrs) {
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            return addr.hostAddress
                        }
                    }
                }
                null
            } catch (_: Exception) {
                null
            }
        }
    }
}