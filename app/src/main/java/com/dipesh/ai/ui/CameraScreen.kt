package com.dipesh.ai.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dipesh.ai.AssistantViewModel
import com.dipesh.ai.ImageUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

private suspend fun android.content.Context.cameraProvider(): ProcessCameraProvider =
    suspendCancellableCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({ cont.resume(future.get()) }, ContextCompat.getMainExecutor(this))
    }

/** Live camera preview. The user frames something, taps Capture, and the AI describes it. */
@Composable
fun CameraScreen(vm: AssistantViewModel, onClose: () -> Unit, onSent: () -> Unit) {
    val ctx = LocalContext.current
    val c = LocalDipeshColors.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var asked by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        asked = true
    }

    if (!granted) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("📷", style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(12.dp))
            Text("Camera access", style = MaterialTheme.typography.titleLarge, color = c.text)
            Spacer(Modifier.height(8.dp))
            Text(
                if (asked) "Camera permission was denied. Without it I can't see anything. You can allow it in the system settings."
                else "I need the camera to look at things and tell you what they are. Photos are only sent to the AI when you tap Capture.",
                style = MaterialTheme.typography.bodyMedium, color = c.textMuted, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            GradientButton("Allow camera", { permissionLauncher.launch(Manifest.permission.CAMERA) }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlineButton("Back", onClose, Modifier.fillMaxWidth())
        }
        return
    }

    var lens by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var torch by remember { mutableStateOf(false) }
    var question by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    val previewView = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lens) {
        val provider = ctx.cameraProvider()
        provider.unbindAll()
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        camera = try {
            provider.bindToLifecycle(
                lifecycleOwner, CameraSelector.Builder().requireLensFacing(lens).build(), preview, imageCapture
            )
        } catch (e: Exception) {
            vm.banner = "Couldn't start that camera."
            null
        }
    }
    LaunchedEffect(torch, camera) { camera?.cameraControl?.enableTorch(torch) }
    DisposableEffect(Unit) {
        onDispose { runCatching { ProcessCameraProvider.getInstance(ctx).get().unbindAll() } }
    }

    fun capture() {
        if (busy) return
        busy = true
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val file = File(File(ctx.cacheDir, "photos").apply { mkdirs() }, "look.jpg")
        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(ctx),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    scope.launch {
                        val b64 = withContext(Dispatchers.IO) { ImageUtil.encodeFile(file) }
                        busy = false
                        if (b64 == null) {
                            vm.banner = "I couldn't read that photo."
                        } else {
                            vm.send(question.ifBlank { "What is this? Identify and describe what you see, briefly." }, b64)
                            onSent()
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    busy = false
                    vm.banner = "Couldn't take the photo."
                }
            }
        )
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            GlassChip("✕  Close", false, onClose)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassChip(if (torch) "🔦 On" else "🔦 Off", torch, { torch = !torch })
                GlassChip("🔄 Flip", false, {
                    torch = false
                    lens = if (lens == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                })
            }
        }

        GlassCard(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(16.dp)
        ) {
            GlassTextField(question, { question = it }, "Ask about it (optional)")
            Spacer(Modifier.height(12.dp))
            GradientButton(
                if (busy) "Looking…" else "📷  Capture & ask DIPESH",
                { capture() }, Modifier.fillMaxWidth()
            )
        }
    }
}
