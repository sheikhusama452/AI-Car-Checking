package com.aicarchecking.domain.usecase

import com.aicarchecking.ai.AIProviderRegistry
import com.aicarchecking.ai.AnalysisRequest
import com.aicarchecking.ai.EvidencePayload
import com.aicarchecking.ai.ProviderError
import com.aicarchecking.ai.ProviderResult
import com.aicarchecking.ai.VehicleContext
import com.aicarchecking.ai.prompt.PromptLibrary
import com.aicarchecking.ai.validator.AiResponseValidator
import com.aicarchecking.ai.validator.ValidationResult
import com.aicarchecking.data.settings.SettingsRepository
import com.aicarchecking.domain.model.AiAnalysisRecord
import com.aicarchecking.domain.model.AnalysisStatus
import com.aicarchecking.domain.model.AnalysisTask
import com.aicarchecking.domain.model.Certainty
import com.aicarchecking.domain.model.Confidence
import com.aicarchecking.domain.model.EvidenceType
import com.aicarchecking.domain.model.Finding
import com.aicarchecking.domain.model.FindingCategory
import com.aicarchecking.domain.model.FindingSource
import com.aicarchecking.domain.model.GenericStatus
import com.aicarchecking.domain.model.Inspection
import com.aicarchecking.domain.model.InspectionStep
import com.aicarchecking.domain.model.MediaAsset
import com.aicarchecking.domain.model.MileageRecord
import com.aicarchecking.domain.model.MileageSource
import com.aicarchecking.domain.model.PaintStatus
import com.aicarchecking.domain.model.ProcessingStatus
import com.aicarchecking.domain.model.Severity
import com.aicarchecking.domain.repository.AiAnalysisRepository
import com.aicarchecking.domain.repository.EvidenceRepository
import com.aicarchecking.domain.repository.FindingRepository
import com.aicarchecking.domain.repository.InspectionRepository
import com.aicarchecking.domain.repository.MileageRepository
import com.aicarchecking.domain.repository.VehicleRepository
import com.aicarchecking.media.ocr.OdometerParser
import com.aicarchecking.media.work.MediaProcessingWorker
import com.aicarchecking.safety.SafetyPolicyEngine
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.UUID

sealed interface AnalysisOutcome {
    data class Success(val findingCount: Int, val summary: String?, val warnings: List<String>) : AnalysisOutcome
    data class NeedsBetterEvidence(val message: String) : AnalysisOutcome
    data class ConsentRequired(val providerName: String, val itemCount: Int) : AnalysisOutcome
    data class Failed(val message: String) : AnalysisOutcome
    data object NoEvidence : AnalysisOutcome
    data object StillProcessing : AnalysisOutcome
    data object NotConfigured : AnalysisOutcome
}

/**
 * Media analysis pipeline for one inspection step (spec §65):
 * evidence → quality gate → user confirmation → provider → validation → safety → evidence-linked findings.
 */
