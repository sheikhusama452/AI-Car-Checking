package com.aicarchecking.ai.validator

import com.aicarchecking.ai.prompt.PromptLibrary
import com.aicarchecking.domain.model.AnalysisTask
import com.aicarchecking.domain.model.Certainty
import com.aicarchecking.domain.model.Confidence
import com.aicarchecking.domain.model.EvidenceQuality
import com.aicarchecking.domain.model.EvidenceType
import com.aicarchecking.domain.model.FindingCategory
import com.aicarchecking.domain.model.GenericStatus
import com.aicarchecking.domain.model.Panel
import com.aicarchecking.domain.model.PaintStatus
import com.aicarchecking.domain.model.Severity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

data class ValidatedFinding(
    val category: FindingCategory,
    val panel: Panel?,
    val status: String,
    val certainty: Certainty,
    val severity: Severity,
    val confidence: Confidence,
    val evidenceIds: List<String>,
    val evidenceType: EvidenceType,
    val observation: String,
    val possibleCauses: List<String>,
    val recommendation: String,
    val verificationMethod: String,
    val frameNumber: Int?,
    val timestampMs: Long?,
)

sealed interface ValidationResult {
    data class Valid(
        val overallStatus: String?,
        val summary: String?,
        val findings: List<ValidatedFinding>,
        val missingEvidence: List<String>,
        val warnings: List<String>,
    ) : ValidationResult

    data class Invalid(val reason: String) : ValidationResult {
        /** The only message the user ever sees for an invalid response (spec §48). */
        val userMessage: String get() = USER_MESSAGE
    }

    companion object {
        const val USER_MESSAGE = "AI analysis could not be completed safely. Please retry."
    }
}

/**
 * Validates provider output before anything reaches the UI: JSON shape, required fields, enum values,
 * evidence IDs, unsupported/overconfident claims and empty results (spec §48, §58).
 */
object AiResponseValidator {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Phrases that constitute unsupported claims. Findings containing them are rejected. */
    private val overclaimPatterns = listOf(
        Regex("""\bdefinitely\b""", RegexOption.IGNORE_CASE),
        Regex("""\bcertainly\b""", RegexOption.IGNORE_CASE),
        Regex("""\bundoubtedly\b""", RegexOption.IGNORE_CASE),
        Regex("""\bwithout (a |any )?doubt\b""", RegexOption.IGNORE_CASE),
        Regex("""(?<!not |cannot |can't |never |no )\bguarantee(d|s)?\b""", RegexOption.IGNORE_CASE),
        Regex("""\b100\s?%""", RegexOption.IGNORE_CASE),
        Regex("""\b(odometer|meter|mileage|clock)\s+(was|has been|is|were)\s+(rolled back|tampered|wound back)""", RegexOption.IGNORE_CASE),
        Regex("""\b(has been|was) in an accident\b""", RegexOption.IGNORE_CASE),
        Regex("""\bis stolen\b|\bstolen vehicle\b""", RegexOption.IGNORE_CASE),
        Regex("""\bfraud(ulent)?\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(engine|transmission|gearbox) (has|is) (failed|seized|destroyed)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(do not|don't) buy\b|\bbuy this car\b|\bgood deal\b|\bbad deal\b""", RegexOption.IGNORE_CASE),
    )

    fun containsOverclaim(text: String): Boolean = overclaimPatterns.any { it.containsMatchIn(text) }

    fun validate(
        rawText: String,
        task: AnalysisTask,
        allowedEvidenceIds: Set<String>,
        localQuality: Map<String, EvidenceQuality>,
    ): ValidationResult {
        val root = parseObject(rawText) ?: return ValidationResult.Invalid("Malformed JSON")
        val warnings = mutableListOf<String>()
        val allowedStatuses = PromptLibrary.allowedStatuses(task).toSet()

        val findingsJson = root["findings"] as? JsonArray ?: return ValidationResult.Invalid("Missing findings array")
        if (findingsJson.isEmpty()) return ValidationResult.Invalid("Empty findings")

        val findings = findingsJson.mapIndexedNotNull { index, element ->
            val obj = element as? JsonObject ?: run { warnings += "Finding $index is not an object"; return@mapIndexedNotNull null }
            validateFinding(obj, index, task, allowedStatuses, allowedEvidenceIds, localQuality, warnings)
        }
        if (findings.isEmpty()) return ValidationResult.Invalid("No finding passed validation")

        val overall = root.str("overallStatus")?.uppercase()?.takeIf { it in allowedStatuses }
        val summary = root.str("summary")?.takeUnless { containsOverclaim(it) }
        if (root.str("summary") != null && summary == null) warnings += "Summary removed: unsupported claim"
        val missing = (root["missingEvidence"] as? JsonArray)?.mapNotNull { it.asString() }?.filterNot(::containsOverclaim).orEmpty()

        return ValidationResult.Valid(overall, summary, findings, missing, warnings)
    }

