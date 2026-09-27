package com.aicarchecking.ui.obd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aicarchecking.data.obd.DtcDictionary
import com.aicarchecking.di.AppContainer
import com.aicarchecking.domain.model.DtcCode
import com.aicarchecking.domain.model.ObdScan
import com.aicarchecking.domain.model.ObdSource
import com.aicarchecking.ui.common.AppTopBar
import com.aicarchecking.ui.common.IntegrationRequiredBanner
import com.aicarchecking.ui.common.LimitationsNote
import com.aicarchecking.ui.common.Pill
import com.aicarchecking.ui.common.SectionHeader
import com.aicarchecking.ui.common.WarningBanner
import com.aicarchecking.ui.common.appViewModel
import com.aicarchecking.ui.theme.Brand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class VoltageContext(val label: String) { ENGINE_OFF("Engine off"), ENGINE_RUNNING("Engine running") }

/** Indicative ranges only; one reading never determines battery or alternator health (spec §20). */
fun interpretVoltage(volts: Double, context: VoltageContext): Pair<String, String> = when (context) {
    VoltageContext.ENGINE_OFF -> when {
        volts >= 12.4 && volts <= 12.9 -> "Normal range indication" to "Resting voltage is within the typical range for a charged 12 V battery."
        volts < 12.4 && volts >= 12.0 -> "Potential issue" to "Resting voltage is lower than typical. The battery may be partly discharged."
        else -> "Needs testing" to "Voltage is outside the typical resting range. Have the battery and charging system tested."
    }
    VoltageContext.ENGINE_RUNNING -> when {
        volts >= 13.5 && volts <= 14.8 -> "Normal range indication" to "Charging voltage is within the typical range."
        volts < 13.5 -> "Potential issue" to "Charging voltage is lower than typical. The charging system should be tested."
        else -> "Needs testing" to "Charging voltage is higher than typical. Have the charging system tested."
    }
}

class ObdViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    val inspectionId: String? = handle.get<String>("inspectionId")?.takeIf { it.isNotBlank() }
    private val _scans = MutableStateFlow<List<ObdScan>>(emptyList())
    val scans = _scans.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    init { refresh() }

    private fun refresh() = viewModelScope.launch {
        _scans.value = inspectionId?.let { c.obdRepository.forInspection(it) }.orEmpty()
    }

    fun saveManual(codesText: String, voltage: String) = viewModelScope.launch {
        val id = inspectionId ?: return@launch
        val insp = c.inspectionRepository.get(id) ?: return@launch
        val codes = codesText.split(Regex("[,\\s]+")).map { it.trim().uppercase() }.filter { it.isNotEmpty() }
        val invalid = codes.filterNot(DtcDictionary::isValidFormat)
        if (invalid.isNotEmpty()) { _message.value = "Not a valid OBD code format: ${invalid.joinToString()}. Codes look like P0420."; return@launch }
        val volts = voltage.replace(',', '.').toDoubleOrNull()
        if (codes.isEmpty() && volts == null) { _message.value = "Enter at least one code or a voltage reading."; return@launch }
        c.obdRepository.save(
            ObdScan(
                id = UUID.randomUUID().toString(), vehicleId = insp.vehicleId, inspectionId = id,
                scannedAt = System.currentTimeMillis(), source = ObdSource.MANUAL,
                codes = codes.map { DtcCode(it, DtcDictionary.lookup(it)?.meaning ?: "Not in the local code list — look up the manufacturer definition") },
                batteryVoltage = volts, notes = "Entered manually",
            )
        )
        _message.value = "Saved. Codes were recorded as entered; they are not verified by the app."
        refresh()
    }

    fun delete(scan: ObdScan) = viewModelScope.launch { c.obdRepository.delete(scan.id); refresh() }
    fun clearMessage() { _message.value = null }
}

