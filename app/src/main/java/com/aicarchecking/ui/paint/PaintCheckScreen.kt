package com.aicarchecking.ui.paint

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aicarchecking.di.AppContainer
import com.aicarchecking.domain.model.Finding
import com.aicarchecking.domain.model.FindingCategory
import com.aicarchecking.domain.model.InspectionStep
import com.aicarchecking.domain.model.Limitations
import com.aicarchecking.domain.model.MediaAsset
import com.aicarchecking.domain.model.PaintStatus
import com.aicarchecking.domain.model.Panel
import com.aicarchecking.domain.usecase.ReportBuilder
import com.aicarchecking.navigation.Routes
import com.aicarchecking.ui.common.AppTopBar
import com.aicarchecking.ui.common.ConfidenceIndicator
import com.aicarchecking.ui.common.EvidenceThumb
import com.aicarchecking.ui.common.FindingCard
import com.aicarchecking.ui.common.LimitationsNote
import com.aicarchecking.ui.common.SectionHeader
import com.aicarchecking.ui.common.WarningBanner
import com.aicarchecking.ui.common.appViewModel
import com.aicarchecking.ui.theme.Brand
import com.aicarchecking.ui.theme.paintStatusColor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class PaintUiState(
    val panelFindings: Map<Panel, Finding> = emptyMap(),
    val allPaintFindings: List<Finding> = emptyList(),
    val accidentFindings: List<Finding> = emptyList(),
    val media: Map<String, MediaAsset> = emptyMap(),
    val isDemo: Boolean = false,
)

