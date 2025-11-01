package com.chesskel.ui.pvp

import android.app.AlertDialog
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.chesskel.R
import com.chesskel.game.ChessEngine
import com.chesskel.game.GameEventListener
import com.chesskel.game.GameResult
import com.chesskel.game.Move
import com.chesskel.game.Side
import com.chesskel.net.LanSession
import com.chesskel.net.UdpDiscovery
import com.chesskel.ui.game.ChessBoardView
import com.chesskel.util.SoundManager

/**
 * PvP networked game screen (LAN).
 * AI code remains untouched.
 */
class PvpGameActivity : ComponentActivity(), GameEventListener {

    private val engine = ChessEngine()
    private lateinit var chessBoard: ChessBoardView
    private lateinit var tvInfo: TextView
    private lateinit var tvMoves: TextView
    private lateinit var btnResign: Button

    private var mySide: Side? = null
    private var lan: LanSession? = null
    private var broadcaster: UdpDiscovery.HostBroadcaster? = null
    private val movesList = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pvp_game)

        SoundManager.init(this)

        chessBoard = findViewById(R.id.chessBoard)
        tvInfo = findViewById(R.id.tvGameInfo)
        tvMoves = findViewById(R.id.tvMoves)
        btnResign = findViewById(R.id.btnResign)

        engine.eventListener = this
        chessBoard.bindEngine(engine)

        val roleStr = intent.getStringExtra("role") ?: "host"
        val role = if (roleStr == "client") LanSession.Role.CLIENT else LanSession.Role.HOST
        val hostIp = intent.getStringExtra("host_ip")
        val hostPlaysWhite = intent.getBooleanExtra("host_white", true)

        // Start LAN session
        lan = LanSession(
            role = role,
            hostIp = hostIp,
            port = LanSession.DEFAULT_PORT,
            hostPlaysWhite = hostPlaysWhite,
            listener = object : LanSession.Listener {
                override fun onConnected(peerIp: String, iPlayWhite: Boolean) {
                    runOnUiThread {
                        mySide = if (iPlayWhite) Side.WHITE else Side.BLACK
                        // Tell the board which side the human controls
                        chessBoard.humanIsWhite = iPlayWhite
                        val sideTxt = if (iPlayWhite) getString(R.string.white) else getString(R.string.black)
                        tvInfo.text = getString(R.string.connected_status, peerIp, sideTxt)
                        // Stop broadcasting once someone connects
                        broadcaster?.stop()
                        broadcaster = null
                        refreshUi()
                    }
                }

                override fun onMove(fromR: Int, fromC: Int, toR: Int, toC: Int, promotion: Char?) {
                    val m = Move(fromR, fromC, toR, toC, promotion)
                    runOnUiThread { performMove(m, isLocal = false) }
                }

                override fun onPeerLeft(reason: String?) {
                    runOnUiThread {
                        Toast.makeText(this@PvpGameActivity, reason ?: "Disconnected", Toast.LENGTH_LONG).show()
                        finish()
                    }
                }

                override fun onError(message: String) {
                    runOnUiThread {
                        Toast.makeText(this@PvpGameActivity, message, Toast.LENGTH_LONG).show()
                        // do not finish; allow retry/observation
                    }
                }
            }
        ).also { it.start() }

        // If I am host, broadcast my presence for clients' Scan
        if (role == LanSession.Role.HOST) {
            val ip = LanSession.getLocalIpv4()
            if (ip != null) {
                broadcaster = UdpDiscovery.HostBroadcaster(
                    hostIp = ip,
                    tcpPort = LanSession.DEFAULT_PORT,
                    name = "ChessKel Host ($ip)"
                ).also { it.start() }
            }
        }

        chessBoard.onMove = { m ->
            runOnUiThread {
                // Ensure handshake finished and it's my turn
                if (mySide == null || lan?.isConnected() != true) {
                    Toast.makeText(this, "Connecting… please wait", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                if (!isMyTurn()) {
                    Toast.makeText(this, getString(R.string.not_your_piece), Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                if (m.promotion != null) {
                    showPromotionDialog(m)
                } else {
                    performMove(m, isLocal = true)
                }
            }
        }

        btnResign.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.btnResgin))
                .setMessage(getString(R.string.resign_confirm))
                .setPositiveButton(getString(R.string.yes)) { d, _ ->
                    try { lan?.resign() } catch (_: Exception) {}
                    d.dismiss()
                    finish()
                }
                .setNegativeButton(getString(R.string.no)) { d, _ -> d.dismiss() }
                .show()
        }

        refreshUi()
    }

    private fun isMyTurn(): Boolean {
        val me = mySide ?: return false
        return (engine.whiteToMove && me == Side.WHITE) || (!engine.whiteToMove && me == Side.BLACK)
    }

    private fun showPromotionDialog(baseMove: Move) {
        val labels = arrayOf("Queen", "Knight", "Rook", "Bishop")
        AlertDialog.Builder(this)
            .setTitle("Promote to")
            .setItems(labels) { d, which ->
                val isWhite = engine.pieceAt(baseMove.fromR, baseMove.fromC).isUpperCase()
                val promoChar = when (which) {
                    0 -> if (isWhite) 'Q' else 'q'
                    1 -> if (isWhite) 'N' else 'n'
                    2 -> if (isWhite) 'R' else 'r'
                    else -> if (isWhite) 'B' else 'b'
                }
                performMove(
                    Move(baseMove.fromR, baseMove.fromC, baseMove.toR, baseMove.toC, promoChar),
                    isLocal = true
                )
                d.dismiss()
            }
            .show()
    }

    private fun performMove(m: Move, isLocal: Boolean) {
        val my = mySide
        if (my == null) {
            Toast.makeText(this, "Not connected", Toast.LENGTH_SHORT).show()
            return
        }

        // Validate: side/turn and legality
        val mover = engine.pieceAt(m.fromR, m.fromC)
        val moverIsWhite = mover != '.' && mover.isUpperCase()
        val iAmWhite = (my == Side.WHITE)

        if (isLocal) {
            if (mover == '.' || moverIsWhite != iAmWhite || !isMyTurn()) {
                Toast.makeText(this, getString(R.string.not_your_piece), Toast.LENGTH_SHORT).show()
                return
            }
        } else {
            // Remote move must come from opponent and be legal in current state
            if (mover == '.' || moverIsWhite == iAmWhite) {
                Toast.makeText(this, "Out of sync (wrong side)", Toast.LENGTH_SHORT).show()
                return
            }
            val isLegal = engine.legalMovesFor(m.fromR, m.fromC).any {
                it.fromR == m.fromR && it.fromC == m.fromC && it.toR == m.toR && it.toC == m.toC && it.promotion == m.promotion
            }
            if (!isLegal) {
                Toast.makeText(this, "Out of sync (illegal move)", Toast.LENGTH_SHORT).show()
                return
            }
        }

        val isCapture = engine.pieceAt(m.toR, m.toC) != '.'
        val san = engine.moveToString(m)
        engine.applyMove(m)
        chessBoard.lastMove = m

        val opponentIsWhite = engine.whiteToMove

        when {
            engine.isCheckmate(opponentIsWhite) -> SoundManager.playGameOver()
            engine.isKingInCheck(opponentIsWhite) -> SoundManager.playCheck()
            m.promotion != null -> SoundManager.playPromote()
            isCapture -> SoundManager.playCapture()
            else -> SoundManager.playMove()
        }

        chessBoard.invalidate()
        movesList.add(san)
        updateMovesText()
        refreshUi()

        if (isLocal) {
            if (lan?.isConnected() == true) {
                try { lan?.sendMove(m.fromR, m.fromC, m.toR, m.toC, m.promotion) } catch (e: Exception) {
                    Toast.makeText(this, "Send failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, "Peer not connected yet", Toast.LENGTH_SHORT).show()
            }
        }

        if (engine.isCheckmate(opponentIsWhite)) {
            val winner = if (!opponentIsWhite) "White" else "Black"
            AlertDialog.Builder(this)
                .setTitle("Game Over")
                .setMessage("Checkmate! $winner wins")
                .setPositiveButton("OK") { d, _ -> d.dismiss(); finish() }
                .show()
        }
    }

    private fun updateMovesText() {
        val sb = StringBuilder()
        var moveNumber = 1
        var i = 0
        while (i < movesList.size) {
            sb.append("$moveNumber. ")
            sb.append(movesList[i])
            i++
            if (i < movesList.size) {
                sb.append("    ")
                sb.append(movesList[i])
                i++
            }
            sb.append("\n")
            moveNumber++
        }
        tvMoves.text = sb.toString()
    }

    private fun refreshUi() {
        tvInfo.text = if (engine.whiteToMove) getString(R.string.white_to_move) else getString(R.string.black_to_move)
    }

    override fun onCheck(side: Side) {
        SoundManager.playCheck()
    }

    override fun onGameEnd(result: GameResult) {
        SoundManager.playGameOver()
        val msg = when (result) {
            GameResult.WHITE_WINS -> "White wins"
            GameResult.BLACK_WINS -> "Black wins"
            GameResult.STALEMATE -> "Stalemate"
            GameResult.DRAW -> "Draw"
        }
        AlertDialog.Builder(this)
            .setTitle("Game Over")
            .setMessage(msg)
            .setPositiveButton("OK") { d, _ -> d.dismiss(); finish() }
            .show()
    }

    // Keep session alive unless Activity is destroyed
    override fun onDestroy() {
        super.onDestroy()
        broadcaster?.stop()
        broadcaster = null
        lan?.close()
        SoundManager.release()
    }
}