class AnalyzeStepUseCase(
    private val inspections: InspectionRepository,
    private val vehicles: VehicleRepository,
    private val evidence: EvidenceRepository,
    private val findings: FindingRepository,
    private val mileage: MileageRepository,
    private val analyses: AiAnalysisRepository,
    private val registry: AIProviderRegistry,
    private val settings: SettingsRepository,
) {

    suspend operator fun invoke(
        inspectionId: String,
        step: InspectionStep,
        task: AnalysisTask,
        userConfirmedUpload: Boolean,
    ): AnalysisOutcome {
        val inspection = inspections.get(inspectionId) ?: return AnalysisOutcome.Failed("Inspection not found.")
        val assets = evidence.listForStep(inspectionId, step)
        if (assets.isEmpty()) return AnalysisOutcome.NoEvidence
        if (assets.any { it.processingStatus == ProcessingStatus.PENDING || it.processingStatus == ProcessingStatus.PROCESSING }) {
            return AnalysisOutcome.StillProcessing
        }

        val usable = assets.filter { it.quality.usableForAnalysis }
        if (usable.isEmpty()) {
            saveInsufficientEvidence(inspection, step, task, assets)
            return AnalysisOutcome.NeedsBetterEvidence(
                "Evidence quality is insufficient. Please capture the ${step.title.lowercase()} again in better lighting."
            )
        }

        if (task == AnalysisTask.DASHBOARD_OCR) return runDashboardOcr(inspection, step, usable)

        val provider = registry.providerFor(inspection.isDemo) ?: return AnalysisOutcome.NotConfigured
        val payloads = buildPayloads(usable)
        if (payloads.isEmpty()) return AnalysisOutcome.NeedsBetterEvidence("This evidence type can't be analyzed for this step. Add a photo or video.")

        if (provider.sendsDataOffDevice && !userConfirmedUpload && !settings.current().aiConsentAccepted) {
            return AnalysisOutcome.ConsentRequired(provider.displayName, payloads.size)
        }

        val vehicle = vehicles.get(inspection.vehicleId)
        val context = vehicle?.let {
            VehicleContext(
                description = listOf(it.make, it.model, it.variant, it.year?.toString().orEmpty()).filter(String::isNotBlank).joinToString(" "),
                fuelType = it.fuelType, transmission = it.transmission,
                recordedMileageKm = inspection.mileageKm ?: it.currentMileageKm,
                inspectionType = inspection.type.label,
            )
        }
        val request = AnalysisRequest(
            task = task, step = step,
            systemInstruction = PromptLibrary.SYSTEM_INSTRUCTION,
            prompt = PromptLibrary.build(task, step, context, payloads),
            evidence = payloads,
        )

        val ids = usable.map { it.id }
        evidence.getMany(ids).forEach { evidence.save(it.copy(analysisStatus = AnalysisStatus.ANALYZING)) }
        val requestedAt = System.currentTimeMillis()

        val result = try {
            withTimeout(PROVIDER_TIMEOUT_MS) { provider.analyze(request) }
        } catch (e: TimeoutCancellationException) {
            ProviderResult.Failure(ProviderError.TIMEOUT)
        }

        fun record(ok: Boolean, message: String?, warnings: List<String>) = AiAnalysisRecord(
            id = UUID.randomUUID().toString(), inspectionId = inspectionId, step = step, task = task,
            providerName = provider.displayName, requestedAt = requestedAt, completedAt = System.currentTimeMillis(),
            succeeded = ok, userMessage = message, validationWarnings = warnings, evidenceIds = ids,
        )

        val text = when (result) {
            is ProviderResult.Failure -> {
                markAnalysis(ids, AnalysisStatus.FAILED)
                analyses.record(record(false, result.error.userMessage, emptyList()))
                return AnalysisOutcome.Failed(result.error.userMessage)
            }
            is ProviderResult.Success -> result.text
        }

        val quality = usable.associate { it.id to it.quality }
        return when (val validation = AiResponseValidator.validate(text, task, ids.toSet(), quality)) {
            is ValidationResult.Invalid -> {
                markAnalysis(ids, AnalysisStatus.FAILED)
                analyses.record(record(false, validation.userMessage, listOf(validation.reason)))
                AnalysisOutcome.Failed(validation.userMessage)
            }
            is ValidationResult.Valid -> {
                val source = if (inspection.isDemo) FindingSource.DEMO else FindingSource.AI
                val mapped = validation.findings.map { v ->
                    Finding(
                        id = UUID.randomUUID().toString(), inspectionId = inspectionId, vehicleId = inspection.vehicleId,
                        step = step, category = v.category, panel = v.panel, status = v.status, certainty = v.certainty,
                        severity = v.severity, confidence = v.confidence, evidenceIds = v.evidenceIds,
                        evidenceType = v.evidenceType, observation = v.observation, possibleCauses = v.possibleCauses,
                        recommendation = v.recommendation, verificationMethod = v.verificationMethod,
                        frameNumber = v.frameNumber, mediaTimestampMs = v.timestampMs,
                        source = source, providerName = provider.displayName,
                    )
                }
                val safe = SafetyPolicyEngine.apply(mapped)
                findings.replaceForStep(inspectionId, step, task.category, safe)
                markAnalysis(ids, AnalysisStatus.ANALYZED)
                analyses.record(record(true, validation.summary, validation.warnings))
                AnalysisOutcome.Success(safe.size, validation.summary, validation.warnings)
            }
        }
    }

    private suspend fun markAnalysis(ids: List<String>, status: AnalysisStatus) {
        evidence.getMany(ids).forEach { evidence.save(it.copy(analysisStatus = status)) }
    }

    /** Only images, video frames, document pages and audio are sent. Originals stay on device. */
    private fun buildPayloads(assets: List<MediaAsset>): List<EvidencePayload> = assets.flatMap { a ->
        when {
            a.framePaths.isNotEmpty() -> a.framePaths.mapIndexedNotNull { i, entry ->
                val (path, ts) = MediaProcessingWorker.parseFrame(entry)
                File(path).takeIf { it.exists() }?.let {
                    EvidencePayload(a.id, a.evidenceType, "image/jpeg", it, a.label ?: "frame", a.quality, frameNumber = i, timestampMs = ts)
                }
            }
            a.isImage -> listOf(EvidencePayload(a.id, a.evidenceType, a.mimeType, File(a.localPath), a.label ?: a.step?.title ?: "photo", a.quality))
            a.isAudio -> listOf(EvidencePayload(a.id, EvidenceType.AUDIO, a.mimeType, File(a.localPath), a.label ?: "audio", a.quality))
            else -> emptyList()
        }
    }

    private suspend fun runDashboardOcr(inspection: Inspection, step: InspectionStep, assets: List<MediaAsset>): AnalysisOutcome {
        val reading = assets.firstNotNullOfOrNull { a -> a.ocrText?.let { OdometerParser.parse(it) }?.let { a to it } }
            ?: return AnalysisOutcome.NeedsBetterEvidence(
                "We couldn't read the odometer. Retake the dashboard photo closer, without glare, or enter the mileage manually on the Mileage screen."
            )
        val (asset, odo) = reading
        val km = OdometerParser.toKm(odo.value, odo.unit)
        mileage.add(
            MileageRecord(
                id = UUID.randomUUID().toString(), vehicleId = inspection.vehicleId, inspectionId = inspection.id,
                source = MileageSource.DASHBOARD_OCR, valueKm = km, originalValue = odo.value, unit = odo.unit,
                recordedDate = asset.createdAt, evidenceId = asset.id, note = "OCR text: ${odo.raw}",
            )
        )
        inspections.save(inspection.copy(mileageKm = km))
        val finding = Finding(
            id = UUID.randomUUID().toString(), inspectionId = inspection.id, vehicleId = inspection.vehicleId, step = step,
            category = FindingCategory.MILEAGE, status = GenericStatus.NEEDS_VERIFICATION.name, certainty = Certainty.OBSERVED,
            severity = Severity.LOW, confidence = odo.confidence, evidenceIds = listOf(asset.id), evidenceType = EvidenceType.OCR,
            observation = "Dashboard odometer read by on-device OCR: ${"%,d".format(odo.value)} ${odo.unit.label}.",
            recommendation = "Confirm the reading on the dashboard and compare it with service records.",
            verificationMethod = "Visual check of the odometer; service history comparison",
            source = FindingSource.LOCAL, providerName = "On-device OCR",
        )
        findings.replaceForStep(inspection.id, step, FindingCategory.MILEAGE, listOf(finding))
        return AnalysisOutcome.Success(1, "Odometer read as ${"%,d".format(odo.value)} ${odo.unit.label}. Please confirm it matches the dashboard.", emptyList())
    }

    private suspend fun saveInsufficientEvidence(inspection: Inspection, step: InspectionStep, task: AnalysisTask, assets: List<MediaAsset>) {
        val panels = if (task == AnalysisTask.PAINT) step.panels.ifEmpty { listOf(null) } else listOf(null)
        val list = panels.map { panel ->
            Finding(
                id = UUID.randomUUID().toString(), inspectionId = inspection.id, vehicleId = inspection.vehicleId, step = step,
                category = task.category, panel = panel,
                status = if (task == AnalysisTask.PAINT) PaintStatus.INSUFFICIENT_EVIDENCE.name else GenericStatus.INSUFFICIENT_EVIDENCE.name,
                certainty = Certainty.CANNOT_DETERMINE, severity = Severity.LOW, confidence = Confidence.LOW,
                evidenceIds = assets.map { it.id }, evidenceType = assets.first().evidenceType,
                observation = "Evidence quality is insufficient for analysis: " + assets.flatMap { it.qualityNotes }.distinct().joinToString(" ").ifBlank { "unusable media." },
                recommendation = "Capture this again in better lighting, closer and holding the phone steady.",
                verificationMethod = "Retake evidence",
                source = FindingSource.LOCAL,
            )
        }
        findings.replaceForStep(inspection.id, step, task.category, list)
    }

    private companion object {
        const val PROVIDER_TIMEOUT_MS = 150_000L
    }
}