class PaintViewModel(c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    val inspectionId: String = checkNotNull(handle["inspectionId"])
    val state: StateFlow<PaintUiState> = combine(
        c.findingRepository.observeForInspection(inspectionId),
        c.evidenceRepository.observeForInspection(inspectionId),
        c.inspectionRepository.observe(inspectionId),
    ) { findings, media, inspection ->
        PaintUiState(
            panelFindings = ReportBuilder.panelStatuses(findings),
            allPaintFindings = findings.filter { it.category == FindingCategory.PAINT },
            accidentFindings = findings.filter { it.category == FindingCategory.ACCIDENT_REPAIR },
            media = media.associateBy { it.id },
            isDemo = inspection?.isDemo == true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PaintUiState())
}

/** Normalised panel regions on a top-down car diagram (front at the top, car's left on the left). */
private val panelRects: Map<Panel, Rect> = mapOf(
    Panel.FRONT_BUMPER to Rect(0.20f, 0.00f, 0.80f, 0.065f),
    Panel.BONNET to Rect(0.23f, 0.075f, 0.77f, 0.30f),
    Panel.FRONT_LEFT_FENDER to Rect(0.08f, 0.075f, 0.215f, 0.30f),
    Panel.FRONT_RIGHT_FENDER to Rect(0.785f, 0.075f, 0.92f, 0.30f),
    Panel.FRONT_LEFT_DOOR to Rect(0.08f, 0.31f, 0.215f, 0.50f),
    Panel.FRONT_RIGHT_DOOR to Rect(0.785f, 0.31f, 0.92f, 0.50f),
    Panel.REAR_LEFT_DOOR to Rect(0.08f, 0.51f, 0.215f, 0.70f),
    Panel.REAR_RIGHT_DOOR to Rect(0.785f, 0.51f, 0.92f, 0.70f),
    Panel.ROOF to Rect(0.23f, 0.38f, 0.77f, 0.67f),
    Panel.REAR_LEFT_FENDER to Rect(0.08f, 0.71f, 0.215f, 0.925f),
    Panel.REAR_RIGHT_FENDER to Rect(0.785f, 0.71f, 0.92f, 0.925f),
    Panel.TRUNK to Rect(0.23f, 0.76f, 0.77f, 0.925f),
    Panel.REAR_BUMPER to Rect(0.20f, 0.935f, 0.80f, 1.00f),
)

private val shortLabels = mapOf(
    Panel.FRONT_BUMPER to "F. Bumper", Panel.BONNET to "Bonnet", Panel.ROOF to "Roof", Panel.TRUNK to "Trunk",
    Panel.REAR_BUMPER to "R. Bumper", Panel.FRONT_LEFT_FENDER to "FL", Panel.FRONT_RIGHT_FENDER to "FR",
    Panel.FRONT_LEFT_DOOR to "FL door", Panel.FRONT_RIGHT_DOOR to "FR door", Panel.REAR_LEFT_DOOR to "RL door",
    Panel.REAR_RIGHT_DOOR to "RR door", Panel.REAR_LEFT_FENDER to "RL", Panel.REAR_RIGHT_FENDER to "RR",
)

@Composable
fun PaintMap(statusOf: (Panel) -> PaintStatus, onPanel: (Panel) -> Unit, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val outline = MaterialTheme.colorScheme.outline
    val glass = MaterialTheme.colorScheme.surfaceVariant
    val labelColor = MaterialTheme.colorScheme.onSurface
    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(0.55f)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val nx = offset.x / size.width
                    val ny = offset.y / size.height
                    panelRects.entries.firstOrNull { it.value.contains(Offset(nx, ny)) }?.let { onPanel(it.key) }
                }
            },
    ) {
        fun Rect.px() = Rect(left * size.width, top * size.height, right * size.width, bottom * size.height)
        // glass areas (not panels)
        listOf(Rect(0.23f, 0.31f, 0.77f, 0.37f), Rect(0.23f, 0.68f, 0.77f, 0.75f)).forEach { r ->
            val p = r.px()
            drawRoundRect(glass, p.topLeft, p.size, CornerRadius(12f, 12f))
        }
        panelRects.forEach { (panel, r) ->
            val p = r.px()
            val status = statusOf(panel)
            drawRoundRect(paintStatusColor(status).copy(alpha = if (status == PaintStatus.NOT_CHECKED) 0.18f else 0.85f), p.topLeft, p.size, CornerRadius(14f, 14f))
            drawRoundRect(outline, p.topLeft, p.size, CornerRadius(14f, 14f), style = Stroke(width = 2f))
            val label = shortLabels[panel] ?: panel.label
            val layout = measurer.measure(label, TextStyle(fontSize = 10.sp, color = labelColor))
            drawText(
                layout,
                topLeft = Offset(p.center.x - layout.size.width / 2f, p.center.y - layout.size.height / 2f),
            )
        }
        // wheels
        listOf(0.19f, 0.81f).forEach { y ->
            drawRoundRect(Color.Black.copy(alpha = 0.6f), Offset(0.02f * size.width, y * size.height - 30f), Size(0.05f * size.width, 60f), CornerRadius(8f, 8f))
            drawRoundRect(Color.Black.copy(alpha = 0.6f), Offset(0.93f * size.width, y * size.height - 30f), Size(0.05f * size.width, 60f), CornerRadius(8f, 8f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PaintCheckScreen(onBack: () -> Unit, navigate: (String) -> Unit) {
    val vm = appViewModel { c, h -> PaintViewModel(c, h) }
    val state by vm.state.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<Panel?>(null) }
    val statusOf: (Panel) -> PaintStatus = { p -> state.panelFindings[p]?.let { PaintStatus.fromCode(it.status) } ?: PaintStatus.NOT_CHECKED }

    Scaffold(topBar = { AppTopBar("Paint Check", onBack) }) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp)) {
            item {
                Text("Painted or Original?", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Check whether visible evidence suggests original paint, repainting or repair.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                LimitationsNote(Limitations.PAINT, Limitations.GENERAL)
                SectionHeader("Paint Map")
                Text("Tap a panel for evidence, confidence and how to verify.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    PaintMap(statusOf, { selected = it }, Modifier.fillMaxWidth(0.8f))
                }
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PaintStatus.entries.forEach { s ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(12.dp).clip(CircleShape).background(paintStatusColor(s)))
                            Spacer(Modifier.width(4.dp))
                            Text(s.label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                val concerns = ReportBuilder.paintConcerns(state.allPaintFindings)
                if (concerns.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    WarningBanner("Possible repaint/repair indicators on: ${concerns.joinToString { it.label }}. Verify with a paint thickness gauge.", color = Brand.Amber)
                }
                SectionHeader("Panels")
            }
            items(Panel.entries, key = { it.name }) { panel ->
                val status = statusOf(panel)
                Surface(
                    Modifier.fillMaxWidth().padding(bottom = 6.dp).clickable { selected = panel },
                    shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surface,
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(14.dp).clip(CircleShape).background(paintStatusColor(status)))
                        Spacer(Modifier.width(10.dp))
                        Text(panel.label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        Text(status.label, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                SectionHeader("Accident / Repair Indicators")
                if (state.accidentFindings.isEmpty()) {
                    Text("Not analyzed yet. Run 'Accident / Repair Analysis' on body steps.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(state.accidentFindings, key = { it.id }) { FindingCard(it, Modifier.padding(bottom = 8.dp)) }
            item {
                Spacer(Modifier.height(8.dp))
                LimitationsNote(Limitations.STRUCTURAL)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { navigate(Routes.step(vm.inspectionId, InspectionStep.FRONT)) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Capture body panels")
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    selected?.let { panel ->
        val finding = state.panelFindings[panel]
        val others = state.allPaintFindings.filter { it.panel == panel && it.id != finding?.id }
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                Text(panel.label, style = MaterialTheme.typography.headlineSmall)
                val status = statusOf(panel)
                Text(status.label, style = MaterialTheme.typography.titleMedium, color = paintStatusColor(status))
                Spacer(Modifier.height(8.dp))
                if (finding == null) {
                    Text("This panel has not been analyzed. Capture it in the relevant body step and run Paint Analysis.")
                } else {
                    ConfidenceIndicator(finding.confidence)
                    Spacer(Modifier.height(8.dp))
                    val evidence = finding.evidenceIds.mapNotNull { state.media[it] }
                    if (evidence.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(evidence, key = { it.id }) { EvidenceThumb(it) } }
                        Spacer(Modifier.height(8.dp))
                    }
                    Text("AI explanation", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(finding.observation)
                    if (finding.possibleCauses.isNotEmpty()) Text("Possible causes: ${finding.possibleCauses.joinToString()}", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Text("Verification method", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(finding.verificationMethod)
                    Text(finding.recommendation, style = MaterialTheme.typography.bodySmall)
                    if (others.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("${others.size} other result(s) for this panel from other photos — the most serious is shown.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(Limitations.PAINT, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
