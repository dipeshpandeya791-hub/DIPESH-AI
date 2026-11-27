package com.dipesh.ai.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.dipesh.ai.Status
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Seconds since the composable entered the screen, updated every frame (60 FPS where the device allows). */
@Composable
fun rememberTime(): androidx.compose.runtime.FloatState {
    val time = remember { mutableFloatStateOf(0f) }
    val resolver = androidx.compose.ui.platform.LocalContext.current.contentResolver
    LaunchedEffect(Unit) {
        // Respect "Remove animations" (animator scale 0): hold a calm still frame instead of looping.
        val still = try {
            android.provider.Settings.Global.getFloat(resolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        } catch (_: Exception) { false }
        if (still) { time.floatValue = 1.5f; return@LaunchedEffect }
        var start = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { n ->
                if (start == 0L) start = n
                time.floatValue = (n - start) / 1_000_000_000f
            }
        }
    }
    return time
}

/** The animated DIPESH AI orb. Reacts to the status and to the live microphone [level]. */
@Composable
fun AiOrb(status: Status, level: Float, modifier: Modifier = Modifier) {
    val c = LocalDipeshColors.current
    val time = rememberTime()
    val lvl = animateFloatAsState(level, tween(90), label = "level")
    val energy = animateFloatAsState(
        when (status) {
            Status.IDLE -> 0.12f
            Status.LISTENING -> 0.45f
            Status.THINKING -> 0.55f
            Status.SPEAKING -> 0.65f
        },
        tween(500), label = "energy"
    )
    Canvas(modifier.aspectRatio(1f)) {
        val t = time.floatValue
        val centre = Offset(size.width / 2, size.height / 2)
        val base = size.minDimension / 2

        // how strongly the wave ring moves
        val amp = when (status) {
            Status.LISTENING -> 0.15f + lvl.value * 0.85f
            Status.SPEAKING -> 0.35f + 0.35f * abs(sin(t * 4.2f)) * (0.6f + 0.4f * sin(t * 1.3f))
            Status.THINKING -> 0.3f
            Status.IDLE -> 0.08f
        }
        val breath = sin(t * 1.5f) * 0.5f + 0.5f

        // 1. ambient glow
        drawCircle(
            Brush.radialGradient(
                listOf(c.violet.copy(alpha = 0.50f * (0.5f + energy.value)), c.blue.copy(alpha = 0.18f), Color.Transparent),
                center = centre, radius = base
            ),
            radius = base, center = centre
        )

        // 2. rotating arcs (faster while thinking)
        val spin = when (status) {
            Status.THINKING -> 140f
            Status.SPEAKING -> 55f
            Status.LISTENING -> 40f
            Status.IDLE -> 14f
        }
        val arcs = listOf(Triple(0.97f, 2.2f, 1f), Triple(0.88f, 3.5f, -1.4f), Triple(0.79f, 1.8f, 1.9f))
        arcs.forEachIndexed { i, (r, w, dir) ->
            val radius = base * r
            val brush = Brush.sweepGradient(listOf(Color.Transparent, c.cyan, c.violet, Color.Transparent), centre)
            rotateBrushArc(centre, radius, w.dp.toPx(), t * spin * dir + i * 70f, 110f + i * 30f, brush)
        }

        // 3. sound wave ring around the core
        val ringR = base * 0.60f
        val path = Path()
        val steps = 160
        for (i in 0..steps) {
            val th = i.toFloat() / steps * 2f * PI.toFloat()
            val wobble = sin(th * 5f + t * 2.4f) + 0.6f * sin(th * 3f - t * 1.7f) + 0.4f * sin(th * 9f + t * 3.3f)
            val rr = ringR + base * 0.075f * amp * wobble
            val p = Offset(centre.x + rr * cos(th), centre.y + rr * sin(th))
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        path.close()
        drawPath(
            path,
            Brush.sweepGradient(listOf(c.violet, c.cyan, c.blue, c.violet), centre),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )

        // 4. glowing core sphere
        val r = base * 0.44f * (1f + 0.035f * breath + 0.10f * amp)
        drawCircle(
            Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.95f), c.cyan, c.blue, c.violet, Color(0xFF14083A)),
                center = centre - Offset(r * 0.28f, r * 0.32f),
                radius = r * 1.5f
            ),
            radius = r, center = centre
        )
        // specular highlight
        drawCircle(Color.White.copy(alpha = 0.30f), radius = r * 0.16f, center = centre - Offset(r * 0.38f, r * 0.42f))

        // 5. orbiting particles
        for (k in 0 until 3) {
            val a = t * (0.6f + k * 0.35f) + k * 2.1f
            val orbit = base * (0.93f - k * 0.05f)
            val p = Offset(centre.x + orbit * cos(a), centre.y + orbit * sin(a))
            drawCircle(c.cyan.copy(alpha = 0.25f), radius = 7.dp.toPx(), center = p)
            drawCircle(Color.White.copy(alpha = 0.9f), radius = 2.2.dp.toPx(), center = p)
        }
    }
}

