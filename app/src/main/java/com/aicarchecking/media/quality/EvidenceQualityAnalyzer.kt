package com.aicarchecking.media.quality

import android.graphics.Bitmap
import com.aicarchecking.domain.model.EvidenceQuality

data class QualityResult(val quality: EvidenceQuality, val notes: List<String>)

/** Pixel statistics extracted from a downscaled image. Kept separate so grading is unit-testable. */
data class ImageStats(
    val width: Int,
    val height: Int,
    val meanLuma: Double,
    val overexposedFraction: Double,
    val underexposedFraction: Double,
    val sharpness: Double,
)

/**
 * Local, heuristic evidence-quality check that runs before any AI call (spec §41).
 * It never inspects content; it only judges whether an image is usable.
 */
object EvidenceQualityAnalyzer {

    fun measure(bitmap: Bitmap, originalWidth: Int, originalHeight: Int): ImageStats {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val luma = DoubleArray(pixels.size)
        var sum = 0.0
        var over = 0
        var under = 0
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val y = 0.299 * r + 0.587 * g + 0.114 * b
            luma[i] = y
            sum += y
            if (y > 245) over++
            if (y < 15) under++
        }
        // Variance of the Laplacian: low values mean a blurry image.
        var lapSum = 0.0
        var lapSq = 0.0
        var n = 0
        for (yy in 1 until h - 1) {
            for (xx in 1 until w - 1) {
                val i = yy * w + xx
                val lap = luma[i - w] + luma[i + w] + luma[i - 1] + luma[i + 1] - 4 * luma[i]
                lapSum += lap
                lapSq += lap * lap
                n++
            }
        }
        val mean = if (n > 0) lapSum / n else 0.0
        val variance = if (n > 0) lapSq / n - mean * mean else 0.0
        return ImageStats(
            width = originalWidth,
            height = originalHeight,
            meanLuma = if (pixels.isNotEmpty()) sum / pixels.size else 0.0,
            overexposedFraction = if (pixels.isNotEmpty()) over.toDouble() / pixels.size else 0.0,
            underexposedFraction = if (pixels.isNotEmpty()) under.toDouble() / pixels.size else 0.0,
            sharpness = variance,
        )
    }

    fun grade(stats: ImageStats): QualityResult {
        val notes = mutableListOf<String>()
        var score = 3 // 3 good, 2 acceptable, 1 poor, 0 insufficient

        val minSide = minOf(stats.width, stats.height)
        when {
            minSide < 320 -> { notes += "Resolution is too low."; score = 0 }
            minSide < 720 -> { notes += "Resolution is low; move closer or use the main camera."; score = minOf(score, 2) }
        }
        when {
            stats.meanLuma < 25 -> { notes += "Image is too dark to analyze."; score = 0 }
            stats.meanLuma < 55 -> { notes += "Image is dark; capture in better lighting."; score = minOf(score, 1) }
            stats.meanLuma > 235 -> { notes += "Image is overexposed."; score = 0 }
        }
        when {
            stats.overexposedFraction > 0.40 -> { notes += "Strong glare/reflections cover much of the image."; score = minOf(score, 1) }
            stats.overexposedFraction > 0.15 -> { notes += "Some glare or reflections detected."; score = minOf(score, 2) }
        }
        when {
            stats.sharpness < 12 -> { notes += "Image appears blurry. Hold the phone steady and retake."; score = 0 }
            stats.sharpness < 40 -> { notes += "Image is slightly soft/blurry."; score = minOf(score, 1) }
            stats.sharpness < 90 -> score = minOf(score, 2)
        }
        val quality = when (score) {
            3 -> EvidenceQuality.GOOD
            2 -> EvidenceQuality.ACCEPTABLE
            1 -> EvidenceQuality.POOR
            else -> EvidenceQuality.INSUFFICIENT
        }
        return QualityResult(quality, notes)
    }

    fun gradeDuration(durationMs: Long?, idealMinSec: Int, idealMaxSec: Int): QualityResult {
        val sec = (durationMs ?: 0L) / 1000.0
        return when {
            durationMs == null -> QualityResult(EvidenceQuality.POOR, listOf("Duration could not be read."))
            sec < 2 -> QualityResult(EvidenceQuality.INSUFFICIENT, listOf("Recording is too short to analyze."))
            sec < idealMinSec -> QualityResult(EvidenceQuality.POOR, listOf("Recording is shorter than the recommended $idealMinSec seconds."))
            sec > idealMaxSec * 4 -> QualityResult(EvidenceQuality.ACCEPTABLE, listOf("Long recording; only representative parts will be analyzed."))
            else -> QualityResult(EvidenceQuality.GOOD, emptyList())
        }
    }

    /** Combines several grades, taking the more conservative result. */
    fun combine(results: List<QualityResult>): QualityResult {
        if (results.isEmpty()) return QualityResult(EvidenceQuality.NOT_ASSESSED, emptyList())
        val order = listOf(EvidenceQuality.INSUFFICIENT, EvidenceQuality.POOR, EvidenceQuality.ACCEPTABLE, EvidenceQuality.GOOD)
        val usableCount = results.count { it.quality.usableForAnalysis }
        val worst = results.minByOrNull { order.indexOf(it.quality).let { i -> if (i < 0) 0 else i } }!!.quality
        // For videos, a few unusable frames among good ones should not reject the whole clip.
        val quality = if (worst == EvidenceQuality.INSUFFICIENT && usableCount >= results.size / 2 && results.size > 2) {
            EvidenceQuality.POOR
        } else worst
        return QualityResult(quality, results.flatMap { it.notes }.distinct())
    }
}
