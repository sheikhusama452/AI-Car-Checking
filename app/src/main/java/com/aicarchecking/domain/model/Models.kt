package com.aicarchecking.domain.model

data class Vehicle(
    val id: String,
    val make: String,
    val model: String,
    val variant: String = "",
    val year: Int? = null,
    val registration: String = "",
    val vin: String = "",
    val engineNumber: String = "",
    val engineType: String = "",
    val engineCapacity: String = "",
    val fuelType: String = "",
    val transmission: String = "",
    val currentMileageKm: Long? = null,
    val purchaseDate: Long? = null,
    val notes: String = "",
    val photoPath: String? = null,
    val isDemo: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val displayName: String
        get() = listOfNotNull(make.takeIf { it.isNotBlank() }, model.takeIf { it.isNotBlank() }, year?.toString())
            .joinToString(" ").ifBlank { "Unnamed vehicle" }
}

data class Inspection(
    val id: String,
    val vehicleId: String,
    val type: InspectionType,
    val status: InspectionStatus = InspectionStatus.IN_PROGRESS,
    val startedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val mileageKm: Long? = null,
    val skippedSteps: Set<InspectionStep> = emptySet(),
    val isDemo: Boolean = false,
)

data class MediaAsset(
    val id: String,
    val vehicleId: String,
    val inspectionId: String,
    val step: InspectionStep?,
    val evidenceType: EvidenceType,
    val mimeType: String,
    val localPath: String,
    val thumbnailPath: String? = null,
    val sizeBytes: Long = 0,
    val durationMs: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val storageTier: StorageTier = StorageTier.TEMPORARY,
    val processingStatus: ProcessingStatus = ProcessingStatus.PENDING,
    val processingProgress: Int = 0,
    val analysisStatus: AnalysisStatus = AnalysisStatus.NOT_ANALYZED,
    val quality: EvidenceQuality = EvidenceQuality.NOT_ASSESSED,
    val qualityNotes: List<String> = emptyList(),
    val framePaths: List<String> = emptyList(),
    val ocrText: String? = null,
    val label: String? = null,
    val isDemo: Boolean = false,
) {
    val isImage: Boolean get() = mimeType.startsWith("image/")
    val isVideo: Boolean get() = mimeType.startsWith("video/")
    val isAudio: Boolean get() = mimeType.startsWith("audio/")
}

/** Every finding is traceable to evidence (spec §40). */
data class Finding(
    val id: String,
    val inspectionId: String,
    val vehicleId: String,
    val step: InspectionStep?,
    val category: FindingCategory,
    val panel: Panel? = null,
    val status: String,
    val certainty: Certainty,
    val severity: Severity,
    val confidence: Confidence,
    val evidenceIds: List<String>,
    val evidenceType: EvidenceType,
    val observation: String,
    val possibleCauses: List<String> = emptyList(),
    val recommendation: String,
    val verificationMethod: String,
    val timestamp: Long = System.currentTimeMillis(),
    val frameNumber: Int? = null,
    val mediaTimestampMs: Long? = null,
    val safetyCritical: Boolean = false,
    val source: FindingSource = FindingSource.AI,
    val providerName: String? = null,
    val resolved: Boolean = false,
) {
    val statusLabel: String
        get() = PaintStatus.fromCode(status)?.label
            ?: AccidentStatus.entries.firstOrNull { it.name == status }?.label
            ?: GenericStatus.entries.firstOrNull { it.name == status }?.label
            ?: status.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
}

data class MileageRecord(
    val id: String,
    val vehicleId: String,
    val inspectionId: String? = null,
    val source: MileageSource,
    val valueKm: Long,
    val originalValue: Long,
    val unit: DistanceUnit,
    val recordedDate: Long,
    val evidenceId: String? = null,
    val note: String = "",
)

data class DtcCode(val code: String, val description: String, val status: String = "Stored")

data class ObdScan(
    val id: String,
    val vehicleId: String,
    val inspectionId: String?,
    val scannedAt: Long,
    val source: ObdSource,
    val codes: List<DtcCode>,
    val batteryVoltage: Double? = null,
    val liveData: Map<String, String> = emptyMap(),
    val notes: String = "",
)

data class ServiceRecord(
    val id: String,
    val vehicleId: String,
    val date: Long,
    val mileageKm: Long?,
    val serviceType: String,
    val workshop: String,
    val notes: String = "",
    val evidenceId: String? = null,
)

data class AiAnalysisRecord(
    val id: String,
    val inspectionId: String,
    val step: InspectionStep?,
    val task: AnalysisTask,
    val providerName: String,
    val requestedAt: Long,
    val completedAt: Long?,
    val succeeded: Boolean,
    val userMessage: String?,
    val validationWarnings: List<String>,
    val evidenceIds: List<String>,
)
