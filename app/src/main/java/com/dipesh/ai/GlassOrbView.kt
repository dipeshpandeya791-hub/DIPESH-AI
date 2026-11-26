package com.dipesh.ai

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.graphics.ColorUtils
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/**
 * The floating assistant icon: a translucent glass disc with a rotating metallic ring, a glowing core that pulses
 * between cyan and magenta, expanding sound-wave ripples and a tiny live waveform. It reacts to the assistant state.
 */
class GlassOrbView(
    ctx: Context,
    private val status: () -> String,
    private val onTap: () -> Unit,
    private val onLongPress: () -> Unit,
    private val onDrag: (dx: Int, dy: Int) -> Unit,
    private val onDragEnd: () -> Unit
) : View(ctx) {
    private val d = ctx.resources.displayMetrics.density
    private val sizePx = (72 * d).toInt()
    private val cyan = Color.parseColor("#00E5FF")
    private val magenta = Color.parseColor("#FF2BD6")
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val wave = Path()
    private val start = System.nanoTime()
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private var downX = 0f; private var downY = 0f; private var lastX = 0f; private var lastY = 0f
    private var downAt = 0L; private var moved = false

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(sizePx, sizePx)

    override fun onDraw(c: Canvas) {
        val t = (System.nanoTime() - start) / 1_000_000_000f
        val cx = width / 2f; val cy = height / 2f
        val r = min(cx, cy) - 5 * d
        val energy = when (status()) { "LISTENING" -> 1f; "SPEAKING" -> 0.8f; "THINKING" -> 0.6f; else -> 0.25f }
        val pulse = (sin(t * 2.2f * (0.6f + energy)) + 1f) / 2f
        val tint = ColorUtils.blendARGB(cyan, magenta, (sin(t * 0.9f) + 1f) / 2f)

        // outer glow
        fill.shader = RadialGradient(cx, cy, r * 1.35f, intArrayOf(ColorUtils.setAlphaComponent(tint, (70 + 80 * pulse * energy).toInt().coerceIn(0, 255)), Color.TRANSPARENT), floatArrayOf(0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r * 1.35f, fill)

        // glass body: dark translucent base + soft reflection
        fill.shader = null; fill.color = Color.argb(150, 8, 12, 28)
        c.drawCircle(cx, cy, r, fill)
        fill.shader = RadialGradient(cx - r * 0.35f, cy - r * 0.45f, r * 1.25f, intArrayOf(Color.argb(85, 255, 255, 255), Color.argb(10, 255, 255, 255), Color.TRANSPARENT), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, fill)
        fill.shader = null

        // sound-wave ripples
        val speed = 0.30f + energy * 0.9f
        stroke.strokeWidth = 1.5f * d
        for (k in 0..2) {
            val phase = (t * speed + k / 3f) % 1f
            stroke.shader = null
            stroke.color = ColorUtils.setAlphaComponent(tint, ((1f - phase) * (60 + 140 * energy)).toInt().coerceIn(0, 255))
            c.drawCircle(cx, cy, r * (0.26f + 0.70f * phase), stroke)
        }

        // glowing core
        val cr = r * (0.22f + 0.05f * pulse + 0.04f * energy)
        fill.shader = RadialGradient(cx, cy, cr * 2.1f, intArrayOf(Color.WHITE, tint, ColorUtils.setAlphaComponent(ColorUtils.blendARGB(magenta, cyan, pulse), 110), Color.TRANSPARENT), floatArrayOf(0f, 0.32f, 0.68f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, cr * 2.1f, fill)
        fill.shader = null

        // mini live waveform across the core
        wave.reset()
        val amp = r * 0.15f * (0.25f + energy)
        val x0 = cx - r * 0.56f; val x1 = cx + r * 0.56f
        var x = x0
        while (x <= x1) {
            val env = 1f - abs((x - cx) / (r * 0.56f)).let { it * it }
            val y = cy + sin((x - x0) / d * 0.35f + t * (4f + 5f * energy)) * amp * env
            if (x == x0) wave.moveTo(x, y) else wave.lineTo(x, y)
            x += 2f * d
        }
        stroke.color = Color.argb(215, 255, 255, 255); stroke.strokeWidth = 1.6f * d
        c.drawPath(wave, stroke)

        // metallic ring (slowly rotating sweep gradient) + inner hairline
        c.save(); c.rotate(t * 24f, cx, cy)
        stroke.shader = SweepGradient(cx, cy, intArrayOf(Color.parseColor("#F4F8FC"), Color.parseColor("#6E7986"), Color.parseColor("#FFFFFF"), Color.parseColor("#4F5A66"), Color.parseColor("#F4F8FC")), null)
        stroke.strokeWidth = 3.2f * d
        c.drawCircle(cx, cy, r - 1.6f * d, stroke)
        c.restore()
        stroke.shader = null; stroke.color = Color.argb(70, 255, 255, 255); stroke.strokeWidth = 1f * d
        c.drawCircle(cx, cy, r - 5.5f * d, stroke)

        postInvalidateOnAnimation()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; lastX = downX; lastY = downY; downAt = System.currentTimeMillis(); moved = false }
            MotionEvent.ACTION_MOVE -> {
                if (!moved && (abs(e.rawX - downX) > slop || abs(e.rawY - downY) > slop)) moved = true
                if (moved) { onDrag((e.rawX - lastX).toInt(), (e.rawY - lastY).toInt()); lastX = e.rawX; lastY = e.rawY }
            }
            MotionEvent.ACTION_UP -> {
                if (moved) onDragEnd() else if (System.currentTimeMillis() - downAt > 650) onLongPress() else onTap()
            }
        }
        return true
    }
}