@Composable
fun ObdScreen(onBack: () -> Unit) {
    val vm = appViewModel { c, h -> ObdViewModel(c, h) }
    val scans by vm.scans.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var codes by remember { mutableStateOf("") }
    var voltage by remember { mutableStateOf("") }
    var lookup by remember { mutableStateOf("") }
    var vContext by remember { mutableStateOf(VoltageContext.ENGINE_RUNNING) }
    val date = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

    Scaffold(topBar = { AppTopBar("OBD Check", onBack) }) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp)) {
            item {
                IntegrationRequiredBanner(
                    "Bluetooth OBD-II adapter (Classic & BLE)",
                    "Live connection to ELM327-type adapters is planned for Phase 3. The adapter interface and PID/DTC decoders are built; the Bluetooth transport is not yet connected. Until then, enter codes read by a scanner or mechanic.",
                )
                Spacer(Modifier.height(12.dp))
                LimitationsNote("A trouble code shows what the engine computer detected, not which part has failed. Diagnose before replacing parts.")
                message?.let {
                    Spacer(Modifier.height(10.dp))
                    WarningBanner(it, color = Brand.Cyan)
                    TextButton(onClick = vm::clearMessage) { Text("Dismiss") }
                }
                SectionHeader("Look up a code")
                OutlinedTextField(
                    lookup, { lookup = it.uppercase().take(5) }, label = { Text("e.g. P0420") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters), modifier = Modifier.fillMaxWidth(),
                )
                if (lookup.length == 5) {
                    Spacer(Modifier.height(8.dp))
                    DtcExplanation(lookup)
                }

                if (vm.inspectionId != null) {
                    SectionHeader("Record scan for this inspection")
                    OutlinedTextField(codes, { codes = it }, label = { Text("Trouble codes (comma separated)") }, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(voltage, { voltage = it }, label = { Text("Battery voltage (optional, multimeter or OBD)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VoltageContext.entries.forEach { v -> FilterChip(selected = vContext == v, onClick = { vContext = v }, label = { Text(v.label) }) }
                    }
                    voltage.replace(',', '.').toDoubleOrNull()?.let { v ->
                        val (title, body) = interpretVoltage(v, vContext)
                        Text("$title — $body Battery health cannot be judged from one reading.", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { vm.saveManual(codes, voltage); codes = "" }) { Text("Save to inspection") }
                    SectionHeader("Recorded scans")
                    if (scans.isEmpty()) Text("No OBD data recorded for this inspection.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Spacer(Modifier.height(12.dp))
                    Text("Open OBD from an inspection to save codes to its report.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(scans, key = { it.id }) { scan ->
                Card(Modifier.fillMaxWidth().padding(bottom = 10.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(14.dp)) {
                        Row {
                            Text(date.format(Date(scan.scannedAt)), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            Pill(scan.source.label, if (scan.source == ObdSource.DEMO) Brand.Amber else Brand.Cyan)
                        }
                        if (scan.codes.isEmpty()) Text("No trouble codes recorded.")
                        scan.codes.forEach { Spacer(Modifier.height(8.dp)); DtcExplanation(it.code) }
                        scan.batteryVoltage?.let { Text("Battery voltage: $it V", style = MaterialTheme.typography.bodyMedium) }
                        if (scan.liveData.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text("Live data", style = MaterialTheme.typography.labelMedium)
                            scan.liveData.forEach { (k, v) -> Text("$k: $v", style = MaterialTheme.typography.bodySmall) }
                        }
                        if (scan.notes.isNotBlank()) Text(scan.notes, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (scan.source != ObdSource.DEMO) TextButton(onClick = { vm.delete(scan) }) { Text("Delete") }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun DtcExplanation(code: String) {
    val info = DtcDictionary.lookup(code)
    Column {
        Text(code, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        when {
            info != null -> {
                Text(info.meaning, style = MaterialTheme.typography.bodyMedium)
                Text("Possible causes: " + info.possibleCauses.joinToString(), style = MaterialTheme.typography.bodySmall)
                Text("Recommended: ${info.recommendation}", style = MaterialTheme.typography.bodySmall)
            }
            DtcDictionary.isValidFormat(code) -> Text(
                "This code isn't in the app's local list. It may be manufacturer-specific. Ask a mechanic or check the manufacturer's definition — the app won't guess.",
                style = MaterialTheme.typography.bodySmall,
            )
            else -> Text("Not a valid OBD code format (example: P0420).", style = MaterialTheme.typography.bodySmall)
        }
    }
}
