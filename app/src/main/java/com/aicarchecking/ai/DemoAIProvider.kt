package com.aicarchecking.ai

import com.aicarchecking.domain.model.AnalysisTask
import com.aicarchecking.domain.model.InspectionStep
import com.aicarchecking.domain.model.Panel
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.JsonNull

/**
 * Offline provider used ONLY for Demo Mode inspections (and the offline AI Mechanic checklist).
 * It returns pre-scripted results for the bundled demo evidence; it does not analyze real photos.
 * Its output goes through the same validator and safety engine as a real provider.
 */
class DemoAIProvider : AIProvider {
    override val id = "demo"
    override val displayName = "Demo Mode (offline, scripted)"
    override val sendsDataOffDevice = false

    override suspend fun isConfigured() = true

    override suspend fun analyze(request: AnalysisRequest): ProviderResult {
        val ids = request.evidence.map { it.evidenceId }.distinct()
        if (ids.isEmpty()) return ProviderResult.Failure(ProviderError.UNKNOWN)
        val firstFrame = request.evidence.firstOrNull { it.frameNumber != null }
        val findings = scriptedFindings(request.task, request.step)
        val root = buildJsonObject {
            put("task", request.task.name)
            put("overallStatus", findings.first().status)
            put("summary", "Demo result: scripted analysis of the bundled sample evidence.")
            put("findings", JsonArray(findings.mapIndexed { i, f ->
                buildJsonObject {
                    put("findingId", "demo_${request.task.name.lowercase()}_${i + 1}")
                    put("category", request.task.category.name)
                    put("panel", f.panel?.name?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("status", f.status)
                    put("certainty", f.certainty)
                    put("severity", f.severity)
                    put("confidence", f.confidence)
                    putJsonArray("evidenceIds") { add(JsonPrimitive(ids.first())) }
                    put("evidenceType", request.evidence.first().type.name)
                    put("observation", f.observation)
                    putJsonArray("possibleCauses") { f.causes.forEach { add(JsonPrimitive(it)) } }
                    put("recommendation", f.recommendation)
                    put("verificationMethod", f.verification)
                    put("frameNumber", firstFrame?.frameNumber?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("timestampMs", firstFrame?.timestampMs?.let { JsonPrimitive(it) } ?: JsonNull)
                }
            }))
            putJsonArray("missingEvidence") {}
        }
        return ProviderResult.Success(root.toString(), "demo-script")
    }

    override suspend fun chat(systemInstruction: String, history: List<ChatTurn>): ProviderResult {
        val last = history.lastOrNull { it.fromUser }?.text.orEmpty()
        return ProviderResult.Success(OfflineMechanicGuide.reply(last), "offline-checklist")
    }

    private data class Scripted(
        val status: String,
        val certainty: String,
        val severity: String,
        val confidence: String,
        val observation: String,
        val causes: List<String>,
        val recommendation: String,
        val verification: String,
        val panel: Panel? = null,
    )

    private fun scriptedFindings(task: AnalysisTask, step: InspectionStep?): List<Scripted> = when (task) {
        AnalysisTask.PAINT -> paintFindings(step)
        AnalysisTask.ACCIDENT -> if (step == InspectionStep.FRONT_LEFT_FENDER) listOf(
            Scripted(
                "POSSIBLE_PREVIOUS_REPAIR", "POSSIBLE", "MEDIUM", "LOW",
                "The gap between the front left fender and the door appears slightly wider at the top than at the bottom.",
                listOf("Panel refitted after repair", "Normal manufacturing tolerance", "Camera angle"),
                "Compare panel gaps by eye and with a gauge on both sides, and inspect fender bolts for tool marks.",
                "Physical inspection of panel gaps and mounting bolts",
            )
        ) else listOf(
            Scripted(
                "NO_OBVIOUS_INDICATORS", "OBSERVED", "LOW", "MEDIUM",
                "No obvious visible repair indicators such as uneven gaps, welds or distorted panels in this photo.",
                emptyList(),
                "Hidden structural damage cannot be ruled out from photographs alone. Physical inspection is recommended.",
                "Workshop lift inspection of chassis rails and mounting points",
            )
        )
        AnalysisTask.DASHBOARD_WARNING -> listOf(
            Scripted(
                "ISSUE_OBSERVED", "OBSERVED", "MEDIUM", "MEDIUM",
                "The Check Engine indicator appears illuminated while the engine is running.",
                listOf("Stored emissions or engine-management fault code", "Sensor issue", "Other engine conditions"),
                "Read the fault codes with an OBD scanner before purchase. Do not ignore the warning.",
                "OBD-II scan and diagnostic inspection",
            )
        )
        AnalysisTask.ENGINE_BAY -> listOf(
            Scripted(
                "NO_OBVIOUS_ISSUE", "OBSERVED", "LOW", "MEDIUM",
                "No obvious visible oil or coolant leaks, damaged hoses or battery terminal corrosion in the sample frames.",
                emptyList(),
                "Hidden/internal engine problems cannot be determined visually. Have a mechanic inspect the engine.",
                "Mechanic inspection; compression and leak-down test if concerns arise",
            )
        )
        AnalysisTask.ENGINE_SOUND -> listOf(
            Scripted(
                "POSSIBLE_ISSUE", "SUSPECTED", "LOW", "LOW",
                "Audio contains a light rhythmic ticking at idle that follows engine speed.",
                listOf("Normal injector or valve-train noise", "Valve clearance", "Accessory drive component"),
                "Audio contains a rhythmic noise that may require inspection of the accessory drive or valve train by a mechanic.",
                "Mechanic listening check with a stethoscope",
            )
        )
        AnalysisTask.SMOKE -> listOf(
            Scripted(
                "POSSIBLE_ISSUE", "POSSIBLE", "MEDIUM", "LOW",
                "Light bluish-grey smoke is visible in the startup frames and appears to reduce after a few seconds.",
                listOf("Oil consumption at startup", "Worn valve stem seals", "Normal condensation vapour", "Other mechanical causes"),
                "Physical inspection and further diagnostic testing required. Watch the exhaust again after a longer idle and a light rev.",
                "Mechanic inspection; compression test; oil consumption check",
            )
        )
        AnalysisTask.TYRES -> listOf(
            Scripted(
                "POSSIBLE_ISSUE", "OBSERVED", "MEDIUM", "MEDIUM",
                "Visible uneven wear on the inner edge of the rear left tyre compared with the outer edge.",
                listOf("Wheel alignment issue", "Suspension component wear", "Incorrect tyre pressure"),
                "Measure tread depth across the tyre and have the wheel alignment checked.",
                "Tread depth gauge; wheel alignment check",
            )
        )
        else -> listOf(
            Scripted(
                "NO_OBVIOUS_ISSUE", "OBSERVED", "LOW", "LOW",
                "No obvious issue is visible in the demo sample evidence.",
                emptyList(),
                "Confirm with a physical inspection.",
                "Physical inspection",
            )
        )
    }

    private fun paintFindings(step: InspectionStep?): List<Scripted> {
        val panels = step?.panels.orEmpty().ifEmpty { listOf(Panel.BONNET) }
        return panels.map { panel ->
            when (panel) {
                Panel.FRONT_LEFT_FENDER -> Scripted(
                    "POSSIBLE_REPAINT", "POSSIBLE", "MEDIUM", "MEDIUM",
                    "Slight colour difference compared with the adjacent door; the surface texture appears smoother and the reflection pattern differs from the adjacent panel.",
                    listOf("Repaint", "Lighting/reflection difference"),
                    "Verify using a paint thickness gauge and inspect panel edges for masking lines or overspray.",
                    "Physical inspection / paint thickness measurement", panel,
                )
                Panel.REAR_RIGHT_DOOR -> Scripted(
                    "POSSIBLE_REPAINT", "POSSIBLE", "MEDIUM", "MEDIUM",
                    "The rear right door shade appears slightly lighter than the front right door and rear fender.",
                    listOf("Repaint", "Different paint batch", "Lighting difference"),
                    "Verify using a paint thickness gauge; check door edges and hinges for overspray.",
                    "Physical inspection / paint thickness measurement", panel,
                )
                else -> Scripted(
                    "APPEARS_ORIGINAL", "LIKELY", "LOW", "MEDIUM",
                    "No visible colour, texture or reflection differences compared with adjacent panels in this photo.",
                    emptyList(),
                    "A photograph cannot guarantee original factory paint. Confirm with a paint thickness gauge if important.",
                    "Paint thickness measurement", panel,
                )
            }
        }
    }
}

/** Offline, rule-based triage used when no AI provider is configured. Clearly labelled in the UI. */
object OfflineMechanicGuide {
    fun reply(message: String): String {
        val m = message.lowercase()
        return when {
            "overheat" in m || "temperature" in m || "steam" in m -> """
                Overheating — safety first:
                1. Is the temperature gauge in the red, or is a temperature warning light on?
                2. Is coolant leaking under the car, or is steam/smoke visible?
                3. Are any other warning lights on?
                If the gauge is red or you see steam: stop safely, switch off the engine and do not open the radiator or coolant reservoir while hot.
                Possible issue categories: low coolant/leak, cooling fan, thermostat, water pump, radiator blockage.
                Have a mechanic pressure-test the cooling system before driving further.
            """.trimIndent()
            "start" in m || "crank" in m -> """
                Car won't start:
                1. Does the engine crank (turn over) or just click?
                2. Do the dashboard lights come on brightly?
                3. Any warning lights or smell of fuel?
                Possible issue categories: battery/charging, starter, fuel delivery, ignition, immobiliser.
                A mechanic can test battery voltage and read fault codes. Do not attempt to bypass wiring.
            """.trimIndent()
            "smoke" in m -> """
                Smoke:
                1. Where is it coming from — exhaust, under the bonnet, or inside the cabin?
                2. What colour — white, blue or black?
                If smoke comes from under the bonnet or the cabin, or you smell burning: move away from the vehicle and call emergency services.
                Exhaust smoke categories: white (coolant or condensation), blue (oil), black (rich fuel mixture). Physical inspection and diagnostic testing required.
            """.trimIndent()
            "brake" in m -> """
                Brake concern: if the pedal is soft, sinks, or braking is weak — do not continue driving. Seek professional assistance.
                If you hear grinding or squealing, have the pads and discs inspected before driving further.
            """.trimIndent()
            "noise" in m || "knock" in m || "tick" in m || "sound" in m -> """
                Unusual noise:
                1. Does it change with engine speed or with road speed?
                2. Is it a tick, knock, squeal, rattle or grind?
                3. Does it happen when turning, braking or over bumps?
                Record a 10–30 second clip in the Engine Sound step so it can be analyzed, and have a mechanic listen to it.
            """.trimIndent()
            "light" in m || "warning" in m -> """
                Warning light:
                Red lights (oil pressure, temperature, brake, battery) mean stop safely and get help.
                Amber lights (check engine, ABS, TPMS) mean have it diagnosed soon. A flashing check-engine light means stop driving.
                Photograph the dashboard in the Warning Lights step and read the codes with an OBD scanner.
            """.trimIndent()
            else -> """
                I can help you work through the symptom. Tell me:
                1. What you notice (sound, smell, smoke, warning light, behaviour).
                2. When it happens (cold start, idle, driving, braking, turning).
                3. Whether any warning lights are on.
            """.trimIndent()
        } + "\n\n(Offline checklist — connect an AI provider in Settings for conversational answers. Always confirm with a qualified mechanic.)"
    }
}
