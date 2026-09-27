package com.aicarchecking.ai

import android.util.Base64
import com.aicarchecking.data.settings.SettingsRepository
import com.aicarchecking.media.image.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Google Gemini (generateContent REST API). The API key is supplied by the user at runtime and
 * stored encrypted on device; no key is compiled into the APK. For a public release, route calls
 * through your own backend instead of using end-user keys.
 */
class GeminiProvider(private val settings: SettingsRepository) : AIProvider {

    override val id = "gemini"
    override val displayName = "Google Gemini"
    override val sendsDataOffDevice = true

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun isConfigured(): Boolean = !settings.geminiKey().isNullOrBlank()

    override suspend fun analyze(request: AnalysisRequest): ProviderResult = withContext(Dispatchers.IO) {
        var totalBytes = 0L
        val body = buildJsonObject {
            putJsonObject("systemInstruction") {
                putJsonArray("parts") { addJsonObject { put("text", request.systemInstruction) } }
            }
            putJsonArray("contents") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("parts") {
                        addJsonObject { put("text", request.prompt) }
                        for (e in request.evidence) {
                            val (mime, bytes) = when {
                                e.mimeType.startsWith("image/") ->
                                    "image/jpeg" to (ImageUtils.jpegBytesForUpload(e.file) ?: continue)
                                e.mimeType.startsWith("audio/") && e.file.length() <= MAX_INLINE_AUDIO ->
                                    e.mimeType to e.file.readBytes()
                                else -> continue
                            }
                            totalBytes += bytes.size
                            val label = buildString {
                                append("Evidence ${e.evidenceId} (${e.type.label}, ${e.label}")
                                e.frameNumber?.let { append(", frame $it") }
                                e.timestampMs?.let { append(", t=${it}ms") }
                                append(", local quality: ${e.quality.label})")
                            }
                            addJsonObject { put("text", label) }
                            addJsonObject {
                                putJsonObject("inlineData") {
                                    put("mimeType", mime)
                                    put("data", Base64.encodeToString(bytes, Base64.NO_WRAP))
                                }
                            }
                        }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("responseMimeType", "application/json")
                put("temperature", 0.2)
            }
        }
        if (totalBytes > MAX_REQUEST_BYTES) return@withContext ProviderResult.Failure(ProviderError.PAYLOAD_TOO_LARGE)
        post(body)
    }

    override suspend fun chat(systemInstruction: String, history: List<ChatTurn>): ProviderResult =
        withContext(Dispatchers.IO) {
            val body = buildJsonObject {
                putJsonObject("systemInstruction") {
                    putJsonArray("parts") { addJsonObject { put("text", systemInstruction) } }
                }
                putJsonArray("contents") {
                    history.takeLast(20).forEach { turn ->
                        addJsonObject {
                            put("role", if (turn.fromUser) "user" else "model")
                            putJsonArray("parts") { addJsonObject { put("text", turn.text) } }
                        }
                    }
                }
                putJsonObject("generationConfig") { put("temperature", 0.3) }
            }
            post(body)
        }

    private suspend fun post(body: JsonObject): ProviderResult {
        val key = settings.geminiKey()
        if (key.isNullOrBlank()) return ProviderResult.Failure(ProviderError.NOT_CONFIGURED)
        val model = settings.current().geminiModel
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .header("x-goog-api-key", key)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        return try {
            client.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                when {
                    resp.code == 400 && text.contains("API_KEY_INVALID") -> ProviderResult.Failure(ProviderError.INVALID_KEY)
                    resp.code == 401 || resp.code == 403 -> ProviderResult.Failure(ProviderError.INVALID_KEY)
                    resp.code == 413 -> ProviderResult.Failure(ProviderError.PAYLOAD_TOO_LARGE)
                    resp.code == 429 -> ProviderResult.Failure(ProviderError.RATE_LIMITED)
                    resp.code >= 500 -> ProviderResult.Failure(ProviderError.UNAVAILABLE)
                    !resp.isSuccessful -> ProviderResult.Failure(ProviderError.UNKNOWN)
                    else -> extractText(text)?.let { ProviderResult.Success(it, model) }
                        ?: ProviderResult.Failure(ProviderError.UNKNOWN)
                }
            }
        } catch (e: UnknownHostException) {
            ProviderResult.Failure(ProviderError.NO_INTERNET)
        } catch (e: SocketTimeoutException) {
            ProviderResult.Failure(ProviderError.TIMEOUT)
        } catch (e: IOException) {
            ProviderResult.Failure(ProviderError.UNAVAILABLE)
        }
    }

    private fun extractText(responseBody: String): String? = runCatching {
        val root = json.parseToJsonElement(responseBody).jsonObject
        val candidates = root["candidates"] as? JsonArray ?: return null
        val parts = candidates.firstOrNull()?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray ?: return null
        parts.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }.joinToString("").ifBlank { null }
    }.getOrNull()

    private companion object {
        const val MAX_INLINE_AUDIO = 8L * 1024 * 1024
        const val MAX_REQUEST_BYTES = 15L * 1024 * 1024
    }
}
