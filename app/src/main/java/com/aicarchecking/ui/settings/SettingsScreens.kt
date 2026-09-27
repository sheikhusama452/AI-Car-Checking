package com.aicarchecking.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aicarchecking.BuildConfig
import com.aicarchecking.data.settings.AppSettings
import com.aicarchecking.data.settings.ProviderType
import com.aicarchecking.data.settings.ThemeMode
import com.aicarchecking.di.AppContainer
import com.aicarchecking.media.storage.StorageStats
import com.aicarchecking.navigation.Routes
import com.aicarchecking.ui.common.AppTopBar
import com.aicarchecking.ui.common.IntegrationRequiredBanner
import com.aicarchecking.ui.common.SectionHeader
import com.aicarchecking.ui.common.WarningBanner
import com.aicarchecking.ui.common.appViewModel
import com.aicarchecking.ui.common.formatBytes
import com.aicarchecking.ui.theme.Brand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val settings: StateFlow<AppSettings> = c.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())
    private val _stats = MutableStateFlow<StorageStats?>(null)
    val stats = _stats.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun setTheme(m: ThemeMode) = viewModelScope.launch { c.settings.setTheme(m) }
    fun setProvider(p: ProviderType) = viewModelScope.launch { c.settings.setProvider(p) }
    fun setModel(m: String) = viewModelScope.launch { c.settings.setGeminiModel(m) }
    fun setConsent(v: Boolean) = viewModelScope.launch { c.settings.setAiConsent(v) }
    fun saveKey(key: String) = viewModelScope.launch {
        c.settings.setGeminiKey(key)
        if (key.isNotBlank()) c.settings.setProvider(ProviderType.GEMINI)
        _message.value = if (key.isBlank()) "API key removed." else "API key saved (encrypted on this device)."
    }

    fun loadStats() = viewModelScope.launch { _stats.value = c.storage.stats() }

    fun clearCache() {
        viewModelScope.launch {
        val n = c.evidenceRepository.clearTemporaryEvidence()
        _message.value = "Deleted $n temporary evidence item(s) and cached frames."
        loadStats()
        }
    }

    fun deleteReports() {
        viewModelScope.launch {
            c.storage.deleteAllReports(); c.db.reportDao().deleteAll()
            _message.value = "Exported reports deleted."
            loadStats()
        }
    }

    fun clearAiHistory() {
        viewModelScope.launch {
            c.aiAnalysisRepository.clearHistory()
            _message.value = "AI analysis history cleared. Findings remain in their inspections."
        }
    }

    fun cleanOrphans() = viewModelScope.launch {
        val referenced = c.db.mediaAssetDao().listAll()
            .flatMap { listOfNotNull(it.localPath, it.thumbnailPath) + it.framePaths.map { f -> f.substringBefore('|') } }.toSet()
        val n = c.storage.cleanOrphans(referenced)
        _message.value = "Removed $n orphaned file(s)."
        loadStats()
    }

    fun removeDemo() {
        viewModelScope.launch { c.demoSeeder.clear(); _message.value = "Demo data removed." }
    }

    fun clearMessage() { _message.value = null }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, navigate: (String) -> Unit) {
    val vm = appViewModel { c, _ -> SettingsViewModel(c) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    Scaffold(topBar = { AppTopBar("Settings", onBack) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            SectionHeader("Appearance")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { m -> FilterChip(selected = settings.themeMode == m, onClick = { vm.setTheme(m) }, label = { Text(m.label) }) }
            }
            SectionHeader("General")
            NavRow("AI Provider", settings.providerType.label) { navigate(Routes.AI_PROVIDER) }
            NavRow("Storage Manager", "Temporary cache vs permanent evidence") { navigate(Routes.STORAGE) }
            NavRow("Privacy", "How your evidence is handled") { navigate(Routes.PRIVACY) }
            NavRow("About", "AI Car Checking ${BuildConfig.VERSION_NAME}") { navigate(Routes.ABOUT) }
            SectionHeader("Coming later")
            IntegrationRequiredBanner("Maintenance assistant, service history & Digital Car Passport", "Planned for Phase 3.")
            Spacer(Modifier.height(8.dp))
            IntegrationRequiredBanner("Language: Urdu & Roman Urdu", "The UI is English for now; strings are resource-based so more languages can be added.")
        }
    }
}

@Composable
private fun NavRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Filled.ChevronRight, null)
    }
    HorizontalDivider()
}

