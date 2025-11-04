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
import kotlin.math.max
import kotlin.math.min

class ChessBoardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    // Colores tablero y resaltados
    private val lightColor = Color.parseColor("#F0D9B5")
    private val darkColor = Color.parseColor("#B58863")
    private val highlightColor = Color.parseColor("#6fa8dc")
    private val lastMoveFromColor = Color.argb(160, 0, 200, 0)   // verde translúcido
    private val lastMoveToColor   = Color.argb(160, 200, 200, 0) // amarillo translúcido

    // Coordenadas (letras/números)
    var showCoordinates: Boolean = true
        set(v) { field = v; requestLayout(); invalidate() }
    private val coordColor = Color.parseColor("#333333")
    private val coordShadow = Color.parseColor("#FFFFFF")
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = coordColor
        textAlign = Paint.Align.CENTER
        isSubpixelText = true
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Tamaños y origen del tablero dentro del View
    private var viewSize = 0f
    private var labelBand = 0f       // banda para números (izq) y letras (abajo)
    private var boardSide = 0f       // lado del tablero (sin bandas)
    private var boardOriginX = 0f    // origen X del tablero (después de banda izquierda)
    private var boardOriginY = 0f    // origen Y del tablero (arriba; la banda está abajo)
    private var squareSize = 0f      // tamaño de casilla (boardSide/8)

    private var engine: ChessEngine? = null
    var onMove: ((Move) -> Unit)? = null

    // Perspectiva: true=controlás Blancas, false=Negras (se invierte si rotateForBlack)
    var humanIsWhite: Boolean = true
        set(value) {
            field = value
            updatePerspective()
        }

    // Si es true y jugás con negras, rota 180° (visual y toques)
    var rotateForBlack: Boolean = true
        set(value) {
            field = value
            updatePerspective()
        }

    // ¿Blancas abajo en pantalla?
    private var whiteAtBottom: Boolean = true
    private fun updatePerspective() {
        whiteAtBottom = if (rotateForBlack) humanIsWhite else true
        invalidate()
    }

    private var selR = -1; private var selC = -1
    private var possibleMoves: List<Move> = emptyList()

    // último movimiento
    var lastMove: Move? = null

    // cache de bitmaps escalados
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
        // lado del View (cuadrado)
        viewSize = min(w, h).toFloat()

        // banda para coordenadas: 8.5% del lado, acotada entre 16dp y 32dp
        val minBand = dp(16f)
        val maxBand = dp(32f)
        labelBand = if (showCoordinates) (viewSize * 0.085f).coerceIn(minBand, maxBand) else 0f

        // el tablero ocupa el resto
        boardSide = (viewSize - labelBand).coerceAtLeast(0f)
        boardOriginX = labelBand
        boardOriginY = 0f
        squareSize = if (boardSide > 0f) boardSide / 8f else 0f

        // ajustar tamaño de fuente
        textPaint.textSize = (labelBand * 0.55f).coerceAtLeast(dp(10f))
        textPaint.setShadowLayer(2f, 0f, 0f, coordShadow)

        // si cambió el tamaño, reciclar cache
        if (w != oldw || h != oldh) {
            pieceCache.values.forEach { if (!it.isRecycled) it.recycle() }
            pieceCache.clear()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val engine = engine ?: return
        if (squareSize <= 0f) return

        // 1) Tablero (según perspectiva)
        for (vr in 0..7) for (vc in 0..7) {
            val (r, c) = toBoard(vr, vc)
            val left = boardOriginX + vc * squareSize
            val top  = boardOriginY + vr * squareSize
            paint.style = Paint.Style.FILL
            paint.color = if ((r + c) % 2 == 0) lightColor else darkColor
            canvas.drawRect(left, top, left + squareSize, top + squareSize, paint)
        }

        // 2) Último movimiento (debajo de piezas)
        lastMove?.let { lm ->
            paint.style = Paint.Style.FILL
            val (vfr, vfc) = toVisual(lm.fromR, lm.fromC)
            paint.color = lastMoveFromColor
            canvas.drawRect(
                boardOriginX + vfc * squareSize,
                boardOriginY + vfr * squareSize,
                boardOriginX + (vfc + 1) * squareSize,
                boardOriginY + (vfr + 1) * squareSize,
                paint
            )
            val (vtr, vtc) = toVisual(lm.toR, lm.toC)
            paint.color = lastMoveToColor
            canvas.drawRect(
                boardOriginX + vtc * squareSize,
                boardOriginY + vtr * squareSize,
                boardOriginX + (vtc + 1) * squareSize,
                boardOriginY + (vtr + 1) * squareSize,
                paint
            )
        }

        // 3) Selección + destinos
        if (selR >= 0 && selC >= 0) {
            val (vsr, vsc) = toVisual(selR, selC)
            paint.color = highlightColor
            paint.alpha = 120
            paint.style = Paint.Style.FILL
            canvas.drawRect(
                boardOriginX + vsc * squareSize,
                boardOriginY + vsr * squareSize,
                boardOriginX + (vsc + 1) * squareSize,
                boardOriginY + (vsr + 1) * squareSize,
                paint
            )
            paint.alpha = 255

            for (m in possibleMoves) {
                val (tr, tc) = toVisual(m.toR, m.toC)
                val cx = boardOriginX + (tc + 0.5f) * squareSize
                val cy = boardOriginY + (tr + 0.5f) * squareSize
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
            paint.alpha = 255
        }

        // 4) Piezas
        for (r in 0..7) for (c in 0..7) {
            val p = engine.pieceAt(r, c)
            if (p == '.') continue
            val resName = if (p.isUpperCase()) "white_" + pieceName(p.lowercaseChar()) else "black_" + pieceName(p)
            val bmp = getBitmapForPiece(resName)
            val (vr, vc) = toVisual(r, c)
            val left = boardOriginX + vc * squareSize
            val top  = boardOriginY + vr * squareSize
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

        // 5) Coordenadas (letras abajo, números a la izquierda)
        if (showCoordinates && labelBand > 0f) {
            drawCoordinates(canvas)
        }
    }

    private fun drawCoordinates(canvas: Canvas) {
        // Fondo suave para bandas (opcional)
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(32, 0, 0, 0)
        // banda izquierda (números)
        canvas.drawRect(0f, 0f, labelBand, boardSide, paint)
        // banda inferior (letras)
        canvas.drawRect(labelBand, boardSide, labelBand + boardSide, labelBand + boardSide, paint)

        // Letras abajo
        val files: List<Char> =
            if (whiteAtBottom) ('a'..'h').toList()
            else ('h' downTo 'a').toList()

        val baseY = boardOriginY + boardSide + (labelBand * 0.70f)
        for (c in 0..7) {
            val cx = boardOriginX + (c + 0.5f) * squareSize
            canvas.drawText(files[c].toString(), cx, baseY, textPaint)
        }

        // Números a la izquierda
        for (r in 0..7) {
            val centerY = boardOriginY + (r + 0.5f) * squareSize
            val label = if (whiteAtBottom) (8 - r) else (r + 1)
            val x = boardOriginX - labelBand * 0.50f
            canvas.drawText(label.toString(), x, centerY + textCenteringOffset(), textPaint)
        }
    }

    private fun textCenteringOffset(): Float {
        val fm = textPaint.fontMetrics
        return - (fm.ascent + fm.descent) / 2f
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
        if (!src.isRecycled && src !== scaled) src.recycle()
        pieceCache[id] = scaled
        return scaled
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val engine = engine ?: return false
        if (event.action != MotionEvent.ACTION_DOWN) return false
        if (squareSize <= 0f) return false

        // Solo permitir cuando es el turno del humano
        if (engine.whiteToMove != humanIsWhite) return false

        // Limitar a zona del tablero (excluyendo bandas)
        val relX = event.x - boardOriginX
        val relY = event.y - boardOriginY
        if (relX < 0f || relY < 0f || relX >= boardSide || relY >= boardSide) return false

        val vc = (relX / squareSize).toInt().coerceIn(0, 7)
        val vr = (relY / squareSize).toInt().coerceIn(0, 7)
        val (r, c) = toBoard(vr, vc)
        val piece = engine.pieceAt(r, c)

        // Deselección
        if (selR == r && selC == c) {
            selR = -1; selC = -1; possibleMoves = emptyList()
            invalidate()
            return true
        }

        // Selección de pieza propia
        if (piece != '.' && piece.isUpperCase() == humanIsWhite) {
            selR = r; selC = c
            possibleMoves = engine.legalMovesFor(r, c)
            try { SoundManager.playSelect() } catch (_: Exception) {}
            invalidate()
            return true
        }

        // Intentar mover
        if (selR >= 0) {
            val match = possibleMoves.firstOrNull { it.toR == r && it.toC == c }
            if (match != null) onMove?.invoke(match)
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

    // --- Mapeos entre coordenadas tablero (r,c) y visuales (vr,vc) ---
    private fun toVisual(r: Int, c: Int): Pair<Int, Int> =
        if (whiteAtBottom) r to c else (7 - r) to (7 - c)

    private fun toBoard(vr: Int, vc: Int): Pair<Int, Int> =
        if (whiteAtBottom) vr to vc else (7 - vr) to (7 - vc)

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}