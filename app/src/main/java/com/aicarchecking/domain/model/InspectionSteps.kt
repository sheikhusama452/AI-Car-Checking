package com.aicarchecking.domain.model

enum class CaptureMode { PHOTO, VIDEO, AUDIO, DOCUMENT, NONE }

/**
 * AI analysis tasks. Each one has its own prompt (see PromptLibrary).
 * [localOnly] tasks never call an external provider.
 */
enum class AnalysisTask(val label: String, val category: FindingCategory, val localOnly: Boolean = false) {
    PAINT("Paint Analysis", FindingCategory.PAINT),
    ACCIDENT("Accident / Repair Analysis", FindingCategory.ACCIDENT_REPAIR),
    DASHBOARD_OCR("Dashboard Mileage OCR", FindingCategory.MILEAGE, localOnly = true),
    VIN_OCR("VIN / Chassis OCR", FindingCategory.VIN, localOnly = true),
    DASHBOARD_WARNING("Dashboard Warning Lights", FindingCategory.DASHBOARD_WARNING),
    ENGINE_BAY("Engine Bay Analysis", FindingCategory.ENGINE_BAY),
    COLD_START("Cold Start Analysis", FindingCategory.COLD_START),
    ENGINE_SOUND("Engine Sound Analysis", FindingCategory.ENGINE_SOUND),
    SMOKE("Exhaust Smoke Analysis", FindingCategory.EXHAUST_SMOKE),
    TYRES("Tyre Analysis", FindingCategory.TYRES),
    BRAKES("Brake Analysis", FindingCategory.BRAKES),
    INTERIOR("Interior Analysis", FindingCategory.INTERIOR),
    UNDERBODY("Underbody Analysis", FindingCategory.UNDERBODY),
    TEST_DRIVE("Test Drive Analysis", FindingCategory.SUSPENSION),
    OBD_EXPLANATION("OBD Explanation", FindingCategory.OBD, localOnly = true),
}

/** Short, honest limitation text shown on analysis screens (spec §62). */
object Limitations {
    const val GENERAL =
        "AI analysis is based only on the evidence provided. Some vehicle problems cannot be confirmed without physical inspection, diagnostic equipment or verified vehicle history."
    const val PAINT = "A photograph cannot guarantee original factory paint."
    const val MILEAGE = "Dashboard mileage alone cannot prove or disprove rollback."
    const val ENGINE = "Audio/video analysis cannot confirm internal engine damage."
    const val ACCIDENT = "Visible evidence cannot rule out hidden structural damage."
    const val STRUCTURAL =
        "Hidden structural damage cannot be ruled out from photographs alone. Physical inspection is recommended."

    fun forTask(task: AnalysisTask?): String? = when (task) {
        AnalysisTask.PAINT -> PAINT
        AnalysisTask.ACCIDENT -> ACCIDENT
        AnalysisTask.DASHBOARD_OCR -> MILEAGE
        AnalysisTask.ENGINE_BAY, AnalysisTask.ENGINE_SOUND, AnalysisTask.COLD_START, AnalysisTask.SMOKE -> ENGINE
        else -> null
    }
}

/**
 * The 27-step guided pre-purchase inspection. Each step carries the AI Inspection Coach
 * instructions shown before capture.
 */
