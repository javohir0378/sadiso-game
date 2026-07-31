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

    companion object {
        private const val TRAY_SIZE = 4
        private const val FLY_DURATION_MS = 260L
        private const val CLEAR_DURATION_MS = 220L
    }

    interface Listener {
        fun onMovesChanged(moves: Int)
        fun onWin(moves: Int)
        fun onLose()
    }

    private data class FlyingTile(
        val tile: Tile,
        val from: RectF,
        val to: RectF,
        val slotIndex: Int,
        val startTime: Long
    )

    private data class ClearingSlot(
        val tile: Tile,
        val index: Int,
        val startTime: Long
    )

    var listener: Listener? = null

    private var tiles: MutableList<Tile> = generateBoard()
    private val traySlots = arrayOfNulls<Tile>(TRAY_SIZE)
    private var flying: FlyingTile? = null
    private val clearingSlots = mutableListOf<ClearingSlot>()
    private var moves = 0
    private var wonFired = false
    private var lostFired = false

    private var unit = 0f
    private var viewW = 0f
    private var boardOffsetX = 0f
    private var boardOffsetY = 0f
    private var trayOffsetY = 0f
    private var traySlotSize = 0f
    private var traySlotGap = 0f
    private var cachedMaxXFine = 1
    private var cachedMaxYFine = 1
    private val layerShiftPx get() = unit * 0.22f

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
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
    private val traySlotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 74, 20, 140)
        style = Paint.Style.FILL
    }
    private val traySlotBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#FFC107")
    }

    fun newGame() {
        tiles = generateBoard()
        for (i in 0 until TRAY_SIZE) traySlots[i] = null
        clearingSlots.clear()
        flying = null
        moves = 0
        wonFired = false
        lostFired = false
        listener?.onMovesChanged(moves)
        requestLayout()
        invalidate()
    }

    fun shuffleRemaining() {
        val symbols = tiles.map { it.symbol }.shuffled()
        tiles = tiles.mapIndexed { i, t -> t.copy(symbol = symbols[i]) }.toMutableList()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeLayout(w, h)
    }

    private fun computeLayout(w: Int, h: Int) {
        if (tiles.isNotEmpty()) {
            val (mx, my) = boardBoundsFine(tiles)
            cachedMaxXFine = mx
            cachedMaxYFine = my
        }
        val marginFine = 2f
        val trayFineH = 2.6f
        val trayFineGap = 0.7f
        val totalFineW = maxOf(cachedMaxXFine + marginFine, TRAY_SIZE * 2.3f + marginFine)
        val totalFineH = cachedMaxYFine + marginFine + trayFineH + trayFineGap
        unit = minOf(w / totalFineW, h / totalFineH)
        viewW = w.toFloat()
        boardOffsetX = (w - cachedMaxXFine * unit) / 2f
        val contentH = (cachedMaxYFine + trayFineH + trayFineGap) * unit
        val topMargin = (h - contentH) / 2f
        trayOffsetY = topMargin
        boardOffsetY = topMargin + (trayFineH + trayFineGap) * unit
        traySlotSize = unit * 2.1f
        traySlotGap = unit * 0.35f
    }

    private fun rectFor(t: Tile): RectF {
        val shift = t.z * layerShiftPx
        val left = boardOffsetX + t.x * unit - shift
        val top = boardOffsetY + t.y * unit - shift
        val w = unit * 1.9f
        val hgt = unit * 1.96f
        return RectF(left, top, left + w, top + hgt)
    }

    private fun traySlotRect(index: Int): RectF {
        val totalW = TRAY_SIZE * traySlotSize + (TRAY_SIZE - 1) * traySlotGap
        val startX = (viewW - totalW) / 2f
        val left = startX + index * (traySlotSize + traySlotGap)
        return RectF(left, trayOffsetY, left + traySlotSize, trayOffsetY + traySlotSize)
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    private fun shrinkRect(r: RectF, scale: Float): RectF {
        val cx = r.centerX()
        val cy = r.centerY()
        val hw = r.width() / 2f * scale
        val hh = r.height() / 2f * scale
        return RectF(cx - hw, cy - hh, cx + hw, cy + hh)
    }

    private fun drawTile(canvas: Canvas, rect: RectF, symbol: String, dim: Boolean, highlight: Boolean, alpha: Int) {
        val radius = unit * 0.28f

        val shadowRect = RectF(rect)
        shadowRect.offset(unit * 0.08f, unit * 0.1f)
        shadowPaint.color = Color.argb(90 * alpha / 255, 0, 0, 0)
        canvas.drawRoundRect(shadowRect, radius, radius, shadowPaint)

        facePaint.shader = LinearGradient(
            rect.left, rect.top, rect.left, rect.bottom,
            Color.parseColor("#FFFDF5"), Color.parseColor("#E8DCC0"),
            Shader.TileMode.CLAMP
        )
        facePaint.alpha = alpha
        canvas.drawRoundRect(rect, radius, radius, facePaint)

        borderPaint.strokeWidth = unit * 0.09f
        borderPaint.alpha = alpha
        canvas.drawRoundRect(rect, radius, radius, borderPaint)

        symbolPaint.textSize = rect.height() * 0.6f
        symbolPaint.alpha = alpha
        val textY = rect.centerY() - (symbolPaint.descent() + symbolPaint.ascent()) / 2f
        canvas.drawText(symbol, rect.centerX(), textY, symbolPaint)

        if (dim) {
            canvas.drawRoundRect(rect, radius, radius, dimPaint)
        }
        if (highlight) {
            selectedBorderPaint.strokeWidth = unit * 0.16f
            canvas.drawRoundRect(rect, radius, radius, selectedBorderPaint)
        }
    }

    private fun drawTray(canvas: Canvas) {
        val radius = unit * 0.22f
        traySlotBorderPaint.strokeWidth = unit * 0.1f
        for (i in 0 until TRAY_SIZE) {
            val r = traySlotRect(i)
            canvas.drawRoundRect(r, radius, radius, traySlotPaint)
            canvas.drawRoundRect(r, radius, radius, traySlotBorderPaint)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (unit <= 0f) return

        drawTray(canvas)

        val sorted = tiles.sortedBy { it.z }
        for (t in sorted) {
            drawTile(canvas, rectFor(t), t.symbol, dim = !isSelectable(t, tiles), highlight = false, alpha = 255)
        }

        traySlots.forEachIndexed { i, t ->
            if (t != null && clearingSlots.none { it.index == i }) {
                drawTile(canvas, traySlotRect(i), t.symbol, dim = false, highlight = false, alpha = 255)
            }
        }

        if (clearingSlots.isNotEmpty()) {
            val now = System.currentTimeMillis()
            var anyDone = false
            for (c in clearingSlots) {
                val raw = ((now - c.startTime).toFloat() / CLEAR_DURATION_MS).coerceIn(0f, 1f)
                val scale = 1f - raw
                val alpha = ((1f - raw) * 255).toInt()
                val rect = shrinkRect(traySlotRect(c.index), scale)
                drawTile(canvas, rect, c.tile.symbol, dim = false, highlight = false, alpha = alpha)
                if (raw >= 1f) anyDone = true
            }
            if (anyDone) {
                clearingSlots.removeAll { (System.currentTimeMillis() - it.startTime) >= CLEAR_DURATION_MS }
                checkWin()
            }
            postInvalidateOnAnimation()
        }

        val fly = flying
        if (fly != null) {
            val now = System.currentTimeMillis()
            val raw = ((now - fly.startTime).toFloat() / FLY_DURATION_MS).coerceIn(0f, 1f)
            val t = 1f - (1f - raw) * (1f - raw)
            val rect = RectF(
                lerp(fly.from.left, fly.to.left, t),
                lerp(fly.from.top, fly.to.top, t),
                lerp(fly.from.right, fly.to.right, t),
                lerp(fly.from.bottom, fly.to.bottom, t)
            )
            drawTile(canvas, rect, fly.tile.symbol, dim = false, highlight = true, alpha = 255)
            if (raw >= 1f) {
                finishFlight(fly)
            } else {
                postInvalidateOnAnimation()
            }
        }
    }

    private fun finishFlight(fly: FlyingTile) {
        flying = null
        traySlots[fly.slotIndex] = fly.tile

        val pairIndex = traySlots.indices.firstOrNull { i ->
            i != fly.slotIndex && traySlots[i]?.symbol == fly.tile.symbol
        }

        if (pairIndex != null) {
            val now = System.currentTimeMillis()
            clearingSlots.add(ClearingSlot(traySlots[fly.slotIndex]!!, fly.slotIndex, now))
            clearingSlots.add(ClearingSlot(traySlots[pairIndex]!!, pairIndex, now))
            traySlots[fly.slotIndex] = null
            traySlots[pairIndex] = null
            moves++
            listener?.onMovesChanged(moves)
        } else if (traySlots.all { it != null }) {
            if (!lostFired) {
                lostFired = true
                listener?.onLose()
            }
        }
        invalidate()
    }

    private fun checkWin() {
        if (!wonFired && tiles.isEmpty() && traySlots.all { it == null }) {
            wonFired = true
            listener?.onWin(moves)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN) return true
        if (flying != null) return true

        val px = event.x
        val py = event.y

        val hit = tiles.filter { rectFor(it).contains(px, py) }
            .maxByOrNull { it.z } ?: return true

        if (!isSelectable(hit, tiles)) {
            return true
        }

        val emptyIndex = traySlots.indexOfFirst { it == null }
        if (emptyIndex == -1) return true

        tiles.remove(hit)
        flying = FlyingTile(hit, rectFor(hit), traySlotRect(emptyIndex), emptyIndex, System.currentTimeMillis())
        invalidate()
        return true
    }
}
