package com.aicarchecking.domain.usecase

import android.net.Uri
import com.aicarchecking.domain.model.EvidenceType
import com.aicarchecking.domain.model.Inspection
import com.aicarchecking.domain.model.InspectionStep
import com.aicarchecking.domain.model.MediaAsset
import com.aicarchecking.domain.model.ProcessingStatus
import com.aicarchecking.domain.repository.EvidenceRepository
import com.aicarchecking.media.storage.MediaStorageManager
import com.aicarchecking.media.work.MediaProcessingScheduler
import java.io.File
import java.util.UUID

/**
 * Local media storage flow (spec §44): copy into app storage → unique Media ID → Room metadata →
 * associate with vehicle/inspection → background processing. Gallery URIs are never kept.
 */
class AddEvidenceUseCase(
    private val storage: MediaStorageManager,
    private val evidence: EvidenceRepository,
    private val scheduler: MediaProcessingScheduler,
) {
    suspend fun fromUri(inspection: Inspection, step: InspectionStep?, uri: Uri, typeOverride: EvidenceType? = null): MediaAsset {
        val imported = storage.importFromUri(uri)
        return register(inspection, step, imported.file, imported.mimeType, typeOverride, imported.displayName)
    }

    suspend fun fromCapturedFile(inspection: Inspection, step: InspectionStep?, file: File, mimeType: String): MediaAsset =
        register(inspection, step, file, mimeType, null, null)

    private suspend fun register(
        inspection: Inspection,
        step: InspectionStep?,
        file: File,
        mimeType: String,
        typeOverride: EvidenceType?,
        label: String?,
    ): MediaAsset {
        val type = typeOverride ?: when {
            mimeType.startsWith("video/") -> if (step == InspectionStep.TEST_DRIVE) EvidenceType.TEST_DRIVE else EvidenceType.VIDEO
            mimeType.startsWith("audio/") -> EvidenceType.AUDIO
            mimeType == "application/pdf" -> EvidenceType.SERVICE_DOCUMENT
            else -> EvidenceType.PHOTO
        }
        val asset = MediaAsset(
            id = "media_" + UUID.randomUUID().toString().replace("-", "").take(12),
            vehicleId = inspection.vehicleId,
            inspectionId = inspection.id,
            step = step,
            evidenceType = type,
            mimeType = mimeType,
            localPath = file.absolutePath,
            sizeBytes = file.length(),
            processingStatus = ProcessingStatus.PENDING,
            label = label ?: step?.title,
            isDemo = inspection.isDemo,
        )
        evidence.save(asset)
        scheduler.enqueue(asset.id)
        return asset
    }
}
