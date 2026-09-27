package com.aicarchecking.ai

import com.aicarchecking.domain.model.AnalysisTask
import com.aicarchecking.domain.model.EvidenceQuality
import com.aicarchecking.domain.model.EvidenceType
import com.aicarchecking.domain.model.InspectionStep
import java.io.File

/** One piece of evidence sent to a provider. For videos, each extracted frame is a separate payload. */
data class EvidencePayload(
    val evidenceId: String,
    val type: EvidenceType,
    val mimeType: String,
    val file: File,
    val label: String,
    val quality: EvidenceQuality,
    val frameNumber: Int? = null,
    val timestampMs: Long? = null,
)

data class VehicleContext(
    val description: String,
    val fuelType: String,
    val transmission: String,
    val recordedMileageKm: Long?,
    val inspectionType: String,
)

data class AnalysisRequest(
    val task: AnalysisTask,
    val step: InspectionStep?,
    val systemInstruction: String,
    val prompt: String,
    val evidence: List<EvidencePayload>,
)

data class ChatTurn(val fromUser: Boolean, val text: String)

enum class ProviderError(val userMessage: String) {
    NOT_CONFIGURED("No AI provider is configured. Add your Gemini API key in Settings → AI Provider, or explore Demo Mode."),
    NO_INTERNET("No internet connection. Your evidence is saved — try the analysis again when you're online."),
    TIMEOUT("The AI provider took too long to respond. Please retry."),
    UNAVAILABLE("The AI provider is temporarily unavailable. Please retry later."),
    RATE_LIMITED("Too many requests to the AI provider. Wait a minute and retry."),
    INVALID_KEY("The AI provider rejected the API key. Check it in Settings → AI Provider."),
    PAYLOAD_TOO_LARGE("We couldn't analyze this evidence because it is too large. Try a shorter clip or fewer photos."),
    UNKNOWN("AI analysis could not be completed. Please retry."),
}

sealed interface ProviderResult {
    data class Success(val text: String, val modelName: String) : ProviderResult
    data class Failure(val error: ProviderError) : ProviderResult
}

/**
 * Vendor-neutral AI provider. Business logic depends only on this interface (spec §47).
 * Future implementations: OpenAIProvider, ClaudeProvider, LocalModelProvider.
 */
interface AIProvider {
    val id: String
    val displayName: String
    /** True if calling this provider sends evidence off the device (requires explicit user confirmation). */
    val sendsDataOffDevice: Boolean

    suspend fun isConfigured(): Boolean

    /** Returns raw structured JSON text. Callers must validate it before anything reaches the UI. */
    suspend fun analyze(request: AnalysisRequest): ProviderResult

    /** Conversational reply for the AI Mechanic. */
    suspend fun chat(systemInstruction: String, history: List<ChatTurn>): ProviderResult
}
