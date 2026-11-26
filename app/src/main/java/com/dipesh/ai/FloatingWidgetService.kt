package com.dipesh.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager

/** Shows the floating glass assistant icon over other apps (needs the "Display over other apps" permission). */
class FloatingWidgetService : Service() {
    private var view: GlassOrbView? = null
    private var wm: WindowManager? = null
    private var params: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!canDraw(this)) { setEnabled(this, false); stopSelf(); return START_NOT_STICKY }
        try {
            promote()
            if (view == null) addView()
        } catch (e: Exception) {
            setEnabled(this, false); stopSelf()
        }
        return START_STICKY
    }

    private fun promote() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Floating assistant", NotificationManager.IMPORTANCE_MIN))
        val open = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 3, Intent(this, FloatingWidgetService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("DIPESH AI floating assistant").setContentText("Tap the glass orb to talk. Long-press the orb to hide it.")
            .setContentIntent(open).setOngoing(true)
            .addAction(Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel), "Hide", stop).build())
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(ID, n)
    }

    private fun addView() {
        val manager = getSystemService(WINDOW_SERVICE) as WindowManager
        wm = manager
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("x", resources.displayMetrics.widthPixels - (88 * resources.displayMetrics.density).toInt())
            y = prefs.getInt("y", (220 * resources.displayMetrics.density).toInt())
        }
        params = lp
        val v = GlassOrbView(
            this,
            status = { WidgetBus.status() },
            onTap = {
                startActivity(Intent(this, MainActivity::class.java).putExtra("listen", true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            },
            onLongPress = { setEnabled(this, false); stopSelf() },
            onDrag = { dx, dy -> lp.x += dx; lp.y += dy; try { manager.updateViewLayout(view, lp) } catch (_: Exception) {} },
            onDragEnd = {
                // snap to the nearest screen edge and remember the position
                val w = resources.displayMetrics.widthPixels
                val size = view?.width ?: 0
                lp.x = if (lp.x + size / 2 < w / 2) 0 else w - size
                try { manager.updateViewLayout(view, lp) } catch (_: Exception) {}
                prefs.edit().putInt("x", lp.x).putInt("y", lp.y).apply()
            }
        )
        view = v
        manager.addView(v, lp)
    }

    override fun onDestroy() {
        try { view?.let { wm?.removeView(it) } } catch (_: Exception) {}
        view = null
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) { super.onTaskRemoved(rootIntent) }

    companion object {
        private const val CHANNEL = "floating_widget"
        private const val ID = 4108
        private const val PREFS = "widget"
        private const val ACTION_STOP = "com.dipesh.ai.HIDE_WIDGET"

        fun canDraw(ctx: Context) = Settings.canDrawOverlays(ctx)
        fun enabled(ctx: Context) = ctx.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("on", false)
        fun setEnabled(ctx: Context, on: Boolean) { ctx.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("on", on).apply() }

        fun overlaySettingsIntent(ctx: Context) =
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        /** @return true when started; false when the overlay permission is still needed. */
        fun start(ctx: Context): Boolean {
            if (!canDraw(ctx)) return false
            setEnabled(ctx, true)
            try { ctx.startForegroundService(Intent(ctx, FloatingWidgetService::class.java)) } catch (_: Exception) { return false }
            return true
        }

        fun stop(ctx: Context) {
            setEnabled(ctx, false)
            ctx.stopService(Intent(ctx, FloatingWidgetService::class.java))
        }
    }
}

/** Lets the floating icon read the assistant state without holding the view model. */
object WidgetBus {
    @Volatile var provider: (() -> String)? = null
    fun status(): String = try { provider?.invoke() ?: "IDLE" } catch (_: Exception) { "IDLE" }
}
