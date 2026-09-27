package com.aicarchecking.ui.inspection

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aicarchecking.domain.model.AnalysisTask
import com.aicarchecking.domain.model.CaptureMode
import com.aicarchecking.domain.model.EvidenceType
import com.aicarchecking.domain.model.InspectionStep
import com.aicarchecking.domain.model.Limitations
import com.aicarchecking.domain.model.MediaAsset
import com.aicarchecking.domain.model.ProcessingStatus
import com.aicarchecking.domain.model.StorageTier
import com.aicarchecking.navigation.Routes
import com.aicarchecking.ui.common.AppTopBar
import com.aicarchecking.ui.common.EvidenceThumb
import com.aicarchecking.ui.common.FindingCard
import com.aicarchecking.ui.common.LimitationsNote
import com.aicarchecking.ui.common.Pill
import com.aicarchecking.ui.common.QualityBadge
import com.aicarchecking.ui.common.SectionHeader
import com.aicarchecking.ui.common.WarningBanner
import com.aicarchecking.ui.common.appViewModel
import com.aicarchecking.ui.common.formatBytes
import com.aicarchecking.ui.theme.Brand

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StepScreen(onBack: () -> Unit, navigate: (String) -> Unit, replace: (String) -> Unit) {
    val vm = appViewModel { c, h -> StepViewModel(c, h) }
    val step = vm.step
    val media by vm.media.collectAsStateWithLifecycle()
    val findings by vm.findings.collectAsStateWithLifecycle()
    val inspection by vm.inspection.collectAsStateWithLifecycle()
    val analysis by vm.analysis.collectAsStateWithLifecycle()
    var detail by remember { mutableStateOf<MediaAsset?>(null) }
    var addMenu by remember { mutableStateOf(false) }

    val visualPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(8)) { uris ->
        uris.forEach { vm.addFromUri(it) }
    }
    val docPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.addFromUri(it) }
    }
    val serviceDocPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.addFromUri(it, EvidenceType.SERVICE_DOCUMENT) }
    }

    val skipped = inspection?.skippedSteps?.contains(step) == true
    val nextStep = InspectionStep.entries.getOrNull(step.ordinal + 1)
    val cameraMode = when (step.capture) {
        CaptureMode.VIDEO -> CaptureMode.VIDEO
        CaptureMode.AUDIO -> CaptureMode.AUDIO
        else -> CaptureMode.PHOTO
    }

    Scaffold(topBar = { AppTopBar("Step ${step.number} of ${InspectionStep.entries.size} · ${step.title}", onBack) }) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp)) {
            item {
                if (inspection?.isDemo == true) {
                    Pill("DEMO MODE — scripted sample analysis", Brand.Amber)
                    Spacer(Modifier.height(8.dp))
                }
                step.safetyNote?.let { WarningBanner(it); Spacer(Modifier.height(10.dp)) }

                // AI Inspection Coach
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            Spacer(Modifier.width(8.dp))
                            Text("AI Inspection Coach", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Spacer(Modifier.height(8.dp))
                        step.coach.forEach {
                            Text("• $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        coachStatus(step, media)?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }

                SectionHeader("Evidence")
                if (media.isEmpty()) {
                    Text(if (skipped) "This step was skipped." else "No evidence yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(media, key = { it.id }) { a ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                EvidenceThumb(a) { detail = a }
                                Spacer(Modifier.height(4.dp))
                                when (a.processingStatus) {
                                    ProcessingStatus.PENDING, ProcessingStatus.PROCESSING -> {
                                        val progressFlow = remember(a.id) { vm.progressFor(a.id) }
                                        val progress by progressFlow.collectAsState(initial = null)
                                        LinearProgressIndicator(progress = { (progress ?: a.processingProgress) / 100f }, modifier = Modifier.width(84.dp))
                                    }
                                    ProcessingStatus.FAILED -> Pill("Failed", Brand.Red)
                                    ProcessingStatus.CANCELLED -> Pill("Cancelled", Brand.Grey)
                                    ProcessingStatus.COMPLETED -> QualityBadge(a.quality)
                                }
                            }
                        }
                    }
                    media.filter { it.processingStatus == ProcessingStatus.COMPLETED && !it.quality.usableForAnalysis }.takeIf { it.isNotEmpty() }?.let {
                        Spacer(Modifier.height(8.dp))
                        WarningBanner("Evidence quality is insufficient. Please capture this again in better lighting.", color = Brand.Amber)
                    }
                }
                Spacer(Modifier.height(14.dp))

                // Capture / Retake / Skip / Add Evidence
                if (step.capture != CaptureMode.NONE) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { navigate(Routes.camera(vm.inspectionId, step, cameraMode)) }) {
                            Icon(
                                when (cameraMode) { CaptureMode.VIDEO -> Icons.Filled.Videocam; CaptureMode.AUDIO -> Icons.Filled.Mic; else -> Icons.Filled.CameraAlt },
                                null,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (cameraMode == CaptureMode.AUDIO) "Record" else "Capture")
                        }
                        OutlinedButton(
                            onClick = { vm.deleteLatest(); navigate(Routes.camera(vm.inspectionId, step, cameraMode)) },
                            enabled = media.isNotEmpty(),
                        ) {
                            Icon(Icons.Filled.Replay, null); Spacer(Modifier.width(6.dp)); Text("Retake")
                        }
                        Column {
                            OutlinedButton(onClick = { addMenu = true }) {
                                Icon(Icons.Filled.UploadFile, null); Spacer(Modifier.width(6.dp)); Text("Add Evidence")
                            }
                            DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                                DropdownMenuItem(text = { Text("Photos / videos from gallery") }, onClick = {
                                    addMenu = false
                                    visualPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                                })
                                DropdownMenuItem(text = { Text("Audio file") }, onClick = {
                                    addMenu = false
                                    docPicker.launch(arrayOf("audio/*"))
                                })
                                if (step == InspectionStep.DASHBOARD) {
                                    DropdownMenuItem(text = { Text("Service document (photo or PDF)") }, onClick = {
                                        addMenu = false
                                        serviceDocPicker.launch(arrayOf("image/*", "application/pdf"))
                                    })
                                }
                            }
                        }
                        TextButton(onClick = { vm.toggleSkip() }) {
                            Icon(Icons.Filled.SkipNext, null); Spacer(Modifier.width(4.dp)); Text(if (skipped) "Unskip" else "Skip")
                        }
                    }
                } else if (step == InspectionStep.OBD) {
                    Button(onClick = { navigate(Routes.obd(vm.inspectionId)) }) { Text("Open OBD check") }
                } else if (step == InspectionStep.FINAL_REPORT) {
                    Button(onClick = { replace(Routes.report(vm.inspectionId)) }) { Text("Generate AI Vehicle Inspection Report") }
                }

                if (step.tasks.isNotEmpty() && step != InspectionStep.OBD) {
                    SectionHeader("AI Analyze")
                    step.tasks.forEach { task ->
                        val running = (analysis as? AnalysisUiState.Running)?.task == task
                        FilledTonalButton(
                            onClick = { vm.analyze(task) },
                            enabled = media.isNotEmpty() && analysis !is AnalysisUiState.Running,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        ) {
                            if (running) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(if (task.localOnly) "${task.label} (on-device)" else task.label)
                        }
                    }
                    if (step.isBodyStep) {
                        TextButton(onClick = { navigate(Routes.paint(vm.inspectionId)) }) { Text("Open Paint Map") }
                    }
                    if (step == InspectionStep.DASHBOARD) {
                        TextButton(onClick = { navigate(Routes.mileage(vm.inspectionId)) }) { Text("Open Mileage Check") }
                    }
                }

                (analysis as? AnalysisUiState.Message)?.let { msg ->
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = (if (msg.isError) Brand.Amber else Brand.Green).copy(alpha = 0.15f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(msg.text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = vm::dismissMessage) { Text("OK") }
                        }
                    }
                }

                if (findings.isNotEmpty()) SectionHeader("Findings (${findings.size})")
            }
            items(findings, key = { it.id }) { f -> FindingCard(f, Modifier.padding(bottom = 8.dp)) }
            item {
                Spacer(Modifier.height(12.dp))
                LimitationsNote(*listOfNotNull(Limitations.GENERAL, step.tasks.firstNotNullOfOrNull { Limitations.forTask(it) }).toTypedArray())
                Spacer(Modifier.height(12.dp))
                nextStep?.let { n ->
                    Button(
                        onClick = { replace(if (n == InspectionStep.FINAL_REPORT) Routes.report(vm.inspectionId) else Routes.step(vm.inspectionId, n)) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text("Next: ${n.title}") }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    (analysis as? AnalysisUiState.NeedsConsent)?.let { c ->
        var dontAsk by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = vm::dismissMessage,
            title = { Text("Send evidence for AI analysis?") },
            text = {
                Column {
                    Text(
                        "${c.itemCount} selected item(s) (photos, video frames or audio) for \"${step.title}\" will be processed by ${c.providerName}. " +
                            "Originals stay on your phone. Nothing is uploaded unless you confirm."
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = dontAsk, onCheckedChange = { dontAsk = it })
                        Text("Don't ask again", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.analyze(c.task, userConfirmed = true, rememberConsent = dontAsk) }) { Text("Analyze") } },
            dismissButton = { TextButton(onClick = vm::dismissMessage) { Text("Cancel") } },
        )
    }

    detail?.let { asset ->
        val current = media.firstOrNull { it.id == asset.id } ?: asset
        ModalBottomSheet(onDismissRequest = { detail = null }) {
            EvidenceDetail(
                current,
                onKeep = { vm.keep(current) },
                onDelete = { vm.delete(current); detail = null },
                onRetry = { vm.retryProcessing(current) },
                onCancel = { vm.cancelProcessing(current) },
            )
        }
    }
}

private fun coachStatus(step: InspectionStep, media: List<MediaAsset>): String? {
    if (step.capture == CaptureMode.NONE) return null
    val done = media.filter { it.processingStatus == ProcessingStatus.COMPLETED }
    val usable = done.count { it.quality.usableForAnalysis }
    return when {
        media.isEmpty() -> null
        done.size < media.size -> "Checking evidence quality on your phone…"
        usable == 0 -> "Evidence quality is insufficient. Please capture again in better lighting."
        usable < step.minEvidence -> "Good start — capture ${step.minEvidence - usable} more to complete this step."
        else -> "Evidence looks sufficient for this step."
    }
}

@Composable
private fun EvidenceDetail(asset: MediaAsset, onKeep: () -> Unit, onDelete: () -> Unit, onRetry: () -> Unit, onCancel: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EvidenceThumb(asset, 96.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(asset.label ?: asset.evidenceType.label, style = MaterialTheme.typography.titleMedium)
                Text("${asset.evidenceType.label} · ${formatBytes(asset.sizeBytes)}" + (asset.durationMs?.let { " · ${it / 1000}s" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("ID: ${asset.id}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    QualityBadge(asset.quality)
                    Pill(asset.storageTier.label, if (asset.storageTier == StorageTier.PERMANENT) Brand.Green else Brand.Grey)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Processing: ${asset.processingStatus.label} · Analysis: ${asset.analysisStatus.label}", style = MaterialTheme.typography.bodySmall)
        if (asset.framePaths.isNotEmpty()) Text("${asset.framePaths.size} representative frame(s) extracted", style = MaterialTheme.typography.bodySmall)
        asset.qualityNotes.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        asset.ocrText?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(8.dp))
            Text("On-device OCR text", style = MaterialTheme.typography.labelMedium)
            Text(it.take(400), style = MaterialTheme.typography.bodySmall)
        }
        if (asset.storageTier == StorageTier.TEMPORARY) {
            Spacer(Modifier.height(8.dp))
            Text("Temporary evidence may be removed by Android when storage is required.", style = MaterialTheme.typography.bodySmall, color = Brand.Amber)
        }
        Spacer(Modifier.height(14.dp))
        FlowRowButtons(asset, onKeep, onDelete, onRetry, onCancel)
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowButtons(asset: MediaAsset, onKeep: () -> Unit, onDelete: () -> Unit, onRetry: () -> Unit, onCancel: () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (asset.storageTier == StorageTier.TEMPORARY) Button(onClick = onKeep) { Text("Keep Evidence") }
        when (asset.processingStatus) {
            ProcessingStatus.PENDING, ProcessingStatus.PROCESSING -> OutlinedButton(onClick = onCancel) { Text("Cancel processing") }
            ProcessingStatus.FAILED, ProcessingStatus.CANCELLED -> OutlinedButton(onClick = onRetry) { Text("Retry processing") }
            ProcessingStatus.COMPLETED -> Unit
        }
        OutlinedButton(onClick = onDelete) { Text("Delete") }
    }
}
