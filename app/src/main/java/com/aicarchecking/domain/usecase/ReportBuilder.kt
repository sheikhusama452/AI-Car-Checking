package com.aicarchecking.domain.usecase

import com.aicarchecking.domain.model.ActionBucket
import com.aicarchecking.domain.model.Finding
import com.aicarchecking.domain.model.FindingCategory
import com.aicarchecking.domain.model.GenericStatus
import com.aicarchecking.domain.model.Inspection
import com.aicarchecking.domain.model.InspectionStep
import com.aicarchecking.domain.model.MediaAsset
import com.aicarchecking.domain.model.ObdScan
import com.aicarchecking.domain.model.PaintStatus
import com.aicarchecking.domain.model.Panel
import com.aicarchecking.domain.model.ReportSection
import com.aicarchecking.domain.model.Severity
import com.aicarchecking.domain.model.Vehicle

data class Completeness(
    val percent: Int,
    val completedSteps: List<InspectionStep>,
    val missingSteps: List<InspectionStep>,
) {
    companion object {
        val METHODOLOGY: String =
            "Inspection Completeness = inspection steps with enough usable evidence (quality Good, Acceptable or Poor), " +
                "divided by all ${InspectionStep.evidenceSteps.size} evidence steps (OBD counts when a scan or codes are recorded). " +
                "Skipped steps and steps with only insufficient evidence count as missing. " +
                "It measures how much evidence was collected — not the condition of the car."
    }
}

data class ReportSectionContent(val section: ReportSection, val headline: String, val findings: List<Finding>)

data class InspectionReportModel(
    val vehicle: Vehicle,
    val inspection: Inspection,
    val generatedAt: Long,
    val completeness: Completeness,
    val paintHeadline: String,
    val mileageHeadline: String,
    val engineHeadline: String,
    val obdHeadline: String,
    val tyresHeadline: String,
    val sections: List<ReportSectionContent>,
    val buckets: Map<ActionBucket, List<Finding>>,
    val whatToVerify: List<String>,
    val severityCounts: Map<Severity, Int>,
    val mileage: MileageAssessment,
    val obdScans: List<ObdScan>,
    val isDemo: Boolean,
)

object ReportBuilder {

    fun completeness(inspection: Inspection, media: List<MediaAsset>, obdScans: List<ObdScan>): Completeness {
        val steps = InspectionStep.evidenceSteps
        val completed = steps.filter { step ->
            if (step in inspection.skippedSteps) return@filter false
            when (step) {
                InspectionStep.OBD -> obdScans.isNotEmpty()
                else -> media.count { it.step == step && it.quality.usableForAnalysis } >= maxOf(1, step.minEvidence)
            }
        }
        val pct = if (steps.isEmpty()) 0 else (completed.size * 100) / steps.size
        return Completeness(pct, completed, steps - completed.toSet())
    }

    fun methodology(): String = Completeness.METHODOLOGY

    fun bucketOf(f: Finding): ActionBucket = when {
        f.safetyCritical || f.severity == Severity.CRITICAL -> ActionBucket.IMMEDIATE_ATTENTION
        isNoIssue(f) -> ActionBucket.NO_OBVIOUS_ISSUE
        f.severity == Severity.HIGH || f.severity == Severity.MEDIUM -> ActionBucket.NEEDS_INSPECTION
        else -> ActionBucket.MONITOR
    }

    private fun isNoIssue(f: Finding) = f.status in setOf(
        PaintStatus.APPEARS_ORIGINAL.name, GenericStatus.NO_OBVIOUS_ISSUE.name, "NO_OBVIOUS_INDICATORS",
    )

