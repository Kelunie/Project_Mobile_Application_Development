package com.chesskel.ui.pvp

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import com.chesskel.R
import com.chesskel.net.LanSession
import com.chesskel.net.UdpDiscovery

class PvpLobbyActivity : ComponentActivity() {

    private var scanner: UdpDiscovery.ClientScanner? = null
    private val hosts = mutableListOf<UdpDiscovery.HostInfo>()
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var list: ListView
    private lateinit var progress: ProgressBar
    private lateinit var scanBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pvp_lobby)

        val tvIp = findViewById<TextView>(R.id.tvLocalIp)
        val hostBtn = findViewById<Button>(R.id.btnHost)
        val joinBtn = findViewById<Button>(R.id.btnJoin)
        list = findViewById(R.id.listHosts)
        progress = findViewById(R.id.progressScan)
        scanBtn = findViewById(R.id.btnScan)

        val localIp = LanSession.getLocalIpv4() ?: "-"
        @Suppress("SetTextI18n")
        // Display local IP and port (not localized compound label)
        tvIp.text = "${getString(R.string.labelIP)}: $localIp:${LanSession.DEFAULT_PORT}"

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        list.adapter = adapter

        list.setOnItemClickListener { _, _, position, _ ->
            val host = hosts[position]
            startActivity(
                Intent(this, PvpGameActivity::class.java)
                    .putExtra("role", "client")
                    .putExtra("host_ip", host.ip)
                    .putExtra("peer_name", host.name)
            )
        }

        scanBtn.setOnClickListener { startScan() }

        hostBtn.setOnClickListener {
            val items = arrayOf(getString(R.string.white), getString(R.string.black))
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.choose_side))
                .setItems(items) { dlg, which ->
                    val iPlayWhite = (which == 0)
                    // IMPORTANT: Host must go into game screen; that starts broadcasting and TCP server
                    val prefs = getSharedPreferences("chesskel_prefs", MODE_PRIVATE)
                    val userId = prefs.getLong("current_user_id", -1L)
                    val db = com.chesskel.data.DBHelper(this)
                    val myName = if (userId > 0L) db.getUserById(userId)?.nombre else null
                    val hostNameToSend = myName ?: "ChessKel Host ($localIp)"
                    startActivity(
                        Intent(this, PvpGameActivity::class.java)
                            .putExtra("role", "host")
                            .putExtra("host_white", iPlayWhite)
                            .putExtra("host_name", hostNameToSend)
                    )
                    dlg.dismiss()
                }.show()
        }

        joinBtn.setOnClickListener {
            val et = EditText(this).apply {
                hint = "Host IP (e.g. 192.168.1.10)"
                inputType = InputType.TYPE_CLASS_PHONE or InputType.TYPE_NUMBER_FLAG_DECIMAL
            }
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.btnJoin))
                .setView(et)
                .setPositiveButton(getString(R.string.btnJoin)) { dlg, _ ->
                    val ip = et.text.toString().trim()
                    if (ip.isEmpty()) {
                        Toast.makeText(this, getString(R.string.enter_valid_ip), Toast.LENGTH_SHORT).show()
                    } else {
                        startActivity(
                            Intent(this, PvpGameActivity::class.java)
                                .putExtra("role", "client")
                                .putExtra("host_ip", ip)
                        )
                    }
                    dlg.dismiss()
                }
                .setNegativeButton(android.R.string.cancel) { dlg, _ -> dlg.dismiss() }
                .show()
        }
    }

    private fun startScan() {
        scanBtn.isEnabled = false
        progress.visibility = View.VISIBLE
        hosts.clear()
        adapter.clear()

        val scanner = UdpDiscovery.ClientScanner(this, durationMs = 6000L)
        this.scanner = scanner
        scanner.start(object : UdpDiscovery.ScanListener {
            override fun onHostFound(host: UdpDiscovery.HostInfo) {
                runOnUiThread {
                    hosts.add(host)
                    adapter.add("${host.name} • ${host.ip}:${host.tcpPort}")
                    adapter.notifyDataSetChanged()
                }
            }

            override fun onError(message: String) {
                runOnUiThread {
                    Toast.makeText(this@PvpLobbyActivity, message, Toast.LENGTH_LONG).show()
                }
            }

            override fun onDone() {
                runOnUiThread {
                    progress.visibility = View.GONE
                    scanBtn.isEnabled = true
                    if (hosts.isEmpty()) {
                        Toast.makeText(this@PvpLobbyActivity, getString(R.string.no_hosts_found), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        scanner?.stop()
        scanner = null
    }
}