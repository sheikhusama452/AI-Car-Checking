package com.aicarchecking.ui.inspection

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicarchecking.di.AppContainer
import com.aicarchecking.domain.model.AnalysisTask
import com.aicarchecking.domain.model.EvidenceType
import com.aicarchecking.domain.model.Finding
import com.aicarchecking.domain.model.Inspection
import com.aicarchecking.domain.model.InspectionStep
import com.aicarchecking.domain.model.InspectionType
import com.aicarchecking.domain.model.MediaAsset
import com.aicarchecking.domain.model.ObdScan
import com.aicarchecking.domain.model.ProcessingStatus
import com.aicarchecking.domain.model.Vehicle
import com.aicarchecking.domain.usecase.AnalysisOutcome
import com.aicarchecking.domain.usecase.Completeness
import com.aicarchecking.domain.usecase.ReportBuilder
import com.aicarchecking.media.storage.StorageException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

// ---------- Start inspection ----------

class StartInspectionViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    val preselectedStep: InspectionStep? = InspectionStep.fromName(handle.get<String>("step"))
    val vehicles: StateFlow<List<Vehicle>> = c.vehicleRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selected = MutableStateFlow<String?>(null)
    val selected = _selected.asStateFlow()
    private val _type = MutableStateFlow(InspectionType.PRE_PURCHASE)
    val type = _type.asStateFlow()

    fun select(id: String) { _selected.value = id }
    fun setType(t: InspectionType) { _type.value = t }

    fun start(onCreated: (inspectionId: String, step: InspectionStep?) -> Unit) {
        val vehicleId = _selected.value ?: vehicles.value.firstOrNull()?.id ?: return
        viewModelScope.launch {
            val inspection = c.inspectionRepository.create(vehicleId, _type.value)
            onCreated(inspection.id, preselectedStep)
        }
    }
}

// ---------- Inspection overview ----------

data class StepProgress(
    val step: InspectionStep,
    val evidenceCount: Int,
    val usableCount: Int,
    val skipped: Boolean,
    val findingCount: Int,
    val hasConcern: Boolean,
)

