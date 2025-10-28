package com.chesskel.game

import kotlin.concurrent.thread
import kotlin.random.Random

class AiBot(
    private val engine: ChessEngine,
    private val mode: AiMode,
    private val onMove: (Move) -> Unit
) {
    @Volatile private var running = false
    private var worker: Thread? = null

    fun thinkAndPlay() {
        if (running) return
        running = true
        worker = thread(name = "AiBot") {
            try {
                // small thinking delay
                Thread.sleep(2000L + Random.nextLong(5000L))

                val forWhite = engine.whiteToMove
                val moves = engine.allLegalMoves(forWhite)
                if (moves.isEmpty()) return@thread

                val chosen = when (mode) {
                    AiMode.EASY -> moves.random()
                    AiMode.NORMAL -> pickPreferCaptures(moves)
                    AiMode.HARD, AiMode.PRO -> pickScoredMove(moves)
                }

                // deliver move on UI thread (GameActivity already wraps performMove in runOnUiThread)
                onMove(chosen)
            } catch (_: InterruptedException) {
                // stop gracefully
            } finally {
                running = false
            }
        }
    }

    fun stop() {
        running = false
        worker?.interrupt()
        worker = null
    }

    // Prefer capture moves, but sometimes pick quiet moves (weighted)
    private fun pickPreferCaptures(moves: List<Move>): Move {
        val scored = moves.map { it to basicScore(it) }.sortedByDescending { it.second }
        // 70% choose among top half, else random
        return if (Random.nextInt(100) < 70) scored.first().first else moves.random()
    }

    // More advanced heuristic: prioritize captures, promotions and material gain
    private fun pickScoredMove(moves: List<Move>): Move {
        val scored = moves.map { it to advancedScore(it) }.sortedByDescending { it.second }
        // pick among top 3 to add variety
        val top = scored.take(3).map { it.first }
        return top.random()
    }

    private fun basicScore(m: Move): Int {
        val dest = engine.pieceAt(m.toR, m.toC)
        var score = pieceValue(dest)
        if (m.promotion != null) {
            score += (pieceValue(m.promotion!!) - pieceValue(if (m.promotion!!.isUpperCase()) 'P' else 'p'))
            score += 200 // prefer promotions
        }
        return score
    }

    private fun advancedScore(m: Move): Int {
        var s = basicScore(m)
        // small bonus for centralization / mobility proxy: encourage moves to center files/ranks
        val centerDist = kotlin.math.abs(3.5 - m.toC) + kotlin.math.abs(3.5 - m.toR)
        s += ((4.0 - centerDist) * 10).toInt()
        // small randomness to avoid deterministic behaviour
        s += Random.nextInt(0, 30)
        return s
    }

    private fun pieceValue(ch: Char): Int {
        return when (ch.lowercaseChar()) {
            'p' -> 100
            'n' -> 320
            'b' -> 330
            'r' -> 500
            'q' -> 900
            'k' -> 20000
            '.' -> 0
            else -> 0
        }
    }
}
