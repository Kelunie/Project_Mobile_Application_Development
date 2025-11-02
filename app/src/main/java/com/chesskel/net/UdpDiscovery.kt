package com.chesskel.net

import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import android.content.Context
import android.net.wifi.WifiManager

/**
 * Lightweight UDP discovery for hosts on the same network.
 *
 * Host: periodically broadcasts a small JSON to 255.255.255.255 on UDP_PORT.
 * Client: listens on UDP_PORT and collects discovered hosts.
 *
 * JSON payload:
 *   {
 *     "type": "chesskel_host",
 *     "name": "Host on <ip>",
 *     "ip": "<host-ip>",
 *     "tcp": 50505
 *   }
 */
object UdpDiscovery {
    const val UDP_PORT = 50506
    const val INTERVAL_MS = 1000L

    class HostBroadcaster(
        private val hostIp: String,
        private val tcpPort: Int,
        private val name: String = "ChessKel Host"
    ) {
        private val running = AtomicBoolean(false)
        private var worker: Thread? = null

        fun start() {
            if (!running.compareAndSet(false, true)) return
            worker = thread(name = "UdpHostBroadcaster") {
                var socket: DatagramSocket? = null
                try {
                    socket = DatagramSocket(null).apply {
                        reuseAddress = true
                        // bind ephemeral port
                        bind(null)
                        try { broadcast = true } catch (_: Exception) {}
                    }
                    // Precompute broadcast targets: global 255.255.255.255 + per-interface broadcast addresses
                    val targets = mutableSetOf<InetAddress>()
                    try { targets.add(InetAddress.getByName("255.255.255.255")) } catch (_: Exception) {}
                    try {
                        val ifaces = NetworkInterface.getNetworkInterfaces()
                        for (iface in ifaces) {
                            if (!iface.isUp || iface.isLoopback) continue
                            for (ia in iface.interfaceAddresses) {
                                val b = ia.broadcast
                                if (b != null) targets.add(b)
                            }
                        }
                    } catch (_: Exception) {}

                    while (running.get()) {
                        val obj = JSONObject()
                            .put("type", "chesskel_host")
                            .put("name", name)
                            .put("ip", hostIp)
                            .put("tcp", tcpPort)
                        val data = obj.toString().toByteArray(Charsets.UTF_8)
                        for (addr in targets) {
                            val packet = DatagramPacket(data, data.size, addr, UDP_PORT)
                            try { socket.send(packet) } catch (_: Exception) {}
                        }
                        Thread.sleep(INTERVAL_MS)
                    }
                } catch (_: Exception) {
                } finally {
                    try { socket?.close() } catch (_: Exception) {}
                }
            }
        }

        fun stop() {
            running.set(false)
            worker?.interrupt()
            worker = null
        }
    }

    data class HostInfo(
        val name: String,
        val ip: String,
        val tcpPort: Int,
        val lastSeen: Long = System.currentTimeMillis()
    )

    interface ScanListener {
        fun onHostFound(host: HostInfo)
        fun onError(message: String)
        fun onDone()
    }

    class ClientScanner(private val context: Context? = null, private val durationMs: Long = 6000L) {
        private val running = AtomicBoolean(false)
        private val seen = ConcurrentHashMap<String, HostInfo>() // key by ip:tcp
        private var worker: Thread? = null
        private var multicastLock: WifiManager.MulticastLock? = null

        fun start(listener: ScanListener) {
            if (!running.compareAndSet(false, true)) return
            worker = thread(name = "UdpClientScanner") {
                var socket: DatagramSocket? = null
                try {
                    // Acquire multicast lock to receive broadcast/multicast packets on Wi‑Fi
                    try {
                        if (multicastLock == null && context != null) {
                            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                            multicastLock = wifi.createMulticastLock("ChessKelDiscovery").apply {
                                setReferenceCounted(true)
                                acquire()
                            }
                        }
                    } catch (_: Exception) {}

                    socket = DatagramSocket(null).apply {
                        reuseAddress = true
                        bind(InetSocketAddress(UDP_PORT))
                        soTimeout = 1000
                        try { broadcast = true } catch (_: Exception) {}
                    }
                    val buf = ByteArray(2048)
                    val start = System.currentTimeMillis()
                    while (running.get() && System.currentTimeMillis() - start < durationMs) {
                        val packet = DatagramPacket(buf, buf.size)
                        try {
                            socket.receive(packet)
                        } catch (_: SocketException) {
                            break
                        } catch (_: Exception) {
                            // timeout or minor
                            continue
                        }
                        val str = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        try {
                            val obj = JSONObject(str)
                            if (obj.optString("type") == "chesskel_host") {
                                val ip = obj.optString("ip", packet.address?.hostAddress ?: "unknown")
                                val tcp = obj.optInt("tcp", 50505)
                                val name = obj.optString("name", "ChessKel Host")
                                val key = "$ip:$tcp"
                                val info = HostInfo(name, ip, tcp, System.currentTimeMillis())
                                if (seen.putIfAbsent(key, info) == null) {
                                    listener.onHostFound(info)
                                }
                            }
                        } catch (_: Exception) {
                            // ignore invalid packet
                        }
                    }
                    listener.onDone()
                } catch (e: Exception) {
                    listener.onError("Scan error: ${e.message}")
                } finally {
                    running.set(false)
                    try { socket?.close() } catch (_: Exception) {}
                    try {
                        multicastLock?.let { if (it.isHeld) it.release() }
                    } catch (_: Exception) {}
                    multicastLock = null
                }
            }
        }

        fun stop() {
            running.set(false)
            worker?.interrupt()
            worker = null
        }
    }
}