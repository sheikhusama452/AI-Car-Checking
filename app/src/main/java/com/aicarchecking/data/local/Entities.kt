package com.aicarchecking.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.aicarchecking.domain.model.AnalysisStatus
import com.aicarchecking.domain.model.AnalysisTask
import com.aicarchecking.domain.model.Certainty
import com.aicarchecking.domain.model.Confidence
import com.aicarchecking.domain.model.DistanceUnit
import com.aicarchecking.domain.model.EvidenceQuality
import com.aicarchecking.domain.model.EvidenceType
import com.aicarchecking.domain.model.FindingCategory
import com.aicarchecking.domain.model.FindingSource
import com.aicarchecking.domain.model.InspectionStatus
import com.aicarchecking.domain.model.InspectionType
import com.aicarchecking.domain.model.MileageSource
import com.aicarchecking.domain.model.ObdSource
import com.aicarchecking.domain.model.ProcessingStatus
import com.aicarchecking.domain.model.Severity
import com.aicarchecking.domain.model.StorageTier

@Entity(tableName = "vehicles")
data class VehicleEntity(
    @PrimaryKey val id: String,
    val make: String,
    val model: String,
    val variant: String,
    val year: Int?,
    val registration: String,
    val vin: String,
    val engineNumber: String,
    val engineType: String,
    val engineCapacity: String,
    val fuelType: String,
    val transmission: String,
    val currentMileageKm: Long?,
    val purchaseDate: Long?,
    val notes: String,
    val photoPath: String?,
    val isDemo: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "inspections",
    foreignKeys = [ForeignKey(
        entity = VehicleEntity::class, parentColumns = ["id"], childColumns = ["vehicleId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("vehicleId")],
)
data class InspectionEntity(
    @PrimaryKey val id: String,
    val vehicleId: String,
    val type: InspectionType,
    val status: InspectionStatus,
    val startedAt: Long,
    val completedAt: Long?,
    val mileageKm: Long?,
    val skippedSteps: List<String>,
    val isDemo: Boolean,
)

@Entity(
    tableName = "media_assets",
    foreignKeys = [
        ForeignKey(
            entity = InspectionEntity::class, parentColumns = ["id"], childColumns = ["inspectionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = VehicleEntity::class, parentColumns = ["id"], childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("inspectionId"), Index("vehicleId")],
)
data class MediaAssetEntity(
    @PrimaryKey val id: String,
    val vehicleId: String,
    val inspectionId: String,
    val stepId: String?,
    val evidenceType: EvidenceType,
    val mimeType: String,
    val localPath: String,
    val thumbnailPath: String?,
    val sizeBytes: Long,
    val durationMs: Long?,
    val width: Int?,
    val height: Int?,
    val createdAt: Long,
    val storageTier: StorageTier,
    val processingStatus: ProcessingStatus,
    val processingProgress: Int,
    val analysisStatus: AnalysisStatus,
    val quality: EvidenceQuality,
    val qualityNotes: List<String>,
    val framePaths: List<String>,
    val ocrText: String?,
    val label: String?,
    val isDemo: Boolean,
)

@Entity(
    tableName = "findings",
    foreignKeys = [ForeignKey(
        entity = InspectionEntity::class, parentColumns = ["id"], childColumns = ["inspectionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("inspectionId"), Index("vehicleId")],
)
data class FindingEntity(
    @PrimaryKey val id: String,
    val inspectionId: String,
    val vehicleId: String,
    val stepId: String?,
    val category: FindingCategory,
    val panel: String?,
    val status: String,
    val certainty: Certainty,
    val severity: Severity,
    val confidence: Confidence,
    val evidenceIds: List<String>,
    val evidenceType: EvidenceType,
    val observation: String,
    val possibleCauses: List<String>,
    val recommendation: String,
    val verificationMethod: String,
    val timestamp: Long,
    val frameNumber: Int?,
    val mediaTimestampMs: Long?,
    val safetyCritical: Boolean,
    val source: FindingSource,
    val providerName: String?,
    val resolved: Boolean,
)

@Entity(
    tableName = "ai_analyses",
    foreignKeys = [ForeignKey(
        entity = InspectionEntity::class, parentColumns = ["id"], childColumns = ["inspectionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("inspectionId")],
)
data class AiAnalysisEntity(
    @PrimaryKey val id: String,
    val inspectionId: String,
    val stepId: String?,
    val task: AnalysisTask,
    val providerName: String,
    val requestedAt: Long,
    val completedAt: Long?,
    val succeeded: Boolean,
    /** Human-friendly outcome message. Raw provider output is never stored or displayed. */
    val userMessage: String?,
    val validationWarnings: List<String>,
    val evidenceIds: List<String>,
)

@Entity(
    tableName = "mileage_records",
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class, parentColumns = ["id"], childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = InspectionEntity::class, parentColumns = ["id"], childColumns = ["inspectionId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("vehicleId"), Index("inspectionId")],
)
data class MileageRecordEntity(
    @PrimaryKey val id: String,
    val vehicleId: String,
    val inspectionId: String?,
    val source: MileageSource,
    val valueKm: Long,
    val originalValue: Long,
    val unit: DistanceUnit,
    val recordedDate: Long,
    val evidenceId: String?,
    val note: String,
)

@Entity(
    tableName = "service_records",
    foreignKeys = [ForeignKey(
        entity = VehicleEntity::class, parentColumns = ["id"], childColumns = ["vehicleId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("vehicleId")],
)
data class ServiceRecordEntity(
    @PrimaryKey val id: String,
    val vehicleId: String,
    val date: Long,
    val mileageKm: Long?,
    val serviceType: String,
    val workshop: String,
    val notes: String,
    val evidenceId: String?,
)

@Entity(
    tableName = "obd_scans",
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class, parentColumns = ["id"], childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = InspectionEntity::class, parentColumns = ["id"], childColumns = ["inspectionId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("vehicleId"), Index("inspectionId")],
)
data class ObdScanEntity(
    @PrimaryKey val id: String,
    val vehicleId: String,
    val inspectionId: String?,
    val scannedAt: Long,
    val source: ObdSource,
    val batteryVoltage: Double?,
    val liveData: Map<String, String>,
    val notes: String,
)

@Entity(
    tableName = "dtc_codes",
    foreignKeys = [ForeignKey(
        entity = ObdScanEntity::class, parentColumns = ["id"], childColumns = ["scanId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("scanId")],
)
data class DtcCodeEntity(
    @PrimaryKey val id: String,
    val scanId: String,
    val code: String,
    val description: String,
    val status: String,
)

@Entity(
    tableName = "inspection_reports",
    foreignKeys = [ForeignKey(
        entity = InspectionEntity::class, parentColumns = ["id"], childColumns = ["inspectionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("inspectionId")],
)
data class InspectionReportEntity(
    @PrimaryKey val id: String,
    val inspectionId: String,
    val generatedAt: Long,
    val pdfPath: String?,
    val completenessPercent: Int,
)
