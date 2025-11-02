package com.chesskel.game

data class Move(val fromR: Int, val fromC: Int, val toR: Int, val toC: Int, val promotion: Char? = null)

class ChessEngine {
    // board[r][c]: char: 'P','R','N','B','Q','K' for white; lowercase for black; '.' empty
    val board = Array(8) { CharArray(8) { '.' } }
    var whiteToMove = true

    // castling/en-passant tracking
    private var whiteKingMoved = false
    private var blackKingMoved = false
    private var whiteRookA_moved = false // rook at (7,0)
    private var whiteRookH_moved = false // rook at (7,7)
    private var blackRookA_moved = false // rook at (0,0)
    private var blackRookH_moved = false // rook at (0,7)
    var enPassantTarget: Pair<Int,Int>? = null // square that can be captured onto (row,col)

    // public listener
    var eventListener: GameEventListener? = null
    private fun notifyCheck(side: Side) {
        eventListener?.onCheck(side)
    }
    // call when a game end condition is found
    private fun notifyGameEnd(result: GameResult) {
        eventListener?.onGameEnd(result)
    }

    // >>>>> ADD: lightweight snapshot/restore for AI search
    data class Snapshot(
        val boardCopy: Array<CharArray>,
        val whiteToMove: Boolean,
        val whiteKingMoved: Boolean,
        val blackKingMoved: Boolean,
        val whiteRookA_moved: Boolean,
        val whiteRookH_moved: Boolean,
        val blackRookA_moved: Boolean,
        val blackRookH_moved: Boolean,
        val enPassantTarget: Pair<Int,Int>?
    )

    fun snapshot(): Snapshot {
        val boardCopy = Array(8) { r -> board[r].copyOf() }
        return Snapshot(
            boardCopy = boardCopy,
            whiteToMove = whiteToMove,
            whiteKingMoved = whiteKingMoved,
            blackKingMoved = blackKingMoved,
            whiteRookA_moved = whiteRookA_moved,
            whiteRookH_moved = whiteRookH_moved,
            blackRookA_moved = blackRookA_moved,
            blackRookH_moved = blackRookH_moved,
            enPassantTarget = enPassantTarget
        )
    }

    fun restore(s: Snapshot) {
        for (r in 0..7) {
            board[r] = s.boardCopy[r].copyOf()
        }
        whiteToMove = s.whiteToMove
        whiteKingMoved = s.whiteKingMoved
        blackKingMoved = s.blackKingMoved
        whiteRookA_moved = s.whiteRookA_moved
        whiteRookH_moved = s.whiteRookH_moved
        blackRookA_moved = s.blackRookA_moved
        blackRookH_moved = s.blackRookH_moved
        enPassantTarget = s.enPassantTarget
    }
    // <<<<< END ADD

    init { reset() }

    fun reset() {
        val rows = arrayOf(
            "r n b q k b n r".replace(" ", ""),
            "p p p p p p p p".replace(" ", ""),
            "........",
            "........",
            "........",
            "........",
            "P P P P P P P P".replace(" ", ""),
            "R N B Q K B N R".replace(" ", "")
        )
        for (r in 0..7) for (c in 0..7) board[r][c] = rows[r][c]
        whiteToMove = true

        whiteKingMoved = false
        blackKingMoved = false
        whiteRookA_moved = false
        whiteRookH_moved = false
        blackRookA_moved = false
        blackRookH_moved = false
        enPassantTarget = null
    }

    fun pieceAt(r: Int, c: Int) = board[r][c]

