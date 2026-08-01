package com.sadiso.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class LoginBackdropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private data class Particle(
        val bx: Float,
        val by: Float,
        val r: Float,
        val phase: Float,
        val speed: Float
    )

    private var particles: List<Particle> = emptyList()
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun ensureParticles() {
        if (particles.isNotEmpty()) return
        particles = List(22) {
            Particle(
                bx = Random.nextFloat(),
                by = Random.nextFloat(),
                r = 6f + Random.nextFloat() * 16f,
                phase = Random.nextFloat() * 6.2832f,
                speed = 0.00025f + Random.nextFloat() * 0.00035f
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        ensureParticles()
        val w = width.toFloat()
        val h = height.toFloat()
        val now = System.currentTimeMillis()

        for (p in particles) {
            val t = now * p.speed + p.phase
            val dx = sin(t) * w * 0.04f
            val dy = cos(t * 0.8f) * h * 0.03f
            val cx = p.bx * w + dx
            val cy = p.by * h + dy
            val pulse = 0.5f + 0.5f * sin(t * 1.3f)
            val rad = p.r * (1f + pulse * 0.4f)
            particlePaint.shader = RadialGradient(
                cx, cy, rad,
                Color.argb((70 + pulse * 60).toInt(), 255, 236, 179),
                Color.argb(0, 255, 236, 179),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, rad, particlePaint)
        }
        postInvalidateOnAnimation()
    }
}
