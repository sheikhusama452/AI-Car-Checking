package com.aicarchecking.ui.inspection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import com.aicarchecking.domain.model.InspectionStatus
import com.aicarchecking.domain.model.InspectionStep
import com.aicarchecking.domain.model.InspectionType
import com.aicarchecking.domain.model.Limitations
import com.aicarchecking.navigation.Routes
import com.aicarchecking.ui.common.AppTopBar
import com.aicarchecking.ui.common.LimitationsNote
import com.aicarchecking.ui.common.Pill
import com.aicarchecking.ui.common.SectionHeader
import com.aicarchecking.ui.common.appViewModel
import com.aicarchecking.ui.theme.Brand

@Composable
fun StartInspectionScreen(navigate: (String) -> Unit, onStarted: (String, InspectionStep?) -> Unit) {
    val vm = appViewModel { c, h -> StartInspectionViewModel(c, h) }
    val vehicles by vm.vehicles.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val type by vm.type.collectAsStateWithLifecycle()
    val effectiveSelected = selected ?: vehicles.firstOrNull()?.id

    Scaffold(topBar = { AppTopBar("Start Car Inspection") }) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp)) {
            item {
                vm.preselectedStep?.let {
                    Pill("Quick start: ${it.title}", MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.height(8.dp))
                }
                Text("1. Select or create the vehicle", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
            }
            items(vehicles, key = { it.id }) { v ->
                val isSel = v.id == effectiveSelected
                Card(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable { vm.select(v.id) },
                    border = if (isSel) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = isSel, onClick = { vm.select(v.id) })
                        Column {
                            Text(v.displayName, style = MaterialTheme.typography.titleSmall)
                            Text(listOf(v.variant, v.registration, if (v.isDemo) "Demo" else "").filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                OutlinedButton(onClick = { navigate(Routes.vehicleEdit()) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Create new vehicle")
                }
                Spacer(Modifier.height(18.dp))
                Text("2. Inspection type", style = MaterialTheme.typography.titleMedium)
                InspectionType.entries.forEach { t ->
                    Row(Modifier.fillMaxWidth().clickable { vm.setType(t) }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = t == type, onClick = { vm.setType(t) })
                        Text(t.label + if (t == InspectionType.PRE_PURCHASE) "  (recommended)" else "")
                    }
                }
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = { vm.start(onStarted) },
                    enabled = effectiveSelected != null,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text("Begin guided inspection") }
                if (vehicles.isEmpty()) {
                    Text("Create a vehicle first — every inspection belongs to a vehicle.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

@Composable
fun InspectionFlowScreen(onBack: () -> Unit, navigate: (String) -> Unit) {
    val vm = appViewModel { c, h -> InspectionFlowViewModel(c, h) }
    val overview by vm.overview.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.refreshObd() }

    Scaffold(topBar = {
        AppTopBar(overview?.vehicle?.displayName ?: "Inspection", onBack) {
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete inspection") }
        }
    }) { padding ->
        val o = overview ?: return@Scaffold
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp)) {
            item {
                if (o.inspection.isDemo) {
                    Pill("DEMO MODE — sample data", Brand.Amber)
                    Spacer(Modifier.height(8.dp))
                }
                Text(o.inspection.type.label, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text("Inspection Completeness: ${o.completeness.percent}%", style = MaterialTheme.typography.titleSmall)
                LinearProgressIndicator(
                    progress = { o.completeness.percent / 100f },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).height(8.dp).clip(CircleShape),
                )
                Text(
                    "${o.completeness.completedSteps.size} of ${InspectionStep.evidenceSteps.size} steps have usable evidence. This measures evidence collected, not car condition.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolTile(Icons.Filled.FormatPaint, "Paint Check", Modifier.weight(1f)) { navigate(Routes.paint(vm.inspectionId)) }
                    ToolTile(Icons.Filled.Speed, "Mileage", Modifier.weight(1f)) { navigate(Routes.mileage(vm.inspectionId)) }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolTile(Icons.Filled.Bluetooth, "OBD", Modifier.weight(1f)) { navigate(Routes.obd(vm.inspectionId)) }
                    ToolTile(Icons.Filled.Summarize, "AI Report", Modifier.weight(1f)) { navigate(Routes.report(vm.inspectionId)) }
                }
                SectionHeader("Guided steps")
            }
            items(o.steps, key = { it.step.name }) { sp ->
                StepRow(sp) {
                    when (sp.step) {
                        InspectionStep.FINAL_REPORT -> navigate(Routes.report(vm.inspectionId))
                        else -> navigate(Routes.step(vm.inspectionId, sp.step))
                    }
                }
            }
            item {
                Spacer(Modifier.height(12.dp))
                if (o.inspection.status != InspectionStatus.COMPLETED) {
                    OutlinedButton(onClick = { vm.complete() }, modifier = Modifier.fillMaxWidth()) { Text("Mark inspection complete") }
                }
                Spacer(Modifier.height(12.dp))
                LimitationsNote(Limitations.GENERAL)
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete inspection?") },
            text = { Text("This deletes the inspection, its findings, reports and evidence files from this phone.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(onBack) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ToolTile(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier.clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun StepRow(sp: StepProgress, onClick: () -> Unit) {
    val (icon, tint, status) = when {
        sp.skipped -> Triple(Icons.Filled.RemoveCircleOutline, Brand.Grey, "Skipped")
        sp.hasConcern -> Triple(Icons.Filled.Warning, Brand.Amber, "${sp.findingCount} finding(s) to verify")
        sp.findingCount > 0 -> Triple(Icons.Filled.CheckCircle, Brand.Green, "Analyzed")
        sp.evidenceCount > 0 -> Triple(Icons.Filled.CheckCircle, Brand.Cyan, "${sp.evidenceCount} evidence item(s)")
        else -> Triple(null, Brand.Grey, if (sp.step == InspectionStep.FINAL_REPORT) "Generate when ready" else "Not captured")
    }
    Surface(
        Modifier.fillMaxWidth().padding(bottom = 6.dp).clickable(onClick = onClick),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) { Text("${sp.step.number}", style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(sp.step.title, style = MaterialTheme.typography.titleSmall)
                Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (icon != null) Icon(icon, null, tint = tint)
        }
    }
}