    fun applyMove(m: Move) {
        val p = board[m.fromR][m.fromC]
        if (p == '.') return

        // handle en-passant capture (using current enPassantTarget)
        if (p.lowercaseChar() == 'p' && enPassantTarget != null) {
            val ep = enPassantTarget!!
            if (m.toR == ep.first && m.toC == ep.second && board[m.toR][m.toC] == '.') {
                // captured pawn is on the same row as the mover's from row, at destination column
                board[m.fromR][m.toC] = '.'
            }
        }

        // handle castling: king moves two squares horizontally
        if (p.lowercaseChar() == 'k' && kotlin.math.abs(m.toC - m.fromC) == 2) {
            // perform rook move
            val r = m.fromR
            if (m.toC == 6) {
                // king-side: rook from col7 -> col5
                board[r][5] = board[r][7]
                board[r][7] = '.'
            } else if (m.toC == 2) {
                // queen-side: rook from col0 -> col3
                board[r][3] = board[r][0]
                board[r][0] = '.'
            }
        }

        // move piece (including promotions)
        board[m.toR][m.toC] = m.promotion ?: p
        board[m.fromR][m.fromC] = '.'

        // update flags
        if (p == 'K') whiteKingMoved = true
        if (p == 'k') blackKingMoved = true

        if (p == 'R') {
            if (m.fromR == 7 && m.fromC == 0) whiteRookA_moved = true
            if (m.fromR == 7 && m.fromC == 7) whiteRookH_moved = true
        }
        if (p == 'r') {
            if (m.fromR == 0 && m.fromC == 0) blackRookA_moved = true
            if (m.fromR == 0 && m.fromC == 7) blackRookH_moved = true
        }

        // if king castled, mark only the rook that moved (not both)
        if (p.lowercaseChar() == 'k' && kotlin.math.abs(m.toC - m.fromC) == 2) {
            if (p.isUpperCase()) {
                // white
                if (m.toC == 6) {
                    // king-side: rook from h-file moved
                    whiteRookH_moved = true
                } else if (m.toC == 2) {
                    // queen-side: rook from a-file moved
                    whiteRookA_moved = true
                }
            } else {
                // black
                if (m.toC == 6) {
                    blackRookH_moved = true
                } else if (m.toC == 2) {
                    blackRookA_moved = true
                }
            }
        }

        // set en-passant target: if pawn moved two squares, set mid-square; else clear
        enPassantTarget = null
        if (p.lowercaseChar() == 'p' && kotlin.math.abs(m.toR - m.fromR) == 2) {
            val midR = (m.fromR + m.toR) / 2
            enPassantTarget = midR to m.fromC
        }

        whiteToMove = !whiteToMove
    }

    // --- helpers for board simulation and attack detection ---
    private fun copyBoard(src: Array<CharArray>): Array<CharArray> {
        return Array(8) { r -> src[r].copyOf() }
    }

    private fun applyMoveOnBoard(b: Array<CharArray>, m: Move, epTarget: Pair<Int,Int>? = null) {
        val p = b[m.fromR][m.fromC]
        if (p == '.') return

        // handle en-passant capture using provided epTarget
        if (p.lowercaseChar() == 'p' && epTarget != null) {
            if (m.toR == epTarget.first && m.toC == epTarget.second && b[m.toR][m.toC] == '.') {
                // captured pawn is on the same row as the mover's from row, at destination column
                b[m.fromR][m.toC] = '.'
            }
        }

        // castling on board: king moves two squares horizontally -> move rook accordingly
        if (p.lowercaseChar() == 'k' && kotlin.math.abs(m.toC - m.fromC) == 2) {
            val r = m.fromR
            if (m.toC == 6) {
                b[r][5] = b[r][7]
                b[r][7] = '.'
            } else if (m.toC == 2) {
                b[r][3] = b[r][0]
                b[r][0] = '.'
            }
        }

        b[m.toR][m.toC] = m.promotion ?: p
        b[m.fromR][m.fromC] = '.'
    }

    private fun inBounds(r: Int, c: Int) = r in 0..7 && c in 0..7
    private fun isWhite(p: Char) = p != '.' && p.isUpperCase()
    private fun isBlack(p: Char) = p != '.' && p.isLowerCase()

