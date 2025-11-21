package com.chesskel.ui.pvp

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.chesskel.R
import com.chesskel.game.*
import com.chesskel.net.LanSession
import com.chesskel.net.UdpDiscovery
import com.chesskel.ui.game.ChessBoardView
import com.chesskel.ui.menu.MainMenuActivity
import com.chesskel.util.SoundManager
import com.chesskel.ui.theme.ThemeUtils
import com.chesskel.ui.theme.CenteredActivity

/**
 * PvP networked game screen (LAN).
 */
class PvpGameActivity : CenteredActivity(), GameEventListener {

    private val engine = ChessEngine()
    private lateinit var chessBoard: ChessBoardView
    private lateinit var tvInfo: TextView
    private lateinit var tvMoves: TextView
    private lateinit var btnResign: Button

    private var mySide: Side? = null
    private var lan: LanSession? = null
    private var broadcaster: UdpDiscovery.HostBroadcaster? = null
    private val movesList = mutableListOf<String>()

    private var gameOverDialog: AlertDialog? = null
    private var rematchRequestedByMe = false
    // Keep the last game-over message so we can re-show the dialog if a rematch is declined
    private var lastGameOverMsg: String? = null
    // Dialog instance for incoming rematch offers so we can dismiss it programmatically
    private var rematchOfferDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeUtils.applySavedTheme(this)
        setCenteredContentView(R.layout.activity_pvp_game)

        SoundManager.init(this)

        chessBoard = findViewById(R.id.chessBoard)
        tvInfo = findViewById(R.id.tvGameInfo)
        tvMoves = findViewById(R.id.tvMoves)
        btnResign = findViewById(R.id.btnResign)

        // Asegurar rotación 180° cuando jugamos con negras (visual y toques)
        chessBoard.rotateForBlack = true

        engine.eventListener = this
        chessBoard.bindEngine(engine)

