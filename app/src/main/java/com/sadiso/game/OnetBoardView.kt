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
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.sin
import kotlin.random.Random

class OnetBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    companion object {
        private const val LINE_MS = 160L
        private const val POP_MS = 200L
        private const val FADE_MS = 320L
        private const val SHAKE_DURATION_MS = 380L
        private const val HINT_DURATION_MS = 1400L
        private const val SPARK_COUNT = 7
    }

    interface Listener {
        fun onMovesChanged(moves: Int)
        fun onWin(moves: Int)
    }

    private data class OnetMatch(
        val a: OnetTile,
        val b: OnetTile,
        val pathPx: List<PointF>,
        val startTime: Long
    )

    private data class RejectShake(val tiles: List<OnetTile>, val startTime: Long)
    private data class HintFlash(val a: OnetTile, val b: OnetTile, val startTime: Long)

    var listener: Listener? = null

    private var tiles: MutableList<OnetTile> = generateOnetBoard()
    private var selected: OnetTile? = null
    private var activeMatch: OnetMatch? = null
    private var rejectShake: RejectShake? = null
    private var hintFlash: HintFlash? = null
    private var moves = 0
    private var wonFired = false

    private var unit = 0f
    private var boardOffsetX = 0f
    private var boardOffsetY = 0f

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glossPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#8D6E4A")
    }
    private val selectedRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#FFC107")
    }
    private val hintRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#FFF176")
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#FFF176")
    }
    private val lineGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#FFD54F")
    }
    private val sparkPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var faceShaderSize = -1f to -1f
    private var faceShader: LinearGradient? = null
    private var glossShaderSize = -1f to -1f
    private var glossShader: RadialGradient? = null
    private var clipPathKey = Triple(-1f, -1f, -1f)
    private val cachedClipPath = Path()

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

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        soundPool?.release()
    }

    fun newGame() {
        tiles = generateOnetBoard()
        selected = null
        activeMatch = null
        rejectShake = null
        hintFlash = null
        moves = 0
        wonFired = false
        listener?.onMovesChanged(moves)
        invalidate()
    }

    fun shuffleRemaining() {
        if (activeMatch != null || tiles.isEmpty()) return
        val positions = tiles.map { it.row to it.col }
        val symbols = tiles.map { it.symbol }.toMutableList()
        var attempt = 0
        var newTiles: MutableList<OnetTile>
        do {
            symbols.shuffle()
            newTiles = positions.mapIndexed { i, (r, c) -> OnetTile(r, c, symbols[i]) }.toMutableList()
            attempt++
        } while (attempt < 30 && !hasValidOnetMove(newTiles))
        tiles = newTiles
        selected = null
        invalidate()
    }

    fun hint(): Boolean {
        if (activeMatch != null) return true
        val pair = findAnyOnetHint(tiles) ?: return false
        hintFlash = HintFlash(pair.first, pair.second, System.currentTimeMillis())
        invalidate()
        return true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val marginFine = 0.6f
        val totalFineW = ONET_COLS + marginFine
        val totalFineH = ONET_ROWS + marginFine
        unit = minOf(w / totalFineW, h / totalFineH)
        boardOffsetX = (w - ONET_COLS * unit) / 2f
        boardOffsetY = (h - ONET_ROWS * unit) / 2f
    }

    private fun rectFor(t: OnetTile): RectF {
        val left = boardOffsetX + t.col * unit
        val top = boardOffsetY + t.row * unit
        val pad = unit * 0.07f
        return RectF(left + pad, top + pad, left + unit - pad, top + unit - pad)
    }

    private fun gridToPx(padRow: Int, padCol: Int): PointF {
        val realRow = padRow - 1
        val realCol = padCol - 1
        return PointF(
            boardOffsetX + (realCol + 0.5f) * unit,
            boardOffsetY + (realRow + 0.5f) * unit
        )
    }

    private fun easeOutCubic(t: Float): Float {
        val f = t - 1f
        return f * f * f + 1f
    }

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

    private fun glossShaderFor(w: Float, h: Float): RadialGradient {
        if (glossShaderSize != (w to h)) {
            glossShader = RadialGradient(
                w * 0.32f, h * 0.14f, w * 0.85f,
                Color.argb(120, 255, 255, 255), Color.argb(0, 255, 255, 255),
                Shader.TileMode.CLAMP
            )
            glossShaderSize = w to h
        }
        return glossShader!!
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

    private fun drawOnetTile(canvas: Canvas, rect: RectF, symbol: String, selected: Boolean, alpha: Int, scale: Float = 1f) {
        val radius = unit * 0.16f
        val w = rect.width() * scale
        val h = rect.height() * scale
        val cx = rect.centerX()
        val cy = rect.centerY()

        canvas.save()
        canvas.translate(cx - w / 2f, cy - h / 2f)
        val localRect = RectF(0f, 0f, w, h)

        val shadowRect = RectF(localRect)
        shadowRect.offset(unit * 0.035f, unit * 0.05f)
        shadowPaint.color = Color.argb(70 * alpha / 255, 0, 0, 0)
        canvas.drawRoundRect(shadowRect, radius, radius, shadowPaint)

        facePaint.shader = faceShaderFor(w, h)
        facePaint.alpha = alpha
        canvas.drawRoundRect(localRect, radius, radius, facePaint)

        canvas.save()
        canvas.clipPath(clipPathFor(w, h, radius))
        glossPaint.shader = glossShaderFor(w, h)
        glossPaint.alpha = alpha
        canvas.drawRect(localRect, glossPaint)
        canvas.restore()

        borderPaint.strokeWidth = unit * 0.045f
        borderPaint.alpha = alpha
        canvas.drawRoundRect(localRect, radius, radius, borderPaint)

        TileIconRenderer.drawSymbolIcon(canvas, localRect, symbol, alpha)

        if (selected) {
            selectedRingPaint.strokeWidth = unit * 0.09f
            selectedRingPaint.alpha = alpha
            canvas.drawRoundRect(localRect, radius, radius, selectedRingPaint)
        }
        canvas.restore()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (unit <= 0f) return

        val now = System.currentTimeMillis()
        val shake = rejectShake
        if (shake != null && now - shake.startTime >= SHAKE_DURATION_MS) rejectShake = null
        val hf = hintFlash
        if (hf != null && now - hf.startTime >= HINT_DURATION_MS) hintFlash = null

        val matchTiles = activeMatch?.let { setOf(it.a, it.b) } ?: emptySet()

        for (t in tiles) {
            if (t in matchTiles) continue
            var rect = rectFor(t)

            val sh = rejectShake
            if (sh != null && t in sh.tiles) {
                val raw = ((now - sh.startTime).toFloat() / SHAKE_DURATION_MS).coerceIn(0f, 1f)
                val amp = unit * 0.1f * (1f - raw)
                val dx = sin(raw * Math.PI.toFloat() * 6f) * amp
                rect = RectF(rect.left + dx, rect.top, rect.right + dx, rect.bottom)
            }

            drawOnetTile(canvas, rect, t.symbol, selected = (t == selected), alpha = 255)

            val h2 = hintFlash
            if (h2 != null && (t == h2.a || t == h2.b)) {
                val raw = ((now - h2.startTime).toFloat() / HINT_DURATION_MS).coerceIn(0f, 1f)
                val pulse = 0.5f + 0.5f * sin(raw * Math.PI.toFloat() * 5f)
                val fade = if (raw > 0.7f) (1f - (raw - 0.7f) / 0.3f) else 1f
                hintRingPaint.strokeWidth = unit * (0.06f + pulse * 0.04f)
                hintRingPaint.alpha = (255 * fade).toInt().coerceIn(0, 255)
                val hw = rect.width() / 2f * (1.08f + pulse * 0.1f)
                val hh = rect.height() / 2f * (1.08f + pulse * 0.1f)
                canvas.drawRoundRect(
                    RectF(rect.centerX() - hw, rect.centerY() - hh, rect.centerX() + hw, rect.centerY() + hh),
                    unit * 0.2f, unit * 0.2f, hintRingPaint
                )
            }
        }

        val m = activeMatch
        if (m != null) {
            val elapsed = now - m.startTime
            when {
                elapsed < LINE_MS -> {
                    val raw = easeOutCubic((elapsed.toFloat() / LINE_MS).coerceIn(0f, 1f))
                    drawPathProgress(canvas, m.pathPx, raw)
                    drawOnetTile(canvas, rectFor(m.a), m.a.symbol, selected = true, alpha = 255)
                    drawOnetTile(canvas, rectFor(m.b), m.b.symbol, selected = true, alpha = 255)
                }
                elapsed < LINE_MS + POP_MS -> {
                    val raw = ((elapsed - LINE_MS).toFloat() / POP_MS).coerceIn(0f, 1f)
                    drawPathProgress(canvas, m.pathPx, 1f, (1f - raw))
                    val scale = 1f + easeOutCubic(raw) * 0.25f
                    drawOnetTile(canvas, rectFor(m.a), m.a.symbol, selected = true, alpha = 255, scale = scale)
                    drawOnetTile(canvas, rectFor(m.b), m.b.symbol, selected = true, alpha = 255, scale = scale)
                }
                elapsed < LINE_MS + POP_MS + FADE_MS -> {
                    val raw = ((elapsed - LINE_MS - POP_MS).toFloat() / FADE_MS).coerceIn(0f, 1f)
                    val alpha = ((1f - raw) * 255).toInt().coerceIn(0, 255)
                    val scale = 1.25f + raw * 0.4f
                    drawOnetTile(canvas, rectFor(m.a), m.a.symbol, selected = false, alpha = alpha, scale = scale)
                    drawOnetTile(canvas, rectFor(m.b), m.b.symbol, selected = false, alpha = alpha, scale = scale)
                    drawSparkle(canvas, rectFor(m.a).centerX(), rectFor(m.a).centerY(), raw)
                    drawSparkle(canvas, rectFor(m.b).centerX(), rectFor(m.b).centerY(), raw)
                }
                else -> {
                    tiles.remove(m.a)
                    tiles.remove(m.b)
                    activeMatch = null
                    checkWin()
                }
            }
        }

        if (activeMatch != null) postInvalidateOnAnimation()
        else if (rejectShake != null || hintFlash != null) postInvalidateOnAnimation()
    }

    private fun drawPathProgress(canvas: Canvas, pts: List<PointF>, raw: Float, fadeAlpha: Float = 1f) {
        if (pts.size < 2) return
        val segLens = FloatArray(pts.size - 1)
        var total = 0f
        for (i in 0 until pts.size - 1) {
            val dx = pts[i + 1].x - pts[i].x
            val dy = pts[i + 1].y - pts[i].y
            segLens[i] = kotlin.math.sqrt(dx * dx + dy * dy)
            total += segLens[i]
        }
        var remain = total * raw
        val path = Path()
        path.moveTo(pts[0].x, pts[0].y)
        for (i in 0 until pts.size - 1) {
            if (remain <= 0f) break
            val segLen = segLens[i]
            if (remain >= segLen) {
                path.lineTo(pts[i + 1].x, pts[i + 1].y)
                remain -= segLen
            } else {
                val t = if (segLen > 0f) remain / segLen else 0f
                val x = pts[i].x + (pts[i + 1].x - pts[i].x) * t
                val y = pts[i].y + (pts[i + 1].y - pts[i].y) * t
                path.lineTo(x, y)
                remain = 0f
            }
        }
        lineGlowPaint.strokeWidth = unit * 0.22f
        lineGlowPaint.alpha = (110 * fadeAlpha).toInt().coerceIn(0, 255)
        canvas.drawPath(path, lineGlowPaint)
        linePaint.strokeWidth = unit * 0.08f
        linePaint.alpha = (255 * fadeAlpha).toInt().coerceIn(0, 255)
        canvas.drawPath(path, linePaint)
    }

    private fun drawSparkle(canvas: Canvas, cx: Float, cy: Float, raw: Float) {
        val seedBase = (cx.toInt() * 7919 + cy.toInt() * 104729)
        val rnd = Random(seedBase)
        val alpha = ((1f - raw) * 255).toInt().coerceIn(0, 255)
        sparkPaint.color = Color.parseColor("#FFF176")
        for (i in 0 until SPARK_COUNT) {
            val angle = (i / SPARK_COUNT.toFloat()) * 6.2832f + rnd.nextFloat()
            val dist = unit * (0.15f + raw * 0.55f) * (0.7f + rnd.nextFloat() * 0.5f)
            val x = cx + kotlin.math.cos(angle) * dist
            val y = cy + kotlin.math.sin(angle) * dist
            sparkPaint.alpha = alpha
            canvas.drawCircle(x, y, unit * 0.045f * (1f - raw * 0.6f), sparkPaint)
        }
    }

    private fun checkWin() {
        listener?.onMovesChanged(moves)
        if (!wonFired && tiles.isEmpty()) {
            wonFired = true
            soundPool?.play(winSoundId, 0.9f, 0.9f, 0, 0, 1f)
            listener?.onWin(moves)
        } else if (!hasValidOnetMove(tiles)) {
            shuffleRemaining()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN) return true
        if (activeMatch != null || unit <= 0f) return true

        val col = ((event.x - boardOffsetX) / unit).toInt()
        val row = ((event.y - boardOffsetY) / unit).toInt()
        val hit = tiles.firstOrNull { it.row == row && it.col == col } ?: return true

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
        if (cur.symbol == hit.symbol) {
            val path = findOnetPath(tiles, cur, hit)
            if (path != null) {
                val pathPx = path.map { (r, c) -> gridToPx(r, c) }
                activeMatch = OnetMatch(cur, hit, pathPx, System.currentTimeMillis())
                selected = null
                moves++
                soundPool?.play(matchSoundId, 0.8f, 0.8f, 0, 0, 1f)
                invalidate()
                return true
            }
            rejectShake = RejectShake(listOf(cur, hit), System.currentTimeMillis())
        }
        selected = hit
        invalidate()
        return true
    }
}
