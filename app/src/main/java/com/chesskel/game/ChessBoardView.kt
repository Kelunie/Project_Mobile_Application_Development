package com.chesskel.ui.game

import android.content.Context
import android.graphics.*
import android.graphics.BitmapFactory
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.chesskel.game.ChessEngine
import com.chesskel.game.Move
import com.chesskel.util.SoundManager

class ChessBoardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val lightColor = Color.parseColor("#F0D9B5")
    private val darkColor = Color.parseColor("#B58863")
    private val highlightColor = Color.parseColor("#6fa8dc")
    private val lastMoveFromColor = Color.argb(160, 0, 200, 0) // translucent green
    private val lastMoveToColor = Color.argb(160, 200, 200, 0) // translucent yellow
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var squareSize = 0f

    private var engine: ChessEngine? = null
    var onMove: ((Move) -> Unit)? = null

    // Qué color controla el humano (true = blancas, false = negras)
    var humanIsWhite: Boolean = true
        set(value) {
            field = value
            updatePerspective()
        }

    // Si es true, cuando el humano juega con negras se rota el tablero 180°
    var rotateForBlack: Boolean = true
        set(value) {
            field = value
            updatePerspective()
        }

    // Perspectiva efectiva: ¿blancas abajo?
    private var whiteAtBottom: Boolean = true
    private fun updatePerspective() {
        whiteAtBottom = if (rotateForBlack) humanIsWhite else true
        invalidate()
    }

    private var selR = -1; private var selC = -1   // selección en coordenadas del tablero (no visual)
    private var possibleMoves: List<Move> = emptyList()

    // último movimiento para resaltar en el tablero (coordenadas del tablero)
    var lastMove: Move? = null

    // caché de bitmaps escalados por recurso
    private val pieceCache = mutableMapOf<Int, Bitmap>()

    fun bindEngine(e: ChessEngine) {
        engine = e
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = MeasureSpec.getSize(widthMeasureSpec).coerceAtMost(MeasureSpec.getSize(heightMeasureSpec))
        setMeasuredDimension(size, size)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        squareSize = if (w > 0) w / 8f else 0f

        if (w != oldw || h != oldh) {
            pieceCache.values.forEach { if (!it.isRecycled) it.recycle() }
            pieceCache.clear()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val engine = engine ?: return
        if (squareSize == 0f) squareSize = width / 8f

        // 1) Dibujar casillas según perspectiva (iteramos por visual y calculamos la casilla real)
        for (vr in 0..7) for (vc in 0..7) {
            val (r, c) = toBoard(vr, vc)
            val left = vc * squareSize
            val top = vr * squareSize
            paint.style = Paint.Style.FILL
            paint.color = if ((r + c) % 2 == 0) lightColor else darkColor
            canvas.drawRect(left, top, left + squareSize, top + squareSize, paint)
        }

        // 2) Resaltar último movimiento (debajo de piezas)
        lastMove?.let { lm ->
            paint.style = Paint.Style.FILL
            val (vfr, vfc) = toVisual(lm.fromR, lm.fromC)
            paint.color = lastMoveFromColor
            canvas.drawRect(
                vfc * squareSize, vfr * squareSize,
                (vfc + 1) * squareSize, (vfr + 1) * squareSize, paint
            )
            paint.color = lastMoveToColor
            val (vtr, vtc) = toVisual(lm.toR, lm.toC)
            canvas.drawRect(
                vtc * squareSize, vtr * squareSize,
                (vtc + 1) * squareSize, (vtr + 1) * squareSize, paint
            )
            paint.alpha = 255
        }

        // 3) Resaltar selección y posibles movimientos (selección está en coords de tablero)
        if (selR >= 0 && selC >= 0) {
            val (vsr, vsc) = toVisual(selR, selC)
            paint.color = highlightColor
            paint.alpha = 120
            paint.style = Paint.Style.FILL
            canvas.drawRect(
                vsc * squareSize, vsr * squareSize,
                (vsc + 1) * squareSize, (vsr + 1) * squareSize, paint
            )
            paint.alpha = 255

            for (m in possibleMoves) {
                val (tr, tc) = toVisual(m.toR, m.toC)
                val cx = (tc + 0.5f) * squareSize
                val cy = (tr + 0.5f) * squareSize
                val isCapture = engine.pieceAt(m.toR, m.toC) != '.'

                if (isCapture) {
                    paint.style = Paint.Style.STROKE
                    paint.color = Color.argb(160, 100, 100, 100)
                    paint.strokeWidth = squareSize * 0.06f
                    canvas.drawCircle(cx, cy, squareSize * 0.42f, paint)
                } else {
                    paint.style = Paint.Style.FILL
                    paint.color = Color.YELLOW
                    canvas.drawCircle(cx, cy, squareSize * 0.12f, paint)
                }
            }
            paint.style = Paint.Style.FILL
        }

        // 4) Dibujar piezas (iteramos tablero y posicionamos visualmente)
        for (r in 0..7) for (c in 0..7) {
            val p = engine.pieceAt(r, c)
            if (p == '.') continue
            val resName = if (p.isUpperCase()) "white_" + pieceName(p.lowercaseChar()) else "black_" + pieceName(p)
            val bmp = getBitmapForPiece(resName)
            val (vr, vc) = toVisual(r, c)
            val left = vc * squareSize
            val top = vr * squareSize
            if (bmp != null && !bmp.isRecycled) {
                canvas.drawBitmap(bmp, left, top, null)
            } else {
                paint.color = if (p.isUpperCase()) Color.WHITE else Color.BLACK
                paint.textSize = squareSize * 0.5f
                paint.style = Paint.Style.FILL
                paint.textAlign = Paint.Align.CENTER
                val cx = left + squareSize * 0.5f
                val fm = paint.fontMetrics
                val cy = top + squareSize * 0.5f - (fm.ascent + fm.descent) / 2f
                canvas.drawText(p.toString(), cx, cy, paint)
                paint.textAlign = Paint.Align.LEFT
            }
        }
    }

    private fun pieceName(c: Char) = when (c) {
        'p' -> "pawn"
        'r' -> "rook"
        'n' -> "knight"
        'b' -> "bishop"
        'q' -> "queen"
        'k' -> "king"
        else -> "pawn"
    }

    private fun getBitmapForPiece(resName: String): Bitmap? {
        val id = resources.getIdentifier(resName, "drawable", context.packageName)
        if (id == 0) return null
        pieceCache[id]?.let { return it }

        val src = BitmapFactory.decodeResource(resources, id) ?: return null
        val size = squareSize.toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(src, size, size, true)
        if (!src.isRecycled && src !== scaled) {
            src.recycle()
        }
        pieceCache[id] = scaled
        return scaled
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val engine = engine ?: return false
        if (event.action != MotionEvent.ACTION_DOWN) return false
        if (squareSize == 0f) squareSize = width / 8f

        // Solo aceptar input cuando es el turno del humano
        if (engine.whiteToMove != humanIsWhite) return false

        val vc = (event.x / squareSize).toInt().coerceIn(0, 7)
        val vr = (event.y / squareSize).toInt().coerceIn(0, 7)
        val (r, c) = toBoard(vr, vc)
        val piece = engine.pieceAt(r, c)

        // Deseleccionar si tocás la misma casilla
        if (selR == r && selC == c) {
            selR = -1; selC = -1; possibleMoves = emptyList()
            invalidate()
            return true
        }

        // Selección: solo piezas del humano
        if (piece != '.' && piece.isUpperCase() == humanIsWhite) {
            selR = r; selC = c
            possibleMoves = engine.legalMovesFor(r, c)
            try { SoundManager.playSelect() } catch (_: Exception) {}
            invalidate()
            return true
        }

        // Intentar mover si la casilla está entre los posibles destinos
        if (selR >= 0) {
            val match = possibleMoves.firstOrNull { it.toR == r && it.toC == c }
            if (match != null) {
                onMove?.invoke(match)
            }
            selR = -1; selC = -1; possibleMoves = emptyList()
            invalidate()
            return true
        }
        return true
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pieceCache.values.forEach { if (!it.isRecycled) it.recycle() }
        pieceCache.clear()
    }

    // --- Mapeos entre coordenadas de tablero (r,c) y visuales (vr,vc) ---
    private fun toVisual(r: Int, c: Int): Pair<Int, Int> {
        return if (whiteAtBottom) r to c else (7 - r) to (7 - c)
    }
    private fun toBoard(vr: Int, vc: Int): Pair<Int, Int> {
        return if (whiteAtBottom) vr to vc else (7 - vr) to (7 - vc)
    }
}