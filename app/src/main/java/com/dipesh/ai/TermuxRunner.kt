package com.dipesh.ai

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Runs shell commands inside Termux (free, open source, F-Droid) via its RUN_COMMAND intent and
 * captures stdout/stderr/exit code. Termux gives a real Linux userland: python, node, git, ssh,
 * adb, ffmpeg, and `proot-distro` for full Debian/Ubuntu environments.
 */
object TermuxRunner {
    private const val PKG = "com.termux"
    const val PERM = "com.termux.permission.RUN_COMMAND"
    private const val BASH = "/data/data/com.termux/files/usr/bin/bash"
    private const val HOME = "/data/data/com.termux/files/home"
    private const val BIN = "/data/data/com.termux/files/usr/bin"
    private val counter = AtomicInteger()

    const val NOT_INSTALLED =
        "Termux is not installed. It is free: install Termux from F-Droid (f-droid.org/packages/com.termux), open it once, then try again."
    const val NO_PERMISSION =
        "DIPESH AI has no permission to run Termux commands. I opened the app's settings: go to Permissions, allow " +
            "'Run commands in Termux environment' (Additional permissions), then ask again."
    const val SETUP_HELP =
        "One-time Termux setup (all free): 1) Install Termux from F-Droid. 2) Open Termux and run: " +
            "mkdir -p ~/.termux && echo 'allow-external-apps=true' >> ~/.termux/termux.properties && termux-reload-settings. " +
            "3) Run: pkg update -y && pkg install -y git python nodejs openssh. 4) Run: termux-setup-storage. " +
            "5) In Android settings > Apps > DIPESH AI > Permissions allow 'Run commands in Termux environment'."

    fun installed(ctx: Context): Boolean = try {
        ctx.packageManager.getPackageInfo(PKG, 0); true
    } catch (e: Exception) { false }

    fun hasPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, PERM) == PackageManager.PERMISSION_GRANTED

    private fun quote(s: String) = "'" + s.replace("'", "'\\''") + "'"

    /** environment: "termux" (default) or "proot:<distro>" for a full Linux distro such as ubuntu or debian. */
    fun run(ctx: Context, command: String, workdir: String, environment: String, timeoutSec: Int): String {
        if (!installed(ctx)) return NOT_INSTALLED
        if (!hasPermission(ctx)) {
            try {
                ctx.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {}
            return NO_PERMISSION
        }
        val body = if (environment.startsWith("proot:", true)) {
            val distro = environment.substringAfter(':').trim().ifBlank { "ubuntu" }
            "proot-distro login $distro --shared-tmp -- bash -lc ${quote(command)}"
        } else command
        val action = "com.dipesh.ai.TERMUX_RESULT_${counter.incrementAndGet()}"
        val latch = CountDownLatch(1)
        val out = AtomicReference<Bundle?>(null)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                out.set(i.getBundleExtra("result")); latch.countDown()
            }
        }
        ContextCompat.registerReceiver(ctx, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
        try {
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pi = PendingIntent.getBroadcast(ctx, counter.get(), Intent(action).setPackage(ctx.packageName), flags)
            val dir = workdir.ifBlank { HOME }
            val intent = Intent("com.termux.RUN_COMMAND")
                .setClassName(PKG, "com.termux.app.RunCommandService")
                .putExtra("com.termux.RUN_COMMAND_PATH", BASH)
                .putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", "export PATH=$BIN:\$PATH; $body"))
                .putExtra("com.termux.RUN_COMMAND_WORKDIR", dir)
                .putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
                .putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", pi)
            try {
                ctx.startForegroundService(intent)
            } catch (e: SecurityException) {
                return "Termux refused the command (${e.message}). $SETUP_HELP"
            } catch (e: Exception) {
                return "Could not reach Termux: ${e.message}. Open Termux once so it can start, then retry. $SETUP_HELP"
            }
            if (!latch.await(timeoutSec.coerceIn(5, 300).toLong(), TimeUnit.SECONDS))
                return "The command did not finish within $timeoutSec seconds (it may still be running in Termux). " +
                    "Retry with a longer timeout, or run it in the background with '&' and poll its output file."
            val r = out.get() ?: return "Termux returned no result. Check allow-external-apps=true in ~/.termux/termux.properties."
            val stdout = r.getString("stdout").orEmpty()
            val stderr = r.getString("stderr").orEmpty()
            val code = if (r.containsKey("exitCode")) r.getInt("exitCode") else -1
            val errMsg = r.getString("errmsg").orEmpty()
            return buildString {
                append("exit code ").append(code)
                if (errMsg.isNotBlank()) append("\nTermux error: ").append(errMsg)
                if (stdout.isNotBlank()) append("\nstdout:\n").append(stdout.take(6000))
                if (stderr.isNotBlank()) append("\nstderr:\n").append(stderr.take(4000))
                hint(code, stderr + errMsg)?.let { append("\nDiagnosis: ").append(it) }
            }
        } finally {
            try { ctx.unregisterReceiver(receiver) } catch (_: Exception) {}
        }
    }

    /** Self-diagnosis: turns common failures into a concrete fix the model can apply and retry. */
    private fun hint(code: Int, text: String): String? = when {
        code == 127 || text.contains("command not found") ->
            "a program is missing. Install it with `pkg install -y <package>` (python, nodejs, git, openssh, clang, ffmpeg) and retry."
        text.contains("No such file or directory") -> "a path is wrong. List the folder first with ls, then fix the path."
        text.contains("Permission denied") && text.contains("storage") ->
            "run `termux-setup-storage` once in Termux to allow /sdcard access."
        text.contains("ModuleNotFoundError") -> "a Python package is missing. Run `pip install <name>` and retry."
        text.contains("Cannot find module") -> "a Node package is missing. Run `npm install <name>` and retry."
        text.contains("Could not resolve host") || text.contains("Network is unreachable") ->
            "Termux has no internet. Check Wi-Fi/mobile data."
        text.contains("proot-distro") && text.contains("not installed") ->
            "install the distro first: `pkg install -y proot-distro && proot-distro install <distro>`."
        else -> null
    }
}
