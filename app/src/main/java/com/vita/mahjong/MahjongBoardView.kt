package com.vita.mahjong

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class MahjongBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface Listener {
        fun onMovesChanged(moves: Int)
        fun onWin(moves: Int)
        fun onNoMovesLeft()
    }

    var listener: Listener? = null

    private var tiles: MutableList<Tile> = generateBoard()
    private var selected: Tile? = null
    private var moves = 0

    private var unit = 0f
    private var boardOffsetX = 0f
    private var boardOffsetY = 0f
    private val layerShiftPx get() = unit * 0.22f

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 0, 0, 0)
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#5D4037")
    }
    private val selectedBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#FFC107")
    }
    private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 10, 20, 15)
    }
    private val symbolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B71C1C")
        textAlign = Paint.Align.CENTER
    }

    fun newGame() {
        tiles = generateBoard()
        selected = null
        moves = 0
        listener?.onMovesChanged(moves)
        requestLayout()
        invalidate()
    }

    fun shuffleRemaining() {
        val remaining = tiles.filter { !it.matched }
        val symbols = remaining.map { it.symbol }.shuffled()
        remaining.forEachIndexed { i, t ->
            val idx = tiles.indexOf(t)
            tiles[idx] = t.copy(symbol = symbols[i])
        }
        selected = null
        invalidate()
        if (!hasValidMove(tiles)) listener?.onNoMovesLeft()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeLayout(w, h)
    }

    private fun computeLayout(w: Int, h: Int) {
        val (maxXFine, maxYFine) = boardBoundsFine(tiles)
        val marginFine = 2
        val totalFineW = maxXFine + marginFine
        val totalFineH = maxYFine + marginFine
        unit = minOf(w / totalFineW.toFloat(), h / totalFineH.toFloat())
        boardOffsetX = (w - maxXFine * unit) / 2f
        boardOffsetY = (h - maxYFine * unit) / 2f
    }

    private fun rectFor(t: Tile): RectF {
        val shift = t.z * layerShiftPx
        val left = boardOffsetX + t.x * unit - shift
        val top = boardOffsetY + t.y * unit - shift
        val w = unit * 1.86f
        val hgt = unit * 1.94f
        return RectF(left, top, left + w, top + hgt)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (unit <= 0f) return

        val sorted = tiles.filter { !it.matched }.sortedBy { it.z }
        val radius = unit * 0.28f

        for (t in sorted) {
            val rect = rectFor(t)
            val selectable = isSelectable(t, tiles)

            val shadowRect = RectF(rect)
            shadowRect.offset(unit * 0.08f, unit * 0.1f)
            canvas.drawRoundRect(shadowRect, radius, radius, shadowPaint)

            facePaint.shader = LinearGradient(
                rect.left, rect.top, rect.left, rect.bottom,
                Color.parseColor("#FFFDF5"), Color.parseColor("#E8DCC0"),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(rect, radius, radius, facePaint)

            borderPaint.strokeWidth = unit * 0.09f
            canvas.drawRoundRect(rect, radius, radius, borderPaint)

            symbolPaint.textSize = unit * 1.35f
            val textY = rect.centerY() - (symbolPaint.descent() + symbolPaint.ascent()) / 2f
            canvas.drawText(t.symbol, rect.centerX(), textY, symbolPaint)

            if (!selectable) {
                canvas.drawRoundRect(rect, radius, radius, dimPaint)
            }
            if (t == selected) {
                selectedBorderPaint.strokeWidth = unit * 0.16f
                canvas.drawRoundRect(rect, radius, radius, selectedBorderPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN) return true
        val px = event.x
        val py = event.y

        val hit = tiles.filter { !it.matched && rectFor(it).contains(px, py) }
            .maxByOrNull { it.z } ?: return true

        if (!isSelectable(hit, tiles)) {
            invalidate()
            return true
        }

        val cur = selected
        if (cur == null) {
            selected = hit
            invalidate()
            return true
        }

        if (cur == hit) {
            selected = null
            invalidate()
            return true
        }

        moves++
        listener?.onMovesChanged(moves)

        if (cur.symbol == hit.symbol) {
            val curIdx = tiles.indexOf(cur)
            val hitIdx = tiles.indexOf(hit)
            tiles[curIdx] = cur.copy(matched = true)
            tiles[hitIdx] = hit.copy(matched = true)
            selected = null
            invalidate()

            if (tiles.all { it.matched }) {
                listener?.onWin(moves)
            } else if (!hasValidMove(tiles)) {
                listener?.onNoMovesLeft()
            }
        } else {
            selected = hit
            invalidate()
        }

        return true
    }
}