data class InspectionOverview(
    val inspection: Inspection,
    val vehicle: Vehicle?,
    val steps: List<StepProgress>,
    val completeness: Completeness,
    val findings: List<Finding>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class InspectionFlowViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    val inspectionId: String = checkNotNull(handle["inspectionId"])

    private val obdScans = MutableStateFlow<List<ObdScan>>(emptyList())

    val overview: StateFlow<InspectionOverview?> = c.inspectionRepository.observe(inspectionId).filterNotNull()
        .flatMapLatest { inspection ->
            combine(
                c.vehicleRepository.observe(inspection.vehicleId),
                c.evidenceRepository.observeForInspection(inspectionId),
                c.findingRepository.observeForInspection(inspectionId),
                obdScans,
            ) { vehicle, media, findings, scans ->
                val steps = InspectionStep.entries.map { step ->
                    val stepMedia = media.filter { it.step == step }
                    val stepFindings = findings.filter { it.step == step }
                    StepProgress(
                        step = step,
                        evidenceCount = if (step == InspectionStep.OBD) scans.size else stepMedia.size,
                        usableCount = stepMedia.count { it.quality.usableForAnalysis },
                        skipped = step in inspection.skippedSteps,
                        findingCount = stepFindings.size,
                        hasConcern = stepFindings.any { ReportBuilder.bucketOf(it).ordinal <= 1 },
                    )
                }
                InspectionOverview(inspection, vehicle, steps, ReportBuilder.completeness(inspection, media, scans), findings)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch { obdScans.value = c.obdRepository.forInspection(inspectionId) }
    }

    fun refreshObd() = viewModelScope.launch { obdScans.value = c.obdRepository.forInspection(inspectionId) }

    fun complete() = viewModelScope.launch { c.inspectionRepository.markCompleted(inspectionId) }

    fun delete(onDone: () -> Unit) = viewModelScope.launch { c.inspectionRepository.delete(inspectionId); onDone() }
}

// ---------- Single step ----------

sealed interface AnalysisUiState {
    data object Idle : AnalysisUiState
    data class Running(val task: AnalysisTask) : AnalysisUiState
    data class Message(val text: String, val isError: Boolean) : AnalysisUiState
    data class NeedsConsent(val task: AnalysisTask, val providerName: String, val itemCount: Int) : AnalysisUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class StepViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    val inspectionId: String = checkNotNull(handle["inspectionId"])
    val step: InspectionStep = checkNotNull(InspectionStep.fromName(handle.get<String>("step")))

    val inspection: StateFlow<Inspection?> = c.inspectionRepository.observe(inspectionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val media: StateFlow<List<MediaAsset>> = c.evidenceRepository.observeForInspection(inspectionId)
        .map { list -> list.filter { it.step == step } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val findings: StateFlow<List<Finding>> = c.findingRepository.observeForInspection(inspectionId)
        .map { list -> list.filter { it.step == step } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _analysis = MutableStateFlow<AnalysisUiState>(AnalysisUiState.Idle)
    val analysis = _analysis.asStateFlow()

    fun progressFor(mediaId: String) = c.scheduler.observeProgress(mediaId)

    fun addFromUri(uri: Uri, type: EvidenceType? = null) = viewModelScope.launch {
        val insp = c.inspectionRepository.get(inspectionId) ?: return@launch
        try {
            c.addEvidence.fromUri(insp, step, uri, type)
            if (step in insp.skippedSteps) c.inspectionRepository.setStepSkipped(inspectionId, step, false)
        } catch (e: StorageException) {
            _analysis.value = AnalysisUiState.Message(e.message ?: "Couldn't import this file.", true)
        } catch (e: Exception) {
            _analysis.value = AnalysisUiState.Message("Couldn't import this file. Try another one.", true)
        }
    }

    fun toggleSkip() = viewModelScope.launch {
        val insp = c.inspectionRepository.get(inspectionId) ?: return@launch
        c.inspectionRepository.setStepSkipped(inspectionId, step, step !in insp.skippedSteps)
    }

    fun delete(asset: MediaAsset) = viewModelScope.launch {
        c.scheduler.cancel(asset.id)
        c.evidenceRepository.delete(asset.id)
    }

    /** Retake = remove the most recent capture for this step; the UI then opens the camera. */
    fun deleteLatest() = viewModelScope.launch {
        media.value.maxByOrNull { it.createdAt }?.let { delete(it).join() }
    }

    fun keep(asset: MediaAsset) = viewModelScope.launch {
        runCatching { c.evidenceRepository.keepPermanently(asset.id) }
            .onFailure { _analysis.value = AnalysisUiState.Message("Couldn't move this evidence to permanent storage.", true) }
    }

    fun cancelProcessing(asset: MediaAsset) = viewModelScope.launch {
        c.scheduler.cancel(asset.id)
        c.evidenceRepository.save(asset.copy(processingStatus = ProcessingStatus.CANCELLED))
    }

    fun retryProcessing(asset: MediaAsset) = viewModelScope.launch {
        c.evidenceRepository.save(asset.copy(processingStatus = ProcessingStatus.PENDING, processingProgress = 0))
        c.scheduler.retry(asset.id)
    }

    fun analyze(task: AnalysisTask, userConfirmed: Boolean = false, rememberConsent: Boolean = false) {
        if (_analysis.value is AnalysisUiState.Running) return
        viewModelScope.launch {
            if (rememberConsent) c.settings.setAiConsent(true)
            _analysis.value = AnalysisUiState.Running(task)
            val outcome = runCatching { c.analyzeStep(inspectionId, step, task, userConfirmed) }
                .getOrElse { AnalysisOutcome.Failed("AI analysis could not be completed safely. Please retry.") }
            _analysis.value = when (outcome) {
                is AnalysisOutcome.Success -> AnalysisUiState.Message(
                    outcome.summary ?: "Analysis complete: ${outcome.findingCount} finding(s). Review them below.", false,
                )
                is AnalysisOutcome.NeedsBetterEvidence -> AnalysisUiState.Message(outcome.message, true)
                is AnalysisOutcome.ConsentRequired -> AnalysisUiState.NeedsConsent(task, outcome.providerName, outcome.itemCount)
                is AnalysisOutcome.Failed -> AnalysisUiState.Message(outcome.message, true)
                AnalysisOutcome.NoEvidence -> AnalysisUiState.Message("Capture or add evidence for this step first.", true)
                AnalysisOutcome.StillProcessing -> AnalysisUiState.Message("Evidence is still being processed on your phone. Try again in a moment.", true)
                AnalysisOutcome.NotConfigured -> AnalysisUiState.Message(
                    "No AI provider is configured. Local quality checks and OCR still work. Add your Gemini API key in Settings → AI Provider to run AI analysis, or open Demo Mode from Home.",
                    true,
                )
            }
        }
    }

    fun dismissMessage() { _analysis.value = AnalysisUiState.Idle }
}

// ---------- Capture ----------

class CaptureViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    val inspectionId: String = checkNotNull(handle["inspectionId"])
    val step: InspectionStep = checkNotNull(InspectionStep.fromName(handle.get<String>("step")))

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun newFile(ext: String): File? = runCatching { c.storage.newCaptureFile(ext) }
        .onFailure { _error.value = it.message }
        .getOrNull()

    fun onCaptured(file: File, mime: String, onDone: () -> Unit) = viewModelScope.launch {
        val insp = c.inspectionRepository.get(inspectionId) ?: return@launch
        c.addEvidence.fromCapturedFile(insp, step, file, mime)
        if (step in insp.skippedSteps) c.inspectionRepository.setStepSkipped(inspectionId, step, false)
        onDone()
    }

    fun onError(message: String) { _error.value = message }
    fun clearError() { _error.value = null }
}
