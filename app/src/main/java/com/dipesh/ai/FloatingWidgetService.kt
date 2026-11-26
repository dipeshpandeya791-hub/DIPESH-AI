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
import com.dipesh.ai.pet.PetPrefs

/** Shows the floating Yeti pet (transparent, creature only) over other apps (needs the "Display over other apps" permission). */
class FloatingWidgetService : Service() {
    private var view: com.dipesh.ai.pet.YetiView? = null
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
            .setContentTitle("DIPESH AI Yeti pet").setContentText("Tap the Yeti to talk. Drag to move it. Tap Hide to put it away.")
            .setContentIntent(open).setOngoing(true)
            .addAction(Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel), "Hide", stop).build())
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(ID, n)
    }

    private val screenReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.action == Intent.ACTION_SCREEN_OFF) view?.pause() else if (i.action == Intent.ACTION_SCREEN_ON) view?.resume()
        }
    }
    private val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        // size/headphone/behaviour changes apply live (position and on/off writes must not rebuild the view)
        if (key != "size" && key != "headphones" && key != "wander" && key != "sleepy") return@OnSharedPreferenceChangeListener
        val manager = wm
        if (manager != null && view != null) { try { manager.removeView(view) } catch (_: Exception) {}; view = null; try { addView() } catch (_: Exception) {} }
    }

    private fun bounds(): IntArray {
        val m = resources.displayMetrics
        return intArrayOf(m.widthPixels, m.heightPixels)
    }

    private fun clampPos(lp: WindowManager.LayoutParams, w: Int, h: Int) {
        val (sw, sh) = bounds().let { it[0] to it[1] }
        val top = (28 * resources.displayMetrics.density).toInt()
        val bottom = (24 * resources.displayMetrics.density).toInt()
        val (x, y) = PetPrefs.clamp(lp.x, lp.y, w, h, sw, sh, top, bottom)
        lp.x = x; lp.y = y
    }

    private fun addView() {
        val manager = getSystemService(WINDOW_SERVICE) as WindowManager
        wm = manager
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val d = resources.displayMetrics.density
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT // transparent window: only the Yeti is visible
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("x", resources.displayMetrics.widthPixels - (120 * d).toInt())
            y = prefs.getInt("y", (300 * d).toInt())
        }
        params = lp
        val sizeDp = PetPrefs.sizeDp(this)
        val w = (sizeDp * d).toInt(); val h = (w * 112f / 70f).toInt()
        clampPos(lp, w, h)
        val v = YetiView(
            this, sizeDp, PetPrefs.headphones(this),
            onTap = {
                // Prefer talking inside the running app (no screen switch); open the app only when it is not running yet.
                val handled = try { WidgetBus.onTap?.invoke() ?: false } catch (_: Exception) { false }
                if (!handled) startActivity(Intent(this, MainActivity::class.java).putExtra("listen", true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            },
            onDrag = { dx, dy -> lp.x += dx; lp.y += dy; clampPos(lp, w, h); try { manager.updateViewLayout(view, lp) } catch (_: Exception) {} },
            onDragEnd = { prefs.edit().putInt("x", lp.x).putInt("y", lp.y).apply() },
            onWalk = { dxPx ->
                val before = lp.x
                lp.x += dxPx; clampPos(lp, w, h)
                val hit = lp.x == before || lp.x == 0 || lp.x == bounds()[0] - w
                try { manager.updateViewLayout(view, lp) } catch (_: Exception) {}
                hit
            }
        )
        v.wander = PetPrefs.wander(this); v.sleepWhenIdle = PetPrefs.sleepy(this)
        view = v
        manager.addView(v, lp)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // rotation: keep the Yeti on screen in the new orientation
        val lp = params ?: return; val v = view ?: return
        clampPos(lp, v.width.coerceAtLeast(1), v.height.coerceAtLeast(1))
        try { wm?.updateViewLayout(v, lp) } catch (_: Exception) {}
    }

    override fun onCreate() {
        super.onCreate()
        androidx.core.content.ContextCompat.registerReceiver(this, screenReceiver, android.content.IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON) }, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        getSharedPreferences(PREFS, MODE_PRIVATE).registerOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onDestroy() {
        params?.let { getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt("x", it.x).putInt("y", it.y).apply() }
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        getSharedPreferences(PREFS, MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(prefListener)
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
