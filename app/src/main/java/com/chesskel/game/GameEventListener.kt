package com.chesskel.game

enum class GameResult { WHITE_WINS, BLACK_WINS, STALEMATE, DRAW }
enum class Side { WHITE, BLACK }

/**
 * Events emitted by the Chess engine. UI subscribes and reacts (play sounds, show dialogs).
 */
interface GameEventListener {
    fun onCheck(side: Side)          // side that is in check
    fun onGameEnd(result: GameResult)
}
