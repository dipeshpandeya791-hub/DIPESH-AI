package com.dipesh.ai

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Built-in fallback shell: runs commands with Android's own /system/bin/sh inside the app's private workspace.
 * No Termux needed. It has toybox basics (ls, cat, echo, cp, mv, mkdir, grep, sed, wc, date, ping...) but not
 * python, node or git; for those the user installs Termux (free). Runs as the app user, so it cannot touch other apps.
 */
object LocalShell {
    fun run(command: String, workdir: File, timeoutSec: Int): String {
        return try {
            workdir.mkdirs()
            val pb = ProcessBuilder("/system/bin/sh", "-c", command).directory(workdir).redirectErrorStream(false)
            val p = pb.start()
            val out = StringBuilder(); val err = StringBuilder()
            val t1 = Thread { try { out.append(p.inputStream.bufferedReader().readText()) } catch (_: Exception) {} }
            val t2 = Thread { try { err.append(p.errorStream.bufferedReader().readText()) } catch (_: Exception) {} }
            t1.start(); t2.start()
            val done = p.waitFor(timeoutSec.coerceIn(5, 120).toLong(), TimeUnit.SECONDS)
            if (!done) { p.destroyForcibly(); return "The command did not finish within $timeoutSec seconds and was stopped." }
            t1.join(1000); t2.join(1000)
            format(p.exitValue(), out.toString(), err.toString())
        } catch (e: Exception) {
            "Could not run the command: ${e.message}"
        }
    }

    fun format(code: Int, stdout: String, stderr: String): String = buildString {
        append("exit code ").append(code)
        if (stdout.isNotBlank()) append("\nstdout:\n").append(stdout.take(6000))
        if (stderr.isNotBlank()) append("\nstderr:\n").append(stderr.take(4000))
        if (code == 127 || stderr.contains("not found"))
            append("\nDiagnosis: that program is not part of Android's built-in shell. For python, node or git, install Termux (free, F-Droid) - say \"termux setup help\".")
    }

    /** Programs that only exist in Termux; used to decide whether the built-in shell is worth trying first. */
    fun needsTermux(command: String): Boolean =
        Regex("(^|[\\s;&|(])(python3?|pip3?|node|npm|git|ssh|ffmpeg|pkg|apt|proot-distro|clang|gcc)(\\s|$)").containsMatchIn(command)
}
