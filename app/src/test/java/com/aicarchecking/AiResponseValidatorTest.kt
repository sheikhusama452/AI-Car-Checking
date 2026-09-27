package com.aicarchecking

import com.aicarchecking.ai.validator.AiResponseValidator
import com.aicarchecking.ai.validator.ValidationResult
import com.aicarchecking.domain.model.AnalysisTask
import com.aicarchecking.domain.model.Confidence
import com.aicarchecking.domain.model.EvidenceQuality
import com.aicarchecking.domain.model.Panel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiResponseValidatorTest {

    private val ids = setOf("media_1", "media_2")
    private val good = mapOf("media_1" to EvidenceQuality.GOOD, "media_2" to EvidenceQuality.GOOD)

    private fun paintFinding(
        status: String = "POSSIBLE_REPAINT",
        confidence: String = "MEDIUM",
        evidence: String = "\"media_1\"",
        observation: String = "Visible colour and texture difference compared with adjacent panel.",
        panel: String = "FRONT_LEFT_FENDER",
    ) = """
        {"findingId":"x","category":"PAINT","panel":"$panel","status":"$status","certainty":"POSSIBLE",
         "severity":"MEDIUM","confidence":"$confidence","evidenceIds":[$evidence],"evidenceType":"PHOTO",
         "observation":"$observation","possibleCauses":["Repaint","Lighting/reflection difference"],
         "recommendation":"Verify using a paint thickness gauge.","verificationMethod":"Paint thickness measurement"}
    """.trimIndent()

    private fun wrap(vararg findings: String) = """{"task":"PAINT","overallStatus":"POSSIBLE_REPAINT","findings":[${findings.joinToString(",")}]}"""

    @Test
    fun validResponseIsAccepted() {
        val r = AiResponseValidator.validate(wrap(paintFinding()), AnalysisTask.PAINT, ids, good)
        assertTrue(r is ValidationResult.Valid)
        val f = (r as ValidationResult.Valid).findings.single()
        assertEquals(Panel.FRONT_LEFT_FENDER, f.panel)
        assertEquals("POSSIBLE_REPAINT", f.status)
    }

    @Test
    fun markdownFencedJsonIsAccepted() {
        val r = AiResponseValidator.validate("```json\n" + wrap(paintFinding()) + "\n```", AnalysisTask.PAINT, ids, good)
        assertTrue(r is ValidationResult.Valid)
    }

    @Test
    fun malformedJsonIsRejected() {
        val r = AiResponseValidator.validate("The car looks fine!", AnalysisTask.PAINT, ids, good)
        assertTrue(r is ValidationResult.Invalid)
        assertEquals("AI analysis could not be completed safely. Please retry.", (r as ValidationResult.Invalid).userMessage)
    }

    @Test
    fun emptyFindingsAreRejected() {
        assertTrue(AiResponseValidator.validate(wrap(), AnalysisTask.PAINT, ids, good) is ValidationResult.Invalid)
    }

    @Test
    fun unknownEvidenceIdIsRejected() {
        val r = AiResponseValidator.validate(wrap(paintFinding(evidence = "\"media_999\"")), AnalysisTask.PAINT, ids, good)
        assertTrue(r is ValidationResult.Invalid)
    }

    @Test
    fun unsupportedStatusIsRejected() {
        val r = AiResponseValidator.validate(wrap(paintFinding(status = "DEFINITELY_PAINTED")), AnalysisTask.PAINT, ids, good)
        assertTrue(r is ValidationResult.Invalid)
    }

    @Test
    fun overclaimIsRejected() {
        val r = AiResponseValidator.validate(
            wrap(paintFinding(observation = "This fender is definitely painted."), paintFinding(panel = "BONNET")),
            AnalysisTask.PAINT, ids, good,
        )
        assertTrue(r is ValidationResult.Valid)
        val v = r as ValidationResult.Valid
        assertEquals(1, v.findings.size)
        assertEquals(Panel.BONNET, v.findings.single().panel)
        assertTrue(v.warnings.any { it.contains("unsupported claim") })
    }

    @Test
    fun appearsOriginalConfidenceIsCapped() {
        val r = AiResponseValidator.validate(wrap(paintFinding(status = "APPEARS_ORIGINAL", confidence = "HIGH")), AnalysisTask.PAINT, ids, good)
        assertEquals(Confidence.MEDIUM, (r as ValidationResult.Valid).findings.single().confidence)
    }

    @Test
    fun insufficientEvidenceDowngradesFinding() {
        val poor = mapOf("media_1" to EvidenceQuality.INSUFFICIENT)
        val r = AiResponseValidator.validate(wrap(paintFinding()), AnalysisTask.PAINT, ids, poor)
        val f = (r as ValidationResult.Valid).findings.single()
        assertEquals("INSUFFICIENT_EVIDENCE", f.status)
        assertEquals(Confidence.LOW, f.confidence)
    }

    @Test
    fun negatedGuaranteeIsAllowed() {
        assertTrue(!AiResponseValidator.containsOverclaim("A photograph cannot guarantee original factory paint."))
        assertTrue(AiResponseValidator.containsOverclaim("The odometer was rolled back."))
    }
}
