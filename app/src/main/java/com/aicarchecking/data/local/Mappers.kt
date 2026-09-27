package com.aicarchecking.data.local

import com.aicarchecking.domain.model.AiAnalysisRecord
import com.aicarchecking.domain.model.DtcCode
import com.aicarchecking.domain.model.Finding
import com.aicarchecking.domain.model.Inspection
import com.aicarchecking.domain.model.InspectionStep
import com.aicarchecking.domain.model.MediaAsset
import com.aicarchecking.domain.model.MileageRecord
import com.aicarchecking.domain.model.ObdScan
import com.aicarchecking.domain.model.Panel
import com.aicarchecking.domain.model.ServiceRecord
import com.aicarchecking.domain.model.Vehicle

fun VehicleEntity.toDomain() = Vehicle(
    id, make, model, variant, year, registration, vin, engineNumber, engineType, engineCapacity,
    fuelType, transmission, currentMileageKm, purchaseDate, notes, photoPath, isDemo, createdAt, updatedAt,
)

fun Vehicle.toEntity() = VehicleEntity(
    id, make, model, variant, year, registration, vin, engineNumber, engineType, engineCapacity,
    fuelType, transmission, currentMileageKm, purchaseDate, notes, photoPath, isDemo, createdAt, updatedAt,
)

fun InspectionEntity.toDomain() = Inspection(
    id = id, vehicleId = vehicleId, type = type, status = status, startedAt = startedAt,
    completedAt = completedAt, mileageKm = mileageKm,
    skippedSteps = skippedSteps.mapNotNull { InspectionStep.fromName(it) }.toSet(),
    isDemo = isDemo,
)

fun Inspection.toEntity() = InspectionEntity(
    id = id, vehicleId = vehicleId, type = type, status = status, startedAt = startedAt,
    completedAt = completedAt, mileageKm = mileageKm, skippedSteps = skippedSteps.map { it.name },
    isDemo = isDemo,
)

fun MediaAssetEntity.toDomain() = MediaAsset(
    id = id, vehicleId = vehicleId, inspectionId = inspectionId, step = InspectionStep.fromName(stepId),
    evidenceType = evidenceType, mimeType = mimeType, localPath = localPath, thumbnailPath = thumbnailPath,
    sizeBytes = sizeBytes, durationMs = durationMs, width = width, height = height, createdAt = createdAt,
    storageTier = storageTier, processingStatus = processingStatus, processingProgress = processingProgress,
    analysisStatus = analysisStatus, quality = quality, qualityNotes = qualityNotes, framePaths = framePaths,
    ocrText = ocrText, label = label, isDemo = isDemo,
)

fun MediaAsset.toEntity() = MediaAssetEntity(
    id = id, vehicleId = vehicleId, inspectionId = inspectionId, stepId = step?.name,
    evidenceType = evidenceType, mimeType = mimeType, localPath = localPath, thumbnailPath = thumbnailPath,
    sizeBytes = sizeBytes, durationMs = durationMs, width = width, height = height, createdAt = createdAt,
    storageTier = storageTier, processingStatus = processingStatus, processingProgress = processingProgress,
    analysisStatus = analysisStatus, quality = quality, qualityNotes = qualityNotes, framePaths = framePaths,
    ocrText = ocrText, label = label, isDemo = isDemo,
)

fun FindingEntity.toDomain() = Finding(
    id = id, inspectionId = inspectionId, vehicleId = vehicleId, step = InspectionStep.fromName(stepId),
    category = category, panel = Panel.fromCode(panel), status = status, certainty = certainty,
    severity = severity, confidence = confidence, evidenceIds = evidenceIds, evidenceType = evidenceType,
    observation = observation, possibleCauses = possibleCauses, recommendation = recommendation,
    verificationMethod = verificationMethod, timestamp = timestamp, frameNumber = frameNumber,
    mediaTimestampMs = mediaTimestampMs, safetyCritical = safetyCritical, source = source,
    providerName = providerName, resolved = resolved,
)

fun Finding.toEntity() = FindingEntity(
    id = id, inspectionId = inspectionId, vehicleId = vehicleId, stepId = step?.name, category = category,
    panel = panel?.name, status = status, certainty = certainty, severity = severity, confidence = confidence,
    evidenceIds = evidenceIds, evidenceType = evidenceType, observation = observation,
    possibleCauses = possibleCauses, recommendation = recommendation, verificationMethod = verificationMethod,
    timestamp = timestamp, frameNumber = frameNumber, mediaTimestampMs = mediaTimestampMs,
    safetyCritical = safetyCritical, source = source, providerName = providerName, resolved = resolved,
)

fun MileageRecordEntity.toDomain() = MileageRecord(
    id, vehicleId, inspectionId, source, valueKm, originalValue, unit, recordedDate, evidenceId, note,
)

fun MileageRecord.toEntity() = MileageRecordEntity(
    id, vehicleId, inspectionId, source, valueKm, originalValue, unit, recordedDate, evidenceId, note,
)

fun ServiceRecordEntity.toDomain() = ServiceRecord(id, vehicleId, date, mileageKm, serviceType, workshop, notes, evidenceId)
fun ServiceRecord.toEntity() = ServiceRecordEntity(id, vehicleId, date, mileageKm, serviceType, workshop, notes, evidenceId)

fun ObdScanEntity.toDomain(codes: List<DtcCodeEntity>) = ObdScan(
    id = id, vehicleId = vehicleId, inspectionId = inspectionId, scannedAt = scannedAt, source = source,
    codes = codes.map { DtcCode(it.code, it.description, it.status) }, batteryVoltage = batteryVoltage,
    liveData = liveData, notes = notes,
)

fun AiAnalysisEntity.toDomain() = AiAnalysisRecord(
    id, inspectionId, InspectionStep.fromName(stepId), task, providerName, requestedAt, completedAt,
    succeeded, userMessage, validationWarnings, evidenceIds,
)
