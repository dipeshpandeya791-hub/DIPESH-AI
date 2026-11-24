package com.dipesh.ai

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.math.min
import kotlin.math.sin

val Cyan = Color(0xFF19F0D0)
val DimCyan = Color(0xFF0B6B60)
val Orange = Color(0xFFFF8A2A)
val Bg = Color(0xFF02161A)

/** Animated JARVIS-style ring display. Speed and colour follow the assistant status. */
@Composable
fun Hud(status: Status, modifier: Modifier = Modifier) {
    var angle by remember { mutableFloatStateOf(0f) }
    val speed by rememberUpdatedState(
        when (status) {
            Status.IDLE -> 0.35f
            Status.LISTENING -> 1.0f
            Status.THINKING -> 2.6f
            Status.SPEAKING -> 1.4f
        }
    )
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameMillis { t ->
                if (last != 0L) angle = (angle + speed * (t - last) / 16f) % 360f
                last = t
            }
        }
    }
    val color = if (status == Status.THINKING) Orange else Cyan

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height / 2)
            val base = min(size.width, size.height) / 2 * 0.95f
            val pulse = if (status == Status.SPEAKING) 1f + 0.04f * sin(Math.toRadians((angle * 3).toDouble())).toFloat() else 1f
            // (radius factor, stroke width, sweep, direction)
            val rings = listOf(
                listOf(1.00f, 4f, 70f, 1f),
                listOf(0.84f, 12f, 40f, -1f),
                listOf(0.68f, 3f, 120f, 1f),
                listOf(0.52f, 8f, 90f, -1f)
            )
            rings.forEachIndexed { i, ring ->
                val r = base * ring[0] * pulse
                val topLeft = Offset(c.x - r, c.y - r)
                val sz = Size(r * 2, r * 2)
                drawCircle(DimCyan, radius = r, center = c, style = Stroke(width = 1.5f))
                for (k in 0 until 3) {
                    val start = angle * ring[3] * (1f + i * 0.3f) + k * 120f
                    drawArc(
                        color = color,
                        startAngle = start,
                        sweepAngle = ring[2],
                        useCenter = false,
                        topLeft = topLeft,
                        size = sz,
                        style = Stroke(width = ring[1])
                    )
                }
            }
            drawCircle(color, radius = base * 0.30f * pulse, center = c, style = Stroke(width = 3f))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("DIPESH", color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text("A.I.", color = color, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        }
    }
}
