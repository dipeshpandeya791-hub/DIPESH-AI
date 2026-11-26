package com.dipesh.ai.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dipesh.ai.Feature
import com.dipesh.ai.FeatureCatalog
import kotlinx.coroutines.delay

private val HoloCyan = Color(0xFF00E5FF)
private val HoloMagenta = Color(0xFFFF2BD6)

/** Translucent holographic panel: tinted glass, gradient hairline border, corner brackets and a slow scan line. */
@Composable
fun HoloPanel(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val scan by rememberInfiniteTransition(label = "scan").animateFloat(
        0f, 1f, infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart), label = "scan"
    )
    var m = modifier
        .drawBehind {
            drawRect(Brush.verticalGradient(listOf(HoloCyan.copy(alpha = 0.10f), Color(0xFF7C4DFF).copy(alpha = 0.05f))))
            val y = size.height * scan
            drawLine(HoloCyan.copy(alpha = 0.16f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5f)
            val len = 14.dp.toPx(); val w = 2.dp.toPx(); val col = HoloCyan.copy(alpha = 0.9f)
            fun corner(x: Float, y2: Float, dx: Float, dy: Float) {
                drawLine(col, Offset(x, y2), Offset(x + dx * len, y2), w, StrokeCap.Square)
                drawLine(col, Offset(x, y2), Offset(x, y2 + dy * len), w, StrokeCap.Square)
            }
            corner(0f, 0f, 1f, 1f); corner(size.width, 0f, -1f, 1f); corner(0f, size.height, 1f, -1f); corner(size.width, size.height, -1f, -1f)
        }
        .border(BorderStroke(1.dp, Brush.linearGradient(listOf(HoloCyan.copy(alpha = 0.55f), HoloMagenta.copy(alpha = 0.30f)))), shape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(m.padding(14.dp), content = content)
}

@Composable
fun HoloLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = HoloCyan, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
    Spacer(Modifier.height(6.dp))
}

/** Line graph with glow, area fill and a dashed forecast continuing from the last real value. */
@Composable
fun HoloGraph(
    title: String, values: List<Float>, forecast: List<Float>, labels: List<String>, accent: Color = HoloCyan,
    modifier: Modifier = Modifier
) {
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val reveal by animateFloatAsState(if (go) 1f else 0f, tween(1400), label = "reveal")
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
        Canvas(Modifier.fillMaxWidth().height(96.dp).padding(vertical = 6.dp)) {
            val all = values + forecast
            if (all.size < 2) return@Canvas
            val maxV = maxOf(1f, all.max())
            val stepX = size.width / (all.size - 1)
            fun pt(i: Int, v: Float) = Offset(i * stepX, size.height - (v / maxV) * (size.height - 8.dp.toPx()) - 4.dp.toPx())
            for (g in 0..3) drawLine(Color.White.copy(alpha = 0.07f), Offset(0f, size.height * g / 3f), Offset(size.width, size.height * g / 3f), 1f)
            clipRect(right = size.width * reveal) {
                val line = Path(); val area = Path()
                values.forEachIndexed { i, v -> val p = pt(i, v); if (i == 0) { line.moveTo(p.x, p.y); area.moveTo(p.x, size.height); area.lineTo(p.x, p.y) } else { line.lineTo(p.x, p.y); area.lineTo(p.x, p.y) } }
                area.lineTo(pt(values.lastIndex, 0f).x, size.height); area.close()
                drawPath(area, Brush.verticalGradient(listOf(accent.copy(alpha = 0.35f), Color.Transparent)))
                drawPath(line, accent.copy(alpha = 0.25f), style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
                drawPath(line, accent, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                values.forEachIndexed { i, v -> drawCircle(Color.White, 2.5.dp.toPx(), pt(i, v)) }
                if (forecast.isNotEmpty()) {
                    val f = Path(); val start = pt(values.lastIndex, values.last()); f.moveTo(start.x, start.y)
                    forecast.forEachIndexed { k, v -> val p = pt(values.size + k, v); f.lineTo(p.x, p.y) }
                    drawPath(f, HoloMagenta, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))
                    forecast.forEachIndexed { k, v -> drawCircle(HoloMagenta, 3.dp.toPx(), pt(values.size + k, v), style = Stroke(1.5.dp.toPx())) }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            labels.forEachIndexed { i, l -> Text(l, fontSize = 10.sp, color = if (i >= values.size) HoloMagenta else Color.White.copy(alpha = 0.55f)) }
        }
    }
}

// ----------------------------------------------------------------------------- features overlay
@Composable
private fun Reveal(index: Int, modifier: Modifier, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(180L + index * 40L); shown = true }
    Box(modifier) {
        AnimatedVisibility(shown, enter = fadeIn(tween(260)) + slideInVertically(tween(320)) { it / 3 }) { content() }
    }
}

/** Expands from the centre of the screen into an organised overlay of everything the app can do. */
@Composable
fun FeaturesOverlay(visible: Boolean, onClose: () -> Unit, onOpen: (String) -> Unit) {
    BackHandler(enabled = visible) { onClose() }
    val origin = TransformOrigin(0.5f, 0.32f)
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(tween(220)), exit = fadeOut(tween(220))) {
            Box(Modifier.fillMaxSize().background(Color(0xD9020409)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() })
        }
        AnimatedVisibility(
            visible,
            enter = scaleIn(spring(0.72f, Spring.StiffnessMediumLow), initialScale = 0.12f, transformOrigin = origin) + fadeIn(tween(260)),
            exit = scaleOut(tween(230), targetScale = 0.12f, transformOrigin = origin) + fadeOut(tween(200))
        ) {
            Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 16.dp), contentAlignment = Alignment.Center) {
                HoloPanel(Modifier.fillMaxWidth().heightIn(max = 720.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            HoloLabel("DIPESH AI · CAPABILITIES")
                            Text("${FeatureCatalog.all.size} abilities. Tap one to open it. Say \"close\" to dismiss.", fontSize = 12.sp, color = Color.White.copy(alpha = 0.65f))
                        }
                        Text("✕", Modifier.clickable { onClose() }.padding(10.dp), color = HoloCyan, fontSize = 20.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                        var index = 0
                        FeatureCatalog.categories.forEach { cat ->
                            Text(cat.uppercase(), Modifier.padding(top = 12.dp, bottom = 6.dp), fontSize = 11.sp, letterSpacing = 1.5.sp, color = HoloMagenta, fontWeight = FontWeight.Bold)
                            FeatureCatalog.all.filter { it.category == cat }.chunked(2).forEach { pair ->
                                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    pair.forEach { f -> val i = index++; Reveal(i, Modifier.weight(1f)) { FeatureCard(f, onOpen) } }
                                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureCard(f: Feature, onOpen: (String) -> Unit) {
    Box(
        Modifier.fillMaxWidth().border(BorderStroke(1.dp, HoloCyan.copy(alpha = 0.35f)), RoundedCornerShape(12.dp))
            .background(Brush.verticalGradient(listOf(HoloCyan.copy(alpha = 0.10f), Color.Transparent)), RoundedCornerShape(12.dp))
            .let { m -> if (f.route != null) m.clickable { onOpen(f.route) } else m }.padding(10.dp)
    ) {
        Column {
            Text(f.icon, fontSize = 22.sp)
            Text(f.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
            Text(f.desc, color = Color.White.copy(alpha = 0.62f), fontSize = 11.sp, lineHeight = 14.sp, textAlign = TextAlign.Start)
        }
    }
}
