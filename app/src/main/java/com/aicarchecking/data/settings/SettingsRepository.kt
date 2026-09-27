package com.aicarchecking.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

enum class ProviderType(val label: String) {
    NONE("Not configured (local checks only)"),
    GEMINI("Google Gemini (your API key)"),
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val providerType: ProviderType = ProviderType.NONE,
    val geminiModel: String = DEFAULT_GEMINI_MODEL,
    val hasGeminiKey: Boolean = false,
    val aiConsentAccepted: Boolean = false,
    val demoSeeded: Boolean = false,
) {
    companion object {
        const val DEFAULT_GEMINI_MODEL = "gemini-2.5-flash"
    }
}

/** Central AI provider configuration and app preferences (spec §47: configuration is centralised). */
class SettingsRepository(private val context: Context, private val keyStore: SecureKeyStore) {

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val PROVIDER = stringPreferencesKey("provider")
        val GEMINI_MODEL = stringPreferencesKey("gemini_model")
        val GEMINI_KEY_ENC = stringPreferencesKey("gemini_key_enc")
        val AI_CONSENT = booleanPreferencesKey("ai_consent")
        val DEMO_SEEDED = booleanPreferencesKey("demo_seeded")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            themeMode = p[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            providerType = p[Keys.PROVIDER]?.let { runCatching { ProviderType.valueOf(it) }.getOrNull() } ?: ProviderType.NONE,
            geminiModel = p[Keys.GEMINI_MODEL]?.takeIf { it.isNotBlank() } ?: AppSettings.DEFAULT_GEMINI_MODEL,
            hasGeminiKey = !p[Keys.GEMINI_KEY_ENC].isNullOrBlank(),
            aiConsentAccepted = p[Keys.AI_CONSENT] ?: false,
            demoSeeded = p[Keys.DEMO_SEEDED] ?: false,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[Keys.THEME] = mode.name }
    suspend fun setProvider(type: ProviderType) = context.dataStore.edit { it[Keys.PROVIDER] = type.name }
    suspend fun setGeminiModel(model: String) = context.dataStore.edit { it[Keys.GEMINI_MODEL] = model.trim() }
    suspend fun setAiConsent(accepted: Boolean) = context.dataStore.edit { it[Keys.AI_CONSENT] = accepted }
    suspend fun setDemoSeeded(seeded: Boolean) = context.dataStore.edit { it[Keys.DEMO_SEEDED] = seeded }

    suspend fun setGeminiKey(key: String?) {
        val encrypted = key?.trim()?.takeIf { it.isNotEmpty() }?.let { keyStore.encrypt(it) }
        context.dataStore.edit { prefs ->
            if (encrypted == null) {
                prefs.remove(Keys.GEMINI_KEY_ENC)
            } else {
                prefs[Keys.GEMINI_KEY_ENC] = encrypted
            }
        }
    }

    /** Decrypted only at the moment of a request; never logged or displayed. */
    suspend fun geminiKey(): String? =
        context.dataStore.data.first()[Keys.GEMINI_KEY_ENC]?.let { keyStore.decrypt(it) }
}