    // Determines if piece at (fr,fc) attacks (tr,tc) on provided board (ignores checks)
    private fun attacksSquare(fr: Int, fc: Int, tr: Int, tc: Int, b: Array<CharArray>): Boolean {
        val p = b[fr][fc]
        if (p == '.') return false
        val lc = p.lowercaseChar()
        val dr = tr - fr
        val dc = tc - fc

        when (lc) {
            'p' -> {
                val dir = if (p.isUpperCase()) -1 else 1
                if (dr == dir && kotlin.math.abs(dc) == 1) return true
                return false
            }
            'n' -> {
                val ad = kotlin.math.abs(dr); val ac = kotlin.math.abs(dc)
                return (ad == 2 && ac == 1) || (ad == 1 && ac == 2)
            }
            'b' -> {
                if (kotlin.math.abs(dr) != kotlin.math.abs(dc)) return false
                val stepR = if (dr > 0) 1 else -1
                val stepC = if (dc > 0) 1 else -1
                var r = fr + stepR; var c = fc + stepC
                while (r != tr && c != tc) {
                    if (b[r][c] != '.') return false
                    r += stepR; c += stepC
                }
                return true
            }
            'r' -> {
                if (dr != 0 && dc != 0) return false
                val stepR = when {
                    dr > 0 -> 1
                    dr < 0 -> -1
                    else -> 0
                }
                val stepC = when {
                    dc > 0 -> 1
                    dc < 0 -> -1
                    else -> 0
                }
                var r = fr + stepR; var c = fc + stepC
                while (r != tr || c != tc) {
                    if (b[r][c] != '.') return false
                    r += stepR; c += stepC
                }
                return true
            }
            'q' -> {
                if (dr == 0 || dc == 0 || kotlin.math.abs(dr) == kotlin.math.abs(dc)) {
                    val stepR = when {
                        dr > 0 -> 1
                        dr < 0 -> -1
                        else -> 0
                    }
                    val stepC = when {
                        dc > 0 -> 1
                        dc < 0 -> -1
                        else -> 0
                    }
                    var r = fr + stepR; var c = fc + stepC
                    while (r != tr || c != tc) {
                        if (b[r][c] != '.') return false
                        r += stepR; c += stepC
                    }
                    return true
                }
                return false
            }
            'k' -> {
                return kotlin.math.abs(dr) <= 1 && kotlin.math.abs(dc) <= 1
            }
        }
        return false
    }

    // is (r,c) attacked by pieces of color byWhite on board b
    private fun isSquareAttacked(r: Int, c: Int, byWhite: Boolean, b: Array<CharArray>): Boolean {
        for (rr in 0..7) for (cc in 0..7) {
            val p = b[rr][cc]
            if (p == '.') continue
            if (isWhite(p) != byWhite) continue
            if (attacksSquare(rr, cc, r, c, b)) return true
        }
        return false
    }

    private fun findKing(forWhite: Boolean, b: Array<CharArray>): Pair<Int,Int>? {
        val target = if (forWhite) 'K' else 'k'
        for (r in 0..7) for (c in 0..7) if (b[r][c] == target) return r to c
        return null
    }

    fun isKingInCheck(forWhite: Boolean): Boolean {
        val king = findKing(forWhite, board) ?: return false
        return isSquareAttacked(king.first, king.second, byWhite = !forWhite, board)
    }

