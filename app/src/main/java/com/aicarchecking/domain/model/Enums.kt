package com.aicarchecking.domain.model

enum class InspectionType(val label: String) {
    PRE_PURCHASE("Pre-Purchase Inspection"),
    GENERAL("General Vehicle Check"),
    PROBLEM_DIAGNOSIS("Problem Diagnosis"),
    POST_REPAIR("Post-Repair Verification"),
    PERIODIC("Periodic Inspection"),
}

enum class InspectionStatus { IN_PROGRESS, COMPLETED }

enum class EvidenceType(val label: String) {
    PHOTO("Photo"),
    VIDEO("Video"),
    AUDIO("Audio"),
    OCR("OCR"),
    OBD("OBD"),
    USER_INPUT("User Input"),
    SERVICE_DOCUMENT("Service Document"),
    TEST_DRIVE("Test Drive"),
    HISTORICAL_RECORD("Historical Record"),
}

/** Quality grade assigned to every evidence item before any AI analysis. */
enum class EvidenceQuality(val label: String, val usableForAnalysis: Boolean) {
    GOOD("Good", true),
    ACCEPTABLE("Acceptable", true),
    POOR("Poor", true),
    INSUFFICIENT("Insufficient", false),
    NOT_ASSESSED("Not assessed", false),
}

enum class ProcessingStatus(val label: String) {
    PENDING("Pending"),
    PROCESSING("Processing"),
    COMPLETED("Completed"),
    FAILED("Failed"),
    CANCELLED("Cancelled"),
}

enum class AnalysisStatus(val label: String) {
    NOT_ANALYZED("Not analyzed"),
    ANALYZING("Analyzing"),
    ANALYZED("Analyzed"),
    FAILED("Failed"),
}

/** Temporary evidence lives in cache (Android may evict it); permanent evidence is retained on request. */
enum class StorageTier(val label: String) { TEMPORARY("Temporary Cache"), PERMANENT("Permanent Evidence") }

enum class Severity(val label: String, val rank: Int) {
    LOW("LOW", 0), MEDIUM("MEDIUM", 1), HIGH("HIGH", 2), CRITICAL("CRITICAL", 3)
}

enum class Confidence(val label: String, val rank: Int) { LOW("Low", 0), MEDIUM("Medium", 1), HIGH("High", 2) }

/** How strongly a finding is asserted. Uncertain inferences can never be presented as confirmed facts. */
enum class Certainty(val label: String) {
    OBSERVED("Observed"),
    LIKELY("Likely"),
    POSSIBLE("Possible"),
    SUSPECTED("Suspected"),
    CANNOT_DETERMINE("Cannot determine"),
    REQUIRES_PHYSICAL_INSPECTION("Requires physical inspection"),
    REQUIRES_DIAGNOSTIC_EQUIPMENT("Requires diagnostic equipment"),
}

enum class ReportSection(val title: String) {
    BODY_PAINT("Body & Paint"),
    ACCIDENT_REPAIR("Accident / Repair Indicators"),
    ENGINE("Engine"),
    TRANSMISSION("Transmission"),
    ELECTRICAL("Electrical"),
    BRAKES("Brakes"),
    TYRES("Tyres"),
    SUSPENSION("Suspension"),
    EXHAUST("Exhaust"),
    OBD("OBD"),
    MILEAGE("Mileage Consistency"),
    SERVICE_HISTORY("Service History"),
    OTHER("Other Observations"),
}

enum class FindingCategory(val label: String, val section: ReportSection) {
    PAINT("Paint", ReportSection.BODY_PAINT),
    ACCIDENT_REPAIR("Accident / Repair", ReportSection.ACCIDENT_REPAIR),
    MILEAGE("Mileage", ReportSection.MILEAGE),
    VIN("VIN / Chassis", ReportSection.OTHER),
    ENGINE_BAY("Engine Bay", ReportSection.ENGINE),
    ENGINE_SOUND("Engine Sound", ReportSection.ENGINE),
    COLD_START("Cold Start", ReportSection.ENGINE),
    EXHAUST_SMOKE("Exhaust Smoke", ReportSection.EXHAUST),
    DASHBOARD_WARNING("Dashboard Warning", ReportSection.ELECTRICAL),
    OBD("OBD", ReportSection.OBD),
    TYRES("Tyres", ReportSection.TYRES),
    BRAKES("Brakes", ReportSection.BRAKES),
    SUSPENSION("Suspension", ReportSection.SUSPENSION),
    TRANSMISSION("Transmission", ReportSection.TRANSMISSION),
    ELECTRICAL("Electrical", ReportSection.ELECTRICAL),
    INTERIOR("Interior", ReportSection.OTHER),
    UNDERBODY("Underbody", ReportSection.OTHER),
    SERVICE_HISTORY("Service History", ReportSection.SERVICE_HISTORY),
    EVIDENCE_QUALITY("Evidence Quality", ReportSection.OTHER),
    GENERAL("General", ReportSection.OTHER),
}