        val roleStr = intent.getStringExtra("role") ?: "host"
        val role = if (roleStr == "client") LanSession.Role.CLIENT else LanSession.Role.HOST
        val hostIp = intent.getStringExtra("host_ip")
        val hostPlaysWhite = intent.getBooleanExtra("host_white", true)
        // optional display names passed from the lobby
        val peerName = intent.getStringExtra("peer_name")
        val hostName = intent.getStringExtra("host_name")

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
                        // Tell the board which side the human controls -> fija perspectiva
                        chessBoard.humanIsWhite = iPlayWhite
                        val sideTxt = if (iPlayWhite) getString(R.string.white) else getString(R.string.black)
                        // Show peer name if available, otherwise show IP
                        val peerDisplay = peerName ?: peerIp
                        tvInfo.text = getString(R.string.connected_status, peerDisplay, sideTxt)
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
                        Toast.makeText(this@PvpGameActivity, reason ?: getString(R.string.disconect), Toast.LENGTH_LONG).show()
                        // Salida ordenada (cierre LAN en background + volver al menú sin bloquear)
                        exitToMenu()
                    }
                }

                override fun onError(message: String) {
                    runOnUiThread {
                        Toast.makeText(this@PvpGameActivity, message, Toast.LENGTH_LONG).show()
                        // do not finish; allow retry/observation
                    }
                }

                // Rematch protocol
                override fun onRematchRequest() {
                    runOnUiThread {
                        // If we already have an offer dialog, ignore duplicate requests
                        if (rematchOfferDialog?.isShowing == true) return@runOnUiThread
                        val dlg = AlertDialog.Builder(this@PvpGameActivity)
                            .setTitle(getString(R.string.rematch))
                            .setMessage(getString(R.string.rematch_offer))
                            .setPositiveButton(getString(R.string.yes)) { d, _ ->
                                // Accept rematch: inform peer, reset game and dismiss both dialogs
                                try { lan?.respondRematch(true) } catch (_: Exception) {}
                                resetForRematch()
                                try { d.dismiss() } catch (_: Exception) {}
                                rematchOfferDialog = null
                            }
                            .setNegativeButton(getString(R.string.no)) { d, _ ->
                                try { lan?.respondRematch(false) } catch (_: Exception) {}
                                try { d.dismiss() } catch (_: Exception) {}
                                rematchOfferDialog = null
                            }
                            .setOnCancelListener {
                                // treat cancel like decline
                                try { lan?.respondRematch(false) } catch (_: Exception) {}
                                rematchOfferDialog = null
                            }
                            .create()
                        rematchOfferDialog = dlg
                        dlg.show()
                    }
                }

                override fun onRematchAccepted() {
                    runOnUiThread {
                        // Dismiss any incoming-offer dialog if present
                        rematchOfferDialog?.let { try { if (it.isShowing) it.dismiss() } catch (_: Exception) {} }
                        rematchOfferDialog = null
                        if (rematchRequestedByMe) resetForRematch()
                    }
                }

                override fun onRematchDeclined() {
                    runOnUiThread {
                        rematchRequestedByMe = false
                        Toast.makeText(this@PvpGameActivity, getString(R.string.rematch_declined), Toast.LENGTH_SHORT).show()
                        // Dismiss any incoming-offer dialog
                        rematchOfferDialog?.let { try { if (it.isShowing) it.dismiss() } catch (_: Exception) {} }
                        rematchOfferDialog = null
                        // If we had requested a rematch and the peer declined, re-show the game-over dialog
                        // so the user can choose to go to menu or attempt rematch again.
                        lastGameOverMsg?.let { msg ->
                            showGameOverDialog(msg)
                        }
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
                    name = hostName ?: "ChessKel Host ($ip)"
                ).also { it.start() }
            }
        }

        chessBoard.onMove = { m ->
            runOnUiThread {
                // Ensure handshake finished and it's my turn
                if (mySide == null || lan?.isConnected() != true) {
                    Toast.makeText(this, getString(R.string.conecting), Toast.LENGTH_SHORT).show()
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
                    // 1) Enviar resign y cerrar LAN en background
                    try { lan?.resignAsync() } catch (_: Exception) {}
                    // 2) Cerrar pantalla de forma “no bloqueante”
                    try { d.dismiss() } catch (_: Exception) {}
                    exitToMenu()
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
        val labels = arrayOf(getString(R.string.promotionQueen), getString(R.string.promotionKnight),
            getString(R.string.promotionRook), getString(R.string.promotionBishop))
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialogPromote))
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
            Toast.makeText(this, getString(R.string.notConnectTxt), Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, getString(R.string.Outofsyncwrongside), Toast.LENGTH_SHORT).show()
                return
            }
            val isLegal = engine.legalMovesFor(m.fromR, m.fromC).any {
                it.fromR == m.fromR && it.fromC == m.fromC && it.toR == m.toR && it.toC == m.toC && it.promotion == m.promotion
            }
            if (!isLegal) {
                Toast.makeText(this, getString(R.string.Outofsyncillegalmove), Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(this, getString(R.string.send_failed_fmt, e.message ?: ""), Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, getString(R.string.peer_not_connected), Toast.LENGTH_SHORT).show()
            }
        }

        // After sending/applying, check for end-of-game conditions once and show options
        val sideToMoveIsWhite = engine.whiteToMove
        if (engine.isCheckmate(sideToMoveIsWhite)) {
            val msg = if (!sideToMoveIsWhite) getString(R.string.white_wins) else getString(R.string.black_wins)
            showGameOverDialog(msg)
            return
        }
        // Stalemate: no legal moves and not in check
        val noMoves = engine.allLegalMoves(sideToMoveIsWhite).isEmpty()
        if (noMoves && !engine.isKingInCheck(sideToMoveIsWhite)) {
            showGameOverDialog(getString(R.string.stalemate))
            return
        }
    }

    private fun updateMovesText() {
        val sb = StringBuilder()
        var moveNumber = 1
        var i = 0
        while (i < movesList.size) {
            val whiteMove = movesList[i]
            val blackMove = if (i + 1 < movesList.size) movesList[i + 1] else ""

            // Formato de tabla: "%-5s %-8s %s"
            // Col 1: Número de jugada (5 caracteres, alineado a la izquierda)
            // Col 2: Movimiento de blancas (8 caracteres, alineado a la izquierda)
            // Col 3: Movimiento de negras (el resto del espacio)
            sb.append("%-5s%-8s%s\n".format("${moveNumber}.", whiteMove, blackMove))

            i += 2
            moveNumber++
        }
        tvMoves.text = sb.toString()
    }

    private fun refreshUi() {
        tvInfo.text = if (engine.whiteToMove) getString(R.string.white_to_move) else getString(R.string.black_to_move)
    }

    private fun showGameOverDialog(msg: String) {
        if (gameOverDialog?.isShowing == true) return
        // store message to allow re-showing if rematch negotiation fails
        lastGameOverMsg = msg
        // Ensure any rematch-offer dialog is dismissed before showing game-over options
        rematchOfferDialog?.let { try { if (it.isShowing) it.dismiss() } catch (_: Exception) {} }
        rematchOfferDialog = null
        val builder = AlertDialog.Builder(this)
            .setTitle(getString(R.string.game_over_title))
            .setMessage(msg)
            .setCancelable(false)
            .setPositiveButton(getString(R.string.rematch)) { d, _ ->
                rematchRequestedByMe = true
                lan?.requestRematch()
                Toast.makeText(this, getString(R.string.rematch_waiting), Toast.LENGTH_SHORT).show()
                d.dismiss()
            }
            .setNegativeButton(getString(R.string.menu)) { d, _ ->
                d.dismiss()
                exitToMenu()
            }
        gameOverDialog = builder.show()
    }

    private fun exitToMenu() {
        // Dismiss any dialogs first so no window leaks or blocked UI
        try { gameOverDialog?.let { if (it.isShowing) it.dismiss() } } catch (_: Exception) {}
        gameOverDialog = null
        try { rematchOfferDialog?.let { if (it.isShowing) it.dismiss() } } catch (_: Exception) {}
        rematchOfferDialog = null

        // Stop broadcasting immediately
        try { broadcaster?.stop() } catch (_: Exception) {}
        broadcaster = null

        // Best-effort: notify the peer that we're returning to menu
        try { lan?.sendExitToMenu() } catch (_: Exception) {}

        // Capture LAN session and null it to prevent races; close in background.
        val lanToClose = lan
        lan = null

        // Navigate immediately so the user returns to the menu without waiting for network teardown.
        startActivity(Intent(this, MainMenuActivity::class.java))
        finish()

        // Close the LAN session asynchronously (fire-and-forget)
        if (lanToClose != null) {
            Thread {
                try { lanToClose.close() } catch (_: Exception) {}
            }.start()
        }
    }

    private fun resetForRematch() {
        rematchRequestedByMe = false
        // Make sure any lingering game-over dialog (or rematch dialogs) are dismissed
        gameOverDialog?.let {
            try { if (it.isShowing) it.dismiss() } catch (_: Exception) {}
        }
        gameOverDialog = null
        rematchOfferDialog?.let { try { if (it.isShowing) it.dismiss() } catch (_: Exception) {} }
        rematchOfferDialog = null
        lastGameOverMsg = null
        movesList.clear()
        chessBoard.lastMove = null
        engine.reset()
        // Keep same sides as negotiated initially; board side is already set via chessBoard.humanIsWhite
        chessBoard.invalidate()
        tvMoves.text = ""
        refreshUi()
        Toast.makeText(this, getString(R.string.rematch_started), Toast.LENGTH_SHORT).show()
    }

    override fun onCheck(side: Side) {
        SoundManager.playCheck()
    }

    override fun onGameEnd(result: GameResult) {
        SoundManager.playGameOver()
        val msg = when (result) {
            GameResult.WHITE_WINS -> getString(R.string.white_wins)
            GameResult.BLACK_WINS -> getString(R.string.black_wins)
            GameResult.STALEMATE -> getString(R.string.stalemate)
            GameResult.DRAW -> getString(R.string.draw)
        }
        runOnUiThread { showGameOverDialog(msg) }
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