package com.dipesh.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat
import androidx.core.content.IntentCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import java.io.File
import kotlin.math.max
import kotlin.math.min

/** Hand-off between the capture service and the rest of the app. */
object ScreenCapture {
    @Volatile
    var prompt: String = "Here is a screenshot of my phone screen. Describe what is on it and help me with it."

    /** Emits the screenshot file, or null if capturing failed. */
    val results = MutableSharedFlow<File?>(extraBufferCapacity = 1)
}

/**
 * Takes ONE screenshot with Android's MediaProjection after the user has explicitly allowed it in the
 * system dialog. It runs as a short-lived foreground service (required by Android) and stops itself.
 */
class ScreenCaptureService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Screen capture", NotificationManager.IMPORTANCE_LOW))
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("DIPESH AI")
            .setContentText("Capturing your screen once…")
            .build()
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)

        val code = intent?.getIntExtra(EXTRA_CODE, 0) ?: 0
        val data = intent?.let { IntentCompat.getParcelableExtra(it, EXTRA_DATA, Intent::class.java) }
        if (data == null) {
            finish(null)
            return START_NOT_STICKY
        }
        try {
            val p = getSystemService(MediaProjectionManager::class.java).getMediaProjection(code, data)
            // Android 14+ requires a callback to be registered before creating the display.
            p.registerCallback(object : MediaProjection.Callback() {}, handler)
            projection = p
            val delay = intent.getLongExtra(EXTRA_DELAY, 0L)
            handler.postDelayed({ capture(p) }, delay)
        } catch (e: Exception) {
            finish(null)
        }
        return START_NOT_STICKY
    }

    private fun capture(p: MediaProjection) {
        try {
            val dm = resources.displayMetrics
            val scale = min(1f, 1280f / max(dm.widthPixels, dm.heightPixels))
            val w = (dm.widthPixels * scale).toInt()
            val h = (dm.heightPixels * scale).toInt()
            val reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
            val display = p.createVirtualDisplay(
                "dipesh-capture", w, h, dm.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.surface, null, handler
            )
            fun attempt(n: Int) {
                var file: File? = null
                try {
                    val image = reader.acquireLatestImage()
                    if (image != null) {
                        file = save(image, w, h)
                        image.close()
                    }
                } catch (_: Exception) {
                }
                if (file == null && n < 6) {
                    handler.postDelayed({ attempt(n + 1) }, 300)
                    return
                }
                display.release()
                reader.close()
                finish(file)
            }
            handler.postDelayed({ attempt(0) }, 500)
        } catch (e: Exception) {
            finish(null)
        }
    }

    private fun save(image: Image, w: Int, h: Int): File {
        val plane = image.planes[0]
        val rowPadding = plane.rowStride - plane.pixelStride * w
        val full = Bitmap.createBitmap(w + rowPadding / plane.pixelStride, h, Bitmap.Config.ARGB_8888)
        full.copyPixelsFromBuffer(plane.buffer)
        val cropped = Bitmap.createBitmap(full, 0, 0, w, h)
        val dir = File(cacheDir, "photos").apply { mkdirs() }
        val file = File(dir, "screen.jpg")
        file.outputStream().use { cropped.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        return file
    }

    private fun finish(file: File?) {
        projection?.stop()
        projection = null
        ScreenCapture.results.tryEmit(file)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        projection?.stop()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        const val EXTRA_DELAY = "delay"
        private const val CHANNEL = "screen_capture"
        private const val NOTIFICATION_ID = 42
    }
}
