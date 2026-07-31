package com.vita.mahjong

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class MahjongBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    companion object {
        private const val TRAY_SIZE = 4
        private const val FLY_DURATION_MS = 260L
        private const val GROW_DURATION_MS = 220L
        private const val SHAKE_DURATION_MS = 320L
        private const val SHUFFLE_DURATION_MS = 550L
        private const val CONVERGE_MS = 140L
        private const val POP_MS = 90L
        private const val BURST_MS = 380L
        private const val COMBO_WINDOW_MS = 2200L
        private const val COMBO_POPUP_MS = 750L
        private const val PARTICLES_PER_TILE = 9
        private const val HISTORY_LIMIT = 20
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

    private data class UndoFlight(
        val tile: Tile,
        val from: RectF,
        val to: RectF,
        val startTime: Long
    )

    private data class GrowingSlot(
        val tile: Tile,
        val index: Int,
        val startTime: Long
    )

    private data class RejectShake(val tile: Tile, val startTime: Long)

    private data class ShuffleAnim(val tile: Tile, val offset: PointF, val startTime: Long)

    private data class PickRecord(
        val tile: Tile,
        val slotIndex: Int,
        val pairTile: Tile?,
        val pairSlot: Int,
        val movesBefore: Int
    )

    private data class Particle(
        val startX: Float,
        val startY: Float,
        val vx: Float,
        val vy: Float,
        val color: Int,
        val size: Float
    )

    private data class MatchBurst(
        val tileA: Tile,
        val tileB: Tile,
        val slotA: Int,
        val slotB: Int,
        val startTime: Long,
        val particles: List<Particle>
    )

    private data class ComboPopup(val text: String, val startTime: Long, val cx: Float, val cy: Float)

    var listener: Listener? = null

    private var tiles: MutableList<Tile> = generateBoard()
    private val traySlots = arrayOfNulls<Tile>(TRAY_SIZE)
    private var flying: FlyingTile? = null
    private var undoFlight: UndoFlight? = null
    private val growingSlots = mutableListOf<GrowingSlot>()
    private var matchBurst: MatchBurst? = null
    private var comboPopup: ComboPopup? = null
    private var comboCount = 0
    private var lastMatchTime = 0L
    private var rejectShake: RejectShake? = null
    private var shuffleAnims: List<ShuffleAnim> = emptyList()
    private val history = ArrayDeque<PickRecord>()
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
    private val layerShiftPx get() = unit * 0.32f

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sidePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#8D6E4A")
    }
    private val selectedBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#FFC107")
    }
    private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val backPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val backBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#7A4A1E")
    }
    private val medallionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#E8B84B")
    }
    private val symbolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFF6D6")
    }
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val comboOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#3E2200")
        textAlign = Paint.Align.CENTER
    }
    private val comboPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FFD54F")
        textAlign = Paint.Align.CENTER
    }

    private fun isAnimating() =
        flying != null || undoFlight != null || shuffleAnims.isNotEmpty()

    fun newGame() {
        tiles = generateBoard()
        for (i in 0 until TRAY_SIZE) traySlots[i] = null
        growingSlots.clear()
        matchBurst = null
        comboPopup = null
        comboCount = 0
        lastMatchTime = 0L
        flying = null
        undoFlight = null
        rejectShake = null
        shuffleAnims = emptyList()
        history.clear()
        moves = 0
        wonFired = false
        lostFired = false
        listener?.onMovesChanged(moves)
        requestLayout()
        invalidate()
    }

    fun shuffleRemaining() {
        if (isAnimating()) return
        val symbols = tiles.map { it.symbol }.shuffled()
        tiles = tiles.mapIndexed { i, t -> t.copy(symbol = symbols[i]) }.toMutableList()
        val now = System.currentTimeMillis()
        shuffleAnims = tiles.map {
            ShuffleAnim(
                it,
                PointF(Random.nextFloat() * 2f - 1f, Random.nextFloat() * 2f - 1f),
                now
            )
        }
        invalidate()
    }

    fun undo() {
        if (isAnimating() || matchBurst != null) return
        val rec = history.removeLastOrNull() ?: return

        moves = rec.movesBefore
        listener?.onMovesChanged(moves)
        wonFired = false
        lostFired = false

        if (rec.pairTile != null) {
            val now = System.currentTimeMillis()
            traySlots[rec.pairSlot] = rec.pairTile
            growingSlots.add(GrowingSlot(rec.pairTile, rec.pairSlot, now))
        }
        traySlots[rec.slotIndex] = null

        undoFlight = UndoFlight(
            rec.tile,
            traySlotRect(rec.slotIndex),
            rectFor(rec.tile),
            System.currentTimeMillis()
        )
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
        val marginFine = 1.2f
        val trayFineH = 2.5f
        val trayFineGap = 0.5f
        val totalFineW = maxOf(cachedMaxXFine + marginFine, TRAY_SIZE * 2.45f + marginFine)
        val totalFineH = cachedMaxYFine + marginFine + trayFineH + trayFineGap
        unit = minOf(w / totalFineW, h / totalFineH)
        viewW = w.toFloat()
        boardOffsetX = (w - cachedMaxXFine * unit) / 2f
        val contentH = (cachedMaxYFine + trayFineH + trayFineGap) * unit
        val topMargin = (h - contentH) / 2f
        trayOffsetY = topMargin
        boardOffsetY = topMargin + (trayFineH + trayFineGap) * unit
        traySlotSize = unit * 2.4f
        traySlotGap = unit * 0.25f
    }

    private fun rectFor(t: Tile): RectF {
        val shift = t.z * layerShiftPx
        val left = boardOffsetX + t.x * unit - shift
        val top = boardOffsetY + t.y * unit - shift
        val w = unit * 1.97f
        val hgt = unit * 2.03f
        return RectF(left, top, left + w, top + hgt)
    }

    private fun traySlotRect(index: Int): RectF {
        val totalW = TRAY_SIZE * traySlotSize + (TRAY_SIZE - 1) * traySlotGap
        val startX = (viewW - totalW) / 2f
        val left = startX + index * (traySlotSize + traySlotGap)
        return RectF(left, trayOffsetY, left + traySlotSize, trayOffsetY + traySlotSize)
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    private fun lerpRect(from: RectF, to: RectF, t: Float): RectF = RectF(
        lerp(from.left, to.left, t),
        lerp(from.top, to.top, t),
        lerp(from.right, to.right, t),
        lerp(from.bottom, to.bottom, t)
    )

    private fun scaleRect(r: RectF, scale: Float): RectF {
        val cx = r.centerX()
        val cy = r.centerY()
        val hw = r.width() / 2f * scale
        val hh = r.height() / 2f * scale
        return RectF(cx - hw, cy - hh, cx + hw, cy + hh)
    }

    private fun rectAt(cx: Float, cy: Float, size: Float): RectF {
        val h = size / 2f
        return RectF(cx - h, cy - h, cx + h, cy + h)
    }

    private fun symbolColor(symbol: String): Int {
        val cp = symbol.codePointAt(0)
        return when (cp) {
            in 0x1F007..0x1F00F -> Color.parseColor("#1A2340")
            in 0x1F010..0x1F018 -> Color.parseColor("#1B7A3D")
            in 0x1F019..0x1F021 -> Color.parseColor("#1565C0")
            0x1F004 -> Color.parseColor("#C62828")
            0x1F005 -> Color.parseColor("#2E7D32")
            else -> Color.parseColor("#C62828")
        }
    }

    private fun drawTile(canvas: Canvas, rect: RectF, symbol: String, highlight: Boolean, alpha: Int) {
        val radius = unit * 0.28f

        val sideRect = RectF(rect.left, rect.top + unit * 0.12f, rect.right, rect.bottom + unit * 0.12f)
        sidePaint.color = Color.argb(alpha, 0xC4, 0xAE, 0x7C)
        canvas.drawRoundRect(sideRect, radius, radius, sidePaint)

        val shadowRect = RectF(rect)
        shadowRect.offset(unit * 0.08f, unit * 0.1f)
        shadowPaint.color = Color.argb(90 * alpha / 255, 0, 0, 0)
        canvas.drawRoundRect(shadowRect, radius, radius, shadowPaint)

        facePaint.shader = LinearGradient(
            rect.left, rect.top, rect.left, rect.bottom,
            Color.parseColor("#FFFEF9"), Color.parseColor("#F3E7C9"),
            Shader.TileMode.CLAMP
        )
        facePaint.alpha = alpha
        canvas.drawRoundRect(rect, radius, radius, facePaint)

        borderPaint.strokeWidth = unit * 0.07f
        borderPaint.alpha = alpha
        canvas.drawRoundRect(rect, radius, radius, borderPaint)

        symbolPaint.color = symbolColor(symbol)
        symbolPaint.textSize = rect.height() * 0.7f
        symbolPaint.alpha = alpha
        val textY = rect.centerY() - (symbolPaint.descent() + symbolPaint.ascent()) / 2f
        canvas.drawText(symbol, rect.centerX(), textY, symbolPaint)

        if (highlight) {
            selectedBorderPaint.strokeWidth = unit * 0.16f
            canvas.drawRoundRect(rect, radius, radius, selectedBorderPaint)
        }
    }

    private fun drawTileBack(canvas: Canvas, rect: RectF) {
        val radius = unit * 0.28f

        val sideRect = RectF(rect.left, rect.top + unit * 0.12f, rect.right, rect.bottom + unit * 0.12f)
        sidePaint.color = Color.parseColor("#A9740F")
        canvas.drawRoundRect(sideRect, radius, radius, sidePaint)

        val shadowRect = RectF(rect)
        shadowRect.offset(unit * 0.08f, unit * 0.1f)
        shadowPaint.color = Color.argb(90, 0, 0, 0)
        canvas.drawRoundRect(shadowRect, radius, radius, shadowPaint)

        backPaint.shader = LinearGradient(
            rect.left, rect.top, rect.left, rect.bottom,
            Color.parseColor("#F6C244"), Color.parseColor("#D89A1E"),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, radius, radius, backPaint)

        backBorderPaint.strokeWidth = unit * 0.07f
        canvas.drawRoundRect(rect, radius, radius, backBorderPaint)

        medallionPaint.strokeWidth = unit * 0.05f
        val cx = rect.centerX()
        val cy = rect.centerY()
        canvas.drawCircle(cx, cy, rect.width() * 0.28f, medallionPaint)
        canvas.drawCircle(cx, cy, rect.width() * 0.15f, medallionPaint)
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

    private fun buildParticles(cx: Float, cy: Float, colorA: Int, colorB: Int): List<Particle> {
        val list = mutableListOf<Particle>()
        repeat(PARTICLES_PER_TILE) {
            val angle = Random.nextFloat() * (Math.PI.toFloat() * 2f)
            val speed = unit * (1.1f + Random.nextFloat() * 1.3f)
            list.add(
                Particle(
                    cx, cy,
                    cos(angle) * speed,
                    sin(angle) * speed - unit * 0.6f,
                    if (it % 2 == 0) colorA else colorB,
                    unit * (0.09f + Random.nextFloat() * 0.07f)
                )
            )
        }
        return list
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (unit <= 0f) return

        drawTray(canvas)

        val sorted = tiles.sortedBy { it.z }
        var needsMoreFrames = false
        val shake = rejectShake
        val now = System.currentTimeMillis()
        val activeShuffle = shuffleAnims

        for (t in sorted) {
            var rect = rectFor(t)

            if (shake != null && shake.tile == t) {
                val raw = ((now - shake.startTime).toFloat() / SHAKE_DURATION_MS).coerceIn(0f, 1f)
                if (raw < 1f) {
                    val amp = unit * 0.14f * (1f - raw)
                    val dx = sin(raw * Math.PI.toFloat() * 6f) * amp
                    rect = RectF(rect.left + dx, rect.top, rect.right + dx, rect.bottom)
                    needsMoreFrames = true
                } else {
                    rejectShake = null
                }
            }

            if (activeShuffle.isNotEmpty()) {
                val anim = activeShuffle.firstOrNull { it.tile == t }
                if (anim != null) {
                    val raw = ((now - anim.startTime).toFloat() / SHUFFLE_DURATION_MS).coerceIn(0f, 1f)
                    if (raw < 1f) {
                        val mag = sin(raw * Math.PI.toFloat()) * unit * 1.1f
                        val dx = anim.offset.x * mag
                        val dy = anim.offset.y * mag
                        rect = RectF(rect.left + dx, rect.top + dy, rect.right + dx, rect.bottom + dy)
                        needsMoreFrames = true
                    }
                }
            }

            if (isCovered(t, tiles)) {
                drawTileBack(canvas, rect)
            } else {
                drawTile(canvas, rect, t.symbol, highlight = false, alpha = 255)
            }
        }

        if (activeShuffle.isNotEmpty() && activeShuffle.all { (now - it.startTime) >= SHUFFLE_DURATION_MS }) {
            shuffleAnims = emptyList()
        }

        traySlots.forEachIndexed { i, t ->
            if (t != null && growingSlots.none { it.index == i }) {
                drawTile(canvas, traySlotRect(i), t.symbol, highlight = false, alpha = 255)
            }
        }

        if (growingSlots.isNotEmpty()) {
            for (g in growingSlots) {
                val raw = ((now - g.startTime).toFloat() / GROW_DURATION_MS).coerceIn(0f, 1f)
                val rect = scaleRect(traySlotRect(g.index), raw)
                drawTile(canvas, rect, g.tile.symbol, highlight = false, alpha = (raw * 255).toInt())
            }
            growingSlots.removeAll { (now - it.startTime) >= GROW_DURATION_MS }
            needsMoreFrames = needsMoreFrames || growingSlots.isNotEmpty()
        }

        val mb = matchBurst
        if (mb != null) {
            val elapsed = now - mb.startTime
            val rectA = traySlotRect(mb.slotA)
            val rectB = traySlotRect(mb.slotB)
            val midX = (rectA.centerX() + rectB.centerX()) / 2f
            val midY = (rectA.centerY() + rectB.centerY()) / 2f

            when {
                elapsed < CONVERGE_MS -> {
                    val raw = elapsed.toFloat() / CONVERGE_MS
                    val scale = lerp(1f, 1.3f, raw)
                    val cax = lerp(rectA.centerX(), midX, raw * 0.4f)
                    val cay = lerp(rectA.centerY(), midY, raw * 0.4f)
                    val cbx = lerp(rectB.centerX(), midX, raw * 0.4f)
                    val cby = lerp(rectB.centerY(), midY, raw * 0.4f)
                    drawTile(canvas, rectAt(cax, cay, traySlotSize * scale), mb.tileA.symbol, highlight = true, alpha = 255)
                    drawTile(canvas, rectAt(cbx, cby, traySlotSize * scale), mb.tileB.symbol, highlight = true, alpha = 255)
                }
                elapsed < CONVERGE_MS + POP_MS -> {
                    val raw = (elapsed - CONVERGE_MS).toFloat() / POP_MS
                    val glowRadius = lerp(traySlotSize * 0.25f, traySlotSize * 1.05f, raw)
                    glowPaint.alpha = ((1f - raw) * 255).toInt()
                    canvas.drawCircle(midX, midY, glowRadius, glowPaint)
                }
                elapsed < CONVERGE_MS + POP_MS + BURST_MS -> {
                    val raw = (elapsed - CONVERGE_MS - POP_MS).toFloat() / BURST_MS
                    for (p in mb.particles) {
                        val px = p.startX + p.vx * raw
                        val py = p.startY + p.vy * raw + 0.5f * unit * 2.4f * raw * raw
                        val alpha = ((1f - raw) * 255).toInt().coerceIn(0, 255)
                        particlePaint.color = p.color
                        particlePaint.alpha = alpha
                        canvas.drawCircle(px, py, p.size * (1f - raw * 0.4f), particlePaint)
                    }
                }
                else -> {
                    matchBurst = null
                    checkWin()
                }
            }
            if (matchBurst != null) needsMoreFrames = true
        }

        val combo = comboPopup
        if (combo != null) {
            val raw = ((now - combo.startTime).toFloat() / COMBO_POPUP_MS).coerceIn(0f, 1f)
            if (raw < 1f) {
                val scale = if (raw < 0.25f) lerp(0.5f, 1.15f, raw / 0.25f) else 1f
                val dy = lerp(0f, -unit * 1.4f, raw)
                val alpha = ((1f - raw) * 255).toInt()
                comboOutlinePaint.textSize = unit * 0.85f * scale
                comboOutlinePaint.strokeWidth = unit * 0.05f
                comboOutlinePaint.alpha = alpha
                comboPaint.textSize = unit * 0.85f * scale
                comboPaint.alpha = alpha
                val cy = combo.cy + dy
                canvas.drawText(combo.text, combo.cx, cy, comboOutlinePaint)
                canvas.drawText(combo.text, combo.cx, cy, comboPaint)
                needsMoreFrames = true
            } else {
                comboPopup = null
            }
        }

        val fly = flying
        if (fly != null) {
            val raw = ((now - fly.startTime).toFloat() / FLY_DURATION_MS).coerceIn(0f, 1f)
            val t = 1f - (1f - raw) * (1f - raw)
            val rect = lerpRect(fly.from, fly.to, t)
            drawTile(canvas, rect, fly.tile.symbol, highlight = true, alpha = 255)
            if (raw >= 1f) {
                finishFlight(fly)
            } else {
                needsMoreFrames = true
            }
        }

        val uf = undoFlight
        if (uf != null) {
            val raw = ((now - uf.startTime).toFloat() / FLY_DURATION_MS).coerceIn(0f, 1f)
            val t = 1f - (1f - raw) * (1f - raw)
            val rect = lerpRect(uf.from, uf.to, t)
            drawTile(canvas, rect, uf.tile.symbol, highlight = true, alpha = 255)
            if (raw >= 1f) {
                tiles.add(uf.tile)
                undoFlight = null
                invalidate()
            } else {
                needsMoreFrames = true
            }
        }

        if (needsMoreFrames) postInvalidateOnAnimation()
    }

    private fun finishFlight(fly: FlyingTile) {
        flying = null
        traySlots[fly.slotIndex] = fly.tile

        val pairIndex = traySlots.indices.firstOrNull { i ->
            i != fly.slotIndex && traySlots[i]?.symbol == fly.tile.symbol
        }

        if (pairIndex != null) {
            val pairTile = traySlots[pairIndex]!!
            history.addLast(PickRecord(fly.tile, fly.slotIndex, pairTile, pairIndex, moves))

            val now = System.currentTimeMillis()
            val rectA = traySlotRect(fly.slotIndex)
            val rectB = traySlotRect(pairIndex)
            val midX = (rectA.centerX() + rectB.centerX()) / 2f
            val midY = (rectA.centerY() + rectB.centerY()) / 2f
            val particles = buildParticles(midX, midY, symbolColor(fly.tile.symbol), symbolColor(pairTile.symbol))
            matchBurst = MatchBurst(fly.tile, pairTile, fly.slotIndex, pairIndex, now, particles)

            traySlots[fly.slotIndex] = null
            traySlots[pairIndex] = null
            moves++
            listener?.onMovesChanged(moves)

            comboCount = if (now - lastMatchTime <= COMBO_WINDOW_MS) comboCount + 1 else 1
            lastMatchTime = now
            val text = if (comboCount >= 2) "Combo x$comboCount!" else "Juft!"
            comboPopup = ComboPopup(text, now, midX, trayOffsetY - unit * 0.2f)
        } else {
            history.addLast(PickRecord(fly.tile, fly.slotIndex, null, -1, moves))
            if (traySlots.all { it != null }) {
                if (!lostFired) {
                    lostFired = true
                    listener?.onLose()
                }
            }
        }
        if (history.size > HISTORY_LIMIT) history.removeFirst()
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
        if (isAnimating()) return true

        val px = event.x
        val py = event.y

        val hit = tiles.filter { rectFor(it).contains(px, py) }
            .maxByOrNull { it.z } ?: return true

        if (!isSelectable(hit, tiles)) {
            rejectShake = RejectShake(hit, System.currentTimeMillis())
            invalidate()
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
