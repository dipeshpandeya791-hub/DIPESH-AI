package com.dipesh.ai.studio

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File
import kotlin.math.max

/**
 * On-device slideshow video maker using only Android's built-in free media APIs (MediaCodec H.264 + MediaMuxer MP4).
 * 720x1280 (9:16), 30 fps, fade in/out and a slow zoom on pictures, captions on each slide. No audio track.
 */
object VideoMaker {
    class Slide(val text: String, val image: Bitmap?)

    const val WIDTH = 720
    const val HEIGHT = 1280
    private const val FPS = 30

    fun frameCount(slides: Int, secondsPerSlide: Float) = slides * max(1, (secondsPerSlide * FPS).toInt())

    fun render(out: File, slides: List<Slide>, secondsPerSlide: Float, progress: (Float) -> Unit) {
        require(slides.isNotEmpty()) { "Add at least one picture or line of text." }
        val perSlide = max(1, (secondsPerSlide * FPS).toInt())
        val total = slides.size * perSlide
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, WIDTH, HEIGHT).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 4_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        out.parentFile?.mkdirs(); if (out.exists()) out.delete()
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        var muxer: MediaMuxer? = null
        var surface: android.view.Surface? = null
        var muxerStarted = false
        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            surface = encoder.createInputSurface()
            encoder.start()
            muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var track = -1
            var written = 0L
            val info = MediaCodec.BufferInfo()

            fun drain(untilEnd: Boolean) {
                var idleSpins = 0
                while (true) {
                    val idx = encoder.dequeueOutputBuffer(info, if (untilEnd) 10_000L else 0L)
                    when {
                        idx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            if (!untilEnd) return
                            if (++idleSpins > 600) throw IllegalStateException("The phone's video encoder stopped responding.")
                        }
                        idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            track = muxer!!.addTrack(encoder.outputFormat); muxer!!.start(); muxerStarted = true
                        }
                        idx >= 0 -> {
                            idleSpins = 0
                            val buf = encoder.getOutputBuffer(idx)
                            if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                            if (info.size > 0 && muxerStarted && buf != null) {
                                info.presentationTimeUs = written * 1_000_000L / FPS // steady timeline, one tick per frame
                                buf.position(info.offset); buf.limit(info.offset + info.size)
                                muxer!!.writeSampleData(track, buf, info)
                                written++
                            }
                            encoder.releaseOutputBuffer(idx, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                        }
                    }
                }
            }

            val bg = Paint().apply { shader = LinearGradient(0f, 0f, 0f, HEIGHT.toFloat(), Color.parseColor("#05070F"), Color.parseColor("#0E1B3D"), Shader.TileMode.CLAMP) }
            val shade = Paint().apply { shader = LinearGradient(0f, HEIGHT * 0.55f, 0f, HEIGHT.toFloat(), Color.TRANSPARENT, Color.argb(190, 0, 0, 0), Shader.TileMode.CLAMP) }
            val imgPaint = Paint(Paint.FILTER_BITMAP_FLAG)
            val accent = Paint().apply { color = Color.parseColor("#00E5FF"); strokeWidth = 6f }
            var frame = 0
            for (slide in slides) {
                val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 54f; isFakeBoldText = true; setShadowLayer(8f, 0f, 2f, Color.BLACK) }
                val layout = StaticLayout.Builder.obtain(slide.text.take(160), 0, slide.text.take(160).length, tp, WIDTH - 112)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER).build()
                for (i in 0 until perSlide) {
                    val fade = minOf(1f, i / 10f, (perSlide - 1 - i) / 10f).coerceIn(0f, 1f)
                    val c: Canvas = surface.lockHardwareCanvas()
                    try {
                        c.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), bg)
                        slide.image?.let { bmp ->
                            val zoom = 1f + 0.08f * i / perSlide
                            val scale = max(WIDTH / bmp.width.toFloat(), HEIGHT / bmp.height.toFloat()) * zoom
                            val m = Matrix().apply {
                                postScale(scale, scale)
                                postTranslate((WIDTH - bmp.width * scale) / 2f, (HEIGHT - bmp.height * scale) / 2f)
                            }
                            imgPaint.alpha = (255 * fade).toInt()
                            c.drawBitmap(bmp, m, imgPaint)
                            c.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), shade)
                        }
                        if (slide.text.isNotBlank()) {
                            val y = if (slide.image != null) HEIGHT - 170f - layout.height else (HEIGHT - layout.height) / 2f
                            c.save(); c.translate(56f, y)
                            tp.alpha = (255 * fade).toInt(); layout.draw(c); c.restore()
                            accent.alpha = (255 * fade).toInt()
                            c.drawLine(56f, y - 24f, 56f + 120f, y - 24f, accent)
                        }
                    } finally {
                        surface.unlockCanvasAndPost(c)
                    }
                    drain(false)
                    frame++
                    if (frame % 10 == 0) progress(frame / total.toFloat())
                }
            }
            encoder.signalEndOfInputStream()
            drain(true)
            progress(1f)
        } finally {
            try { encoder.stop() } catch (_: Exception) {}
            encoder.release()
            surface?.release()
            try { if (muxerStarted) muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
        }
    }
}
