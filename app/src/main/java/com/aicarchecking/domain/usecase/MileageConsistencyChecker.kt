package com.aicarchecking.domain.usecase

import com.aicarchecking.domain.model.MileageRecord
import kotlin.math.abs

enum class MileageVerdict(val title: String) {
    INSUFFICIENT("Mileage cannot be assessed yet"),
    NO_CONFLICT("No conflicting readings found"),
    INCONSISTENT("Mileage Inconsistency Detected"),
}

data class MileageConflict(val earlier: MileageRecord, val later: MileageRecord, val differenceKm: Long)

data class MileageAssessment(
    val verdict: MileageVerdict,
    val explanation: String,
    val timeline: List<MileageRecord>,
    val conflicts: List<MileageConflict>,
)

/**
 * Builds a chronological mileage timeline and flags readings that go backwards in time.
 * It never concludes that an odometer was rolled back (spec §12).
 */
object MileageConsistencyChecker {

    /** Readings within this tolerance of each other are treated as equal (OCR/rounding noise). */
    private const val TOLERANCE_KM = 500L

    fun assess(records: List<MileageRecord>): MileageAssessment {
        val timeline = records.sortedWith(compareBy({ it.recordedDate }, { it.valueKm }))
        if (timeline.size < 2) {
            return MileageAssessment(
                MileageVerdict.INSUFFICIENT,
                "Mileage rollback cannot be determined from the provided evidence. Add a dashboard photo and service documents to build a mileage timeline.",
                timeline, emptyList(),
            )
        }
        val conflicts = mutableListOf<MileageConflict>()
        for (i in timeline.indices) {
            for (j in i + 1 until timeline.size) {
                val earlier = timeline[i]
                val later = timeline[j]
                val sameDay = abs(later.recordedDate - earlier.recordedDate) < DAY_MS
                val diff = earlier.valueKm - later.valueKm
                if (!sameDay && diff > TOLERANCE_KM) conflicts += MileageConflict(earlier, later, diff)
                if (sameDay && abs(diff) > TOLERANCE_KM) conflicts += MileageConflict(earlier, later, abs(diff))
            }
        }
        return if (conflicts.isEmpty()) {
            MileageAssessment(
                MileageVerdict.NO_CONFLICT,
                "The available readings increase over time. This does not prove the mileage is genuine — dashboard mileage alone cannot prove or disprove rollback.",
                timeline, emptyList(),
            )
        } else {
            MileageAssessment(
                MileageVerdict.INCONSISTENT,
                "The available evidence contains conflicting mileage readings. A later record shows a lower reading than an earlier one. " +
                    "This can have several explanations (document errors, instrument cluster replacement, OCR misread or mileage alteration) and must be verified before purchase.",
                timeline, conflicts,
            )
        }
    }

    private const val DAY_MS = 24L * 60 * 60 * 1000
}
