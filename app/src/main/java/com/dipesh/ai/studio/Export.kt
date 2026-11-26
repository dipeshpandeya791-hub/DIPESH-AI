package com.dipesh.ai.studio

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

/** Sharing and saving exported files (ZIP, CSV, JSON backup) with user-visible results. */
object Export {
    fun cacheFile(ctx: Context, name: String): File = File(File(ctx.cacheDir, "exports").apply { mkdirs() }, name)

    fun share(ctx: Context, file: File, mime: String) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, "Share ${file.name}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Saves into the public Downloads folder (Android 10+), else the app's own files folder. Returns a message. */
    fun saveToDownloads(ctx: Context, file: File, mime: String): String = try {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/DipeshAI")
            }
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Downloads is not available")
            ctx.contentResolver.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
            "Saved to Downloads/DipeshAI/${file.name}"
        } else {
            val dir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: ctx.filesDir
            val target = File(dir, file.name); file.copyTo(target, overwrite = true)
            "Saved to ${target.absolutePath}"
        }
    } catch (e: Exception) {
        "Couldn't save the file (${e.message}). Use Share instead."
    }
}
