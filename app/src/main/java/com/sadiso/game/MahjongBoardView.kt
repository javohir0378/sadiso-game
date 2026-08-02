package com.sadiso.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.SoundPool
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
        private const val GROW_DURATION_MS = 340L
        private const val SHAKE_DURATION_MS = 420L
        private const val SHUFFLE_DURATION_MS = 950L
        private const val PULL_MS = 190L
        private const val CONVERGE_MS = 230L
        private const val POP_MS = 190L
        private const val BURST_MS = 750L
        private const val COMBO_WINDOW_MS = 2200L
        private const val COMBO_POPUP_MS = 1150L
        private const val SHARD_GRID = 3
        private const val HISTORY_LIMIT = 20
        private const val HINT_DURATION_MS = 1400L
        // Real mahjong tiles are noticeably taller than wide, like a brick -
        // not the near-square footprint the fine-grid math would give by
        // default. This only scales rendering; the underlying fine-grid
        // stays uniform per axis so adjacency/covering logic is untouched.
        private const val TILE_ASPECT = 0.74f
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

    private data class HintFlash(val tileA: Tile, val tileB: Tile, val startTime: Long)

    private data class ShuffleAnim(val tile: Tile, val offset: PointF, val startTime: Long)

    private data class PickRecord(
        val tile: Tile,
        val slotIndex: Int,
        val pairTile: Tile?,
        val pairSlot: Int,
        val movesBefore: Int
    )

    private data class Shard(
        val relLeft: Float,
        val relTop: Float,
        val relRight: Float,
        val relBottom: Float,
        val vx: Float,
        val vy: Float,
        val rotSpeed: Float,
        val tint: Int
    )

    private data class MatchBurst(
        val tileA: Tile,
        val tileB: Tile,
        val slotA: Int,
        val slotB: Int,
        val startTime: Long,
        val fromA: RectF,
        val fromB: RectF,
        val windupA: PointF,
        val windupB: PointF,
        val impact: PointF,
        val shards: List<Shard>
    )

    private data class ComboPopup(val text: String, val startTime: Long, val cx: Float, val cy: Float)

    private data class AmbientParticle(
        val bx: Float,
        val by: Float,
        val r: Float,
        val phase: Float,
        val speed: Float
    )

    private data class Confetto(
        val x: Float,
        val y: Float,
        val vx: Float,
        val vy: Float,
        val color: Int,
        val phase: Float
    )

    var listener: Listener? = null

    private var tiles: MutableList<Tile> = generateBoard()
    private val traySlots = arrayOfNulls<Tile>(TRAY_SIZE)
    private val flyingTiles = mutableListOf<FlyingTile>()
    private var undoFlight: UndoFlight? = null
    private val growingSlots = mutableListOf<GrowingSlot>()
    // A list, not a single nullable value - matching quickly enough that a
    // second pair completes before the first burst finishes playing must
    // not cut the first one's animation short.
    private val matchBursts = mutableListOf<MatchBurst>()
    private var confetti: List<Confetto> = emptyList()
    private var confettiStartTime = 0L
    private var comboPopup: ComboPopup? = null
    private var comboCount = 0
    private var lastMatchTime = 0L
    private var rejectShake: RejectShake? = null
    private var hintFlash: HintFlash? = null
    private var shuffleAnims: List<ShuffleAnim> = emptyList()
    private var ambientParticles: List<AmbientParticle> = emptyList()
    private val history = ArrayDeque<PickRecord>()
    private var moves = 0
    private var wonFired = false
    private var lostFired = false

    private var unit = 0f
    private var unitX = 0f
    private var trayUnit = 0f
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
    private val glossPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bevelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
    }
    private val numeralPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val iconStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val selectPulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#FFD54F")
    }
    private val hintRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#FFF176")
    }
    private val hintGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFF176")
    }
    private val ambientParticlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val traySlotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 74, 20, 140)
        style = Paint.Style.FILL
    }
    private val traySlotBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#FFC107")
    }
    private val trayDividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(90, 0, 0, 0)
        strokeCap = Paint.Cap.ROUND
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFF6D6")
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#FFE082")
    }
    private val shardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
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
    private val confettiPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val soundPool: SoundPool? = if (isInEditMode) null else SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private val matchSoundId = soundPool?.load(context, R.raw.match_pop, 1) ?: 0
    private val winSoundId = soundPool?.load(context, R.raw.win_fanfare, 1) ?: 0

    private fun playMatchSound() {
        soundPool?.play(matchSoundId, 0.8f, 0.8f, 0, 0, 1f)
    }

    private fun playWinSound() {
        soundPool?.play(winSoundId, 0.9f, 0.9f, 0, 0, 1f)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        soundPool?.release()
    }

    private fun spawnWinCelebration() {
        val w = width.toFloat()
        if (w <= 0f) return
        val colors = intArrayOf(
            Color.parseColor("#FFD54F"), Color.parseColor("#FFF176"),
            Color.parseColor("#4FC3F7"), Color.parseColor("#81C784"),
            Color.parseColor("#FF8A65"), Color.parseColor("#F5EAD3")
        )
        confettiStartTime = System.currentTimeMillis()
        confetti = List(70) {
            Confetto(
                x = Random.nextFloat() * w,
                y = -Random.nextFloat() * unit * 5f,
                vx = (Random.nextFloat() * 2f - 1f) * unit * 0.7f,
                vy = unit * (1.3f + Random.nextFloat() * 1.5f),
                color = colors[Random.nextInt(colors.size)],
                phase = Random.nextFloat() * 6.2832f
            )
        }
    }

    // Tile faces/backs are drawn every frame for every visible tile, and
    // almost all of them share the exact same size - allocating a fresh
    // LinearGradient/RadialGradient/Path per tile per frame (as this used
    // to do) creates enough garbage every 16ms to cause GC-pause jank.
    // These cache the last shader/path built for a given size and only
    // rebuild when that size actually changes, combined with drawing at a
    // canvas-translated local origin so the cached shader still lines up.
    private var faceShaderSize = -1f to -1f
    private var faceShader: LinearGradient? = null
    private var glossFaceShaderSize = -1f to -1f
    private var glossFaceShader: RadialGradient? = null
    private var backShaderSize = -1f to -1f
    private var backShader: RadialGradient? = null
    private var glossBackShaderSize = -1f to -1f
    private var glossBackShader: RadialGradient? = null
    private var clipPathKey = Triple(-1f, -1f, -1f)
    private val cachedClipPath = Path()

    private fun faceShaderFor(w: Float, h: Float): LinearGradient {
        if (faceShaderSize != (w to h)) {
            faceShader = LinearGradient(
                0f, 0f, 0f, h,
                Color.parseColor("#FFFEF9"), Color.parseColor("#F1E4C4"),
                Shader.TileMode.CLAMP
            )
            faceShaderSize = w to h
        }
        return faceShader!!
    }

    private fun glossFaceShaderFor(w: Float, h: Float): RadialGradient {
        if (glossFaceShaderSize != (w to h)) {
            glossFaceShader = RadialGradient(
                w * 0.32f, h * 0.14f, w * 0.85f,
                Color.argb(120, 255, 255, 255), Color.argb(0, 255, 255, 255),
                Shader.TileMode.CLAMP
            )
            glossFaceShaderSize = w to h
        }
        return glossFaceShader!!
    }

    private fun backShaderFor(w: Float, h: Float): RadialGradient {
        if (backShaderSize != (w to h)) {
            backShader = RadialGradient(
                w * 0.32f, h * 0.24f, w * 0.95f,
                Color.parseColor("#FBD26B"), Color.parseColor("#C4860F"),
                Shader.TileMode.CLAMP
            )
            backShaderSize = w to h
        }
        return backShader!!
    }

    private fun glossBackShaderFor(w: Float, h: Float): RadialGradient {
        if (glossBackShaderSize != (w to h)) {
            glossBackShader = RadialGradient(
                w * 0.3f, h * 0.12f, w * 0.7f,
                Color.argb(110, 255, 255, 255), Color.argb(0, 255, 255, 255),
                Shader.TileMode.CLAMP
            )
            glossBackShaderSize = w to h
        }
        return glossBackShader!!
    }

    private fun clipPathFor(w: Float, h: Float, radius: Float): Path {
        val key = Triple(w, h, radius)
        if (clipPathKey != key) {
            cachedClipPath.rewind()
            cachedClipPath.addRoundRect(RectF(0f, 0f, w, h), radius, radius, Path.Direction.CW)
            clipPathKey = key
        }
        return cachedClipPath
    }

    private fun isAnimating() =
        flyingTiles.isNotEmpty() || undoFlight != null || shuffleAnims.isNotEmpty()

    fun newGame() {
        tiles = generateBoard()
        for (i in 0 until TRAY_SIZE) traySlots[i] = null
        growingSlots.clear()
        matchBursts.clear()
        confetti = emptyList()
        comboPopup = null
        comboCount = 0
        lastMatchTime = 0L
        flyingTiles.clear()
        undoFlight = null
        rejectShake = null
        shuffleAnims = emptyList()
        history.clear()
        moves = 0
        wonFired = false
        lostFired = false
        listener?.onMovesChanged(moves)
        // A new template can have different bounds than the previous board;
        // onSizeChanged only fires when the view's own size actually
        // changes, so a same-size relayout wouldn't otherwise recompute the
        // scale/offsets for the new shape.
        if (width > 0 && height > 0) computeLayout(width, height)
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

    fun hint(): Boolean {
        if (isAnimating() || matchBursts.isNotEmpty()) return true
        val selectable = tiles.filter { isSelectable(it, tiles) }
        val pair = selectable.groupBy { it.symbol }.values.firstOrNull { it.size >= 2 } ?: return false
        hintFlash = HintFlash(pair[0], pair[1], System.currentTimeMillis())
        invalidate()
        return true
    }

    fun undo() {
        if (isAnimating() || matchBursts.isNotEmpty()) return
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
        val marginFine = 1.0f
        val trayFineH = 2.5f
        val trayFineGap = 0.5f
        // Extra empty space kept ABOVE the tray, inside the view's own
        // bounds - a burst effect centered in the tray needs somewhere to
        // fly upward into, or it gets clipped by the view's own edge and
        // just vanishes.
        val trayTopBufferFine = 1.3f

        // The tray's own size/position depends only on the view's width,
        // never on which board template is active, so it never grows,
        // shrinks or shifts between games the way the board itself does.
        val trayFineW = TRAY_SIZE * 2.45f + marginFine
        trayUnit = w / trayFineW
        viewW = w.toFloat()
        trayOffsetY = trayUnit * trayTopBufferFine
        traySlotSize = trayUnit * 2.4f
        traySlotGap = trayUnit * 0.25f

        val boardTopY = trayOffsetY + (trayFineH + trayFineGap) * trayUnit
        val boardFineW = (cachedMaxXFine + marginFine) * TILE_ASPECT
        val availableBoardH = (h - boardTopY - marginFine * trayUnit).coerceAtLeast(1f)
        unit = minOf(w / boardFineW, availableBoardH / (cachedMaxYFine + marginFine))
        unitX = unit * TILE_ASPECT
        boardOffsetX = (w - cachedMaxXFine * unitX) / 2f
        val boardContentH = cachedMaxYFine * unit
        val extra = (availableBoardH - boardContentH).coerceAtLeast(0f)
        boardOffsetY = boardTopY + extra / 2f
    }

    private fun rectFor(t: Tile): RectF {
        val shift = t.z * layerShiftPx
        val left = boardOffsetX + t.x * unitX - shift
        val top = boardOffsetY + t.y * unit - shift
        val w = unitX * 1.97f
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

    private fun easeInOutCubic(t: Float): Float =
        if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).let { it * it * it } / 2f

    private fun easeOutCubic(t: Float): Float {
        val f = t - 1f
        return f * f * f + 1f
    }

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
        val radius = minOf(unitX, unit) * 0.28f
        val w = rect.width()
        val h = rect.height()

        canvas.save()
        canvas.translate(rect.left, rect.top)
        val localRect = RectF(0f, 0f, w, h)

        val sideRect = RectF(0f, unit * 0.13f, w, h + unit * 0.13f)
        sidePaint.color = Color.argb(alpha, 0xC4, 0xAE, 0x7C)
        canvas.drawRoundRect(sideRect, radius, radius, sidePaint)

        for (i in 2 downTo 0) {
            val off = unit * (0.05f + i * 0.045f)
            val shadowRect = RectF(off, off * 1.15f, w + off, h + off * 1.15f)
            shadowPaint.color = Color.argb((26 - i * 6) * alpha / 255, 0, 0, 0)
            canvas.drawRoundRect(shadowRect, radius, radius, shadowPaint)
        }

        facePaint.shader = faceShaderFor(w, h)
        facePaint.alpha = alpha
        canvas.drawRoundRect(localRect, radius, radius, facePaint)

        canvas.save()
        canvas.clipPath(clipPathFor(w, h, radius))
        glossPaint.shader = glossFaceShaderFor(w, h)
        glossPaint.alpha = alpha
        canvas.drawRect(localRect, glossPaint)
        canvas.restore()

        borderPaint.strokeWidth = unit * 0.055f
        borderPaint.alpha = alpha
        canvas.drawRoundRect(localRect, radius, radius, borderPaint)

        val innerRect = RectF(localRect)
        innerRect.inset(unit * 0.07f, unit * 0.07f)
        bevelPaint.strokeWidth = unit * 0.03f
        bevelPaint.alpha = 90 * alpha / 255
        canvas.drawRoundRect(innerRect, radius * 0.8f, radius * 0.8f, bevelPaint)

        drawSymbolIcon(canvas, localRect, symbol, alpha)

        if (highlight) {
            selectedBorderPaint.strokeWidth = unit * 0.16f
            selectedBorderPaint.alpha = alpha
            canvas.drawRoundRect(localRect, radius, radius, selectedBorderPaint)
        }
        canvas.restore()
    }

    private fun suitAndNumber(symbol: String): Pair<Int, Int> {
        val cp = symbol.codePointAt(0)
        return when (cp) {
            in 0x1F007..0x1F00F -> 0 to (cp - 0x1F007 + 1)
            in 0x1F010..0x1F018 -> 1 to (cp - 0x1F010 + 1)
            in 0x1F019..0x1F021 -> 2 to (cp - 0x1F019 + 1)
            0x1F004 -> 3 to 1
            0x1F005 -> 3 to 2
            else -> 0 to 1
        }
    }

    private fun drawSymbolIcon(canvas: Canvas, rect: RectF, symbol: String, alpha: Int) {
        val (suit, num) = suitAndNumber(symbol)
        val box = RectF(rect)
        box.inset(rect.width() * 0.17f, rect.height() * 0.15f)
        when (suit) {
            0 -> drawCharacterIcon(canvas, box, num, alpha)
            1 -> drawBambooIcon(canvas, box, num, alpha)
            2 -> drawDotIcon(canvas, box, num, alpha)
            else -> drawHonorIcon(canvas, box, num, alpha)
        }
    }

    private fun drawCharacterIcon(canvas: Canvas, box: RectF, num: Int, alpha: Int) {
        numeralPaint.color = Color.parseColor("#28345E")
        numeralPaint.alpha = alpha
        numeralPaint.textSize = box.height() * 0.74f
        val cy = box.centerY() - box.height() * 0.07f
        val textY = cy - (numeralPaint.descent() + numeralPaint.ascent()) / 2f
        canvas.drawText(num.toString(), box.centerX(), textY, numeralPaint)

        accentPaint.color = Color.parseColor("#D8A93A")
        accentPaint.alpha = alpha
        val barHalfW = box.width() * 0.24f
        val barY = box.bottom - box.height() * 0.05f
        val barH = box.height() * 0.05f
        val barRect = RectF(box.centerX() - barHalfW, barY - barH, box.centerX() + barHalfW, barY + barH)
        canvas.drawRoundRect(barRect, barH, barH, accentPaint)
    }

    private fun drawBambooIcon(canvas: Canvas, box: RectF, count: Int, alpha: Int) {
        val n = count.coerceIn(1, 5)
        val gap = box.width() * 0.1f
        val stalkW = (box.width() - gap * (n - 1)) / n
        for (i in 0 until n) {
            val left = box.left + i * (stalkW + gap)
            val stalkRect = RectF(left, box.top, left + stalkW, box.bottom)

            accentPaint.color = Color.parseColor("#2E8B45")
            accentPaint.alpha = alpha
            canvas.drawRoundRect(stalkRect, stalkW * 0.4f, stalkW * 0.4f, accentPaint)

            accentPaint.color = Color.parseColor("#7ED08A")
            accentPaint.alpha = alpha * 140 / 255
            val hlRect = RectF(stalkRect.left + stalkW * 0.14f, stalkRect.top + box.height() * 0.05f, stalkRect.left + stalkW * 0.34f, stalkRect.bottom - box.height() * 0.05f)
            canvas.drawRoundRect(hlRect, stalkW * 0.15f, stalkW * 0.15f, accentPaint)

            iconStrokePaint.color = Color.parseColor("#195C29")
            iconStrokePaint.strokeWidth = stalkW * 0.16f
            iconStrokePaint.alpha = alpha
            val j1 = box.top + box.height() * 0.36f
            val j2 = box.top + box.height() * 0.67f
            canvas.drawLine(stalkRect.left, j1, stalkRect.right, j1, iconStrokePaint)
            canvas.drawLine(stalkRect.left, j2, stalkRect.right, j2, iconStrokePaint)
        }
    }

    private fun drawDotIcon(canvas: Canvas, box: RectF, count: Int, alpha: Int) {
        val positions = when (count.coerceIn(1, 3)) {
            1 -> listOf(PointF(0.5f, 0.5f))
            2 -> listOf(PointF(0.26f, 0.26f), PointF(0.74f, 0.74f))
            else -> listOf(PointF(0.22f, 0.22f), PointF(0.5f, 0.5f), PointF(0.78f, 0.78f))
        }
        val r = box.width() * (if (count == 1) 0.32f else 0.22f)
        for (p in positions) {
            val cx = box.left + box.width() * p.x
            val cy = box.top + box.height() * p.y
            accentPaint.shader = RadialGradient(
                cx - r * 0.3f, cy - r * 0.3f, r * 1.6f,
                Color.parseColor("#6FB7F0"), Color.parseColor("#14508F"),
                Shader.TileMode.CLAMP
            )
            accentPaint.alpha = alpha
            canvas.drawCircle(cx, cy, r, accentPaint)
            accentPaint.shader = null

            iconStrokePaint.color = Color.parseColor("#0B3760")
            iconStrokePaint.strokeWidth = r * 0.14f
            iconStrokePaint.alpha = alpha
            canvas.drawCircle(cx, cy, r, iconStrokePaint)

            accentPaint.color = Color.argb(150 * alpha / 255, 255, 255, 255)
            canvas.drawCircle(cx - r * 0.32f, cy - r * 0.32f, r * 0.26f, accentPaint)
        }
    }

    private fun drawHonorIcon(canvas: Canvas, box: RectF, num: Int, alpha: Int) {
        val cx = box.centerX()
        val cy = box.centerY()
        val r = box.width() * 0.42f
        val baseColor = if (num == 1) Color.parseColor("#C62828") else Color.parseColor("#2E7D32")
        val darkColor = if (num == 1) Color.parseColor("#7A1414") else Color.parseColor("#1B4F1F")

        accentPaint.shader = RadialGradient(
            cx - r * 0.3f, cy - r * 0.3f, r * 1.6f,
            baseColor, darkColor, Shader.TileMode.CLAMP
        )
        accentPaint.alpha = alpha
        canvas.drawCircle(cx, cy, r, accentPaint)
        accentPaint.shader = null

        iconStrokePaint.color = Color.parseColor("#F5E7BE")
        iconStrokePaint.strokeWidth = r * 0.12f
        iconStrokePaint.alpha = alpha
        canvas.drawCircle(cx, cy, r, iconStrokePaint)

        iconStrokePaint.color = Color.WHITE
        iconStrokePaint.strokeWidth = r * 0.22f
        iconStrokePaint.alpha = alpha
        if (num == 1) {
            canvas.drawLine(cx - r * 0.38f, cy, cx + r * 0.38f, cy, iconStrokePaint)
            canvas.drawLine(cx, cy - r * 0.38f, cx, cy + r * 0.38f, iconStrokePaint)
        } else {
            val path = Path()
            path.moveTo(cx - r * 0.35f, cy)
            path.lineTo(cx - r * 0.08f, cy + r * 0.32f)
            path.lineTo(cx + r * 0.4f, cy - r * 0.32f)
            canvas.drawPath(path, iconStrokePaint)
        }
    }

    private fun drawTileBack(canvas: Canvas, rect: RectF) {
        val radius = minOf(unitX, unit) * 0.28f
        val w = rect.width()
        val h = rect.height()

        canvas.save()
        canvas.translate(rect.left, rect.top)
        val localRect = RectF(0f, 0f, w, h)

        val sideRect = RectF(0f, unit * 0.13f, w, h + unit * 0.13f)
        sidePaint.color = Color.parseColor("#A9740F")
        canvas.drawRoundRect(sideRect, radius, radius, sidePaint)

        for (i in 2 downTo 0) {
            val off = unit * (0.05f + i * 0.045f)
            val shadowRect = RectF(off, off * 1.15f, w + off, h + off * 1.15f)
            shadowPaint.color = Color.argb(26 - i * 6, 0, 0, 0)
            canvas.drawRoundRect(shadowRect, radius, radius, shadowPaint)
        }

        backPaint.shader = backShaderFor(w, h)
        canvas.drawRoundRect(localRect, radius, radius, backPaint)

        canvas.save()
        canvas.clipPath(clipPathFor(w, h, radius))
        glossPaint.shader = glossBackShaderFor(w, h)
        canvas.drawRect(localRect, glossPaint)
        canvas.restore()

        backBorderPaint.strokeWidth = unit * 0.06f
        canvas.drawRoundRect(localRect, radius, radius, backBorderPaint)

        medallionPaint.strokeWidth = unit * 0.045f
        val cx = localRect.centerX()
        val cy = localRect.centerY()
        canvas.drawCircle(cx, cy, w * 0.3f, medallionPaint)
        canvas.drawCircle(cx, cy, w * 0.17f, medallionPaint)
        accentPaint.shader = null
        accentPaint.color = Color.parseColor("#7A4A1E")
        accentPaint.alpha = 255
        canvas.drawCircle(cx, cy, w * 0.05f, accentPaint)
        canvas.restore()
    }

    private fun ensureAmbientParticles() {
        if (ambientParticles.isNotEmpty()) return
        val list = mutableListOf<AmbientParticle>()
        repeat(18) {
            list.add(
                AmbientParticle(
                    bx = Random.nextFloat(),
                    by = Random.nextFloat(),
                    r = unit * (0.05f + Random.nextFloat() * 0.09f),
                    phase = Random.nextFloat() * 6.2832f,
                    speed = 0.00025f + Random.nextFloat() * 0.00035f
                )
            )
        }
        ambientParticles = list
    }

    private fun drawAmbientBackdrop(canvas: Canvas, now: Long) {
        if (width <= 0 || height <= 0 || unit <= 0f) return
        ensureAmbientParticles()
        val w = width.toFloat()
        val h = height.toFloat()

        for (p in ambientParticles) {
            val t = now * p.speed + p.phase
            val dx = sin(t) * w * 0.03f
            val dy = cos(t * 0.8f) * h * 0.025f
            val cx = p.bx * w + dx
            val cy = p.by * h + dy
            val pulse = 0.5f + 0.5f * sin(t * 1.3f)
            val rad = p.r * (1f + pulse * 0.4f)
            ambientParticlePaint.shader = RadialGradient(
                cx, cy, rad,
                Color.argb((70 + pulse * 60).toInt(), 255, 236, 179),
                Color.argb(0, 255, 236, 179),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, rad, ambientParticlePaint)
        }
    }

    private fun drawTray(canvas: Canvas) {
        val radius = unit * 0.3f
        val first = traySlotRect(0)
        val last = traySlotRect(TRAY_SIZE - 1)
        val padX = traySlotGap * 0.9f
        val padY = traySlotGap * 0.7f
        val band = RectF(first.left - padX, first.top - padY, last.right + padX, first.bottom + padY)

        val shadowRect = RectF(band)
        shadowRect.offset(unit * 0.05f, unit * 0.09f)
        shadowPaint.color = Color.argb(70, 0, 0, 0)
        canvas.drawRoundRect(shadowRect, radius, radius, shadowPaint)

        traySlotPaint.shader = LinearGradient(
            band.left, band.top, band.left, band.bottom,
            Color.parseColor("#7A5027"), Color.parseColor("#432B14"),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(band, radius, radius, traySlotPaint)

        traySlotBorderPaint.strokeWidth = unit * 0.05f
        traySlotBorderPaint.color = Color.parseColor("#C99A54")
        canvas.drawRoundRect(band, radius, radius, traySlotBorderPaint)

        trayDividerPaint.strokeWidth = unit * 0.025f
        for (i in 1 until TRAY_SIZE) {
            val r = traySlotRect(i)
            val x = r.left - traySlotGap / 2f
            canvas.drawLine(x, band.top + band.height() * 0.14f, x, band.bottom - band.height() * 0.14f, trayDividerPaint)
        }
    }

    private fun buildShards(colorA: Int, colorB: Int): List<Shard> {
        val list = mutableListOf<Shard>()
        var i = 0
        for (row in 0 until SHARD_GRID) {
            for (col in 0 until SHARD_GRID) {
                val relLeft = col / SHARD_GRID.toFloat()
                val relTop = row / SHARD_GRID.toFloat()
                val relRight = (col + 1) / SHARD_GRID.toFloat()
                val relBottom = (row + 1) / SHARD_GRID.toFloat()
                // Direction radiating out from the tile's own center, so pieces
                // fly outward like they've physically broken apart.
                val dirX = (col + 0.5f) / SHARD_GRID - 0.5f
                val dirY = (row + 0.5f) / SHARD_GRID - 0.5f
                val jitterAngle = Random.nextFloat() * 0.6f - 0.3f
                val cosJ = cos(jitterAngle)
                val sinJ = sin(jitterAngle)
                val rx = dirX * cosJ - dirY * sinJ
                val ry = dirX * sinJ + dirY * cosJ
                val speed = unit * (2.6f + Random.nextFloat() * 1.8f)
                // The impact point sits near the tray at the top of the
                // view, so shards can't be allowed to fly upward much - the
                // view clips its own canvas at y=0 and they'd just vanish.
                // Damping the direction's vertical component and adding a
                // strong downward push keeps the burst inside the visible
                // board area even for the top row of shards.
                list.add(
                    Shard(
                        relLeft, relTop, relRight, relBottom,
                        rx * speed, ry * speed * 0.45f + unit * 1.5f,
                        (Random.nextFloat() * 2f - 1f) * 2.2f,
                        if (i % 2 == 0) colorA else colorB
                    )
                )
                i++
            }
        }
        return list
    }

    private fun drawShard(canvas: Canvas, baseRect: RectF, shard: Shard, raw: Float, alpha: Int) {
        val shardRect = RectF(
            baseRect.left + shard.relLeft * baseRect.width(),
            baseRect.top + shard.relTop * baseRect.height(),
            baseRect.left + shard.relRight * baseRect.width(),
            baseRect.top + shard.relBottom * baseRect.height()
        )
        val dx = shard.vx * raw
        val dy = shard.vy * raw + 0.5f * unit * 2.6f * raw * raw
        val cx = shardRect.centerX()
        val cy = shardRect.centerY()

        canvas.save()
        canvas.translate(dx, dy)
        canvas.rotate(shard.rotSpeed * raw * 360f, cx, cy)

        shardPaint.shader = LinearGradient(
            shardRect.left, shardRect.top, shardRect.left, shardRect.bottom,
            Color.parseColor("#FFFEF9"), Color.parseColor("#F3E7C9"),
            Shader.TileMode.CLAMP
        )
        shardPaint.alpha = alpha
        canvas.drawRoundRect(shardRect, unit * 0.06f, unit * 0.06f, shardPaint)

        shardBorderPaint.color = shard.tint
        shardBorderPaint.strokeWidth = unit * 0.035f
        shardBorderPaint.alpha = alpha
        canvas.drawRoundRect(shardRect, unit * 0.06f, unit * 0.06f, shardBorderPaint)

        canvas.restore()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (unit <= 0f) return

        var needsMoreFrames = true
        val now = System.currentTimeMillis()
        drawAmbientBackdrop(canvas, now)

        drawTray(canvas)

        val sorted = tiles.sortedBy { it.z }
        val shake = rejectShake
        val activeShuffle = shuffleAnims
        val hf = hintFlash
        if (hf != null && now - hf.startTime >= HINT_DURATION_MS) {
            hintFlash = null
        }

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
                if (isSelectable(t, tiles)) {
                    val pulse = 0.5f + 0.5f * sin(now * 0.004f + t.x * 0.3f + t.y * 0.17f)
                    val ringHalfW = rect.width() / 2f * (1.05f + pulse * 0.08f)
                    val ringHalfH = rect.height() / 2f * (1.05f + pulse * 0.08f)
                    selectPulsePaint.strokeWidth = unit * (0.05f + pulse * 0.03f)
                    selectPulsePaint.alpha = (70 + pulse * 90).toInt()
                    canvas.drawRoundRect(
                        RectF(
                            rect.centerX() - ringHalfW, rect.centerY() - ringHalfH,
                            rect.centerX() + ringHalfW, rect.centerY() + ringHalfH
                        ),
                        unit * 0.32f, unit * 0.32f, selectPulsePaint
                    )
                }
                drawTile(canvas, rect, t.symbol, highlight = false, alpha = 255)

                if (hf != null && (t == hf.tileA || t == hf.tileB) && now - hf.startTime < HINT_DURATION_MS) {
                    val raw = ((now - hf.startTime).toFloat() / HINT_DURATION_MS).coerceIn(0f, 1f)
                    val pulse = 0.5f + 0.5f * sin(raw * Math.PI.toFloat() * 5f)
                    val fade = 1f - easeOutCubic(raw).let { if (raw > 0.7f) (raw - 0.7f) / 0.3f else 0f }
                    val ringHalfW = rect.width() / 2f * (1.1f + pulse * 0.12f)
                    val ringHalfH = rect.height() / 2f * (1.1f + pulse * 0.12f)
                    hintGlowPaint.alpha = ((60 + pulse * 60) * fade).toInt().coerceIn(0, 180)
                    canvas.drawRoundRect(
                        RectF(
                            rect.centerX() - ringHalfW * 1.3f, rect.centerY() - ringHalfH * 1.3f,
                            rect.centerX() + ringHalfW * 1.3f, rect.centerY() + ringHalfH * 1.3f
                        ),
                        unit * 0.4f, unit * 0.4f, hintGlowPaint
                    )
                    hintRingPaint.strokeWidth = unit * (0.09f + pulse * 0.04f)
                    hintRingPaint.alpha = (255 * fade).toInt().coerceIn(0, 255)
                    canvas.drawRoundRect(
                        RectF(
                            rect.centerX() - ringHalfW, rect.centerY() - ringHalfH,
                            rect.centerX() + ringHalfW, rect.centerY() + ringHalfH
                        ),
                        unit * 0.32f, unit * 0.32f, hintRingPaint
                    )
                }
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

        if (matchBursts.isNotEmpty()) {
            var anyFinished = false
            for (mb in matchBursts) {
                val elapsed = now - mb.startTime
                val impact = mb.impact

                when {
                    elapsed < PULL_MS -> {
                        val raw = easeOutCubic((elapsed.toFloat() / PULL_MS).coerceIn(0f, 1f))
                        val scale = lerp(1f, 1.12f, raw)
                        val ax = lerp(mb.fromA.centerX(), mb.windupA.x, raw)
                        val ay = lerp(mb.fromA.centerY(), mb.windupA.y, raw)
                        val bx = lerp(mb.fromB.centerX(), mb.windupB.x, raw)
                        val by = lerp(mb.fromB.centerY(), mb.windupB.y, raw)
                        drawTile(canvas, rectAt(ax, ay, traySlotSize * scale), mb.tileA.symbol, highlight = true, alpha = 255)
                        drawTile(canvas, rectAt(bx, by, traySlotSize * scale), mb.tileB.symbol, highlight = true, alpha = 255)
                    }
                    elapsed < PULL_MS + CONVERGE_MS -> {
                        val t = ((elapsed - PULL_MS).toFloat() / CONVERGE_MS).coerceIn(0f, 1f)
                        val raw = t * t * t
                        val scale = lerp(1.12f, 1.5f, raw)
                        val ax = lerp(mb.windupA.x, impact.x, raw)
                        val ay = lerp(mb.windupA.y, impact.y, raw)
                        val bx = lerp(mb.windupB.x, impact.x, raw)
                        val by = lerp(mb.windupB.y, impact.y, raw)
                        drawTile(canvas, rectAt(ax, ay, traySlotSize * scale), mb.tileA.symbol, highlight = true, alpha = 255)
                        drawTile(canvas, rectAt(bx, by, traySlotSize * scale), mb.tileB.symbol, highlight = true, alpha = 255)
                    }
                    elapsed < PULL_MS + CONVERGE_MS + POP_MS -> {
                        val raw = (elapsed - PULL_MS - CONVERGE_MS).toFloat() / POP_MS
                        val eased = easeOutCubic(raw)
                        // The impact point can sit close to the top of the view
                        // (near the tray) - never let the glow/ring reach past
                        // the view's own top edge or it just gets clipped away.
                        val headroom = impact.y * 0.95f
                        val glowRadius = lerp(traySlotSize * 0.3f, traySlotSize * 1.2f, eased).coerceAtMost(headroom)
                        glowPaint.alpha = ((1f - raw) * 255).toInt()
                        canvas.drawCircle(impact.x, impact.y, glowRadius, glowPaint)

                        val ringRadius = lerp(traySlotSize * 0.35f, traySlotSize * 1.6f, eased).coerceAtMost(headroom)
                        ringPaint.strokeWidth = unit * 0.09f * (1f - raw)
                        ringPaint.alpha = ((1f - raw) * 220).toInt()
                        canvas.drawCircle(impact.x, impact.y, ringRadius, ringPaint)
                    }
                    elapsed < PULL_MS + CONVERGE_MS + POP_MS + BURST_MS -> {
                        val raw = (elapsed - PULL_MS - CONVERGE_MS - POP_MS).toFloat() / BURST_MS
                        val alpha = ((1f - raw) * 255).toInt().coerceIn(0, 255)
                        val baseRect = rectAt(impact.x, impact.y, traySlotSize * 1.5f)
                        for (s in mb.shards) {
                            drawShard(canvas, baseRect, s, raw, alpha)
                        }
                    }
                    else -> anyFinished = true
                }
            }
            if (anyFinished) {
                matchBursts.removeAll { now - it.startTime >= PULL_MS + CONVERGE_MS + POP_MS + BURST_MS }
                checkWin()
            }
            needsMoreFrames = true
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
                if (raw < 0.35f) {
                    val glowRaw = raw / 0.35f
                    glowPaint.alpha = ((1f - glowRaw) * 150).toInt()
                    canvas.drawCircle(combo.cx, cy, unit * (0.6f + glowRaw * 1.5f), glowPaint)
                }
                canvas.drawText(combo.text, combo.cx, cy, comboOutlinePaint)
                canvas.drawText(combo.text, combo.cx, cy, comboPaint)
            } else {
                comboPopup = null
            }
        }

        if (flyingTiles.isNotEmpty()) {
            val landed = mutableListOf<FlyingTile>()
            for (fly in flyingTiles) {
                val raw = ((now - fly.startTime).toFloat() / FLY_DURATION_MS).coerceIn(0f, 1f)
                val t = easeOutCubic(raw)
                val rect = lerpRect(fly.from, fly.to, t)
                drawTile(canvas, rect, fly.tile.symbol, highlight = true, alpha = 255)
                if (raw >= 1f) landed.add(fly) else needsMoreFrames = true
            }
            if (landed.isNotEmpty()) {
                flyingTiles.removeAll(landed)
                landed.forEach { finishFlight(it) }
            }
        }

        val uf = undoFlight
        if (uf != null) {
            val raw = ((now - uf.startTime).toFloat() / FLY_DURATION_MS).coerceIn(0f, 1f)
            val t = easeInOutCubic(raw)
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

        if (confetti.isNotEmpty()) {
            val elapsedS = (now - confettiStartTime) / 1000f
            val duration = 2.6f
            if (elapsedS > duration) {
                confetti = emptyList()
            } else {
                val fadeStart = duration * 0.7f
                val fadeAlpha = if (elapsedS > fadeStart) 1f - (elapsedS - fadeStart) / (duration - fadeStart) else 1f
                for (c in confetti) {
                    val cx = c.x + c.vx * elapsedS
                    val cy = c.y + c.vy * elapsedS + 0.5f * unit * 1.6f * elapsedS * elapsedS
                    if (cy > height + unit) continue
                    val twinkle = 0.5f + 0.5f * sin(now * 0.008f + c.phase)
                    confettiPaint.color = c.color
                    confettiPaint.alpha = ((0.5f + twinkle * 0.5f) * 255 * fadeAlpha).toInt().coerceIn(0, 255)
                    canvas.save()
                    canvas.translate(cx, cy)
                    canvas.rotate((now * 0.09f + c.phase * 60f) % 360f)
                    canvas.drawRect(-unit * 0.07f, -unit * 0.14f, unit * 0.07f, unit * 0.14f, confettiPaint)
                    canvas.restore()
                }
            }
        }

        if (needsMoreFrames) postInvalidateOnAnimation()
    }

    private fun finishFlight(fly: FlyingTile) {
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
            // Pull both tiles out to a fixed separation before they slam
            // together, regardless of how close their original slots were -
            // otherwise adjacent slots barely move and the tiles just merge.
            val leftIsA = rectA.centerX() <= rectB.centerX()
            val windupDist = traySlotSize * 0.85f
            val windupA = if (leftIsA) PointF(midX - windupDist, midY) else PointF(midX + windupDist, midY)
            val windupB = if (leftIsA) PointF(midX + windupDist, midY) else PointF(midX - windupDist, midY)
            val shards = buildShards(symbolColor(fly.tile.symbol), symbolColor(pairTile.symbol))
            matchBursts.add(MatchBurst(
                fly.tile, pairTile, fly.slotIndex, pairIndex, now,
                fromA = RectF(rectA), fromB = RectF(rectB),
                windupA = windupA, windupB = windupB,
                impact = PointF(midX, midY),
                shards = shards
            ))

            traySlots[fly.slotIndex] = null
            traySlots[pairIndex] = null
            moves++
            listener?.onMovesChanged(moves)

            comboCount = if (now - lastMatchTime <= COMBO_WINDOW_MS) comboCount + 1 else 1
            lastMatchTime = now
            val text = if (comboCount >= 2) "Combo x$comboCount!" else "Juft!"
            comboPopup = ComboPopup(text, now, midX, trayOffsetY - unit * 0.2f)
            playMatchSound()
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
            playWinSound()
            spawnWinCelebration()
            listener?.onWin(moves)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN) return true
        if (undoFlight != null || shuffleAnims.isNotEmpty()) return true

        val px = event.x
        val py = event.y

        val hit = tiles.filter { rectFor(it).contains(px, py) }
            .maxByOrNull { it.z } ?: return true

        if (!isSelectable(hit, tiles)) {
            rejectShake = RejectShake(hit, System.currentTimeMillis())
            invalidate()
            return true
        }

        val reserved = flyingTiles.map { it.slotIndex }.toSet() +
            matchBursts.flatMap { listOf(it.slotA, it.slotB) }.toSet()
        val emptyIndex = traySlots.indices.firstOrNull { traySlots[it] == null && it !in reserved } ?: return true

        tiles.remove(hit)
        flyingTiles.add(FlyingTile(hit, rectFor(hit), traySlotRect(emptyIndex), emptyIndex, System.currentTimeMillis()))
        invalidate()
        return true
    }
}
