package com.dipesh.ai.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material3.Text
import com.dipesh.ai.Status
import com.dipesh.ai.WidgetBus
import com.dipesh.ai.holo.HoloMath
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Plays the startup sequence only the first time per app launch. */
object HoloPlayed { var stage = false; var title = false }

private val Cyan = Color(0xFF22D3EE)
private val Electric = Color(0xFF8B5CF6)
private val Neon = Color(0xFF4F7CFF)
private val Silver = Color(0xFFCBD5E1)
private val Magenta = Color(0xFFE879F9)

/** Phone tilt (-1..1 on each axis), low-pass filtered, used as a gentle camera parallax. Free: only the accelerometer. */
@Composable
fun rememberTilt(): State<Offset> {
    val ctx = LocalContext.current
    val state = remember { mutableStateOf(Offset.Zero) }
    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sm == null || sensor == null) return@DisposableEffect onDispose { }
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val tx = (-e.values[0] / 9.81f).coerceIn(-1f, 1f)
                val ty = (e.values[2] / 9.81f).coerceIn(-1f, 1f)
                val o = state.value
                state.value = Offset(o.x + (tx - o.x) * 0.12f, o.y + (ty - o.y) * 0.12f)
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(l) }
    }
    return state
}

private class Cam(val t: Float, val yaw: Float, val pitch: Float, val energy: Float, val lvl: Float, val status: Status, val startup: Float, val mouth: Float)

private val icons = listOf("🎤", "💬", "🎛", "🛠", "🌐", "🎬", "📅", "🧠")

/**
 * The holographic AI lab stage: perspective floor grid, depth-sorted particles, tilted energy rings that pass behind and in
 * front of the [core], orbiting icons, data streams, parallax camera (phone tilt + finger drag) and a startup sequence.
 * It reacts to the assistant status. Rendered with Compose Canvas (perspective maths, additive glows); it is not a game-engine
 * or ray-traced renderer.
 */
@Composable
fun HoloStage(status: Status, level: Float, modifier: Modifier = Modifier, core: @Composable () -> Unit) {
    val time = rememberTime()
    val tilt = rememberTilt()
    val scope = rememberCoroutineScope()
    val dragYaw = remember { Animatable(0f) }
    val startup = remember { Animatable(if (HoloPlayed.stage) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!HoloPlayed.stage) { startup.animateTo(1f, tween(2600, easing = FastOutSlowInEasing)); HoloPlayed.stage = true }
    }
    val energy = animateFloatAsState(when (status) { Status.IDLE -> 0.15f; Status.LISTENING -> 0.55f; Status.THINKING -> 0.85f; Status.SPEAKING -> 0.7f }, tween(600), label = "e")
    val lvl = animateFloatAsState(level, tween(90), label = "l")
    val measurer = rememberTextMeasurer()
    val layouts = remember { icons.map { measurer.measure(AnnotatedString(it), TextStyle(fontSize = 17.sp)) } }

    fun cam(): Cam {
        val t = time.floatValue
        val yaw = sin(t * 0.25f) * 0.12f + tilt.value.x * 0.35f + dragYaw.value
        val pitch = 0.2f + tilt.value.y * 0.25f + sin(t * 0.2f) * 0.04f
        val mouth = if (status == Status.SPEAKING) (if (WidgetBus.mouth > 0.02f) WidgetBus.mouth else 0.3f + 0.4f * abs(sin(t * 6f))) else 0f
        return Cam(t, yaw, pitch, energy.value, lvl.value, status, startup.value, mouth)
    }

    Box(
        modifier.widthIn(max = 380.dp).pointerInput(Unit) {
            // horizontal only, so vertical page scrolling still works when the finger starts on the stage
            detectHorizontalDragGestures(
                onHorizontalDrag = { change, dx ->
                    change.consume()
                    scope.launch { dragYaw.snapTo((dragYaw.value + dx * 0.006f).coerceIn(-0.9f, 0.9f)) }
                },
                onDragEnd = { scope.launch { dragYaw.animateTo(0f, spring(0.55f, 60f)) } },
                onDragCancel = { scope.launch { dragYaw.animateTo(0f) } }
            )
        }
    ) {
        Canvas(Modifier.fillMaxSize()) { val c = cam(); drawBackdrop(c); drawFloor(c); drawParticles(c); drawStreams(c); drawIcons(c, layouts, front = false); drawRings(c, front = false) }
        Box(Modifier.fillMaxSize().padding(horizontal = 0.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxSize(0.62f), contentAlignment = Alignment.Center) { core() }
        }
        Canvas(Modifier.fillMaxSize()) { val c = cam(); drawRings(c, front = true); drawIcons(c, layouts, front = true); drawSweep(c); drawFlash(c) }
    }
}