    // --- move generation: core pseudo moves (no side filter) ---
    private fun generatePseudoMovesFor(r: Int, c: Int, b: Array<CharArray>, epTarget: Pair<Int,Int>? = null): List<Move> {
        val p = b[r][c]
        if (p == '.') return emptyList()
        val moves = mutableListOf<Move>()
        val isW = isWhite(p)
        val dir = if (isW) -1 else 1

        when (p.lowercaseChar()) {
            'p' -> {
                val nr = r + dir
                // determine last rank for promotion
                val lastRank = if (isW) 0 else 7

                // forward one
                if (inBounds(nr, c) && b[nr][c] == '.') {
                    if (nr == lastRank) {
                        // generate promotion variants
                        val promos = if (isW) listOf('Q','N','R','B') else listOf('q','n','r','b')
                        for (pr in promos) moves.add(Move(r, c, nr, c, pr))
                    } else {
                        moves.add(Move(r, c, nr, c))
                        // forward two from starting rank
                        val startRow = if (isW) 6 else 1
                        val nr2 = r + dir * 2
                        if (r == startRow && inBounds(nr2, c) && b[nr2][c] == '.' ) {
                            moves.add(Move(r, c, nr2, c))
                        }
                    }
                }
                // captures and en-passant
                for (dc in listOf(-1, 1)) {
                    val cc = c + dc
                    if (inBounds(nr, cc)) {
                        val target = b[nr][cc]
                        if (target != '.' && (isW && isBlack(target) || !isW && isWhite(target))) {
                            if (nr == lastRank) {
                                val promos = if (isW) listOf('Q','N','R','B') else listOf('q','n','r','b')
                                for (pr in promos) moves.add(Move(r, c, nr, cc, pr))
                            } else {
                                moves.add(Move(r, c, nr, cc))
                            }
                        } else if (epTarget != null && nr == epTarget.first && cc == epTarget.second) {
                            // en-passant capture square available
                            if (nr == lastRank) {
                                val promos = if (isW) listOf('Q','N','R','B') else listOf('q','n','r','b')
                                for (pr in promos) moves.add(Move(r, c, nr, cc, pr))
                            } else {
                                moves.add(Move(r, c, nr, cc))
                            }
                        }
                    }
                }
            }
            'n' -> {
                val offsets = listOf(
                    -2 to -1, -2 to 1, -1 to -2, -1 to 2,
                    1 to -2, 1 to 2, 2 to -1, 2 to 1
                )
                for ((dr, dc) in offsets) {
                    val rr = r + dr; val cc = c + dc
                    if (!inBounds(rr, cc)) continue
                    val t = b[rr][cc]
                    if (t == '.' || (isW && isBlack(t)) || (!isW && isWhite(t))) moves.add(Move(r,c,rr,cc))
                }
            }
            'b' -> {
                val dirs = listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)
                for ((dr, dc) in dirs) {
                    var rr = r + dr; var cc = c + dc
                    while (inBounds(rr, cc)) {
                        val t = b[rr][cc]
                        if (t == '.') {
                            moves.add(Move(r,c,rr,cc))
                        } else {
                            if ((isW && isBlack(t)) || (!isW && isWhite(t))) moves.add(Move(r,c,rr,cc))
                            break
                        }
                        rr += dr; cc += dc
                    }
                }
            }
            'r' -> {
                val dirs = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
                for ((dr, dc) in dirs) {
                    var rr = r + dr; var cc = c + dc
                    while (inBounds(rr, cc)) {
                        val t = b[rr][cc]
                        if (t == '.') {
                            moves.add(Move(r,c,rr,cc))
                        } else {
                            if ((isW && isBlack(t)) || (!isW && isWhite(t))) moves.add(Move(r,c,rr,cc))
                            break
                        }
                        rr += dr; cc += dc
                    }
                }
            }
            'q' -> {
                val dirs = listOf(
                    -1 to -1, -1 to 1, 1 to -1, 1 to 1,
                    -1 to 0, 1 to 0, 0 to -1, 0 to 1
                )
                for ((dr, dc) in dirs) {
                    var rr = r + dr; var cc = c + dc
                    while (inBounds(rr, cc)) {
                        val t = b[rr][cc]
                        if (t == '.') {
                            moves.add(Move(r,c,rr,cc))
                        } else {
                            if ((isW && isBlack(t)) || (!isW && isWhite(t))) moves.add(Move(r,c,rr,cc))
                            break
                        }
                        rr += dr; cc += dc
                    }
                }
            }
            'k' -> {
                for (dr in -1..1) for (dc in -1..1) {
                    if (dr == 0 && dc == 0) continue
                    val rr = r + dr; val cc = c + dc
                    if (!inBounds(rr, cc)) continue
                    val t = b[rr][cc]
                    if (t == '.' || (isW && isBlack(t)) || (!isW && isWhite(t))) moves.add(Move(r,c,rr,cc))
                }

                // Castling: only as pseudo-moves (we will still filter legality later)
                if (p.isUpperCase()) {
                    // white castling (king at 7,4)
                    if (r == 7 && c == 4 && !whiteKingMoved) {
                        // king-side
                        if (!whiteRookH_moved && b[7][5] == '.' && b[7][6] == '.' ) {
                            // note: check passing through attacked squares handled in legality phase
                            moves.add(Move(7,4,7,6))
                        }
                        // queen-side
                        if (!whiteRookA_moved && b[7][1] == '.' && b[7][2] == '.' && b[7][3] == '.' ) {
                            moves.add(Move(7,4,7,2))
                        }
                    }
                } else {
                    // black castling (king at 0,4)
                    if (r == 0 && c == 4 && !blackKingMoved) {
                        if (!blackRookH_moved && b[0][5] == '.' && b[0][6] == '.' ) {
                            moves.add(Move(0,4,0,6))
                        }
                        if (!blackRookA_moved && b[0][1] == '.' && b[0][2] == '.' && b[0][3] == '.' ) {
                            moves.add(Move(0,4,0,2))
                        }
                    }
                }
            }
        }
        return moves
    }

    // public generator used previously (keeps old behavior but now delegates)
    fun generateMovesFor(r: Int, c: Int): List<Move> {
        val all = generatePseudoMovesFor(r, c, board, enPassantTarget)
        // only moves for the side that piece belongs to
        return all.filter { board[it.fromR][it.fromC].isUpperCase() == whiteToMove }
    }

    // legal moves: simulate each pseudo-move and discard those that leave own king in check
    fun legalMovesFor(r: Int, c: Int): List<Move> {
        val p = board[r][c]
        if (p == '.') return emptyList()
        val sideIsWhite = isWhite(p)
        val pseudo = generatePseudoMovesFor(r, c, board, enPassantTarget)
        val legal = mutableListOf<Move>()
        for (m in pseudo) {
            val bcopy = copyBoard(board)
            // apply move taking into account current en-passant target
            applyMoveOnBoard(bcopy, m, enPassantTarget)
            // locate king of side on bcopy
            val kingPos = findKing(sideIsWhite, bcopy)
            if (kingPos == null) continue
            val inCheckAfter = isSquareAttacked(kingPos.first, kingPos.second, !sideIsWhite, bcopy)
            // additional castling passing-through check: ensure king not currently in check and not passing squares attacked
            if (!inCheckAfter) {
                // if m is a castling king move, check intermediate square not attacked and starting square not attacked
                if (p.lowercaseChar() == 'k' && kotlin.math.abs(m.toC - m.fromC) == 2) {
                    val midC = (m.fromC + m.toC) / 2
                    val startSafe = !isSquareAttacked(m.fromR, m.fromC, !sideIsWhite, board)
                    val passSafe = !isSquareAttacked(m.fromR, midC, !sideIsWhite, board)
                    val destSafe = !isSquareAttacked(m.toR, m.toC, !sideIsWhite, bcopy) // already checked final
                    if (startSafe && passSafe && destSafe) legal.add(m)
                } else {
                    legal.add(m)
                }
            }
        }
        return legal
    }

    fun allLegalMoves(forWhite: Boolean = whiteToMove): List<Move> {
        return allLegalMovesOnBoard(forWhite, board, enPassantTarget)
    }

    private fun allLegalMovesOnBoard(forWhite: Boolean, b: Array<CharArray>, epTarget: Pair<Int,Int>?): List<Move> {
        val moves = mutableListOf<Move>()
        for (r in 0..7) for (c in 0..7) {
            val p = b[r][c]
            if (p == '.') continue
            if (isWhite(p) != forWhite) continue
            val pseudo = generatePseudoMovesFor(r, c, b, epTarget)
            for (m in pseudo) {
                val bcopy = copyBoard(b)
                // apply using provided epTarget
                applyMoveOnBoard(bcopy, m, epTarget)
                val kingPos = findKing(forWhite, bcopy) ?: continue
                val inCheckAfter = isSquareAttacked(kingPos.first, kingPos.second, !forWhite, bcopy)
                if (!inCheckAfter) {
                    // for castling make sure king doesn't pass through attacked squares (use original b)
                    val origP = b[m.fromR][m.fromC]
                    if (origP.lowercaseChar() == 'k' && kotlin.math.abs(m.toC - m.fromC) == 2) {
                        val midC = (m.fromC + m.toC)/2
                        val startSafe = !isSquareAttacked(m.fromR, m.fromC, !forWhite, b)
                        val passSafe = !isSquareAttacked(m.fromR, midC, !forWhite, b)
                        if (startSafe && passSafe) moves.add(m)
                    } else moves.add(m)
                }
            }
        }
        return moves
    }

    fun isCheckmate(forWhite: Boolean): Boolean {
        return isKingInCheck(forWhite) && allLegalMoves(forWhite).isEmpty()
    }

    // Simple SAN-like formatting; now simulates the move to determine check/checkmate suffix.
    // Enhanced: produce 0-0 and 0-0-0 for castling and preserve promotion/capture/check/mate suffixes.
    fun moveToString(m: Move): String {
        fun sq(r: Int, c: Int) = "${('a' + c)}${8 - r}"
        val fromP = board[m.fromR][m.fromC]
        val toP = board[m.toR][m.toC] // captured piece if any (call before applyMove)
        val dest = sq(m.toR, m.toC)
        val isCapture = toP != '.'
        val promo = m.promotion

        // detect castling on the current board: king move of two squares horizontally
        if (fromP.lowercaseChar() == 'k' && kotlin.math.abs(m.toC - m.fromC) == 2) {
            // simulate resulting board to determine check / mate suffix
            val bcopy = copyBoard(board)
            applyMoveOnBoard(bcopy, m, enPassantTarget)
            val moverIsWhite = fromP.isUpperCase()
            val opponentIsWhite = !moverIsWhite
            val kingPos = findKing(opponentIsWhite, bcopy)
            val oppInCheck = if (kingPos != null) isSquareAttacked(kingPos.first, kingPos.second, moverIsWhite, bcopy) else false
            val newEp = if (fromP.lowercaseChar() == 'p' && kotlin.math.abs(m.toR - m.fromR) == 2) {
                ((m.fromR + m.toR) / 2) to m.fromC
            } else null
            val oppHasMoves = allLegalMovesOnBoard(opponentIsWhite, bcopy, newEp).isNotEmpty()
            val suffix = when {
                oppInCheck && !oppHasMoves -> "#"
                oppInCheck -> "+"
                else -> ""
            }
            // short vs long castle
            return if (m.toC > m.fromC) "0-0$suffix" else "0-0-0$suffix"
        }

        // simulate move on a copy to determine check / mate for generic moves
        val bcopy = copyBoard(board)
        // apply move using current enPassantTarget (so en-passant captures are handled)
        applyMoveOnBoard(bcopy, m, enPassantTarget)
        // compute new en-passant target for the resulting board (only needed for deeper legal generation)
        val newEp = if (fromP.lowercaseChar() == 'p' && kotlin.math.abs(m.toR - m.fromR) == 2) {
            ((m.fromR + m.toR) / 2) to m.fromC
        } else null

        val moverIsWhite = fromP.isUpperCase()
        val opponentIsWhite = !moverIsWhite

        // check if opponent king is in check on bcopy
        val kingPos = findKing(opponentIsWhite, bcopy)
        val oppInCheck = if (kingPos != null) isSquareAttacked(kingPos.first, kingPos.second, moverIsWhite, bcopy) else false

        // check checkmate on bcopy (need to generate opponent legal moves on bcopy with newEp)
        val oppHasMoves = allLegalMovesOnBoard(opponentIsWhite, bcopy, newEp).isNotEmpty()
        val suffix = when {
            oppInCheck && !oppHasMoves -> "#"
            oppInCheck -> "+"
            else -> ""
        }

        val base = when (fromP.lowercaseChar()) {
            'p' -> {
                if (isCapture) {
                    "${('a' + m.fromC)}x$dest" + (promo?.let { "=${it.uppercaseChar()}" } ?: "")
                } else {
                    dest + (promo?.let { "=${it.uppercaseChar()}" } ?: "")
                }
            }
            else -> {
                val letter = when (fromP.lowercaseChar()) {
                    'n' -> "N"
                    'b' -> "B"
                    'r' -> "R"
                    'q' -> "Q"
                    'k' -> "K"
                    else -> ""
                }
                val cap = if (isCapture) "x" else ""
                "$letter$cap$dest" + (promo?.let { "=${it.uppercaseChar()}" } ?: "")
            }
        }
        return base + suffix
    }
}