enum class InspectionStep(
    val number: Int,
    val title: String,
    val capture: CaptureMode,
    val tasks: List<AnalysisTask>,
    val coach: List<String>,
    val panels: List<Panel> = emptyList(),
    val safetyNote: String? = null,
    val minEvidence: Int = 1,
) {
    VEHICLE_OVERVIEW(
        1, "Vehicle Overview", CaptureMode.PHOTO, listOf(AnalysisTask.ACCIDENT),
        listOf(
            "Stand 3–5 meters away at a front corner so two sides are visible.",
            "Capture all four corners of the car.",
            "Shoot in even daylight or shade; avoid direct sun reflections.",
        ),
        minEvidence = 2,
    ),
    FRONT(
        2, "Front", CaptureMode.PHOTO, listOf(AnalysisTask.PAINT, AnalysisTask.ACCIDENT),
        listOf(
            "Stand 2–3 meters in front of the car, centred.",
            "Keep the camera level with the headlights.",
            "Make sure the bonnet line, grille and both headlights are visible.",
        ),
        panels = listOf(Panel.FRONT_BUMPER, Panel.BONNET),
    ),
    FRONT_LEFT_FENDER(
        3, "Front Left Fender", CaptureMode.PHOTO, listOf(AnalysisTask.PAINT, AnalysisTask.ACCIDENT),
        listOf(
            "Stand approximately 1–2 meters away.",
            "Keep the camera straight.",
            "Make sure the entire panel is visible, including the gap to the door and bonnet.",
            "Avoid strong reflections. Take a second photo at a 45° angle to show paint texture.",
        ),
        panels = listOf(Panel.FRONT_LEFT_FENDER),
    ),
    FRONT_RIGHT_FENDER(
        4, "Front Right Fender", CaptureMode.PHOTO, listOf(AnalysisTask.PAINT, AnalysisTask.ACCIDENT),
        listOf(
            "Stand approximately 1–2 meters away.",
            "Keep the camera straight.",
            "Make sure the entire panel is visible, including the gap to the door and bonnet.",
            "Avoid strong reflections. Take a second photo at a 45° angle to show paint texture.",
        ),
        panels = listOf(Panel.FRONT_RIGHT_FENDER),
    ),
    LEFT_SIDE(
        5, "Left Side", CaptureMode.PHOTO, listOf(AnalysisTask.PAINT, AnalysisTask.ACCIDENT),
        listOf(
            "Stand 3–4 meters from the left side so the full length fits in frame.",
            "Hold the phone at door-handle height.",
            "Look along the side at a shallow angle for a second photo — ripples and shade differences show up best this way.",
        ),
        panels = listOf(Panel.FRONT_LEFT_DOOR, Panel.REAR_LEFT_DOOR, Panel.REAR_LEFT_FENDER),
    ),
    RIGHT_SIDE(
        6, "Right Side", CaptureMode.PHOTO, listOf(AnalysisTask.PAINT, AnalysisTask.ACCIDENT),
        listOf(
            "Stand 3–4 meters from the right side so the full length fits in frame.",
            "Hold the phone at door-handle height.",
            "Take a second photo along the side at a shallow angle.",
        ),
        panels = listOf(Panel.FRONT_RIGHT_DOOR, Panel.REAR_RIGHT_DOOR, Panel.REAR_RIGHT_FENDER),
    ),
    DOORS(
        7, "Doors", CaptureMode.PHOTO, listOf(AnalysisTask.PAINT, AnalysisTask.ACCIDENT),
        listOf(
            "Open each door and photograph the edges, hinges and inner sill.",
            "Look for overspray, masking lines, or disturbed bolts on hinges.",
            "Close-ups from about 30–50 cm work best.",
        ),
        panels = listOf(Panel.FRONT_LEFT_DOOR, Panel.FRONT_RIGHT_DOOR, Panel.REAR_LEFT_DOOR, Panel.REAR_RIGHT_DOOR),
    ),
    ROOF(
        8, "Roof", CaptureMode.PHOTO, listOf(AnalysisTask.PAINT, AnalysisTask.ACCIDENT),
        listOf(
            "Hold the phone above head height, angled down at the roof.",
            "Include the roof pillars if possible.",
            "Do not climb on the vehicle.",
        ),
        panels = listOf(Panel.ROOF),
    ),
    BONNET(
        9, "Bonnet", CaptureMode.PHOTO, listOf(AnalysisTask.PAINT, AnalysisTask.ACCIDENT),
        listOf(
            "Photograph the bonnet from the front at a low angle to show reflections.",
            "Open the bonnet and photograph its underside edges and hinge bolts.",
        ),
        panels = listOf(Panel.BONNET),
    ),
    TRUNK(
        10, "Trunk", CaptureMode.PHOTO, listOf(AnalysisTask.PAINT, AnalysisTask.ACCIDENT),
        listOf(
            "Photograph the trunk lid closed, then open.",
            "Lift the floor cover and photograph the spare-wheel well for creases or welds.",
        ),
        panels = listOf(Panel.TRUNK),
    ),
    REAR(
        11, "Rear", CaptureMode.PHOTO, listOf(AnalysisTask.PAINT, AnalysisTask.ACCIDENT),
        listOf(
            "Stand 2–3 meters behind the car, centred.",
            "Make sure both tail lights and the full bumper are visible.",
        ),
        panels = listOf(Panel.REAR_BUMPER, Panel.TRUNK),
    ),
    BUMPERS(
        12, "Bumpers", CaptureMode.PHOTO, listOf(AnalysisTask.PAINT, AnalysisTask.ACCIDENT),
        listOf(
            "Photograph each bumper corner from about 1 meter.",
            "Capture the gap between bumper and fenders.",
        ),
        panels = listOf(Panel.FRONT_BUMPER, Panel.REAR_BUMPER),
    ),
    LIGHTS(
        13, "Lights", CaptureMode.PHOTO, listOf(AnalysisTask.ACCIDENT),
        listOf(
            "Photograph both headlights together, then both tail lights together.",
            "Mismatched clarity or brand between left and right can indicate replacement.",
        ),
    ),
    WINDSHIELD(
        14, "Windshield", CaptureMode.PHOTO, listOf(AnalysisTask.ACCIDENT),
        listOf(
            "Photograph the windshield from outside, then its lower corner markings (brand/date).",
            "Look for cracks, chips and mismatched glass markings.",
        ),
    ),
    WHEELS(
        15, "Wheels", CaptureMode.PHOTO, listOf(AnalysisTask.BRAKES),
        listOf(
            "Photograph each wheel straight on from about 1 meter.",
            "If visible through the spokes, capture the brake disc surface.",
        ),
    ),
    TYRES(
        16, "Tyres", CaptureMode.PHOTO, listOf(AnalysisTask.TYRES),
        listOf(
            "Photograph the tread across its full width, then the sidewall.",
            "Capture the DOT/date code on the sidewall if readable.",
            "Tread depth should still be measured physically.",
        ),
        minEvidence = 2,
    ),
    INTERIOR(
        17, "Interior", CaptureMode.PHOTO, listOf(AnalysisTask.INTERIOR),
        listOf(
            "Photograph the driver seat, steering wheel, pedals and gear knob.",
            "Wear on these parts helps judge whether mileage is plausible.",
        ),
    ),
    DASHBOARD(
        18, "Dashboard", CaptureMode.PHOTO, listOf(AnalysisTask.DASHBOARD_OCR, AnalysisTask.DASHBOARD_WARNING),
        listOf(
            "Switch the ignition ON so the odometer is lit.",
            "Hold the phone 30–50 cm from the instrument cluster, straight on.",
            "Avoid glare on the cluster glass; shade it with your hand if needed.",
            "Make sure the odometer digits are sharp.",
        ),
    ),
    ENGINE_BAY(
        19, "Engine Bay", CaptureMode.PHOTO, listOf(AnalysisTask.ENGINE_BAY),
        listOf(
            "With the engine OFF and cool, open the bonnet.",
            "Photograph the whole bay from above, then close-ups of hoses, belts and the battery.",
            "Do not touch hot components.",
        ),
        safetyNote = "Only inspect the engine bay with the engine off. Hot components and moving belts can cause serious injury.",
        minEvidence = 2,
    ),
    COLD_START(
        20, "Cold Start", CaptureMode.VIDEO, listOf(AnalysisTask.COLD_START),
        listOf(
            "The engine must be cold (not run for several hours).",
            "Start recording, then start the engine.",
            "Keep recording 20–30 seconds after it starts, with the dashboard or exhaust in view.",
        ),
    ),
    ENGINE_SOUND(
        21, "Engine Sound", CaptureMode.AUDIO, listOf(AnalysisTask.ENGINE_SOUND),
        listOf(
            "Record 10–30 seconds at idle with the bonnet open, phone ~50 cm from the engine.",
            "Optionally ask someone to gently raise the RPM for 5 seconds.",
            "Keep hands, hair and the phone away from moving belts and fans.",
        ),
        safetyNote = "Keep clear of moving belts and the cooling fan while the engine is running.",
    ),
    EXHAUST_SMOKE(
        22, "Exhaust Smoke", CaptureMode.VIDEO, listOf(AnalysisTask.SMOKE),
        listOf(
            "Record the exhaust from 1–2 meters to the side, never directly behind.",
            "Capture startup, idle, and a light rev.",
            "Record against a plain background if possible.",
        ),
        safetyNote = "Never stand directly behind a running vehicle.",
    ),
    WARNING_LIGHTS(
        23, "Warning Lights", CaptureMode.PHOTO, listOf(AnalysisTask.DASHBOARD_WARNING),
        listOf(
            "Photograph the cluster with ignition ON (all lights test), then with the engine running.",
            "Warning lights should go out after the engine starts.",
        ),
    ),
    OBD(
        24, "OBD", CaptureMode.NONE, listOf(AnalysisTask.OBD_EXPLANATION),
        listOf(
            "Enter any diagnostic trouble codes read by a scanner or mechanic.",
            "Bluetooth OBD adapter connection is coming in Phase 3.",
        ),
        minEvidence = 0,
    ),
    UNDERBODY(
        25, "Underbody", CaptureMode.PHOTO, listOf(AnalysisTask.UNDERBODY),
        listOf(
            "Only photograph what is visible from the side without going under the car.",
            "Use a lift at a workshop for a full underbody view.",
        ),
        safetyNote = "Never place yourself under an unsupported vehicle. Use proper lifting equipment and professional assistance.",
    ),
    TEST_DRIVE(
        26, "Test Drive", CaptureMode.VIDEO, listOf(AnalysisTask.TEST_DRIVE),
        listOf(
            "Mount the phone securely before driving, then start recording.",
            "Record only when safe. Do not interact with the phone while driving.",
            "A passenger can note any pulling, vibration or noises.",
        ),
        safetyNote = "Record only when safe. Do not interact with the phone while driving. Only test drive where it is legal and safe.",
    ),
    FINAL_REPORT(
        27, "Final AI Report", CaptureMode.NONE, emptyList(),
        listOf("Review all findings and generate the AI Vehicle Inspection Report."),
        minEvidence = 0,
    );

    val isBodyStep: Boolean get() = AnalysisTask.PAINT in tasks

    companion object {
        fun fromName(name: String?): InspectionStep? = entries.firstOrNull { it.name == name }

        /** Steps that count towards Inspection Completeness. */
        val evidenceSteps: List<InspectionStep> get() = entries.filter { it != FINAL_REPORT }
    }
}