// ------------------------------------------------------------------ drawing layers
private fun DrawScope.unit() = size.minDimension * 0.165f

private fun DrawScope.drawBackdrop(c: Cam) {
    val centre = Offset(size.width / 2, size.height * 0.48f)
    val r = size.minDimension * 0.62f
    drawCircle(Brush.radialGradient(listOf(Electric.copy(alpha = 0.40f * (0.5f + c.energy)), Neon.copy(alpha = 0.14f), Color.Transparent), centre, r), r, centre)
    drawCircle(Brush.radialGradient(listOf(Cyan.copy(alpha = 0.20f + 0.25f * c.energy + 0.2f * c.mouth), Color.Transparent), centre, r * 0.5f), r * 0.5f, centre)
}

/** Lab floor: perspective grid scrolling slowly toward the viewer. */
private fun DrawScope.drawFloor(c: Cam) {
    val u = unit(); val cx = size.width / 2; val cy = size.height * 0.48f
    val o = FloatArray(4); val o2 = FloatArray(4)
    val floorY = 2.3f
    val fade = HoloMath.ease(c.startup)
    val scroll = HoloMath.frac(c.t * 0.12f)
    for (i in -6..6) {
        HoloMath.project(i * 1f, floorY, -1.5f, c.yaw, c.pitch, o); HoloMath.project(i * 1f, floorY, 11f, c.yaw, c.pitch, o2)
        drawLine(Cyan.copy(alpha = 0.16f * fade), Offset(cx + o[0] * u, cy + o[1] * u), Offset(cx + o2[0] * u, cy + o2[1] * u), 1.2f)
    }
    for (j in 0..10) {
        val z = -1.5f + ((j + scroll) % 11f) * 1.1f
        HoloMath.project(-6f, floorY, z, c.yaw, c.pitch, o); HoloMath.project(6f, floorY, z, c.yaw, c.pitch, o2)
        val a = (0.20f * (1f - (z + 1.5f) / 12f)).coerceAtLeast(0f) * fade
        drawLine(Electric.copy(alpha = a), Offset(cx + o[0] * u, cy + o[1] * u), Offset(cx + o2[0] * u, cy + o2[1] * u), 1.2f)
    }
}

private fun DrawScope.drawParticles(c: Cam) {
    val u = unit(); val cx = size.width / 2; val cy = size.height * 0.48f
    val o = FloatArray(4)
    val s = HoloMath.ease(c.startup)
    for (i in 0 until 84) {
        val hx = HoloMath.hash(i * 3 + 1); val hy = HoloMath.hash(i * 3 + 2); val hz = HoloMath.hash(i * 3 + 3)
        val speed = 0.02f + 0.05f * hz * (1f + c.energy * 2f)
        val x = (hx - 0.5f) * 8.5f * s
        val y = (HoloMath.frac(hy + c.t * speed) - 0.5f) * 6.2f * s
        val z = (hz * 6f - 1.5f) * s
        HoloMath.project(x, y, z, c.yaw, c.pitch, o)
        val depthFade = (1f - (o[3] + 2f) / 8f).coerceIn(0.1f, 1f)
        val tw = 0.55f + 0.45f * sin(c.t * (1.5f + hx * 3f) + i)
        val col = when (i % 4) { 0 -> Cyan; 1 -> Electric; 2 -> Silver; else -> Neon }
        drawCircle(col.copy(alpha = 0.75f * depthFade * tw), (0.9f + 2.4f * hz) * o[2], Offset(cx + o[0] * u, cy + o[1] * u), blendMode = BlendMode.Plus)
    }
}

