// kotlin
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
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var squareSize = 0f

    private var engine: ChessEngine? = null
    var onMove: ((Move) -> Unit)? = null

    // New: which color the human controls (true = White, false = Black)
    var humanIsWhite: Boolean = true

    private var selR = -1; private var selC = -1
    private var possibleMoves: List<Move> = emptyList()

    // cache scaled piece bitmaps by resource id
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
        // set squareSize here so touches are safe before first onDraw
        squareSize = if (w > 0) w / 8f else 0f

        // if size changed, clear and recycle cache to recreate scaled bitmaps
        if (w != oldw || h != oldh) {
            pieceCache.values.forEach { if (!it.isRecycled) it.recycle() }
            pieceCache.clear()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val engine = engine ?: return
        // ensure squareSize is valid (fallback)
        if (squareSize == 0f) squareSize = width / 8f

        // draw squares
        for (r in 0..7) for (c in 0..7) {
            val left = c * squareSize
            val top = r * squareSize
            paint.style = Paint.Style.FILL
            paint.color = if ((r + c) % 2 == 0) lightColor else darkColor
            canvas.drawRect(left, top, left + squareSize, top + squareSize, paint)
        }

        // highlight selection square
        if (selR >= 0 && selC >= 0) {
            paint.color = highlightColor
            paint.alpha = 120
            paint.style = Paint.Style.FILL
            canvas.drawRect(selC * squareSize, selR * squareSize, (selC + 1) * squareSize, (selR + 1) * squareSize, paint)
            paint.alpha = 255
        }

        // draw move indicators: capture = ring, quiet = small dot
        if (selR >= 0 && selC >= 0) {
            for (m in possibleMoves) {
                val cx = (m.toC + 0.5f) * squareSize
                val cy = (m.toR + 0.5f) * squareSize
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
            paint.alpha = 255
            paint.style = Paint.Style.FILL
        }

        // draw pieces (use cached, pre-scaled bitmaps when available)
        for (r in 0..7) for (c in 0..7) {
            val p = engine.pieceAt(r, c)
            if (p == '.') continue
            val resName = if (p.isUpperCase()) "white_" + pieceName(p.lowercaseChar()) else "black_" + pieceName(p)
            val bmp = getBitmapForPiece(resName)
            if (bmp != null && !bmp.isRecycled) {
                // draw pre-scaled bitmap at board cell
                canvas.drawBitmap(bmp, c * squareSize, r * squareSize, null)
            } else {
                paint.color = if (p.isUpperCase()) Color.WHITE else Color.BLACK
                paint.textSize = squareSize * 0.5f
                paint.style = Paint.Style.FILL

                // center text in the square
                paint.textAlign = Paint.Align.CENTER
                // compute center coordinates
                val cx = c * squareSize + squareSize * 0.5f
                // vertical center using font metrics
                val fm = paint.fontMetrics
                val cy = r * squareSize + squareSize * 0.5f - (fm.ascent + fm.descent) / 2f

                canvas.drawText(p.toString(), cx, cy, paint)

                // restore default alignment if needed elsewhere
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

        // decode and scale to current squareSize
        val src = BitmapFactory.decodeResource(resources, id) ?: return null
        val size = squareSize.toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(src, size, size, true)
        // recycle original to avoid leaks
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

        // New: ignore input when it's not the human's turn
        if (engine.whiteToMove != humanIsWhite) return false

        val c = (event.x / squareSize).toInt().coerceIn(0, 7)
        val r = (event.y / squareSize).toInt().coerceIn(0, 7)
        val piece = engine.pieceAt(r, c)

        // if tapping the already selected square -> deselect
        if (selR == r && selC == c) {
            selR = -1; selC = -1; possibleMoves = emptyList()
            invalidate()
            return true
        }

        // select human's piece only, and only on human's turn
        if (piece != '.' && piece.isUpperCase() == humanIsWhite) {
            selR = r; selC = c
            possibleMoves = engine.legalMovesFor(r, c)

            // play selection sound
            try { SoundManager.playSelect() } catch (_: Exception) { /* safe guard */ }

            invalidate()
            return true
        }

        // if square is a possible move, send move
        if (selR >= 0) {
            val match = possibleMoves.firstOrNull { it.toR == r && it.toC == c }
            if (match != null) {
                onMove?.invoke(match)
            }
            // clear selection
            selR = -1; selC = -1; possibleMoves = emptyList()
            invalidate()
            return true
        }
        return true
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // recycle cached bitmaps to avoid leaks
        pieceCache.values.forEach { if (!it.isRecycled) it.recycle() }
        pieceCache.clear()
    }
}