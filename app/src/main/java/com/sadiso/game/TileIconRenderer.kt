package com.sadiso.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface

/**
 * Hand-drawn vector suit icons shared by every game that uses the same
 * 16-symbol tile set (characters/bamboo/dots/honors) - Mahjong and Onet
 * both draw through here so they stay visually consistent and neither
 * duplicates this drawing code.
 */
object TileIconRenderer {

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

    fun suitAndNumber(symbol: String): Pair<Int, Int> {
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

    fun symbolColor(symbol: String): Int {
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

    fun drawSymbolIcon(canvas: Canvas, rect: RectF, symbol: String, alpha: Int) {
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
}