/** Vertical data streams on both sides, with bright packets falling through them. */
private fun DrawScope.drawStreams(c: Cam) {
    val u = unit(); val cx = size.width / 2; val cy = size.height * 0.48f
    val a = FloatArray(4); val b = FloatArray(4)
    val fade = HoloMath.ease(c.startup)
    for (k in 0 until 6) {
        val side = if (k % 2 == 0) -1f else 1f
        val x = side * (3.0f + (k / 2) * 0.42f); val z = 0.8f + (k / 2) * 0.5f
        HoloMath.project(x, -2.4f, z, c.yaw, c.pitch, a); HoloMath.project(x, 2.4f, z, c.yaw, c.pitch, b)
        drawLine(Cyan.copy(alpha = 0.10f * fade), Offset(cx + a[0] * u, cy + a[1] * u), Offset(cx + b[0] * u, cy + b[1] * u), 1f)
        for (p in 0 until 7) {
            val ph = HoloMath.frac(c.t * (0.18f + 0.05f * k + c.energy * 0.3f) + p / 7f + k * 0.13f)
            HoloMath.project(x, -2.4f + 4.8f * ph, z, c.yaw, c.pitch, a)
            val al = (1f - abs(ph - 0.5f) * 2f).coerceIn(0f, 1f) * fade
            drawRect((if (k % 3 == 0) Magenta else Cyan).copy(alpha = 0.85f * al), Offset(cx + a[0] * u - 1.5f, cy + a[1] * u - 6f * a[2]), androidx.compose.ui.geometry.Size(3f, 12f * a[2]), blendMode = BlendMode.Plus)
        }
    }
}

private fun DrawScope.drawIcons(c: Cam, layouts: List<TextLayoutResult>, front: Boolean) {
    val u = unit(); val cx = size.width / 2; val cy = size.height * 0.48f
    val o = FloatArray(4); val p = FloatArray(3)
    val s = HoloMath.back(c.startup)
    for (i in icons.indices) {
        val phi = c.t * 0.32f + i * (2f * PI.toFloat() / icons.size)
        HoloMath.ringPoint(3.0f * s, phi, 0.45f, 0.22f, p)
        HoloMath.project(p[0], p[1] + 0.15f * sin(c.t * 1.3f + i), p[2], c.yaw, c.pitch, o)
        if ((o[3] < 0f) != front) continue
        val k = o[2]; val x = cx + o[0] * u; val y = cy + o[1] * u
        val al = (if (front) 1f else 0.55f) * HoloMath.ease(c.startup)
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.22f * al), Electric.copy(alpha = 0.18f * al), Color.Transparent), Offset(x, y), 22f * k), 22f * k, Offset(x, y))
        drawCircle(Cyan.copy(alpha = 0.55f * al), 15f * k, Offset(x, y), style = Stroke(1.2f))
        val l = layouts[i]
        withTransform({ translate(x, y); scale(k, k, Offset.Zero) }) {
            drawText(l, topLeft = Offset(-l.size.width / 2f, -l.size.height / 2f), alpha = al)
        }
    }
}

private val ringTiltX = floatArrayOf(1.20f, 0.62f, 1.45f)
private val ringTiltZ = floatArrayOf(0.35f, -0.70f, 1.15f)

