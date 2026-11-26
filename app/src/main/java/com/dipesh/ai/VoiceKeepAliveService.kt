package com.dipesh.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.ServiceCompat

/**
 * Keeps the microphone usable while the app is in the background or the screen is off. Android only lets an
 * app listen in the background when it runs a visible foreground service of type "microphone", so while
 * hands-free or voice conversation is ON this shows a "listening" notification with a Stop button. Turning
 * the mic off (in the app or from the notification) stops the service.
 */
class VoiceKeepAliveService : Service() {
    private var wake: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Handler(Looper.getMainLooper()).post { onStopRequested?.invoke() }
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            startListeningForeground()
        } catch (e: Exception) {
            // Missing microphone permission or background-start limits must never crash the app.
            stopSelf()
            return START_NOT_STICKY
        }
        return START_NOT_STICKY
    }

    private fun startListeningForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Voice listening", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(
            this, 1, Intent(this, VoiceKeepAliveService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("DIPESH AI is listening")
            .setContentText("Microphone is on. Tap Stop to turn it off.")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(
                android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_media_pause), "Stop", stop).build())
            .build()
        ServiceCompat.startForeground(this, ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        if (wake == null) {
            wake = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "dipesh:voice").apply { setReferenceCounted(false); acquire(6 * 60 * 60 * 1000L) }
        }
    }

    override fun onDestroy() {
        try { wake?.takeIf { it.isHeld }?.release() } catch (_: Exception) {}
        wake = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "voice_listening"
        private const val ID = 4107
        private const val ACTION_STOP = "com.dipesh.ai.STOP_LISTENING"

        /** Set by the view model; called when the user taps Stop on the notification. */
        @Volatile var onStopRequested: (() -> Unit)? = null

        fun start(ctx: Context) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) return
            try { ctx.startForegroundService(Intent(ctx, VoiceKeepAliveService::class.java)) } catch (_: Exception) {}
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, VoiceKeepAliveService::class.java))
        }
    }
}
