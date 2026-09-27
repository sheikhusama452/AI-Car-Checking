package com.aicarchecking.media.video

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.ParcelFileDescriptor
import java.io.File

data class MediaMetadata(val durationMs: Long?, val width: Int?, val height: Int?, val hasAudio: Boolean)

data class ExtractedFrame(val index: Int, val timestampMs: Long, val bitmap: Bitmap)

/**
 * Reads metadata and representative frames from video/audio using MediaMetadataRetriever,
 * which decodes single frames on demand — the full video is never loaded into memory.
 */
object MediaInspector {

    fun metadata(file: File): MediaMetadata {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(file.absolutePath)
            val duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            var width = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            var height = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            val rotation = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rotation == 90 || rotation == 270) { val t = width; width = height; height = t }
            val hasAudio = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
            MediaMetadata(duration, width, height, hasAudio)
        } finally {
            runCatching { r.release() }
        }
    }

    /**
     * Extracts [count] frames evenly spread across the clip (skipping the very start/end),
     * scaled so the longest side is <= [maxDim].
     */
    fun extractFrames(
        file: File,
        count: Int,
        maxDim: Int,
        isCancelled: () -> Boolean = { false },
        onFrame: (ExtractedFrame) -> Unit,
    ) {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(file.absolutePath)
            val duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: return
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: maxDim
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: maxDim
            val ratio = minOf(1f, maxDim.toFloat() / maxOf(w, h))
            for (i in 0 until count) {
                if (isCancelled()) return
                val t = duration * (i + 1) / (count + 1)
                val frame = if (Build.VERSION.SDK_INT >= 27) {
                    r.getScaledFrameAtTime(
                        t * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        (w * ratio).toInt().coerceAtLeast(1), (h * ratio).toInt().coerceAtLeast(1),
                    )
                } else {
                    r.getFrameAtTime(t * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } ?: continue
                onFrame(ExtractedFrame(i, t, frame))
            }
        } finally {
            runCatching { r.release() }
        }
    }

    fun thumbnail(file: File, maxDim: Int): Bitmap? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(file.absolutePath)
            val frame = r.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: r.frameAtTime
            frame?.let { com.aicarchecking.media.image.ImageUtils.scaleDown(it, maxDim) }
        } catch (e: Exception) {
            null
        } finally {
            runCatching { r.release() }
        }
    }

    /** Renders the first page of a PDF document (e.g. a service invoice) to a bitmap. */
    fun renderPdfFirstPage(file: File, maxDim: Int): Bitmap? = runCatching {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                if (renderer.pageCount == 0) return@use null
                renderer.openPage(0).use { page ->
                    val scale = maxDim.toFloat() / maxOf(page.width, page.height)
                    val bmp = Bitmap.createBitmap(
                        (page.width * scale).toInt().coerceAtLeast(1), (page.height * scale).toInt().coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888,
                    )
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                }
            }
        }
    }.getOrNull()
}