enum class PaintStatus(val label: String, val defaultSeverity: Severity) {
    APPEARS_ORIGINAL("Appears Original", Severity.LOW),
    POSSIBLE_REPAINT("Possible Repaint", Severity.MEDIUM),
    STRONG_REPAINT_INDICATORS("Strong Repaint Indicators", Severity.HIGH),
    POSSIBLE_REPAIR("Possible Repair", Severity.MEDIUM),
    POSSIBLE_PANEL_REPLACEMENT("Possible Panel Replacement", Severity.HIGH),
    INSUFFICIENT_EVIDENCE("Insufficient Evidence", Severity.LOW),
    CANNOT_DETERMINE("Cannot Determine", Severity.LOW),
    NOT_CHECKED("Not Checked", Severity.LOW);

    companion object {
        fun fromCode(code: String?): PaintStatus? = entries.firstOrNull { it.name == code }
    }
}

enum class AccidentStatus(val label: String) {
    NO_OBVIOUS_INDICATORS("No obvious visible repair indicators"),
    POSSIBLE_PREVIOUS_REPAIR("Possible previous repair"),
    SIGNIFICANT_INDICATORS("Significant visible repair indicators"),
    INSUFFICIENT_EVIDENCE("Insufficient evidence"),
}

/** Generic statuses used by non-paint categories. */
enum class GenericStatus(val label: String) {
    NO_OBVIOUS_ISSUE("No obvious issue detected"),
    POSSIBLE_ISSUE("Possible issue"),
    ISSUE_OBSERVED("Issue observed"),
    NEEDS_VERIFICATION("Needs verification"),
    INSUFFICIENT_EVIDENCE("Insufficient evidence"),
    CANNOT_DETERMINE("Cannot determine"),
}

enum class Panel(val label: String) {
    BONNET("Bonnet"),
    ROOF("Roof"),
    FRONT_LEFT_FENDER("Front Left Fender"),
    FRONT_RIGHT_FENDER("Front Right Fender"),
    FRONT_LEFT_DOOR("Front Left Door"),
    FRONT_RIGHT_DOOR("Front Right Door"),
    REAR_LEFT_DOOR("Rear Left Door"),
    REAR_RIGHT_DOOR("Rear Right Door"),
    REAR_LEFT_FENDER("Rear Left Fender"),
    REAR_RIGHT_FENDER("Rear Right Fender"),
    FRONT_BUMPER("Front Bumper"),
    REAR_BUMPER("Rear Bumper"),
    TRUNK("Trunk");

    companion object {
        fun fromCode(code: String?): Panel? = entries.firstOrNull { it.name == code }
    }
}

enum class FindingSource { AI, LOCAL, DEMO, USER }

enum class MileageSource(val label: String) {
    DASHBOARD_OCR("Dashboard (OCR)"),
    DASHBOARD_MANUAL("Dashboard (entered)"),
    USER_ENTERED("User entered"),
    OBD("OBD"),
    SERVICE_DOCUMENT("Service document"),
    INSPECTION_REPORT("Inspection report"),
    PREVIOUS_INSPECTION("Previous inspection"),
}

enum class DistanceUnit(val label: String) { KM("km"), MILES("mi") }

enum class ObdSource(val label: String) {
    DEMO("Demo data"),
    MANUAL("Entered manually"),
    ADAPTER("OBD adapter"),
}

/** Action bucket shown in the report. */
enum class ActionBucket(val title: String) {
    IMMEDIATE_ATTENTION("Immediate Attention"),
    NEEDS_INSPECTION("Needs Inspection"),
    MONITOR("Monitor"),
    NO_OBVIOUS_ISSUE("No Obvious Issue Detected"),
}
