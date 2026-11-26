package com.dipesh.ai.pet

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import com.dipesh.ai.WidgetBus
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The Yeti. Drawn procedurally with layered, lit shapes (sphere-style gradients, fluffy fur tufts, rim light, soft ground
 * shadow) and animated by springs from [YetiBrain] poses. Nothing is drawn except the creature: the view is fully transparent.
 * Logical drawing space is 70 x 112 units (x 15..85, y 4..116 of a 100-wide character sheet).
 */
class YetiView(
    ctx: Context,
    sizeDp: Int,
    var headphones: Boolean,
    private val onTap: () -> Unit,
    private val onDrag: (dx: Int, dy: Int) -> Unit,
    private val onDragEnd: () -> Unit,
    /** Window movement request in px; returns true when the screen edge was hit. */
    private val onWalk: (dxPx: Int) -> Boolean
) : View(ctx) {
    var wander = true
    var sleepWhenIdle = true

    private val d = ctx.resources.displayMetrics.density
    private val widthPx = (sizeDp * d).toInt()
    private val heightPx = (widthPx * 112f / 70f).toInt()
    private val u = widthPx / 70f
    private val ui = Handler(Looper.getMainLooper())

    private val brain = YetiBrain()
    private val pose = Pose()
    private val target = Pose()
    private var lastFrame = 0L
    private var running = false
    private var dragging = false
    private var carry = 0f
    private var clock = 0f

    // ---- paints (allocated once) ----
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val tuft = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tmp = Path()
    private val rect = RectF()
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD); textAlign = Paint.Align.CENTER }

    private val furWhite = Color.parseColor("#FFFFFF")
    private val furMid = Color.parseColor("#E3F2FD")
    private val furShade = Color.parseColor("#BBD9F2")
    private val ice = Color.parseColor("#8FE3FF")
    private val cyan = Color.parseColor("#00E5FF")
    private val purple = Color.parseColor("#9B5CFF")

    // lit "sphere" shaders in logical units (light from the upper left, cool bounce light from the lower right)
    private val bodyShader = RadialGradient(43f, 74f, 38f, intArrayOf(furWhite, furMid, furShade), floatArrayOf(0f, 0.62f, 1f), Shader.TileMode.CLAMP)
    private val headShader = RadialGradient(41f, 40f, 40f, intArrayOf(furWhite, furMid, furShade), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
    private val faceShader = RadialGradient(47f, 50f, 24f, intArrayOf(Color.parseColor("#F4FBFF"), Color.parseColor("#CDEBFF"), Color.parseColor("#A9D8FA")), floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP)
    private val irisShader = RadialGradient(0f, 1.5f, 6.4f, intArrayOf(Color.parseColor("#9BEBFF"), Color.parseColor("#2F86E8"), Color.parseColor("#0C2F73")), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
    private val cheekShader = RadialGradient(0f, 0f, 6f, intArrayOf(Color.parseColor("#FFA6D6"), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
    private val glossShader = RadialGradient(0f, 0f, 20f, intArrayOf(Color.argb(150, 255, 255, 255), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
    private val footShader = RadialGradient(-2f, -2f, 11f, intArrayOf(furWhite, furMid, furShade), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
    private val cupShader = LinearGradient(0f, 0f, 0f, 14f, cyan, purple, Shader.TileMode.CLAMP)
    private val bandShader = LinearGradient(20f, 0f, 80f, 0f, intArrayOf(cyan, purple), null, Shader.TileMode.CLAMP)

    private val tuftSeed = FloatArray(64) { sin(it * 12.9898f) * 0.5f + 0.5f }

    init { setLayerType(LAYER_TYPE_HARDWARE, null) }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(widthPx, heightPx)

    // ------------------------------------------------------------ animation loop
    private val frame = object : Runnable {
        override fun run() {
            if (!running) return
            val now = System.nanoTime()
            val dt = if (lastFrame == 0L) 0.033f else ((now - lastFrame) / 1e9f).coerceIn(0.005f, 0.1f)
            lastFrame = now
            clock += dt
            val wallMs = System.currentTimeMillis()
            val input = PetInput(WidgetBus.status(), WidgetBus.level(), WidgetBus.mouth, dragging, wander, sleepWhenIdle)
            if (WidgetBus.eventAt != 0L && wallMs - WidgetBus.eventAt < 4000) brain.react(WidgetBus.event, WidgetBus.eventAt)
            val act = brain.tick(dt, wallMs, input, target)
            pose.approach(target, dt, when (act) { Act.JUMP, Act.SURPRISED, Act.WAKE -> 16f; Act.SLEEP -> 4f; else -> 9f })
            // walking moves the window; reaching a screen edge makes the Yeti react
            if (brain.velocityX != 0f && !dragging) {
                carry += brain.velocityX * d * dt
                val px = carry.toInt()
                if (px != 0) { carry -= px; if (onWalk(px)) brain.hitEdge() }
            }
            invalidate()
            ui.postDelayed(this, YetiBrain.frameDelayMs(act))
        }
    }

    fun resume() { if (!running) { running = true; lastFrame = 0L; ui.post(frame) } }
    fun pause() { running = false; ui.removeCallbacks(frame) }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); resume() }
    override fun onDetachedFromWindow() { pause(); super.onDetachedFromWindow() }
    override fun onWindowVisibilityChanged(v: Int) { super.onWindowVisibilityChanged(v); if (v == VISIBLE) resume() else pause() }

    // ------------------------------------------------------------ touch
    private var downX = 0f; private var downY = 0f; private var moved = false; private var longFired = false
    private val longPress = Runnable { if (!moved) { longFired = true; brain.petted(System.currentTimeMillis()) } }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; moved = false; longFired = false; ui.postDelayed(longPress, 550); return true }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.rawX - downX; val dy = e.rawY - downY
                if (!moved && sqrt(dx * dx + dy * dy) > 10 * d) { moved = true; dragging = true; ui.removeCallbacks(longPress) }
                if (moved) { onDrag(dx.toInt(), dy.toInt()); downX = e.rawX; downY = e.rawY }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                ui.removeCallbacks(longPress)
                if (moved) { dragging = false; onDragEnd(); brain.touched(System.currentTimeMillis()) }
                else if (!longFired && e.actionMasked == MotionEvent.ACTION_UP) { brain.touched(System.currentTimeMillis()); onTap() }
                dragging = false
                return true
            }
        }
        return true
    }

    // ------------------------------------------------------------ drawing
    override fun onDraw(c: Canvas) {
        val p = pose
        c.save()
        c.scale(u, u)
        c.translate(-15f, -4f)

        val lift = p.lift * 14f
        // soft ground shadow (shrinks and fades as the Yeti jumps)
        val sh = 1f - p.lift * 0.45f
        fill.shader = null; fill.color = Color.argb((70 * sh).toInt(), 20, 40, 80)
        rect.set(50f - 24f * sh, 111f, 50f + 24f * sh, 115f); c.drawOval(rect, fill)

        c.save()
        c.translate(0f, -lift - p.bob * 1.1f)
        c.rotate(p.lean, 50f, 108f)
        c.scale(1f + p.squash * 0.10f, 1f - p.squash * 0.12f, 50f, 108f)

        drawFeet(c, p)
        drawBody(c)
        drawArm(c, 31f, 79f, p.armL, -1f)
        drawArm(c, 69f, 79f, p.armR, 1f)

        // head
        c.save()
        c.translate(p.lookX * 1.4f, p.lookY * 0.8f)
        c.rotate(p.headTilt, 50f, 72f)
        drawHead(c, p)
        c.restore()
        c.restore()

        drawExtras(c, p, lift)
        c.restore()
    }

    private fun fur(c: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, n: Int, len: Float, half: Float) {
        for (i in 0 until n) {
            val th = (2.0 * PI * i / n).toFloat()
            val cs = cos(th); val sn = sin(th)
            val bx = cx + rx * cs; val by = cy + ry * sn
            var nx = cs * ry; var ny = sn * rx
            val nl = sqrt(nx * nx + ny * ny); nx /= nl; ny /= nl
            val tx = -ny; val ty = nx
            val s = tuftSeed[i % 64]
            val wob = sin(clock * 2.1f + i * 1.7f) * 0.7f + pose.ear * 0.0f
            val l = len * (0.75f + 0.5f * s)
            tmp.reset()
            tmp.moveTo(bx - tx * half, by - ty * half)
            tmp.quadTo(bx + nx * l * 0.5f + tx * wob * 0.5f, by + ny * l * 0.5f + ty * wob * 0.5f, bx + nx * l + tx * wob, by + ny * l + ty * wob)
            tmp.quadTo(bx + nx * l * 0.5f + tx * (wob * 0.5f + half * 0.4f), by + ny * l * 0.5f + ty * (wob * 0.5f + half * 0.4f), bx + tx * half, by + ty * half)
            tmp.close()
            // light from the upper left: tufts facing it are brighter, those facing away take the cool shade
            val lightness = (-cs * 0.55f - sn * 0.6f).coerceIn(-1f, 1f)
            tuft.color = blend(furShade, furWhite, (0.55f + lightness * 0.45f).coerceIn(0f, 1f))
            c.drawPath(tmp, tuft)
        }
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        val r = Color.red(a) + ((Color.red(b) - Color.red(a)) * t).toInt()
        val g = Color.green(a) + ((Color.green(b) - Color.green(a)) * t).toInt()
        val bl = Color.blue(a) + ((Color.blue(b) - Color.blue(a)) * t).toInt()
        return Color.rgb(r, g, bl)
    }

    private fun drawFeet(c: Canvas, p: Pose) {
        for (side in intArrayOf(-1, 1)) {
            val sw = if (side < 0) p.legL else p.legR
            val x = 50f + side * 10f + sw * 3f
            val y = 107f - max(0f, sw) * 3f
            c.save(); c.translate(x, y)
            fill.shader = footShader; rect.set(-8f, -5f, 8f, 5f); c.drawOval(rect, fill)
            // toe pads
            fill.shader = null; fill.color = Color.argb(70, 120, 170, 220)
            for (t in -1..1) c.drawCircle(t * 3.6f, 1.2f, 1.5f, fill)
            c.restore()
        }
    }

    private fun drawBody(c: Canvas) {
        tuft.shader = null
        fur(c, 50f, 86f, 22.5f, 21f, 44, 4.2f, 2.4f)
        fill.shader = bodyShader; rect.set(27.5f, 65f, 72.5f, 107f); c.drawOval(rect, fill)
        // belly
        fill.shader = null; fill.color = Color.argb(150, 255, 255, 255); rect.set(38f, 76f, 62f, 104f); c.drawOval(rect, fill)
        // cool bounce light on the lower right edge
        fill.color = Color.argb(40, 120, 220, 255); rect.set(58f, 80f, 72f, 104f); c.drawOval(rect, fill)
    }

    private fun drawArm(c: Canvas, sx: Float, sy: Float, deg: Float, side: Float) {
        val a = Math.toRadians(deg.toDouble()).toFloat()
        val len = 14f
        val ex = sx + side * sin(a) * len
        val ey = sy + cos(a) * len
        line.shader = null
        line.strokeWidth = 11f; line.color = furShade; c.drawLine(sx, sy, ex, ey, line)
        line.strokeWidth = 9.2f; line.color = furMid; c.drawLine(sx, sy, ex, ey, line)
        line.strokeWidth = 5f; line.color = furWhite; c.drawLine(sx - 0.6f, sy - 0.6f, ex - 0.6f, ey - 0.6f, line)
        fill.shader = null; fill.color = furMid; c.drawCircle(ex, ey, 5.2f, fill)
        fill.color = furWhite; c.drawCircle(ex - 1f, ey - 1.2f, 3.6f, fill)
    }

    private fun drawHead(c: Canvas, p: Pose) {
        // ears (with wiggle)
        for (side in intArrayOf(-1, 1)) {
            c.save(); c.translate(50f + side * 28f, 44f); c.rotate(side * (p.ear * 14f))
            fill.shader = null; fill.color = furMid; c.drawCircle(0f, 0f, 6.6f, fill)
            fill.color = Color.parseColor("#9FD8FF"); c.drawCircle(side * -0.3f, 0.5f, 3.6f, fill)
            c.restore()
        }
        tuft.shader = null
        fur(c, 50f, 50f, 28f, 24f, 56, 4.6f, 2.5f)
        // top tuft
        fill.shader = null; fill.color = furMid
        tmp.reset(); tmp.moveTo(44f, 28f); tmp.quadTo(46f, 18f, 51f, 21f); tmp.quadTo(54f, 16f, 57f, 28f); tmp.close(); c.drawPath(tmp, fill)
        fill.shader = headShader; rect.set(21f, 26f, 79f, 76f); c.drawOval(rect, fill)
        // soft gloss
        c.save(); c.translate(38f, 36f); fill.shader = glossShader; rect.set(-20f, -14f, 20f, 14f); c.drawOval(rect, fill); c.restore()

        // face mask
        fill.shader = faceShader; rect.set(30f, 41f, 70f, 71f); c.drawOval(rect, fill)
        // cheeks
        if (p.blush > 0.02f) {
            for (side in intArrayOf(-1, 1)) {
                c.save(); c.translate(50f + side * 16.5f, 61f); c.scale(1.15f, 0.8f)
                fill.shader = cheekShader; fill.alpha = (p.blush * 200).toInt().coerceIn(0, 255)
                rect.set(-6f, -6f, 6f, 6f); c.drawOval(rect, fill); fill.alpha = 255
                c.restore()
            }
        }
        // eyes
        drawEye(c, 41.5f, 53f, p, -1f)
        drawEye(c, 58.5f, 53f, p, 1f)
        // brows
        line.shader = null; line.color = Color.parseColor("#6FA6D9"); line.strokeWidth = 1.5f
        for (side in intArrayOf(-1, 1)) {
            val bx = 50f + side * 8.5f
            val by = 43.3f - p.brow * 2.6f
            val tilt = (if (p.brow < 0f) p.brow * 2.4f else 0f) * side
            tmp.reset(); tmp.moveTo(bx - 4.6f, by + tilt * -1f + 0.4f); tmp.quadTo(bx, by - 1.5f, bx + 4.6f, by + tilt + 0.4f); c.drawPath(tmp, line)
        }
        // nose
        fill.shader = null; fill.color = Color.parseColor("#2B5F9E"); rect.set(47.8f, 59.3f, 52.2f, 62f); c.drawOval(rect, fill)
        fill.color = Color.argb(170, 255, 255, 255); c.drawCircle(49.4f, 59.9f, 0.7f, fill)
        drawMouth(c, p)
        if (headphones) drawHeadphones(c, p)
    }

    private fun drawEye(c: Canvas, cx: Float, cy: Float, p: Pose, side: Float) {
        val open = p.eyeOpen.coerceIn(0f, 1.3f)
        if (open < 0.12f) {
            line.shader = null; line.color = Color.parseColor("#1B3E7A"); line.strokeWidth = 1.7f
            tmp.reset(); tmp.moveTo(cx - 5.2f, cy + 0.6f); tmp.quadTo(cx, cy + 3.4f, cx + 5.2f, cy + 0.6f); c.drawPath(tmp, line)
            return
        }
        c.save(); c.translate(cx, cy); c.scale(1f, open.coerceAtMost(1.2f))
        // dark rim + sclera glow
        fill.shader = null; fill.color = Color.parseColor("#0A2760"); rect.set(-6.4f, -7.4f, 6.4f, 7.4f); c.drawOval(rect, fill)
        // iris (looks where the head looks)
        c.save(); c.translate(pose.lookX * 1.5f, pose.lookY * 1.6f)
        fill.shader = irisShader; rect.set(-5.4f, -6.4f, 5.4f, 6.4f); c.drawOval(rect, fill)
        fill.shader = null; fill.color = Color.parseColor("#04122E"); c.drawCircle(0f, 0.6f, 2.6f, fill)
        c.restore()
        // glossy highlights (these make the eyes look alive)
        fill.color = Color.WHITE; c.drawCircle(-2f + pose.lookX, -2.8f, 2.1f, fill)
        fill.color = Color.argb(210, 255, 255, 255); c.drawCircle(2.1f + pose.lookX, 2.6f, 1.05f, fill)
        if (p.sparkle > 0.5f) { fill.color = Color.argb(((p.sparkle - 0.4f) * 255).toInt().coerceIn(0, 255), 200, 245, 255); c.drawCircle(2.6f, -3.6f, 0.8f, fill) }
        c.restore()
    }

    private fun drawMouth(c: Canvas, p: Pose) {
        val y = 64.4f
        if (p.mouthOpen > 0.07f) {
            val h = 1.2f + 6.2f * p.mouthOpen
            val w = 3.2f + 1.6f * p.mouthOpen + max(0f, p.smile) * 1.6f
            fill.shader = null; fill.color = Color.parseColor("#7B1F3F")
            tmp.reset(); tmp.moveTo(50f - w, y); tmp.quadTo(50f, y - 1f + max(0f, p.smile) * 0.5f, 50f + w, y)
            tmp.quadTo(50f + w * 0.8f, y + h * 1.15f, 50f, y + h * 1.2f); tmp.quadTo(50f - w * 0.8f, y + h * 1.15f, 50f - w, y); tmp.close()
            c.drawPath(tmp, fill)
            fill.color = Color.parseColor("#FF8DA6"); rect.set(50f - w * 0.55f, y + h * 0.62f, 50f + w * 0.55f, y + h * 1.15f); c.drawOval(rect, fill)
        } else {
            line.shader = null; line.color = Color.parseColor("#2B5F9E"); line.strokeWidth = 1.3f
            val w = 3.4f + p.smile * 0.8f
            tmp.reset(); tmp.moveTo(50f - w, y - p.smile * 0.2f); tmp.quadTo(50f, y + p.smile * 3.6f, 50f + w, y - p.smile * 0.2f); c.drawPath(tmp, line)
        }
    }

    private fun drawHeadphones(c: Canvas, p: Pose) {
        line.shader = bandShader; line.strokeWidth = 3.4f
        tmp.reset(); tmp.moveTo(21.5f, 52f); tmp.cubicTo(20f, 20f, 80f, 20f, 78.5f, 52f); c.drawPath(tmp, line); line.shader = null
        val glow = 0.45f + 0.35f * sin(clock * 3f) + p.sparkle * 0.4f
        for (side in intArrayOf(-1, 1)) {
            c.save(); c.translate(if (side < 0) 14.5f else 78f, 46f)
            fill.shader = null; fill.color = Color.argb((glow * 90).toInt().coerceIn(0, 140), 0, 229, 255); rect.set(-3.5f, -2f, 10.5f, 18f); c.drawRoundRect(rect, 6f, 6f, fill)
            fill.shader = cupShader; rect.set(-1f, 0f, 8f, 15f); c.drawRoundRect(rect, 4f, 4f, fill)
            fill.shader = null; fill.color = Color.argb(90, 255, 255, 255); rect.set(0.2f, 1.2f, 3f, 8f); c.drawRoundRect(rect, 1.4f, 1.4f, fill)
            c.restore()
        }
    }

    private fun drawExtras(c: Canvas, p: Pose, lift: Float) {
        // sleeping "z z z"
        if (brain.act == Act.SLEEP || brain.act == Act.SLEEPY) {
            textPaint.color = Color.argb(200, 150, 220, 255)
            for (i in 0..2) {
                val t = ((clock * 0.5f + i / 3f) % 1f)
                textPaint.textSize = 5f + 4f * t
                textPaint.alpha = (220 * (1f - t)).toInt()
                c.drawText("z", 70f + t * 9f, 38f - t * 20f, textPaint)
            }
        }
        // sparkles when really happy
        if (p.sparkle > 0.65f && (brain.act == Act.CELEBRATE || brain.act == Act.EXCITED || brain.act == Act.DANCE || brain.act == Act.HAPPY)) {
            for (i in 0..5) {
                val ph = (clock * 0.9f + i / 6f) % 1f
                val ang = i * 1.05f + clock
                val r = 26f + 10f * ph
                val sx = 50f + cos(ang) * r; val sy = 54f - lift + sin(ang) * r * 0.8f - ph * 6f
                fill.shader = null; fill.color = Color.argb((230 * (1f - ph)).toInt(), if (i % 2 == 0) 0 else 200, if (i % 2 == 0) 229 else 120, 255)
                drawStar(c, sx, sy, 2.4f * (1f - ph * 0.5f))
            }
        }
    }

    private fun drawStar(c: Canvas, x: Float, y: Float, r: Float) {
        tmp.reset()
        tmp.moveTo(x, y - r); tmp.quadTo(x, y, x + r, y); tmp.quadTo(x, y, x, y + r); tmp.quadTo(x, y, x - r, y); tmp.quadTo(x, y, x, y - r); tmp.close()
        c.drawPath(tmp, fill)
    }

}
