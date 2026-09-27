package com.aicarchecking.safety

import com.aicarchecking.domain.model.Finding
import com.aicarchecking.domain.model.InspectionStep
import com.aicarchecking.domain.model.Severity

enum class SafetyHazard(val title: String, val guidance: String, val minSeverity: Severity) {
    FIRE_SMOKE(
        "Smoke or fire risk",
        "Move away from the vehicle if safe and contact emergency services (Rescue 1122 in Pakistan, or your local emergency number).",
        Severity.CRITICAL,
    ),
    FUEL_LEAK(
        "Possible fuel leak",
        "Switch off the engine, do not smoke or create sparks, move away from the vehicle and get professional help. Do not drive the car.",
        Severity.CRITICAL,
    ),
    BRAKE_FAILURE(
        "Brake concern",
        "Do not continue driving. Seek professional assistance.",
        Severity.CRITICAL,
    ),
    OVERHEATING(
        "Overheating",
        "Stop safely and switch off the engine. Do not open a hot cooling system — pressurised coolant can cause severe burns. Wait for it to cool and get professional help.",
        Severity.HIGH,
    ),
    OIL_PRESSURE(
        "Oil pressure warning",
        "Stop the engine as soon as it is safe. Driving with an oil pressure warning can cause severe engine damage. Get professional help.",
        Severity.HIGH,
    ),
    BATTERY_RISK(
        "Battery hazard",
        "Do not touch, charge or jump-start a swollen, leaking or hot battery. Keep sparks and flames away and get professional help.",
        Severity.HIGH,
    ),
    SEVERE_ACCIDENT_DAMAGE(
        "Possible severe structural damage",
        "Do not drive the vehicle until a professional structural inspection has been carried out.",
        Severity.HIGH,
    ),
    INJURY(
        "Injury",
        "Call emergency services immediately (Rescue 1122 in Pakistan, or your local emergency number). Do not move injured people unless they are in danger.",
        Severity.CRITICAL,
    ),
    SEVERE_SMOKE(
        "Heavy exhaust smoke",
        "Stop driving if continued operation may cause damage or create a safety risk.",
        Severity.HIGH,
    ),
}

data class SafetyAlert(val hazard: SafetyHazard) {
    val title get() = hazard.title
    val guidance get() = hazard.guidance
}

/**
 * Centralised safety rules (spec §49). Safety-critical findings override normal recommendations,
 * and user messages are screened before any AI response is shown.
 */
object SafetyPolicyEngine {

    private val rules: List<Pair<SafetyHazard, Regex>> = listOf(
        SafetyHazard.FIRE_SMOKE to Regex("""\b(fire|flames?|on fire|burning smell|smell of burning|smoke (from|under|coming out of) (the )?(bonnet|hood|engine|dash(board)?|wiring))\b""", RegexOption.IGNORE_CASE),
        SafetyHazard.FUEL_LEAK to Regex("""\b(fuel|petrol|gasoline|diesel|cng) (leak|leaking|dripping|smell)|smell(s|ing)? (of )?(fuel|petrol|gas)\b""", RegexOption.IGNORE_CASE),
        SafetyHazard.BRAKE_FAILURE to Regex("""\bbrakes? (failure|failed|not working|don'?t work|fade|fading)|brake pedal (sinks|goes to the floor|is soft|spongy)|no brakes\b|brake (fluid )?leak""", RegexOption.IGNORE_CASE),
        SafetyHazard.OVERHEATING to Regex("""\b(overheat(ing|ed)?|temperature (gauge|warning|light)[^.]{0,30}(red|high|on)|coolant temp(erature)? (high|warning)|steam (from|coming))""", RegexOption.IGNORE_CASE),
        SafetyHazard.OIL_PRESSURE to Regex("""\boil pressure (warning|light|low)|low oil pressure\b""", RegexOption.IGNORE_CASE),
        SafetyHazard.BATTERY_RISK to Regex("""\bbattery (is )?(swollen|bulging|leaking|smoking|very hot|hot)\b""", RegexOption.IGNORE_CASE),
        SafetyHazard.SEVERE_ACCIDENT_DAMAGE to Regex("""\b(structural damage|chassis (is )?(bent|damaged)|frame damage|bent (chassis|frame)|airbag(s)? deployed)\b""", RegexOption.IGNORE_CASE),
        SafetyHazard.INJURY to Regex("""\b(injur(ed|y|ies)|bleeding|unconscious|hurt)\b""", RegexOption.IGNORE_CASE),
        SafetyHazard.SEVERE_SMOKE to Regex("""\b(heavy|thick|excessive|dense) (white |blue |black )?smoke\b""", RegexOption.IGNORE_CASE),
    )

    /** Screens free text (chat messages, emergency answers) for hazards. */
    fun screenText(text: String): List<SafetyAlert> =
        rules.filter { (_, regex) -> regex.containsMatchIn(text) }.map { SafetyAlert(it.first) }

    /**
     * Applies safety overrides to findings: marks them safety-critical, raises severity and puts the
     * safety guidance in front of any other recommendation.
     */
    fun apply(findings: List<Finding>): List<Finding> = findings.map { f ->
        val text = listOf(f.observation, f.recommendation, f.possibleCauses.joinToString(" ")).joinToString(" ")
        val hazards = rules.filter { (_, regex) -> regex.containsMatchIn(text) }.map { it.first }
            // "no fuel leak observed" style negations should not trigger
            .filterNot { isNegated(text, it) }
        if (hazards.isEmpty()) return@map f
        val maxSeverity = (hazards.map { it.minSeverity } + f.severity).maxBy { it.rank }
        val guidance = hazards.joinToString(" ") { it.guidance }
        f.copy(
            safetyCritical = true,
            severity = maxSeverity,
            recommendation = if (f.recommendation.startsWith(guidance)) f.recommendation else "SAFETY: $guidance ${f.recommendation}",
        )
    }

    private fun isNegated(text: String, hazard: SafetyHazard): Boolean {
        val regex = rules.first { it.first == hazard }.second
        val match = regex.find(text) ?: return false
        val before = text.substring(0, match.range.first).takeLast(30)
        return negation.containsMatchIn(before)
    }

    private val negation = Regex("""\b(no|not|without|nor|never)\b(\s+[\w-]+){0,3}\s*$""", RegexOption.IGNORE_CASE)

    /** Fixed safety notes for steps that are inherently risky (underbody, test drive, engine running). */
    fun stepWarning(step: InspectionStep): String? = step.safetyNote
}
