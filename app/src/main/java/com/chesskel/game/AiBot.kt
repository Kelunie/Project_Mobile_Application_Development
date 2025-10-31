package com.chesskel.game

import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class AiBot(
    private val engine: ChessEngine,
    private val mode: AiMode,
    private val onMove: (Move) -> Unit
) {
    @Volatile private var running = false
    private var worker: Thread? = null

    // --- Human strength profiles mapped to Elo bands ---
    private data class Profile(
        val minDelayMs: Long,
        val maxDelayMs: Long,
        val searchDepth: Int,
        val noiseCp: Int,             // evaluation noise at root (centipawns)
        val topK: Int,                // consider top-K at root
        val softmaxTemp: Double,      // sampling temperature among top-K
        val mistakeChancePct: Int,    // pick suboptimal among top group
        val blunderChancePct: Int,    // sample from bottom quantile
        val blunderBottomQuantile: Double
    )

    private val profile: Profile = when (mode) {
        AiMode.EASY -> Profile(
            minDelayMs = 600, maxDelayMs = 2500,
            searchDepth = 1,
            noiseCp = 180,
            topK = 5, softmaxTemp = 1.2,
            mistakeChancePct = 35, blunderChancePct = 18, blunderBottomQuantile = 0.30
        )
        AiMode.NORMAL -> Profile(
            minDelayMs = 1000, maxDelayMs = 3000,
            searchDepth = 2,
            noiseCp = 110,
            topK = 4, softmaxTemp = 0.9,
            mistakeChancePct = 22, blunderChancePct = 8, blunderBottomQuantile = 0.25
        )
        AiMode.HARD -> Profile(
            minDelayMs = 1500, maxDelayMs = 4000,
            searchDepth = 2,
            noiseCp = 60,
            topK = 3, softmaxTemp = 0.7,
            mistakeChancePct = 10, blunderChancePct = 3, blunderBottomQuantile = 0.20
        )
        AiMode.PRO -> Profile(
            minDelayMs = 2000, maxDelayMs = 5000,
            searchDepth = 3,
            noiseCp = 25,
            topK = 2, softmaxTemp = 0.5,
            mistakeChancePct = 5, blunderChancePct = 1, blunderBottomQuantile = 0.15
        )
    }

    // --- Search constants and piece values (centipawns) ---
    private val INF = 2_000_000
    private val MATE = 1_000_000
    private val pieceVal = mapOf(
        'p' to 100, 'n' to 320, 'b' to 330, 'r' to 500, 'q' to 900, 'k' to 0
    )

    // Piece-square tables (simple, middlegame-ish). White’s perspective; flip for black.
    // Tuned lightly; adjust to taste.
    private val pstPawn = intArrayOf(
        0,  5,  5, -10, -10,  5,  5,  0,
        0, 10, -5,   0,   0, -5, 10,  0,
        0, 10, 10,  10,  10, 10, 10,  0,
        5, 15, 20,  25,  25, 20, 15,  5,
        10, 20, 30,  35,  35, 30, 20, 10,
        15, 25, 35,  40,  40, 35, 25, 15,
        60, 60, 60,  60,  60, 60, 60, 60,
        0,  0,  0,   0,   0,  0,  0,  0
    )
    private val pstKnight = intArrayOf(
        -50,-30,-20,-20,-20,-20,-30,-50,
        -30,-10,  0,  0,  0,  0,-10,-30,
        -20,  0, 10, 15, 15, 10,  0,-20,
        -20,  5, 15, 20, 20, 15,  5,-20,
        -20,  0, 15, 20, 20, 15,  0,-20,
        -20,  5, 10, 15, 15, 10,  5,-20,
        -30,-10,  0,  5,  5,  0,-10,-30,
        -50,-30,-20,-20,-20,-20,-30,-50
    )
    private val pstBishop = intArrayOf(
        -20,-10,-10,-10,-10,-10,-10,-20,
        -10,  5,  0,  0,  0,  0,  5,-10,
        -10, 10, 10, 10, 10, 10, 10,-10,
        -10,  0, 10, 10, 10, 10,  0,-10,
        -10,  5, 10, 10, 10, 10,  5,-10,
        -10, 10, 10, 10, 10, 10, 10,-10,
        -10,  5,  0,  0,  0,  0,  5,-10,
        -20,-10,-10,-10,-10,-10,-10,-20
    )
    private val pstRook = intArrayOf(
        0,  0,  5, 10, 10,  5,  0,  0,
        -5,  0,  0,  0,  0,  0,  0, -5,
        -5,  0,  0,  0,  0,  0,  0, -5,
        -5,  0,  0,  5,  5,  0,  0, -5,
        -5,  0,  0,  5,  5,  0,  0, -5,
        -5,  0,  0,  0,  0,  0,  0, -5,
        5, 10, 10, 10, 10, 10, 10,  5,
        0,  0,  0,  0,  0,  0,  0,  0
    )
    private val pstQueen = intArrayOf(
        -20,-10,-10, -5, -5,-10,-10,-20,
        -10,  0,  0,  0,  0,  5,  0,-10,
        -10,  0,  5,  5,  5,  5,  0,-10,
        -5,  0,  5,  5,  5,  5,  0, -5,
        0,  0,  5,  5,  5,  5,  0, -5,
        -10,  5,  5,  5,  5,  5,  0,-10,
        -10,  0,  5,  0,  0,  0,  0,-10,
        -20,-10,-10, -5, -5,-10,-10,-20
    )
    private val pstKing = intArrayOf(
        -30,-40,-40,-50,-50,-40,-40,-30,
        -30,-40,-40,-50,-50,-40,-40,-30,
        -30,-40,-40,-50,-50,-40,-40,-30,
        -30,-40,-40,-50,-50,-40,-40,-30,
        -20,-30,-30,-40,-40,-30,-30,-20,
        -10,-20,-20,-20,-20,-20,-20,-10,
        20, 20,  0,  0,  0,  0, 20, 20,
        20, 30, 10,  0,  0, 10, 30, 20
    )

    // --- Public control ---
    fun thinkAndPlay() {
        if (running) return
        running = true
        worker = thread(name = "AiBot") {
            try {
                Thread.sleep(humanDelay(profile.minDelayMs, profile.maxDelayMs))

                val moves = engine.allLegalMoves(engine.whiteToMove)
                if (moves.isEmpty()) return@thread

                val chosen = chooseHumanLike(moves)

                onMove(chosen)
            } catch (_: InterruptedException) {
                // graceful stop
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

    // --- Humanized selection on top of search ---
    private fun chooseHumanLike(moves: List<Move>): Move {
        // Evaluate root moves with limited-depth search
        val scored = rootScores(moves, profile.searchDepth)
            .shuffled() // avoid deterministic tie order before softmax
            .map { (m, s) -> m to (s + Random.nextInt(-profile.noiseCp, profile.noiseCp + 1)) }
            .sortedByDescending { it.second }

        // Blunder modeling: sample from bottom quantile
        if (randPct(profile.blunderChancePct) && scored.size >= 2) {
            val start = max(1, (scored.size * (1.0 - profile.blunderBottomQuantile)).toInt())
            val bottom = scored.subList(start, scored.size).map { it.first }
            if (bottom.isNotEmpty()) return bottom.random()
        }

        val k = min(profile.topK, scored.size)
        val top = scored.take(k)

        // Suboptimal "mistake" within the strong set
        if (k > 1 && randPct(profile.mistakeChancePct)) {
            return top.random().first
        }

        // Softmax sampling among the top-K
        val probs = softmax(top.map { it.second.toDouble() / 100.0 }, profile.softmaxTemp)
        return weightedChoice(top.map { it.first }, probs)
    }

    // --- Root scoring with snapshot/restore-driven negamax + quiescence ---
    private fun rootScores(moves: List<Move>, depth: Int): List<Pair<Move, Int>> {
        val results = ArrayList<Pair<Move, Int>>(moves.size)
        for (m in orderMoves(moves)) {
            val snap = engine.snapshot()
            engine.applyMove(m)
            val score = -negamax(depth - 1, -INF, INF)
            engine.restore(snap)
            results += m to score
        }
        return results
    }

    private fun negamax(depth: Int, alpha: Int, beta: Int): Int {
        // Terminal
        val legal = engine.allLegalMoves(engine.whiteToMove)
        if (legal.isEmpty()) {
            return if (engine.isKingInCheck(engine.whiteToMove)) {
                -MATE
            } else 0 // stalemate
        }

        if (depth <= 0) {
            return quiescence(alpha, beta)
        }

        var a = alpha
        var best = -INF
        for (m in orderMoves(legal)) {
            val snap = engine.snapshot()
            engine.applyMove(m)
            val score = -negamax(depth - 1, -beta, -a)
            engine.restore(snap)

            if (score > best) best = score
            if (best > a) a = best
            if (a >= beta) break
            if (!running) break
        }
        return best
    }

    private fun quiescence(alpha: Int, beta: Int): Int {
        var a = alpha
        val stand = evalForSideToMove()
        if (stand >= beta) return stand
        if (stand > a) a = stand

        val legal = engine.allLegalMoves(engine.whiteToMove)
        val captures = legal.filter { isCaptureOrPromotion(it) }
        if (captures.isEmpty()) return stand

        var best = -INF
        for (m in orderMoves(captures, capturesOnly = true)) {
            val snap = engine.snapshot()
            engine.applyMove(m)
            val score = -quiescence(-beta, -a)
            engine.restore(snap)

            if (score > best) best = score
            if (best > a) a = best
            if (a >= beta) break
            if (!running) break
        }
        return max(stand, best)
    }

    // --- Evaluation: material + PST + small bonuses ---
    private fun evaluate(): Int {
        var score = 0
        for (r in 0..7) for (c in 0..7) {
            val p = engine.board[r][c]
            if (p == '.') continue
            val isW = p.isUpperCase()
            val base = pieceVal[p.lowercaseChar()] ?: 0
            val pst = pstValue(p, r, c)
            val valCp = base + pst

            // Pawn advancement slight bonus
            val advance = when (p.lowercaseChar()) {
                'p' -> if (isW) (6 - r) * 2 else r * 2
                else -> 0
            }

            score += if (isW) valCp + advance else -(valCp + advance)
        }

        // Tiny tempo bonus for side to move (encourages activity)
        score += if (engine.whiteToMove) 10 else -10

        return score
    }

    private fun evalForSideToMove(): Int {
        val color = if (engine.whiteToMove) 1 else -1
        return color * evaluate()
    }

    private fun pstValue(p: Char, r: Int, c: Int): Int {
        val idxWhite = (r * 8 + c)
        val idxBlack = ((7 - r) * 8 + c)
        return when (p.lowercaseChar()) {
            'p' -> if (p.isUpperCase()) pstPawn[idxWhite] else pstPawn[idxBlack]
            'n' -> if (p.isUpperCase()) pstKnight[idxWhite] else pstKnight[idxBlack]
            'b' -> if (p.isUpperCase()) pstBishop[idxWhite] else pstBishop[idxBlack]
            'r' -> if (p.isUpperCase()) pstRook[idxWhite] else pstRook[idxBlack]
            'q' -> if (p.isUpperCase()) pstQueen[idxWhite] else pstQueen[idxBlack]
            'k' -> if (p.isUpperCase()) pstKing[idxWhite] else pstKing[idxBlack]
            else -> 0
        }
    }

    // --- Move utilities ---
    private fun isCaptureOrPromotion(m: Move): Boolean {
        val dest = engine.pieceAt(m.toR, m.toC)
        if (dest != '.') return true
        val from = engine.board[m.fromR][m.fromC]
        if (from.lowercaseChar() == 'p') {
            val ep = engine.enPassantTarget
            if (ep != null && ep.first == m.toR && ep.second == m.toC) return true
        }
        return m.promotion != null
    }

    private fun orderMoves(moves: List<Move>, capturesOnly: Boolean = false): List<Move> {
        // MVV-LVA style: victim value - small attacker value -> high first
        fun score(m: Move): Int {
            val from = engine.board[m.fromR][m.fromC]
            val to = engine.pieceAt(m.toR, m.toC)
            val victim = pieceVal[to.lowercaseChar()] ?: 0
            val attacker = pieceVal[from.lowercaseChar()] ?: 0
            val captureScore = victim * 10 - attacker
            val promoScore = when (m.promotion?.lowercaseChar()) {
                'q' -> 900
                'r' -> 500
                'n' -> 320
                'b' -> 330
                else -> 0
            }
            // Mild centralization preference at root ordering
            val center = ((4 - abs(3 - m.toR)) + (4 - abs(3 - m.toC))) * 2
            return captureScore + promoScore + center
        }
        val sorted = moves.sortedByDescending { score(it) }
        return if (capturesOnly) sorted.filter { isCaptureOrPromotion(it) } else sorted
    }

    // --- Humanization helpers ---
    private fun humanDelay(minMs: Long, maxMs: Long): Long {
        val u = Random.nextDouble(0.0, 1.0)
        val t = exp(ln(1.0 + 4.0 * u) / 2.5) - 1.0
        val frac = (t / (exp(ln(1.0 + 4.0) / 2.5) - 1.0)).coerceIn(0.0, 1.0)
        return (minMs + frac * (maxMs - minMs)).toLong()
    }

    private fun softmax(values: List<Double>, temperature: Double): List<Double> {
        val t = max(1e-3, temperature)
        val mx = values.maxOrNull() ?: 0.0
        val exps = values.map { kotlin.math.exp((it - mx) / t) }
        val sum = exps.sum().coerceAtLeast(1e-9)
        return exps.map { it / sum }
    }

    private fun <T> weightedChoice(items: List<T>, probs: List<Double>): T {
        var r = Random.nextDouble()
        for (i in items.indices) {
            r -= probs[i]
            if (r <= 0.0) return items[i]
        }
        return items.last()
    }

    private fun randPct(p: Int): Boolean = Random.nextInt(100) < p
}