private fun DrawScope.rotateBrushArc(centre: Offset, radius: Float, width: Float, start: Float, sweep: Float, brush: Brush) {
    drawArc(
        brush = brush, startAngle = start, sweepAngle = sweep, useCenter = false,
        topLeft = Offset(centre.x - radius, centre.y - radius), size = Size(radius * 2, radius * 2),
        style = Stroke(width = width, cap = StrokeCap.Round)
    )
}

/** Sound-wave bars. Real microphone level while listening, procedural motion while speaking. */
@Composable
fun SoundWave(status: Status, level: Float, modifier: Modifier = Modifier) {
    val c = LocalDipeshColors.current
    val time = rememberTime()
    val lvl = animateFloatAsState(level, tween(80), label = "wave")
    Canvas(modifier.fillMaxWidth().height(56.dp)) {
        val t = time.floatValue
        val bars = 34
        val step = size.width / bars
        val barW = step * 0.5f
        for (i in 0 until bars) {
            val x = (i + 0.5f) * step
            val phase = i * 0.42f
            val wave = ((sin(t * 4f + phase) + sin(t * 2.3f + phase * 1.7f)) / 2f) * 0.5f + 0.5f
            val a = when (status) {
                Status.LISTENING -> 0.10f + lvl.value * 0.90f
                Status.SPEAKING -> 0.30f + 0.55f * abs(sin(t * 3.1f + i * 0.2f))
                Status.THINKING -> abs(sin(t * 3f - i * 0.28f)).let { it * it }
                Status.IDLE -> 0.05f
            }
            val envelope = 1f - abs((i - bars / 2f) / (bars / 2f)) * 0.6f
            val h = (size.height * 0.10f + size.height * 0.90f * a * wave * envelope).coerceAtMost(size.height)
            val color = lerp(c.violet, c.cyan, i / bars.toFloat()).copy(alpha = if (status == Status.IDLE) 0.45f else 0.95f)
            drawLine(
                color, Offset(x, size.height / 2 - h / 2), Offset(x, size.height / 2 + h / 2),
                strokeWidth = barW, cap = StrokeCap.Round
            )
        }
    }
}

/** Large glowing microphone button. Becomes a stop button while the assistant is busy. */
@Composable
fun MicButton(status: Status, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalDipeshColors.current
    val infinite = rememberInfiniteTransition(label = "mic")
    val pulse = infinite.animateFloat(
        0.92f, 1.35f,
        infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse"
    )
    val busy = status != Status.IDLE
    Box(modifier.size(96.dp), contentAlignment = Alignment.Center) {
        if (status == Status.LISTENING) {
            Canvas(Modifier.size(96.dp)) {
                drawCircle(c.cyan.copy(alpha = 0.18f), radius = size.minDimension / 2 * pulse.value * 0.95f)
                drawCircle(c.violet.copy(alpha = 0.25f), radius = size.minDimension / 2 * pulse.value * 0.75f)
            }
        }
        Box(
            Modifier.size(72.dp)
                .shadow(20.dp, CircleShape, ambientColor = c.violet, spotColor = c.blue)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(c.violet, c.blue)))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(30.dp)) {
                if (busy) drawStop(Color.White) else drawMic(Color.White)
            }
        }
    }
}

private fun DrawScope.drawStop(color: Color) {
    val s = size.minDimension * 0.62f
    drawRoundRect(
        color, topLeft = Offset((size.width - s) / 2, (size.height - s) / 2), size = Size(s, s),
        cornerRadius = CornerRadius(s * 0.22f)
    )
}

private fun DrawScope.drawMic(color: Color) {
    val w = size.width
    val h = size.height
    val capW = w * 0.36f
    drawRoundRect(
        color, topLeft = Offset((w - capW) / 2, h * 0.04f), size = Size(capW, h * 0.56f),
        cornerRadius = CornerRadius(capW / 2)
    )
    drawArc(
        color, startAngle = 0f, sweepAngle = 180f, useCenter = false,
        topLeft = Offset(w * 0.2f, h * 0.22f), size = Size(w * 0.6f, h * 0.55f),
        style = Stroke(width = w * 0.075f, cap = StrokeCap.Round)
    )
    drawLine(color, Offset(w / 2, h * 0.77f), Offset(w / 2, h * 0.95f), strokeWidth = w * 0.075f, cap = StrokeCap.Round)
    drawLine(color, Offset(w * 0.34f, h * 0.95f), Offset(w * 0.66f, h * 0.95f), strokeWidth = w * 0.075f, cap = StrokeCap.Round)
}

/** Three bouncing dots shown while the assistant is preparing a reply. */
@Composable
fun TypingDots(modifier: Modifier = Modifier) {
    val c = LocalDipeshColors.current
    val infinite = rememberInfiniteTransition(label = "dots")
    Row(modifier, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until 3) {
            val a = infinite.animateFloat(
                0.25f, 1f,
                infiniteRepeatable(tween(600, delayMillis = i * 160, easing = LinearEasing), RepeatMode.Reverse),
                label = "dot$i"
            )
            Canvas(Modifier.size(8.dp)) {
                drawCircle(lerp(c.violet, c.cyan, i / 2f).copy(alpha = a.value), radius = size.minDimension / 2 * (0.6f + 0.4f * a.value))
            }
        }
    }
}