@Composable
fun AiProviderScreen(onBack: () -> Unit) {
    val vm = appViewModel { c, _ -> SettingsViewModel(c) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var key by remember { mutableStateOf("") }
    var model by remember(settings.geminiModel) { mutableStateOf(settings.geminiModel) }

    Scaffold(topBar = { AppTopBar("AI Provider", onBack) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "AI analysis is optional. Evidence quality checks, dashboard OCR, the mileage timeline, safety guidance and Demo Mode all work on your phone without any provider.",
                style = MaterialTheme.typography.bodyMedium,
            )
            ProviderType.entries.forEach { p ->
                Row(Modifier.fillMaxWidth().clickable { vm.setProvider(p) }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = settings.providerType == p, onClick = { vm.setProvider(p) })
                    Text(p.label)
                }
            }
            IntegrationRequiredBanner("OpenAI, Claude and on-device models", "The provider interface supports them; they are not connected in this version.")
            if (settings.providerType == ProviderType.GEMINI) {
                SectionHeader("Google Gemini")
                Text(if (settings.hasGeminiKey) "An API key is saved (encrypted with Android Keystore)." else "No API key saved.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    key, { key = it }, label = { Text("Your Gemini API key") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.saveKey(key); key = "" }, enabled = key.isNotBlank()) { Text("Save key") }
                    if (settings.hasGeminiKey) OutlinedButton(onClick = { vm.saveKey("") }) { Text("Remove key") }
                }
                OutlinedTextField(model, { model = it }, label = { Text("Model name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { vm.setModel(model) }) { Text("Save model") }
                WarningBanner(
                    "Your key is used only from this phone. For a public release, route AI calls through your own server so no end-user key is needed.",
                    color = Brand.Amber,
                )
            }
            SectionHeader("Consent")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Ask before sending evidence", style = MaterialTheme.typography.titleSmall)
                    Text("When on, the app asks every time before selected evidence is sent to the AI provider.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = !settings.aiConsentAccepted, onCheckedChange = { vm.setConsent(!it) })
            }
            message?.let { WarningBanner(it, color = Brand.Cyan); TextButton(onClick = vm::clearMessage) { Text("OK") } }
        }
    }
}

@Composable
fun StorageScreen(onBack: () -> Unit) {
    val vm = appViewModel { c, _ -> SettingsViewModel(c) }
    val stats by vm.stats.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    LaunchedEffect(Unit) { vm.loadStats() }

    Scaffold(topBar = { AppTopBar("Storage Manager", onBack) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val s = stats
            if (s == null) Text("Calculating…") else {
                StatRow("Photos", s.photosBytes)
                StatRow("Videos", s.videosBytes)
                StatRow("Audio", s.audioBytes)
                StatRow("Reports", s.reportsBytes)
                StatRow("Thumbnails", s.thumbnailsBytes)
                HorizontalDivider()
                StatRow("Temporary Cache", s.temporaryBytes)
                Text("Temporary evidence may be removed by Android when storage is required.", style = MaterialTheme.typography.bodySmall, color = Brand.Amber)
                StatRow("Permanent Evidence", s.permanentBytes)
                Text("Kept until you delete it. Use 'Keep Evidence' on an item to move it here.", style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                StatRow("Free space on phone", s.freeBytes)
            }
            SectionHeader("Manage Storage")
            OutlinedButton(onClick = { confirm = "Delete all temporary evidence and cached frames? Evidence you chose to keep is not affected." to { vm.clearCache() } }, modifier = Modifier.fillMaxWidth()) { Text("Delete Cache") }
            OutlinedButton(onClick = { confirm = "Delete all exported PDF reports?" to { vm.deleteReports() } }, modifier = Modifier.fillMaxWidth()) { Text("Delete Reports") }
            OutlinedButton(onClick = { confirm = "Clear the AI analysis log? Findings are kept." to { vm.clearAiHistory() } }, modifier = Modifier.fillMaxWidth()) { Text("Clear AI History") }
            OutlinedButton(onClick = { vm.cleanOrphans() }, modifier = Modifier.fillMaxWidth()) { Text("Clean orphaned files") }
            OutlinedButton(onClick = { confirm = "Remove the demo vehicle and all demo data?" to { vm.removeDemo() } }, modifier = Modifier.fillMaxWidth()) { Text("Remove Demo Mode data") }
            Text("To delete a specific inspection's media, open the inspection and delete evidence items or the whole inspection. Deleting a car removes everything linked to it.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            message?.let { WarningBanner(it, color = Brand.Cyan); TextButton(onClick = vm::clearMessage) { Text("OK") } }
        }
    }
    confirm?.let { (text, action) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { action(); confirm = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun StatRow(label: String, bytes: Long) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, Modifier.weight(1f))
        Text(formatBytes(bytes), style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    InfoScreen(
        "Privacy", onBack,
        listOf(
            "Local-first" to "Vehicles, inspections, findings and evidence are stored only on this phone in app-private storage. They are excluded from cloud backup.",
            "No automatic uploads" to "Nothing is uploaded automatically. Evidence is sent to an AI provider only after you tap AI Analyze and confirm. Only the selected photos, representative video frames or audio are sent; original videos stay on the phone.",
            "What stays on the phone" to "Quality checks, dashboard/document OCR (ML Kit on-device), mileage timeline, safety guidance and Demo Mode never leave the device.",
            "Your control" to "Delete individual evidence, an inspection, a vehicle, the cache, reports or AI history at any time. Exported reports are shared only when you choose.",
            "API keys" to "No production API keys are built into the app. A key you enter is encrypted with the Android Keystore and never shown or logged.",
            "Untrusted content" to "Photos, videos, documents and AI responses are treated as untrusted input. AI output is validated before display and never executed.",
        ),
    )
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    InfoScreen(
        "About", onBack,
        listOf(
            "AI Car Checking" to "Painted or Original? Check Before You Buy.\nAI-powered vehicle inspection, diagnosis and evidence assistant. Version ${BuildConfig.VERSION_NAME}.",
            "Our promise" to "See the evidence. Understand the risks. Know what to check next.",
            "Important limitations" to "AI analysis is based only on the evidence provided. A photograph cannot guarantee original factory paint. Dashboard mileage alone cannot prove or disprove rollback. Audio/video analysis cannot confirm internal engine damage. Visible evidence cannot rule out hidden structural damage.",
            "Decision support" to "The app never says BUY or DON'T BUY. It shows evidence, confidence and what to verify so you can decide.",
        ),
    )
}

@Composable
private fun InfoScreen(title: String, onBack: () -> Unit, sections: List<Pair<String, String>>) {
    Scaffold(topBar = { AppTopBar(title, onBack) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            sections.forEach { (h, body) ->
                SectionHeader(h)
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
