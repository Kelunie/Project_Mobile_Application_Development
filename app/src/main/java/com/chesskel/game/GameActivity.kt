// kotlin
package com.chesskel.ui.game

import android.app.AlertDialog
import android.media.MediaPlayer
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chesskel.R
import com.chesskel.game.AiBot
import com.chesskel.game.AiMode
import com.chesskel.game.ChessEngine
import com.chesskel.game.GameEventListener
import com.chesskel.game.GameResult
import com.chesskel.game.Move
import com.chesskel.game.Side
import com.chesskel.ui.theme.ThemeUtils
import com.chesskel.util.SoundManager
import com.chesskel.ui.theme.CenteredActivity

class GameActivity : CenteredActivity(), GameEventListener {

    private val engine = ChessEngine()
    private var aiBot: AiBot? = null
    private var aiMode: AiMode? = null
    // If true, AI controls White; human controls Black. Otherwise human is White.
    private var aiPlaysWhite = false

    private lateinit var chessBoard: ChessBoardView
    private lateinit var tvGameInfo: TextView
    private lateinit var rvMoves: RecyclerView
    private lateinit var btnResign: Button

    // New buttons requested: Play Again and Main Menu
    private lateinit var btnPlayAgain: Button
    private lateinit var btnMainMenu: Button

    // container for post-game buttons (important: make container visible when showing buttons)
    private lateinit var postGameButtons: View

    private val movesList = mutableListOf<String>()

    private var mpCheck: MediaPlayer? = null
    private var mpWin: MediaPlayer? = null
    private var mpDraw: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeUtils.applySavedTheme(this)
        setCenteredContentView(R.layout.activity_game)

        // Initialize SoundManager once for the entire activity.
        SoundManager.init(this)

        chessBoard = findViewById(R.id.chessBoard)
        tvGameInfo = findViewById(R.id.tvGameInfo)
        rvMoves = findViewById(R.id.rvMoves)
        btnResign = findViewById(R.id.btnResign)

        rvMoves.layoutManager = LinearLayoutManager(this)

        // find new buttons (they must exist in the activity_game layout)
        btnPlayAgain = findViewById(R.id.btnPlayAgain)
        btnMainMenu = findViewById(R.id.btnMainMenu)
        postGameButtons = findViewById(R.id.post_game_buttons)

        engine.eventListener = this

        mpCheck = MediaPlayer.create(this, R.raw.sound_check)
        mpWin = MediaPlayer.create(this, R.raw.sound_gameover)
        mpDraw = MediaPlayer.create(this, R.raw.sound_gameover)

        chessBoard.bindEngine(engine)

        // Read AI mode first
        intent?.getStringExtra("ai_mode")?.let { modeName ->
            try {
                aiMode = AiMode.valueOf(modeName)
            } catch (_: IllegalArgumentException) {
                // ignore invalid mode
            }
        }

        // Decide sides from Intent (prefer human_plays_white; fall back to ai_plays_white)
        aiPlaysWhite = when {
            intent?.hasExtra("human_plays_white") == true ->
                !intent.getBooleanExtra("human_plays_white", true)
            intent?.hasExtra("ai_plays_white") == true ->
                intent.getBooleanExtra("ai_plays_white", false)
            else -> false // default: AI plays Black (human plays White)
        }

        // Tell the board which side the human controls
        chessBoard.humanIsWhite = !aiPlaysWhite

        // Initialize AI bot if mode is set
        aiMode?.let { mode ->
            aiBot = AiBot(engine, mode) { aiMove ->
                runOnUiThread { performMove(aiMove) }
            }
        }

        chessBoard.onMove = { m ->
            runOnUiThread { onPlayerAttemptMove(m) }
        }

        btnResign.setOnClickListener {
            Toast.makeText(this, getString(R.string.btnResgin), Toast.LENGTH_SHORT).show()
            finish()
        }

        // Wire new buttons
        btnPlayAgain.setOnClickListener {
            resetGame()
        }

        btnMainMenu.setOnClickListener {
            finish() // return to previous activity (MainMenuActivity is expected)
        }

        // ensure the post-game container is hidden initially
        postGameButtons.visibility = View.GONE

        refreshUi()