private fun DrawScope.drawRings(c: Cam, front: Boolean) {
    val u = unit(); val cx = size.width / 2; val cy = size.height * 0.48f
    val o = FloatArray(4); val p = FloatArray(3)
    val path = Path()
    val n = 72
    val thinking = c.status == Status.THINKING
    for (i in 0 until 3) {
        val appear = HoloMath.back(((c.startup - i * 0.12f) / 0.7f))
        val r = (1.62f + i * 0.42f) * appear * (1f + c.lvl * 0.10f * (3 - i) + c.mouth * 0.07f)
        if (r <= 0.01f) continue
        val spin = c.t * (0.45f + i * 0.33f) * (1f + c.energy * 2.2f) * (if (i == 1) -1f else 1f)
        val col = when (i) { 0 -> Cyan; 1 -> Electric; else -> Silver }
        path.reset(); var open = false
        for (k in 0..n) {
            HoloMath.ringPoint(r, spin + 2f * PI.toFloat() * k / n, ringTiltX[i], ringTiltZ[i] + sin(c.t * 0.3f + i) * 0.06f, p)
            HoloMath.project(p[0], p[1], p[2], c.yaw, c.pitch, o)
            val inFront = o[3] < 0f
            if (inFront == front) {
                val x = cx + o[0] * u; val y = cy + o[1] * u
                if (!open) { path.moveTo(x, y); open = true } else path.lineTo(x, y)
            } else open = false
        }
        val a = if (front) 1f else 0.38f
        drawPath(path, col.copy(alpha = 0.16f * a), style = Stroke(9f, cap = StrokeCap.Round), blendMode = BlendMode.Plus)
        drawPath(path, col.copy(alpha = 0.90f * a), style = Stroke(if (i == 0) 2.4f else 1.8f, cap = StrokeCap.Round,
            pathEffect = if (thinking && i != 0) PathEffect.dashPathEffect(floatArrayOf(16f, 10f), c.t * 60f) else null))
        // the bright comet travelling along the ring
        val th = spin * 1.9f + i * 2f
        HoloMath.ringPoint(r, th, ringTiltX[i], ringTiltZ[i], p); HoloMath.project(p[0], p[1], p[2], c.yaw, c.pitch, o)
        if ((o[3] < 0f) == front) {
            val x = cx + o[0] * u; val y = cy + o[1] * u
            drawCircle(Brush.radialGradient(listOf(Color.White, col.copy(alpha = 0.6f), Color.Transparent), Offset(x, y), 16f * o[2]), 16f * o[2], Offset(x, y), blendMode = BlendMode.Plus)
        }
    }
}

/** A soft diagonal light sweep, like a reflection travelling over glass. */
private fun DrawScope.drawSweep(c: Cam) {
    val ph = HoloMath.frac(c.t / 7f)
    val x = -size.width * 0.4f + ph * size.width * 1.8f
    drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.07f), Color.Transparent), Offset(x, 0f), Offset(x + size.width * 0.35f, size.height * 0.5f)), blendMode = BlendMode.Plus)
}

/** The flash at the end of the startup sequence. */
private fun DrawScope.drawFlash(c: Cam) {
    val f = ((c.startup - 0.55f) / 0.25f).coerceIn(0f, 1f)
    if (f <= 0f || c.startup >= 1f) return
    val a = sin(f * PI.toFloat()) * 0.55f
    val centre = Offset(size.width / 2, size.height * 0.48f)
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = a), Cyan.copy(alpha = a * 0.5f), Color.Transparent), centre, size.minDimension * 0.6f), size.minDimension * 0.6f, centre, blendMode = BlendMode.Plus)
}

// ------------------------------------------------------------------ title
/** The glowing "DIPESH AI ASSISTANT" title with a moving light gradient and a scan-in reveal. */
@Composable
fun GlowTitle(modifier: Modifier = Modifier) {
    val time = rememberTime()
    val reveal = remember { Animatable(if (HoloPlayed.title) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!HoloPlayed.title) { kotlinx.coroutines.delay(450); reveal.animateTo(1f, tween(1700, easing = FastOutSlowInEasing)); HoloPlayed.title = true }
    }
    val title = "DIPESH AI ASSISTANT"
    val base = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
    Column(modifier.fillMaxWidth().drawWithContent { clipRect(right = size.width * reveal.value) { this@drawWithContent.drawContent() } }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Text(title, style = base.copy(color = Cyan.copy(alpha = 0.9f), shadow = Shadow(Cyan, Offset.Zero, 34f)), maxLines = 1, softWrap = false)
            Text(title, style = base.copy(brush = Brush.linearGradient(
                listOf(Cyan, Color.White, Electric, Silver, Cyan),
                start = Offset((time.floatValue * 140f) % 900f, 0f), end = Offset((time.floatValue * 140f) % 900f + 500f, 0f), tileMode = TileMode.Mirror)), maxLines = 1, softWrap = false)
        }
        Box(Modifier.padding(top = 4.dp).fillMaxWidth(0.62f).height(1.5.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, Cyan, Electric, Color.Transparent))))
    }
}
