package com.aicarchecking.ui.mechanic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aicarchecking.ai.ChatTurn
import com.aicarchecking.ai.DemoAIProvider
import com.aicarchecking.ai.ProviderResult
import com.aicarchecking.ai.prompt.PromptLibrary
import com.aicarchecking.ai.validator.AiResponseValidator
import com.aicarchecking.di.AppContainer
import com.aicarchecking.safety.SafetyAlert
import com.aicarchecking.safety.SafetyPolicyEngine
import com.aicarchecking.ui.common.AppTopBar
import com.aicarchecking.ui.common.IntegrationRequiredBanner
import com.aicarchecking.ui.common.Pill
import com.aicarchecking.ui.common.WarningBanner
import com.aicarchecking.ui.common.appViewModel
import com.aicarchecking.ui.theme.Brand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ChatMessage(val fromUser: Boolean, val text: String, val alerts: List<SafetyAlert> = emptyList(), val offline: Boolean = false)

class MechanicViewModel(private val c: AppContainer) : ViewModel() {
    private val _messages = MutableStateFlow(
        listOf(
            ChatMessage(
                false,
                "Hi, I'm the AI Mechanic. Describe what you notice — a sound, smell, smoke, warning light or how the car behaves — and I'll help you understand it safely.",
            )
        )
    )
    val messages = _messages.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _busy.value) return
        // Safety screening happens locally, before and independently of any AI provider.
        val alerts = SafetyPolicyEngine.screenText(trimmed)
        _messages.value = _messages.value + ChatMessage(true, trimmed, alerts)
        viewModelScope.launch {
            _busy.value = true
            val provider = c.providerRegistry.chatProvider()
            val vehicle = c.vehicleRepository.observeAll().first().firstOrNull { !it.isDemo }
            val recent = c.findingRepository.observeImportantUnresolved(5).first().map { "${it.category.label}: ${it.statusLabel} — ${it.observation}" }
            val system = PromptLibrary.mechanicSystemInstruction(vehicle?.let { "${it.displayName} ${it.variant}".trim() }, recent)
            val history = _messages.value.drop(1).map { ChatTurn(it.fromUser, it.text) }
            val reply = when (val r = provider.chat(system, history)) {
                is ProviderResult.Success -> sanitize(r.text)
                is ProviderResult.Failure -> r.error.userMessage
            }
            _messages.value = _messages.value + ChatMessage(false, reply, offline = provider is DemoAIProvider)
            _busy.value = false
        }
    }

    /** Chat output is validated too: overconfident claims get an explicit caution. */
    private fun sanitize(text: String): String {
        val cleaned = text.take(4000).trim()
        return if (AiResponseValidator.containsOverclaim(cleaned)) {
            "$cleaned\n\nNote: this cannot be confirmed without a physical inspection or diagnostic equipment."
        } else cleaned
    }
}

@Composable
fun MechanicScreen() {
    val vm = appViewModel { c, _ -> MechanicViewModel(c) }
    val messages by vm.messages.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size) }

    Scaffold(topBar = { AppTopBar("AI Mechanic") }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
            LazyColumn(Modifier.weight(1f), state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    IntegrationRequiredBanner(
                        "Voice input, photo/video/audio attachments",
                        "Speech-to-text, text-to-speech (English, Urdu, Roman Urdu) and media in chat arrive in Phase 2. Text chat works now.",
                    )
                }
                items(messages) { m -> Bubble(m) }
                if (busy) item { CircularProgressIndicator(Modifier.padding(8.dp)) }
            }
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    input, { input = it }, Modifier.weight(1f),
                    placeholder = { Text("e.g. Car is overheating") }, maxLines = 4,
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { vm.send(input); input = "" }, enabled = input.isNotBlank() && !busy) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Send")
                }
            }
        }
    }
}

@Composable
private fun Bubble(m: ChatMessage) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (m.fromUser) Alignment.End else Alignment.Start) {
        m.alerts.forEach { a ->
            WarningBanner("${a.title}: ${a.guidance}")
            Spacer(Modifier.padding(2.dp))
        }
        Box(Modifier.widthIn(max = 320.dp)) {
            Surface(
                color = if (m.fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(Modifier.padding(12.dp)) {
                    if (m.offline) Pill("Offline checklist", Brand.Amber)
                    Text(m.text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
