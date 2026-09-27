package com.aicarchecking.media.work

import android.content.Context
import android.graphics.Bitmap
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.aicarchecking.AiCarCheckingApp
import com.aicarchecking.domain.model.EvidenceQuality
import com.aicarchecking.domain.model.EvidenceType
import com.aicarchecking.domain.model.InspectionStep
import com.aicarchecking.domain.model.MediaAsset
import com.aicarchecking.domain.model.ProcessingStatus
import com.aicarchecking.media.image.ImageUtils
import com.aicarchecking.media.quality.EvidenceQualityAnalyzer
import com.aicarchecking.media.quality.QualityResult
import com.aicarchecking.media.video.MediaInspector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Background media pipeline (spec §42, §65): metadata → thumbnail → quality → representative frames
 * → on-device OCR where relevant. Nothing is uploaded here.
 */
class MediaProcessingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val container get() = (applicationContext as AiCarCheckingApp).container

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val mediaId = inputData.getString(KEY_MEDIA_ID) ?: return@withContext Result.failure()
        val repo = container.evidenceRepository
        val asset = repo.get(mediaId) ?: return@withContext Result.failure()
        val file = File(asset.localPath)
        try {
            if (!file.exists()) {
                repo.save(asset.copy(processingStatus = ProcessingStatus.FAILED, qualityNotes = listOf("File is missing.")))
                return@withContext Result.failure()
            }
            progress(asset, 5)
            val processed = when {
                asset.isImage -> processImage(asset, file)
                asset.isVideo -> processVideo(asset, file)
                asset.isAudio -> processAudio(asset, file)
                asset.mimeType == "application/pdf" -> processPdf(asset, file)
                else -> asset.copy(quality = EvidenceQuality.INSUFFICIENT, qualityNotes = listOf("Unsupported media type."))
            }
            if (isStopped) {
                repo.save(processed.copy(processingStatus = ProcessingStatus.CANCELLED))
                return@withContext Result.failure()
            }
            repo.save(processed.copy(processingStatus = ProcessingStatus.COMPLETED, processingProgress = 100))
            Result.success()
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                container.db.mediaAssetDao().updateProcessing(mediaId, ProcessingStatus.CANCELLED, 0)
            }
            throw e
        } catch (e: Exception) {
            repo.get(mediaId)?.let {
                repo.save(
                    it.copy(
                        processingStatus = ProcessingStatus.FAILED,
                        qualityNotes = listOf("We couldn't process this file. Try retrying or capture it again."),
                    )
                )
            }
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    private suspend fun progress(asset: MediaAsset, pct: Int) {
        container.db.mediaAssetDao().updateProcessing(asset.id, ProcessingStatus.PROCESSING, pct)
        setProgress(workDataOf(KEY_PROGRESS to pct))
    }

    private suspend fun processImage(asset: MediaAsset, file: File): MediaAsset {
        val (w, h) = ImageUtils.dimensions(file) ?: (0 to 0)
        val thumb = ImageUtils.decodeSampled(file, THUMB_DIM)?.let { bmp ->
            val out = ImageUtils.saveJpeg(bmp, container.storage.newThumbnailFile(asset.id), 80)
            bmp.recycle()
            out.absolutePath
        }
        progress(asset, 40)
        val analysisBmp = ImageUtils.decodeSampled(file, ANALYSIS_DIM)
            ?: return asset.copy(quality = EvidenceQuality.INSUFFICIENT, qualityNotes = listOf("Image could not be decoded."))
        val quality = EvidenceQualityAnalyzer.grade(EvidenceQualityAnalyzer.measure(analysisBmp, w, h))
        progress(asset, 70)
        val ocr = if (needsOcr(asset)) runOcr(analysisBmp) else null
        analysisBmp.recycle()
        return asset.copy(
            thumbnailPath = thumb, width = w, height = h, sizeBytes = file.length(),
            quality = quality.quality, qualityNotes = quality.notes, ocrText = ocr ?: asset.ocrText,
        )
    }

    private suspend fun processVideo(asset: MediaAsset, file: File): MediaAsset {
        val meta = MediaInspector.metadata(file)
        val thumb = MediaInspector.thumbnail(file, THUMB_DIM)?.let { bmp ->
            val out = ImageUtils.saveJpeg(bmp, container.storage.newThumbnailFile(asset.id), 80)
            bmp.recycle()
            out.absolutePath
        }
        progress(asset, 20)
        val frameGrades = mutableListOf<QualityResult>()
        val framePaths = mutableListOf<String>()
        MediaInspector.extractFrames(file, FRAME_COUNT, ANALYSIS_DIM, isCancelled = { isStopped }) { frame ->
            frameGrades += EvidenceQualityAnalyzer.grade(
                EvidenceQualityAnalyzer.measure(frame.bitmap, meta.width ?: frame.bitmap.width, meta.height ?: frame.bitmap.height)
            )
            val out = ImageUtils.saveJpeg(frame.bitmap, container.storage.newFrameFile(asset.id, frame.index), 85)
            frame.bitmap.recycle()
            framePaths += "${out.absolutePath}|${frame.timestampMs}"
        }
        progress(asset, 85)
        val (minSec, maxSec) = idealDuration(asset.step)
        val combined = EvidenceQualityAnalyzer.combine(frameGrades + EvidenceQualityAnalyzer.gradeDuration(meta.durationMs, minSec, maxSec))
        return asset.copy(
            thumbnailPath = thumb, durationMs = meta.durationMs, width = meta.width, height = meta.height,
            sizeBytes = file.length(), framePaths = framePaths, quality = combined.quality, qualityNotes = combined.notes,
        )
    }

    private fun processAudio(asset: MediaAsset, file: File): MediaAsset {
        val meta = MediaInspector.metadata(file)
        val q = EvidenceQualityAnalyzer.gradeDuration(meta.durationMs, 10, 30)
        return asset.copy(durationMs = meta.durationMs, sizeBytes = file.length(), quality = q.quality, qualityNotes = q.notes)
    }

    private suspend fun processPdf(asset: MediaAsset, file: File): MediaAsset {
        val page = MediaInspector.renderPdfFirstPage(file, ANALYSIS_DIM * 2)
            ?: return asset.copy(quality = EvidenceQuality.INSUFFICIENT, qualityNotes = listOf("PDF could not be opened."))
        val thumb = ImageUtils.saveJpeg(ImageUtils.scaleDown(page.copy(Bitmap.Config.ARGB_8888, false), THUMB_DIM), container.storage.newThumbnailFile(asset.id), 80)
        val frame = ImageUtils.saveJpeg(page, container.storage.newFrameFile(asset.id, 0), 90)
        val ocr = runOcr(page)
        page.recycle()
        return asset.copy(
            thumbnailPath = thumb.absolutePath, framePaths = listOf("${frame.absolutePath}|0"), sizeBytes = file.length(),
            quality = if (ocr.isNullOrBlank()) EvidenceQuality.POOR else EvidenceQuality.GOOD,
            qualityNotes = if (ocr.isNullOrBlank()) listOf("No readable text found on the first page.") else emptyList(),
            ocrText = ocr,
        )
    }

    private fun needsOcr(asset: MediaAsset) =
        asset.step == InspectionStep.DASHBOARD || asset.evidenceType == EvidenceType.SERVICE_DOCUMENT

    private suspend fun runOcr(bitmap: Bitmap): String? = runCatching { container.ocr.recognize(bitmap) }.getOrNull()

    private fun idealDuration(step: InspectionStep?): Pair<Int, Int> = when (step) {
        InspectionStep.COLD_START -> 20 to 45
        InspectionStep.EXHAUST_SMOKE -> 10 to 45
        InspectionStep.TEST_DRIVE -> 30 to 300
        else -> 5 to 60
    }

    companion object {
        const val KEY_MEDIA_ID = "media_id"
        const val KEY_PROGRESS = "progress"
        private const val THUMB_DIM = 320
        private const val ANALYSIS_DIM = 1024
        private const val FRAME_COUNT = 6

        fun uniqueName(mediaId: String) = "media-$mediaId"

        /** Frame paths are stored as "path|timestampMs". */
        fun parseFrame(entry: String): Pair<String, Long> {
            val path = entry.substringBefore('|')
            val ts = entry.substringAfter('|', "0").toLongOrNull() ?: 0L
            return path to ts
        }
    }
}

class MediaProcessingScheduler(private val workManager: WorkManager) {

    fun enqueue(mediaId: String) {
        val request = OneTimeWorkRequestBuilder<MediaProcessingWorker>()
            .setInputData(workDataOf(MediaProcessingWorker.KEY_MEDIA_ID to mediaId))
            .addTag(TAG)
            .build()
        workManager.enqueueUniqueWork(MediaProcessingWorker.uniqueName(mediaId), ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(mediaId: String) {
        workManager.cancelUniqueWork(MediaProcessingWorker.uniqueName(mediaId))
    }

    fun retry(mediaId: String) = enqueue(mediaId)

    fun observeProgress(mediaId: String): Flow<Int?> =
        workManager.getWorkInfosForUniqueWorkFlow(MediaProcessingWorker.uniqueName(mediaId)).map { infos ->
            infos.firstOrNull { it.state == WorkInfo.State.RUNNING }?.progress?.getInt(MediaProcessingWorker.KEY_PROGRESS, 0)
        }

    companion object { const val TAG = "media-processing" }
}
