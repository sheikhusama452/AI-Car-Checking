package com.aicarchecking.ui.report

import android.content.Context
import android.content.Intent
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aicarchecking.data.local.InspectionReportEntity
import com.aicarchecking.di.AppContainer
import com.aicarchecking.domain.model.ActionBucket
import com.aicarchecking.domain.model.Limitations
import com.aicarchecking.domain.model.Severity
import com.aicarchecking.domain.usecase.InspectionReportModel
import com.aicarchecking.domain.usecase.MileageConsistencyChecker
import com.aicarchecking.domain.usecase.ReportBuilder
import com.aicarchecking.ui.common.AppTopBar
import com.aicarchecking.ui.common.FindingCard
import com.aicarchecking.ui.common.LimitationsNote
import com.aicarchecking.ui.common.Pill
import com.aicarchecking.ui.common.SectionHeader
import com.aicarchecking.ui.common.SeverityBadge
import com.aicarchecking.ui.common.appViewModel
import com.aicarchecking.ui.theme.Brand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class ReportViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    val inspectionId: String = checkNotNull(handle["inspectionId"])
    private val _model = MutableStateFlow<InspectionReportModel?>(null)
    val model = _model.asStateFlow()
    private val _exporting = MutableStateFlow(false)
    val exporting = _exporting.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun load() = viewModelScope.launch {
        val inspection = c.inspectionRepository.get(inspectionId) ?: return@launch
        val vehicle = c.vehicleRepository.get(inspection.vehicleId) ?: return@launch
        val findings = c.findingRepository.listForInspection(inspectionId)
        val media = c.evidenceRepository.listForInspection(inspectionId)
        val mileage = MileageConsistencyChecker.assess(c.mileageRepository.listForVehicle(vehicle.id))
        val scans = c.obdRepository.forInspection(inspectionId)
        _model.value = ReportBuilder.build(vehicle, inspection, findings, media, mileage, scans)
    }

    fun exportPdf(context: Context) = viewModelScope.launch {
        val m = _model.value ?: return@launch
        _exporting.value = true
        runCatching {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
            val file = File(c.storage.reportsDir, "AI_Car_Checking_Report_$stamp.pdf")
            c.newPdfGenerator().generate(m, file)
            c.db.reportDao().insert(InspectionReportEntity(UUID.randomUUID().toString(), inspectionId, System.currentTimeMillis(), file.absolutePath, m.completeness.percent))
            file
        }.onSuccess { file ->
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "AI Car Checking report — ${m.vehicle.displayName}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "Export report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            _error.value = "We couldn't create the PDF. Check free storage and try again."
        }
        _exporting.value = false
    }

    fun clearError() { _error.value = null }
}

@Composable
fun ReportScreen(onBack: () -> Unit) {
    val vm = appViewModel { c, h -> ReportViewModel(c, h) }
    val model by vm.model.collectAsStateWithLifecycle()
    val exporting by vm.exporting.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.load() }

    Scaffold(topBar = { AppTopBar("AI Vehicle Inspection Report", onBack) }) { padding ->
        val m = model
        if (m == null) {
            Column(Modifier.padding(padding).fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        val date = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp)) {
            item {
                Text("AI CAR CHECKING REPORT", style = MaterialTheme.typography.headlineSmall)
                if (m.isDemo) Pill("DEMO MODE — sample data", Brand.Amber)
                Spacer(Modifier.height(10.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp)) {
                        KeyValue("Vehicle", m.vehicle.displayName)
                        KeyValue("Mileage", m.inspection.mileageKm?.let { "%,d km".format(it) } ?: "Not recorded")
                        KeyValue("Inspection Date", date.format(Date(m.inspection.startedAt)))
                        KeyValue("Inspection Type", m.inspection.type.label)
                        Spacer(Modifier.height(8.dp))
                        Text("Evidence Completeness: ${m.completeness.percent}%", style = MaterialTheme.typography.titleMedium)
                        LinearProgressIndicator(progress = { m.completeness.percent / 100f }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).height(8.dp).clip(CircleShape))
                        Text(ReportBuilder.methodology(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                SectionHeader("Summary")
                Summary("Paint", m.paintHeadline)
                Summary("Mileage", m.mileageHeadline)
                Summary("Engine", m.engineHeadline)
                Summary("OBD", m.obdHeadline)
                Summary("Tyres", m.tyresHeadline)
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Finding Summary: ", style = MaterialTheme.typography.labelLarge)
                    Severity.entries.reversed().forEach { s ->
                        SeverityBadge(s); Text(" ${m.severityCounts[s] ?: 0}  ", style = MaterialTheme.typography.labelMedium)
                    }
                }

                SectionHeader("What to verify before buying")
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        m.whatToVerify.forEachIndexed { i, s ->
                            Text("${i + 1}. $s", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(vertical = 3.dp))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "This report supports your decision — it does not tell you whether to buy. Physically verify every item above.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = { vm.exportPdf(context) }, enabled = !exporting, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    if (exporting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Filled.PictureAsPdf, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Export PDF report")
                }
                error?.let {
                    Text(it, color = Brand.Red, modifier = Modifier.padding(top = 6.dp))
                    TextButton(onClick = vm::clearError) { Text("Dismiss") }
                }
            }

            ActionBucket.entries.forEach { bucket ->
                val list = m.buckets[bucket].orEmpty()
                if (list.isNotEmpty()) {
                    item(key = "h_${bucket.name}") { SectionHeader("${bucket.title} (${list.size})") }
                    items(list, key = { "${bucket.name}_${it.id}" }) { FindingCard(it, Modifier.padding(bottom = 8.dp)) }
                }
            }

            item {
                SectionHeader("Report sections")
                m.sections.forEach { s ->
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Text(s.section.title, style = MaterialTheme.typography.titleSmall)
                        Text(s.headline, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider()
                }
                Spacer(Modifier.height(12.dp))
                LimitationsNote(Limitations.GENERAL, Limitations.PAINT, Limitations.MILEAGE, Limitations.ENGINE, Limitations.ACCIDENT)
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun KeyValue(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(k, Modifier.weight(0.45f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(v, Modifier.weight(0.55f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Summary(title: String, text: String) {
    Column(Modifier.padding(vertical = 5.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
