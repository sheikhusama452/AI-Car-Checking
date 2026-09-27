package com.aicarchecking.media.ocr

import com.aicarchecking.domain.model.Confidence
import com.aicarchecking.domain.model.DistanceUnit
import java.util.Calendar
import java.util.Locale

data class OdometerReading(val value: Long, val unit: DistanceUnit, val confidence: Confidence, val raw: String)

data class DocumentFacts(val dateMillis: Long?, val mileage: OdometerReading?, val rawDate: String?)

/** Pure-Kotlin parsers for OCR output. Deliberately conservative: when unsure they return null. */
object OdometerParser {

    private val unitAfter = Regex("""(?i)(?<![\d,.])(\d{1,3}(?:[,.]\d{3})+|\d{2,7})\s*(km|kms|mi|miles)\b""")
    private val odoBefore = Regex("""(?i)\b(?:odo(?:meter)?|mileage|total)\s*[:\-]?\s*(\d{1,3}(?:[,.]\d{3})+|\d{3,7})""")
    private val bareNumber = Regex("""(?<![\d.:])(\d{1,3}(?:,\d{3})+|\d{4,7})(?![\d.:])""")

    fun parse(text: String): OdometerReading? {
        unitAfter.findAll(text).mapNotNull { m ->
            val value = digits(m.groupValues[1]) ?: return@mapNotNull null
            val unit = if (m.groupValues[2].lowercase().startsWith("mi")) DistanceUnit.MILES else DistanceUnit.KM
            OdometerReading(value, unit, Confidence.HIGH, m.value)
        }.filter { plausible(it.value) }.maxByOrNull { it.value }?.let { return it }

        odoBefore.find(text)?.let { m ->
            digits(m.groupValues[1])?.takeIf(::plausible)?.let {
                return OdometerReading(it, DistanceUnit.KM, Confidence.MEDIUM, m.value)
            }
        }

        // Last resort: the largest bare integer. Trip meters usually have a decimal and are excluded.
        return bareNumber.findAll(text)
            .mapNotNull { m -> digits(m.groupValues[1])?.let { it to m.value } }
            .filter { plausible(it.first) && !looksLikeYear(it.first) }
            .maxByOrNull { it.first }
            ?.let { OdometerReading(it.first, DistanceUnit.KM, Confidence.LOW, it.second) }
    }

    private fun digits(s: String): Long? = s.filter { it.isDigit() }.toLongOrNull()
    private fun plausible(v: Long) = v in 10..1_999_999
    private fun looksLikeYear(v: Long) = v in 1950..2100

    fun toKm(value: Long, unit: DistanceUnit): Long = when (unit) {
        DistanceUnit.KM -> value
        DistanceUnit.MILES -> Math.round(value * 1.609344)
    }
}

object VinParser {
    private val vin = Regex("""\b[A-HJ-NPR-Z0-9]{17}\b""")

    /** Returns a 17-character VIN if one is found (letters I, O and Q are never used in VINs). */
    fun parse(text: String): String? = vin.findAll(text.uppercase().replace(" ", ""))
        .map { it.value }
        .firstOrNull { v -> v.any { it.isDigit() } && v.any { it.isLetter() } }

    /** Compares ignoring spaces, dashes and case. */
    fun matches(a: String, b: String): Boolean {
        fun norm(s: String) = s.uppercase().filter { it.isLetterOrDigit() }
        return norm(a).isNotEmpty() && norm(a) == norm(b)
    }
}

object ServiceDocumentParser {
    private val numericDate = Regex("""\b(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{4})\b""")
    private val isoDate = Regex("""\b(\d{4})-(\d{2})-(\d{2})\b""")
    private val textDate = Regex("""(?i)\b(\d{1,2})[\s\-]([A-Za-z]{3})[A-Za-z]*[\s\-,]+(\d{4})\b""")
    private val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    fun parse(text: String): DocumentFacts {
        var raw: String? = null
        val date: Long? = isoDate.find(text)?.let { m ->
            raw = m.value
            millis(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        } ?: textDate.find(text)?.let { m ->
            val month = months.indexOf(m.groupValues[2].lowercase(Locale.ROOT).take(3)) + 1
            if (month == 0) null else {
                raw = m.value
                millis(m.groupValues[3].toInt(), month, m.groupValues[1].toInt())
            }
        } ?: numericDate.find(text)?.let { m ->
            raw = m.value
            // Pakistan/UK convention: day/month/year.
            millis(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt())
        }
        return DocumentFacts(date, OdometerParser.parse(text), raw)
    }

    private fun millis(year: Int, month: Int, day: Int): Long? {
        if (month !in 1..12 || day !in 1..31 || year !in 1970..2100) return null
        return Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, 12, 0)
        }.timeInMillis
    }
}
