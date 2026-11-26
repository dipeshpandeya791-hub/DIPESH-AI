package com.dipesh.ai.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.dipesh.ai.studio.Calc
import com.dipesh.ai.studio.DateMath
import com.dipesh.ai.studio.Export
import com.dipesh.ai.studio.PasswordGen
import com.dipesh.ai.studio.TextTools
import com.dipesh.ai.studio.Units
import com.dipesh.ai.studio.VideoMaker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ============================================================ Offline utilities
@Composable
fun OfflineToolsScreen(onBack: () -> Unit) {
    val c = LocalDipeshColors.current
    val clipboard = LocalClipboardManager.current
    val tools = listOf("Calculator", "Converter", "Password", "Text", "Dates")
    var tool by remember { mutableStateOf(tools[0]) }
    var input by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var length by remember { mutableStateOf("16") }

    fun hint() = when (tool) {
        "Calculator" -> "Expression (e.g. (12+8)*3, sqrt(81), 15%*200)"
        "Converter" -> "e.g. 10 km to mi, 100 c to f, 5 kg to lb"
        "Password" -> "Nothing to type; choose a length below"
        "Text" -> "Type or paste text"
        else -> "2026-12-25 or 2026-01-01 to 2026-12-25"
    }

    fun run(decodeBase64: Boolean = false, encodeBase64: Boolean = false) {
        error = null; result = null
        try {
            result = when {
                decodeBase64 -> TextTools.base64(input, true)
                encodeBase64 -> TextTools.base64(input, false)
                tool == "Calculator" -> Calc.format(Calc.eval(input))
                tool == "Converter" -> Units.convertText(input)
                tool == "Password" -> PasswordGen.generate(length.toIntOrNull() ?: 16)
                tool == "Text" -> TextTools.stats(input)
                else -> DateMath.describe(input)
            }
        } catch (e: Exception) {
            error = when (e) { is IllegalArgumentException -> e.message; else -> "That input isn't valid." } ?: "That input isn't valid."
        }
    }

    Page {
        ScreenHeader("Offline utilities", onBack)
        Text("These work with no internet connection.", style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tools.forEach { t -> GlassChip(t, tool == t, { tool = t; input = ""; result = null; error = null }) }
        }
        Spacer(Modifier.height(10.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            if (tool == "Password") GlassTextField(length, { length = it.filter { ch -> ch.isDigit() }.take(2) }, "Length (8-64)")
            else GlassTextField(input, { input = it }, hint(), singleLine = tool != "Text", minLines = if (tool == "Text") 4 else 1)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GradientButton(if (tool == "Password") "Generate" else if (tool == "Text") "Count" else "Calculate", { run() })
                if (tool == "Text") {
                    OutlineButton("Base64 encode", { run(encodeBase64 = true) })
                    OutlineButton("Base64 decode", { run(decodeBase64 = true) })
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        result?.let { r ->
            GlassCard(Modifier.fillMaxWidth()) {
                Text(r, color = c.text, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlineButton("Copy", { clipboard.setText(AnnotatedString(r)) })
            }
        }
        error?.let { Text(it, Modifier.padding(top = 4.dp), color = c.danger, style = MaterialTheme.typography.bodyMedium) }
        if (tool == "Converter") Text("Units: ${Units.supported}", Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
    }
}

// ============================================================ On-device video maker
private fun loadBitmap(ctx: android.content.Context, uri: Uri, max: Int = 1280): Bitmap? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / sample > max * 2 || bounds.outHeight / sample > max * 2) sample *= 2
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
} catch (_: Exception) { null }

@Composable
fun VideoMakerScreen(onBack: () -> Unit) {
    val c = LocalDipeshColors.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var lines by remember { mutableStateOf("") }
    var images by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var seconds by remember { mutableStateOf(3f) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var message by remember { mutableStateOf<String?>(null) }
    var outFile by remember { mutableStateOf<File?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        images = uris.take(20)
    }

    Page {
        ScreenHeader("Video Maker", onBack)
        Text("Make a vertical (9:16) slideshow video right on your phone using Android's built-in encoder. One slide per line of text; add pictures to pair with the lines. No audio.",
            style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
        Spacer(Modifier.height(10.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            GlassTextField(lines, { lines = it }, "Slide captions, one per line", singleLine = false, minLines = 4)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlineButton(if (images.isEmpty()) "Pick pictures (optional)" else "${images.size} picture(s) · change", { picker.launch(arrayOf("image/*")) })
                listOf(2f, 3f, 5f).forEach { s -> GlassChip("${s.toInt()} s / slide", seconds == s, { seconds = s }) }
            }
            Spacer(Modifier.height(12.dp))
            GradientButton(if (busy) "Rendering ${(progress * 100).toInt()}%" else "Create video", {
                if (busy) return@GradientButton
                val captions = lines.lines().map { it.trim() }.filter { it.isNotEmpty() }.take(20)
                val count = maxOf(captions.size, images.size)
                if (count == 0) { message = "Add at least one caption or picture first."; return@GradientButton }
                busy = true; progress = 0f; outFile = null; message = "Rendering…"
                scope.launch {
                    try {
                        val file = Export.cacheFile(ctx, "dipesh-video-${System.currentTimeMillis()}.mp4")
                        withContext(Dispatchers.Default) {
                            val slides = (0 until count).map { i -> VideoMaker.Slide(captions.getOrNull(i).orEmpty(), images.getOrNull(i)?.let { loadBitmap(ctx, it) }) }
                            try { VideoMaker.render(file, slides, seconds) { progress = it } } finally { slides.forEach { it.image?.recycle() } }
                        }
                        outFile = file; message = "Done: ${count} slide(s), ${(count * seconds).toInt()} seconds."
                    } catch (e: Throwable) {
                        message = "Couldn't create the video (${e.message?.take(140)}). Some phones can't encode this size; try fewer or smaller pictures."
                    } finally { busy = false }
                }
            })
        }
        message?.let { Text(it, Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodyMedium, color = c.cyan) }
        outFile?.let { f ->
            Spacer(Modifier.height(10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GradientButton("Share video", { Export.share(ctx, f, "video/mp4") })
                OutlineButton("Save to Downloads", { message = Export.saveToDownloads(ctx, f, "video/mp4") })
            }
        }
        Text("Uses the free MediaCodec H.264 encoder built into Android. Video quality and support depend on your phone.",
            Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
    }
}