    fun build(
        vehicle: Vehicle,
        inspection: Inspection,
        findings: List<Finding>,
        media: List<MediaAsset>,
        mileage: MileageAssessment,
        obdScans: List<ObdScan>,
        now: Long = System.currentTimeMillis(),
    ): InspectionReportModel {
        val completeness = completeness(inspection, media, obdScans)
        val sorted = findings.sortedWith(compareByDescending<Finding> { it.safetyCritical }.thenByDescending { it.severity.rank })

        val paintIssues = paintConcerns(findings)
        val paintHeadline = when {
            paintIssues.isNotEmpty() -> "Possible repaint/repair indicators found on: " + paintIssues.joinToString { it.label }
            findings.any { it.category == FindingCategory.PAINT && it.status == PaintStatus.APPEARS_ORIGINAL.name } ->
                "No repaint indicators seen in the captured photos. A photograph cannot guarantee original factory paint."
            else -> "Paint not analyzed yet."
        }
        val mileageHeadline = when (mileage.verdict) {
            MileageVerdict.INCONSISTENT -> "Mileage Inconsistency Detected — evidence requires verification."
            MileageVerdict.NO_CONFLICT -> "No conflicting readings in the available evidence (not proof of genuine mileage)."
            MileageVerdict.INSUFFICIENT -> "Mileage rollback cannot be determined from the provided evidence."
        }
        val engineFindings = findings.filter { it.category.section == ReportSection.ENGINE }
        val engineHeadline = when {
            engineFindings.isEmpty() -> "Engine not analyzed yet."
            engineFindings.all(::isNoIssue) -> "No obvious abnormal visual indicator detected."
            else -> engineFindings.filterNot(::isNoIssue).joinToString("; ") { "${it.category.label}: ${it.statusLabel}" }
        }
        val codes = obdScans.flatMap { s -> s.codes.map { it.code } }.distinct()
        val obdHeadline = when {
            obdScans.isEmpty() -> "No OBD scan recorded."
            codes.isEmpty() -> "No trouble codes recorded in the OBD scan."
            else -> codes.joinToString() + " detected."
        }
        val tyreFindings = findings.filter { it.category == FindingCategory.TYRES && !isNoIssue(it) }
        val tyresHeadline = when {
            findings.none { it.category == FindingCategory.TYRES } -> "Tyres not analyzed yet."
            tyreFindings.isEmpty() -> "No obvious visible tyre issue."
            else -> tyreFindings.first().observation
        }

        val sections = ReportSection.entries.map { section ->
            val list = sorted.filter { it.category.section == section }
            val headline = when (section) {
                ReportSection.BODY_PAINT -> paintHeadline
                ReportSection.MILEAGE -> mileageHeadline
                ReportSection.OBD -> obdHeadline
                ReportSection.ACCIDENT_REPAIR -> if (list.isEmpty()) "Not analyzed yet." else
                    list.firstOrNull { !isNoIssue(it) }?.statusLabel ?: "No obvious visible repair indicators. Hidden structural damage cannot be ruled out from photographs alone."
                else -> when {
                    list.isEmpty() -> "Not analyzed yet."
                    list.all(::isNoIssue) -> "No obvious issue detected in the provided evidence."
                    else -> "${list.count { !isNoIssue(it) }} item(s) to verify."
                }
            }
            ReportSectionContent(section, headline, list)
        }

        val buckets = ActionBucket.entries.associateWith { b -> sorted.filter { bucketOf(it) == b } }

        val verify = buildList {
            findings.filter { it.safetyCritical }.forEach { add("${it.category.label}: ${it.recommendation}") }
            if (paintIssues.isNotEmpty()) add("Paint thickness on: ${paintIssues.joinToString { it.label }}.")
            if (mileage.verdict != MileageVerdict.NO_CONFLICT) add("Mileage history (service records, previous inspections, registration documents).")
            codes.forEach { add("$it — diagnostic inspection before purchase.") }
            findings.filter { it.category == FindingCategory.ENGINE_SOUND || it.category == FindingCategory.EXHAUST_SMOKE || it.category == FindingCategory.ENGINE_BAY }
                .filterNot(::isNoIssue).forEach { add("${it.category.label}: ${it.verificationMethod}.") }
            tyreFindings.forEach { add("Tyres: ${it.verificationMethod}.") }
            findings.filter { it.category == FindingCategory.DASHBOARD_WARNING && !isNoIssue(it) }.forEach { add("Warning lights: ${it.verificationMethod}.") }
            add("Physical accident/structural inspection on a workshop lift.")
            completeness.missingSteps.takeIf { it.isNotEmpty() }?.let { missing ->
                add("Collect missing evidence: " + missing.take(6).joinToString { it.title } + if (missing.size > 6) " and ${missing.size - 6} more." else ".")
            }
        }.distinct()

        return InspectionReportModel(
            vehicle = vehicle, inspection = inspection, generatedAt = now, completeness = completeness,
            paintHeadline = paintHeadline, mileageHeadline = mileageHeadline, engineHeadline = engineHeadline,
            obdHeadline = obdHeadline, tyresHeadline = tyresHeadline, sections = sections, buckets = buckets,
            whatToVerify = verify, severityCounts = Severity.entries.associateWith { s -> findings.count { it.severity == s } },
            mileage = mileage, obdScans = obdScans, isDemo = inspection.isDemo,
        )
    }

    /** Panels whose best available finding suggests repaint/repair/replacement. */
    fun paintConcerns(findings: List<Finding>): List<Panel> {
        val concern = setOf(
            PaintStatus.POSSIBLE_REPAINT, PaintStatus.STRONG_REPAINT_INDICATORS,
            PaintStatus.POSSIBLE_REPAIR, PaintStatus.POSSIBLE_PANEL_REPLACEMENT,
        ).map { it.name }.toSet()
        return panelStatuses(findings).filter { it.value.status in concern }.keys.toList()
    }

    /**
     * Resolves one status per panel. When several findings cover a panel, the most serious concern wins
     * (a concern is never hidden by an 'appears original' result from another photo).
     */
    fun panelStatuses(findings: List<Finding>): Map<Panel, Finding> =
        findings.filter { it.category == FindingCategory.PAINT && it.panel != null }
            .groupBy { it.panel!! }
            .mapValues { (_, list) -> list.maxWith(compareBy<Finding>({ panelRank(it.status) }, { it.confidence.rank })) }

    private fun panelRank(status: String): Int = when (PaintStatus.fromCode(status)) {
        PaintStatus.STRONG_REPAINT_INDICATORS, PaintStatus.POSSIBLE_PANEL_REPLACEMENT -> 5
        PaintStatus.POSSIBLE_REPAINT, PaintStatus.POSSIBLE_REPAIR -> 4
        PaintStatus.APPEARS_ORIGINAL -> 3
        PaintStatus.CANNOT_DETERMINE -> 2
        PaintStatus.INSUFFICIENT_EVIDENCE -> 1
        else -> 0
    }
}
