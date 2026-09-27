package com.aicarchecking.ai

import com.aicarchecking.data.settings.ProviderType
import com.aicarchecking.data.settings.SettingsRepository

/** Single place that decides which provider serves a request (spec §47). */
class AIProviderRegistry(
    private val settings: SettingsRepository,
    private val gemini: GeminiProvider,
    private val demo: DemoAIProvider,
) {
    /** Demo inspections always use the scripted demo provider; real inspections never do. */
    suspend fun providerFor(isDemoInspection: Boolean): AIProvider? {
        if (isDemoInspection) return demo
        return when (settings.current().providerType) {
            ProviderType.GEMINI -> gemini.takeIf { it.isConfigured() }
            ProviderType.NONE -> null
        }
    }

    /** Chat falls back to the offline checklist when no provider is configured. */
    suspend fun chatProvider(): AIProvider = providerFor(isDemoInspection = false) ?: demo
}
