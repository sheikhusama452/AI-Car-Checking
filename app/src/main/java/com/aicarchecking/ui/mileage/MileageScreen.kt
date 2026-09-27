package com.aicarchecking.ui.mileage

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aicarchecking.di.AppContainer
import com.aicarchecking.domain.model.AnalysisTask
import com.aicarchecking.domain.model.DistanceUnit
import com.aicarchecking.domain.model.EvidenceType
import com.aicarchecking.domain.model.InspectionStep
import com.aicarchecking.domain.model.Limitations
import com.aicarchecking.domain.model.MediaAsset
import com.aicarchecking.domain.model.MileageRecord
import com.aicarchecking.domain.model.MileageSource
import com.aicarchecking.domain.model.ProcessingStatus
import com.aicarchecking.domain.usecase.AnalysisOutcome
import com.aicarchecking.domain.usecase.MileageAssessment
import com.aicarchecking.domain.usecase.MileageConsistencyChecker
import com.aicarchecking.domain.usecase.MileageVerdict
import com.aicarchecking.media.ocr.OdometerParser
import com.aicarchecking.media.ocr.ServiceDocumentParser
import com.aicarchecking.navigation.Routes
import com.aicarchecking.ui.common.AppTopBar
import com.aicarchecking.ui.common.EvidenceThumb
import com.aicarchecking.ui.common.LimitationsNote
import com.aicarchecking.ui.common.Pill
import com.aicarchecking.ui.common.SectionHeader
import com.aicarchecking.ui.common.WarningBanner
import com.aicarchecking.ui.common.appViewModel
import com.aicarchecking.ui.theme.Brand
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class MileageViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    val inspectionId: String = checkNotNull(handle["inspectionId"])
    private val inspectionFlow = c.inspectionRepository.observe(inspectionId).filterNotNull()

    val assessment: StateFlow<MileageAssessment?> = inspectionFlow
        .flatMapLatest { c.mileageRepository.observeForVehicle(it.vehicleId) }
        .map { MileageConsistencyChecker.assess(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val documents: StateFlow<List<MediaAsset>> = c.evidenceRepository.observeForInspection(inspectionId)
        .map { l -> l.filter { it.evidenceType == EvidenceType.SERVICE_DOCUMENT } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    fun clearMessage() { _message.value = null }

    fun readDashboard() = viewModelScope.launch {
        val outcome = c.analyzeStep(inspectionId, InspectionStep.DASHBOARD, AnalysisTask.DASHBOARD_OCR, userConfirmedUpload = false)
        _message.value = when (outcome) {
            is AnalysisOutcome.Success -> outcome.summary
            is AnalysisOutcome.NeedsBetterEvidence -> outcome.message
            is AnalysisOutcome.Failed -> outcome.message
            AnalysisOutcome.NoEvidence -> "Capture a dashboard photo in the Dashboard step first."
            AnalysisOutcome.StillProcessing -> "The dashboard photo is still being processed. Try again in a moment."
            else -> null
        }
    }

    fun addManual(value: String, unit: DistanceUnit, source: MileageSource, dateText: String) = viewModelScope.launch {
        val v = value.filter { it.isDigit() }.toLongOrNull()
        if (v == null || v <= 0) { _message.value = "Enter a valid mileage."; return@launch }
        val date = if (dateText.isBlank()) System.currentTimeMillis() else
            runCatching { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(dateText)?.time }.getOrNull()
        if (date == null) { _message.value = "Date must be dd/mm/yyyy."; return@launch }
        val insp = c.inspectionRepository.get(inspectionId) ?: return@launch
        c.mileageRepository.add(
            MileageRecord(
                UUID.randomUUID().toString(), insp.vehicleId, if (source == MileageSource.DASHBOARD_MANUAL) inspectionId else null,
                source, OdometerParser.toKm(v, unit), v, unit, date, null, "Entered by user",
            )
        )
        if (source == MileageSource.DASHBOARD_MANUAL) c.inspectionRepository.save(insp.copy(mileageKm = OdometerParser.toKm(v, unit)))
    }

    fun addDocument(uri: android.net.Uri) = viewModelScope.launch {
        val insp = c.inspectionRepository.get(inspectionId) ?: return@launch
        runCatching { c.addEvidence.fromUri(insp, null, uri, EvidenceType.SERVICE_DOCUMENT) }
            .onFailure { _message.value = it.message ?: "Couldn't import this document." }
    }

    fun extractFromDocument(doc: MediaAsset) = viewModelScope.launch {
        val text = doc.ocrText
        if (text.isNullOrBlank()) { _message.value = "No readable text found. Try a clearer photo of the document."; return@launch }
        val facts = ServiceDocumentParser.parse(text)
        val odo = facts.mileage
        if (odo == null) { _message.value = "We couldn't find a mileage on this document. You can enter it manually."; return@launch }
        val insp = c.inspectionRepository.get(inspectionId) ?: return@launch
        c.mileageRepository.add(
            MileageRecord(
                UUID.randomUUID().toString(), insp.vehicleId, null, MileageSource.SERVICE_DOCUMENT,
                OdometerParser.toKm(odo.value, odo.unit), odo.value, odo.unit, facts.dateMillis ?: doc.createdAt, doc.id,
                "OCR: ${odo.raw}" + (facts.rawDate?.let { " · date $it" } ?: " · date not found, import date used"),
            )
        )
        _message.value = "Added ${"%,d".format(odo.value)} ${odo.unit.label}" +
            (facts.rawDate?.let { " dated $it" } ?: " (no date found — please check)") + ". Verify against the document."
    }

    fun delete(record: MileageRecord) = viewModelScope.launch { c.mileageRepository.delete(record.id) }
}

@Composable
fun MileageScreen(onBack: () -> Unit, navigate: (String) -> Unit) {
    val vm = appViewModel { c, h -> MileageViewModel(c, h) }
    val assessment by vm.assessment.collectAsStateWithLifecycle()
    val documents by vm.documents.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::addDocument) }
    val date = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    Scaffold(topBar = { AppTopBar("Mileage Check", onBack) }) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp)) {
            item {
                LimitationsNote(Limitations.MILEAGE, "Mileage rollback cannot be determined from a single reading.")
                Spacer(Modifier.height(12.dp))
                assessment?.let { a ->
                    val color = when (a.verdict) {
                        MileageVerdict.INCONSISTENT -> Brand.Orange
                        MileageVerdict.NO_CONFLICT -> Brand.Green
                        MileageVerdict.INSUFFICIENT -> Brand.Grey
                    }
                    Card(colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.14f))) {
                        Column(Modifier.padding(16.dp)) {
                            Text(a.verdict.title, style = MaterialTheme.typography.titleLarge, color = color)
                            Spacer(Modifier.height(6.dp))
                            Text(a.explanation, style = MaterialTheme.typography.bodyMedium)
                            a.conflicts.take(3).forEach { cf ->
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "• ${"%,d".format(cf.earlier.valueKm)} km (${cf.earlier.source.label}, ${date.format(Date(cf.earlier.recordedDate))}) " +
                                        "→ ${"%,d".format(cf.later.valueKm)} km (${cf.later.source.label}, ${date.format(Date(cf.later.recordedDate))})",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.readDashboard() }) { Text("Read dashboard (OCR)") }
                    OutlinedButton(onClick = { showAdd = true }) { Text("Enter reading") }
                }
                TextButton(onClick = { navigate(Routes.step(vm.inspectionId, InspectionStep.DASHBOARD)) }) { Text("Capture dashboard photo") }
                message?.let {
                    Spacer(Modifier.height(6.dp))
                    WarningBanner(it, color = Brand.Cyan)
                    TextButton(onClick = vm::clearMessage) { Text("Dismiss") }
                }
                SectionHeader("Mileage timeline")
                if (assessment?.timeline.isNullOrEmpty()) Text("No readings yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(assessment?.timeline.orEmpty(), key = { it.id }) { r ->
                val conflicting = assessment?.conflicts?.any { it.earlier.id == r.id || it.later.id == r.id } == true
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.layout.Box(
                        Modifier.size(12.dp).clip(CircleShape).background(if (conflicting) Brand.Orange else MaterialTheme.colorScheme.primary),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${"%,d".format(r.valueKm)} km" + if (r.unit == DistanceUnit.MILES) " (${"%,d".format(r.originalValue)} mi)" else "", style = MaterialTheme.typography.titleSmall)
                        Text("${date.format(Date(r.recordedDate))} · ${r.source.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (r.note.isNotBlank()) Text(r.note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (conflicting) Pill("Conflict", Brand.Orange)
                    IconButton(onClick = { vm.delete(r) }) { Icon(Icons.Filled.Delete, "Delete reading") }
                }
            }
            item {
                SectionHeader("Service documents") {
                    TextButton(onClick = { picker.launch(arrayOf("image/*", "application/pdf")) }) { Text("Add document") }
                }
                Text("Invoices, service receipts and inspection reports are read with on-device OCR.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
            }
            items(documents, key = { it.id }) { d ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    EvidenceThumb(d, 64.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(d.label ?: "Document", style = MaterialTheme.typography.titleSmall)
                        Text(d.processingStatus.label, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = { vm.extractFromDocument(d) }, enabled = d.processingStatus == ProcessingStatus.COMPLETED) { Text("Extract mileage") }
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    if (showAdd) {
        var value by remember { mutableStateOf("") }
        var dateText by remember { mutableStateOf("") }
        var unit by remember { mutableStateOf(DistanceUnit.KM) }
        var source by remember { mutableStateOf(MileageSource.DASHBOARD_MANUAL) }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Add mileage reading") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value, { value = it }, label = { Text("Reading") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DistanceUnit.entries.forEach { u -> FilterChip(selected = unit == u, onClick = { unit = u }, label = { Text(u.label) }) }
                    }
                    Text("Source", style = MaterialTheme.typography.labelMedium)
                    listOf(MileageSource.DASHBOARD_MANUAL, MileageSource.SERVICE_DOCUMENT, MileageSource.INSPECTION_REPORT, MileageSource.USER_ENTERED).forEach { s ->
                        FilterChip(selected = source == s, onClick = { source = s }, label = { Text(s.label) })
                    }
                    OutlinedTextField(dateText, { dateText = it }, label = { Text("Date (dd/mm/yyyy, blank = today)") }, singleLine = true)
                }
            },
            confirmButton = { TextButton(onClick = { vm.addManual(value, unit, source, dateText); showAdd = false }) { Text("Add") } },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } },
        )
    }
}