    private fun validateFinding(
        obj: JsonObject,
        index: Int,
        task: AnalysisTask,
        allowedStatuses: Set<String>,
        allowedEvidenceIds: Set<String>,
        localQuality: Map<String, EvidenceQuality>,
        warnings: MutableList<String>,
    ): ValidatedFinding? {
        fun reject(reason: String): ValidatedFinding? { warnings += "Finding $index rejected: $reason"; return null }

        var status = obj.str("status")?.uppercase() ?: return reject("missing status")
        if (status !in allowedStatuses) return reject("unsupported status '$status'")

        var severity = enumOrNull<Severity>(obj.str("severity")) ?: return reject("invalid severity")
        var confidence = enumOrNull<Confidence>(obj.str("confidence")) ?: return reject("invalid confidence")
        var certainty = enumOrNull<Certainty>(obj.str("certainty")) ?: Certainty.POSSIBLE.also {
            warnings += "Finding $index: missing certainty, defaulted to POSSIBLE"
        }

        val evidenceIds = (obj["evidenceIds"] as? JsonArray)?.mapNotNull { it.asString() }.orEmpty()
        if (evidenceIds.isEmpty()) return reject("no evidence IDs")
        val unknown = evidenceIds.filterNot { it in allowedEvidenceIds }
        if (unknown.isNotEmpty()) return reject("unknown evidence IDs")

        val observation = obj.str("observation")?.trim().orEmpty()
        val recommendation = obj.str("recommendation")?.trim().orEmpty()
        val verification = obj.str("verificationMethod")?.trim().orEmpty()
        if (observation.isBlank() || recommendation.isBlank() || verification.isBlank()) {
            return reject("missing observation/recommendation/verificationMethod")
        }
        val causes = (obj["possibleCauses"] as? JsonArray)?.mapNotNull { it.asString() }.orEmpty()
        if (containsOverclaim(observation) || containsOverclaim(recommendation) || causes.any(::containsOverclaim)) {
            return reject("unsupported claim")
        }

        val panel = if (task == AnalysisTask.PAINT) {
            Panel.fromCode(obj.str("panel")?.uppercase()) ?: return reject("missing/invalid panel")
        } else null

        // Rule: a photo can never establish original paint with high confidence.
        if (status == PaintStatus.APPEARS_ORIGINAL.name && confidence == Confidence.HIGH) {
            confidence = Confidence.MEDIUM
            warnings += "Finding $index: 'Appears Original' confidence capped at MEDIUM"
        }
        // Rule: tentative statuses cannot be asserted as 'observed facts' about causes.
        if (status.startsWith("POSSIBLE") && certainty == Certainty.OBSERVED) certainty = Certainty.POSSIBLE

        // Rule: findings resting only on locally-unusable evidence become 'insufficient evidence'.
        val allUnusable = evidenceIds.all { localQuality[it]?.usableForAnalysis == false }
        if (allUnusable) {
            status = if (task == AnalysisTask.PAINT) PaintStatus.INSUFFICIENT_EVIDENCE.name else GenericStatus.INSUFFICIENT_EVIDENCE.name
            confidence = Confidence.LOW
            severity = Severity.LOW
            certainty = Certainty.CANNOT_DETERMINE
            warnings += "Finding $index: downgraded — evidence quality insufficient"
        }
        // Rule: HIGH confidence requires at least one GOOD evidence item.
        if (confidence == Confidence.HIGH && evidenceIds.none { localQuality[it] == EvidenceQuality.GOOD }) {
            confidence = Confidence.MEDIUM
            warnings += "Finding $index: confidence capped — no GOOD-quality evidence"
        }

        return ValidatedFinding(
            category = task.category,
            panel = panel,
            status = status,
            certainty = certainty,
            severity = severity,
            confidence = confidence,
            evidenceIds = evidenceIds,
            evidenceType = enumOrNull<EvidenceType>(obj.str("evidenceType")) ?: EvidenceType.PHOTO,
            observation = observation,
            possibleCauses = causes,
            recommendation = recommendation,
            verificationMethod = verification,
            frameNumber = (obj["frameNumber"] as? JsonPrimitive)?.intOrNull,
            timestampMs = (obj["timestampMs"] as? JsonPrimitive)?.longOrNull,
        )
    }

    /** Accepts raw JSON, JSON wrapped in markdown fences, or JSON with leading/trailing prose. */
    internal fun parseObject(raw: String): JsonObject? {
        val trimmed = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { json.parseToJsonElement(trimmed.substring(start, end + 1)) as? JsonObject }.getOrNull()
    }

    private fun JsonObject.str(key: String): String? = this[key]?.asString()

    private fun kotlinx.serialization.json.JsonElement.asString(): String? =
        if (this is JsonNull) null else (this as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private inline fun <reified T : Enum<T>> enumOrNull(value: String?): T? =
        value?.uppercase()?.let { v -> enumValues<T>().firstOrNull { it.name == v } }
}