        // If AI should play white and it's white's turn, start AI immediately
        if (aiMode != null && aiPlaysWhite && engine.whiteToMove) {
            aiBot?.thinkAndPlay()
        }
    }

    private fun onPlayerAttemptMove(m: Move) {
        // Guard against moving opponent pieces or playing out of turn
        val humanIsWhite = !aiPlaysWhite
        val mover = engine.pieceAt(m.fromR, m.fromC)
        if (engine.whiteToMove != humanIsWhite || mover == '.' || mover.isUpperCase() != humanIsWhite) {
            // Ignore or show a brief hint
            Toast.makeText(this, getString(R.string.not_your_piece), Toast.LENGTH_SHORT).show()
            return
        }

        if (m.promotion != null) {
            showPromotionDialog(m)
        } else {
            performMove(m)
        }
    }

    private fun showPromotionDialog(m: Move) {
        val mover = engine.pieceAt(m.fromR, m.fromC)
        val isWhite = mover != '.' && mover.isUpperCase()
        val labels = arrayOf(
            getString(R.string.promotionQueen),
            getString(R.string.promotionKnight),
            getString(R.string.promotionRook),
            getString(R.string.promotionBishop)
        )
        val chars = if (isWhite) arrayOf('Q', 'N', 'R', 'B') else arrayOf('q', 'n', 'r', 'b')

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialogPromote))
            .setItems(labels) { _, which ->
                val promo = chars[which]
                val chosen = Move(m.fromR, m.fromC, m.toR, m.toC, promo)
                performMove(chosen)
            }
            .setCancelable(true)
            .show()
    }

    // Centralized move logic
    private fun performMove(m: Move) {
        // Check state BEFORE move
        val isCapture = engine.pieceAt(m.toR, m.toC) != '.'

        // Get SAN and apply the move
        val san = engine.moveToString(m)
        engine.applyMove(m)

        // store last move for board highlighting and pass it to the board view
        chessBoard.lastMove = m

        // After applying the move, engine.whiteToMove indicates who is to move now (the opponent).
        val opponentIsWhite = engine.whiteToMove

        // Play appropriate sound
        when {
            engine.isCheckmate(opponentIsWhite) -> SoundManager.playGameOver()
            engine.isKingInCheck(opponentIsWhite) -> SoundManager.playCheck()
            m.promotion != null -> SoundManager.playPromote()
            isCapture -> SoundManager.playCapture()
            else -> SoundManager.playMove()
        }

        // UI updates
        chessBoard.invalidate()
        movesList.add(san)
        updateMoves()
        refreshUi()

        // Game over handling
        if (engine.isCheckmate(opponentIsWhite)) {
            val winner = if (!opponentIsWhite) getString(R.string.white) else getString(R.string.black)
            Toast.makeText(this, getString(R.string.checkmate_template, winner), Toast.LENGTH_LONG).show()
            aiBot?.stop()
            showPostGameOptions()
            return
        }

        // Stalemate / draw detection: if no legal moves but not in check -> stalemate
        if (engine.allLegalMoves(engine.whiteToMove).isEmpty() && !engine.isKingInCheck(engine.whiteToMove)) {
            Toast.makeText(this, getString(R.string.stalemate), Toast.LENGTH_LONG).show()
            aiBot?.stop()
            showPostGameOptions()
            return
        }

        // If AI exists and it's AI's turn, start thinking
        aiMode?.let {
            if (engine.whiteToMove == aiPlaysWhite) {
                aiBot?.thinkAndPlay()
            }
        }
    }

    private fun updateMoves() {
        val movesText = movesList.joinToString("\n")
        rvMoves.adapter = MovesAdapter(movesList)
        rvMoves.scrollToPosition(movesList.size - 1)
    }

    private fun refreshUi() {
        tvGameInfo.text = if (engine.whiteToMove) getString(R.string.white_to_move) else getString(R.string.black_to_move)
    }

    override fun onPause() {
        super.onPause()
        aiBot?.stop()
    }

    override fun onCheck(side: Side) {
        mpCheck?.start()
        // optionally show indicator in UI
    }

    override fun onGameEnd(result: GameResult) {
        when (result) {
            GameResult.WHITE_WINS, GameResult.BLACK_WINS -> mpWin?.start()
            GameResult.STALEMATE, GameResult.DRAW -> mpDraw?.start()
        }
        // stop AI and surface post-game options
        aiBot?.stop()
        showPostGameOptions()
    }

    private fun showPostGameOptions() {
        // Make the container visible and show buttons so user can choose next step.
        postGameButtons.visibility = View.VISIBLE
        // Optionally also disable resign to avoid confusion
        btnResign.isEnabled = false
    }

    private fun resetGame() {
        // Reset engine state, UI and lists
        aiBot?.stop()
        engine.reset()
        movesList.clear()
        updateMoves()
        chessBoard.bindEngine(engine)
        chessBoard.lastMove = null
        chessBoard.invalidate()
        refreshUi()

        // hide post-game container again and re-enable resign
        postGameButtons.visibility = View.GONE
        btnResign.isEnabled = true

        // Re-create AI bot in case preferences/intent remain (reuse aiMode)
        aiMode?.let { mode ->
            aiBot = AiBot(engine, mode) { aiMove ->
                runOnUiThread { performMove(aiMove) }
            }
        }

        // If AI should play white and it's white's turn, start AI immediately
        if (aiMode != null && aiPlaysWhite && engine.whiteToMove) {
            aiBot?.thinkAndPlay()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        aiBot?.stop()

        // Release MediaPlayer instances to avoid leaks
        mpCheck?.let { it.stop(); it.release() }
        mpWin?.let { it.stop(); it.release() }
        mpDraw?.let { it.stop(); it.release() }
        mpCheck = null
        mpWin = null
        mpDraw = null

        SoundManager.release() // free SoundPool and bitmaps
    }
}