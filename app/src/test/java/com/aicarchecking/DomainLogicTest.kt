package com.aicarchecking

import com.aicarchecking.data.obd.DtcDictionary
import com.aicarchecking.data.obd.ObdPid
import com.aicarchecking.data.obd.ObdPidDecoder
import com.aicarchecking.domain.model.Certainty
import com.aicarchecking.domain.model.Confidence
import com.aicarchecking.domain.model.DistanceUnit
import com.aicarchecking.domain.model.EvidenceType
import com.aicarchecking.domain.model.Finding
import com.aicarchecking.domain.model.FindingCategory
import com.aicarchecking.domain.model.MileageRecord
import com.aicarchecking.domain.model.MileageSource
import com.aicarchecking.domain.model.Panel
import com.aicarchecking.domain.model.Severity
import com.aicarchecking.domain.usecase.MileageConsistencyChecker
import com.aicarchecking.domain.usecase.MileageVerdict
import com.aicarchecking.domain.usecase.ReportBuilder
import com.aicarchecking.media.ocr.OdometerParser
import com.aicarchecking.media.ocr.ServiceDocumentParser
import com.aicarchecking.media.ocr.VinParser
import com.aicarchecking.safety.SafetyHazard
import com.aicarchecking.safety.SafetyPolicyEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainLogicTest {

    private val day = 24L * 60 * 60 * 1000

    private fun rec(km: Long, date: Long, source: MileageSource = MileageSource.SERVICE_DOCUMENT) =
        MileageRecord("r$km$date", "v", null, source, km, km, DistanceUnit.KM, date)

    @Test
    fun singleReadingIsInsufficient() {
        val a = MileageConsistencyChecker.assess(listOf(rec(86_200, 1000 * day)))
        assertEquals(MileageVerdict.INSUFFICIENT, a.verdict)
        assertTrue(a.explanation.contains("cannot be determined"))
    }

    @Test
    fun decreasingMileageIsInconsistentButNeverCalledRollback() {
        val a = MileageConsistencyChecker.assess(listOf(rec(119_000, 100 * day), rec(86_200, 900 * day, MileageSource.DASHBOARD_OCR)))
        assertEquals(MileageVerdict.INCONSISTENT, a.verdict)
        assertEquals(1, a.conflicts.size)
        assertFalse(a.explanation.contains("rolled back", ignoreCase = true))
    }

    @Test
    fun increasingMileageHasNoConflict() {
        val a = MileageConsistencyChecker.assess(listOf(rec(72_500, 10 * day), rec(86_200, 400 * day)))
        assertEquals(MileageVerdict.NO_CONFLICT, a.verdict)
    }

    @Test
    fun odometerParsing() {
        assertEquals(86_200L, OdometerParser.parse("ODO 86,200 km\nTRIP 123.4 km")?.value)
        assertEquals(DistanceUnit.MILES, OdometerParser.parse("53,560 mi")?.unit)
        assertEquals(119_000L, OdometerParser.parse("Date: 14/03/2023\nMileage: 119,000 km")?.value)
        assertNull(OdometerParser.parse("no numbers here"))
        assertEquals(1609L, OdometerParser.toKm(1000, DistanceUnit.MILES))
    }

    @Test
    fun serviceDocumentParsing() {
        val facts = ServiceDocumentParser.parse("Demo Motors\nDate: 14/03/2023\nMileage: 119,000 km")
        assertNotNull(facts.dateMillis)
        assertEquals("14/03/2023", facts.rawDate)
        assertEquals(119_000L, facts.mileage?.value)
    }

    @Test
    fun vinParsing() {
        assertEquals("JTDBR32E720123456", VinParser.parse("VIN: JTDBR32E720123456"))
        assertNull(VinParser.parse("VIN: JTDBR32E72012345O")) // contains the letter O
        assertTrue(VinParser.matches("jtdbr32e7-2012 3456", "JTDBR32E720123456"))
    }

    @Test
    fun obdDecoding() {
        assertEquals(1726.0, ObdPidDecoder.decode(ObdPid.ENGINE_RPM, "41 0C 1A F8")!!, 0.01)
        assertEquals(50.0, ObdPidDecoder.decode(ObdPid.COOLANT_TEMP, "41 05 5A")!!, 0.01)
        assertNull(ObdPidDecoder.decode(ObdPid.ENGINE_RPM, "NO DATA"))
        assertEquals(listOf("P0133", "P0420"), ObdPidDecoder.decodeDtcs("43 01 33 04 20 00 00"))
        assertNotNull(DtcDictionary.lookup("p0420"))
        assertTrue(DtcDictionary.isValidFormat("P0420"))
        assertFalse(DtcDictionary.isValidFormat("X1234"))
    }

    private fun finding(obs: String, severity: Severity = Severity.MEDIUM, status: String = "POSSIBLE_ISSUE", panel: Panel? = null, category: FindingCategory = FindingCategory.GENERAL) = Finding(
        id = obs.hashCode().toString(), inspectionId = "i", vehicleId = "v", step = null, category = category, panel = panel,
        status = status, certainty = Certainty.POSSIBLE, severity = severity, confidence = Confidence.MEDIUM,
        evidenceIds = listOf("m"), evidenceType = EvidenceType.PHOTO, observation = obs, recommendation = "Inspect.",
        verificationMethod = "Physical inspection",
    )

    @Test
    fun safetyEngineOverridesFuelLeak() {
        val out = SafetyPolicyEngine.apply(listOf(finding("Wet residue consistent with a possible fuel leak near the tank.")))
        assertTrue(out.single().safetyCritical)
        assertEquals(Severity.CRITICAL, out.single().severity)
        assertTrue(out.single().recommendation.startsWith("SAFETY:"))
    }

    @Test
    fun safetyEngineIgnoresNegation() {
        val out = SafetyPolicyEngine.apply(listOf(finding("No visible fuel leak observed.", Severity.LOW, "NO_OBVIOUS_ISSUE")))
        assertFalse(out.single().safetyCritical)
    }

    @Test
    fun safetyScreensChatText() {
        assertTrue(SafetyPolicyEngine.screenText("my car is overheating and there is steam coming out").any { it.hazard == SafetyHazard.OVERHEATING })
        assertTrue(SafetyPolicyEngine.screenText("the tyres look fine").isEmpty())
    }

    @Test
    fun paintMapMostSeriousStatusWins() {
        val a = finding("Looks consistent", Severity.LOW, "APPEARS_ORIGINAL", Panel.FRONT_LEFT_FENDER, FindingCategory.PAINT)
        val b = finding("Shade differs", Severity.MEDIUM, "POSSIBLE_REPAINT", Panel.FRONT_LEFT_FENDER, FindingCategory.PAINT)
        assertEquals("POSSIBLE_REPAINT", ReportBuilder.panelStatuses(listOf(a, b))[Panel.FRONT_LEFT_FENDER]?.status)
        assertEquals(listOf(Panel.FRONT_LEFT_FENDER), ReportBuilder.paintConcerns(listOf(a, b)))
    }
}
