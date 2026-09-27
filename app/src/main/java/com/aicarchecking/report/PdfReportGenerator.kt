package com.aicarchecking.report

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.aicarchecking.domain.model.ActionBucket
import com.aicarchecking.domain.model.Finding
import com.aicarchecking.domain.model.Limitations
import com.aicarchecking.domain.model.Severity
import com.aicarchecking.domain.usecase.InspectionReportModel
import com.aicarchecking.domain.usecase.ReportBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Renders the AI Vehicle Inspection Report to an A4 PDF using the platform PdfDocument API. */
class PdfReportGenerator {

    private val pageWidth = 595
    private val pageHeight = 842
    private val margin = 40f
    private val contentWidth get() = (pageWidth - 2 * margin).toInt()

    private val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 20f; typeface = Typeface.DEFAULT_BOLD; color = Color.rgb(11, 18, 32) }
    private val h2 = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13f; typeface = Typeface.DEFAULT_BOLD; color = Color.rgb(0, 119, 150) }
    private val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = Color.rgb(30, 30, 30) }
    private val small = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8.5f; color = Color.rgb(90, 90, 90) }
    private val bold = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; typeface = Typeface.DEFAULT_BOLD; color = Color.rgb(30, 30, 30) }

    private lateinit var doc: PdfDocument
    private var page: PdfDocument.Page? = null
    private var y = 0f
    private var pageNo = 0

    suspend fun generate(model: InspectionReportModel, target: File): File = withContext(Dispatchers.IO) {
        doc = PdfDocument()
        pageNo = 0
        newPage()
        val date = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

        text("AI CAR CHECKING REPORT", title)
        text("AI Vehicle Inspection Report — Painted or Original? Check Before You Buy.", small)
        if (model.isDemo) text("DEMO MODE — sample data, not a real vehicle inspection.", bold)
        gap(8f)
        text("Vehicle: ${model.vehicle.displayName}${model.vehicle.variant.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""}", bold)
        text("Mileage: ${model.inspection.mileageKm?.let { "%,d km".format(it) } ?: "Not recorded"}", body)
        text("Inspection date: ${date.format(Date(model.inspection.startedAt))}", body)
        text("Inspection type: ${model.inspection.type.label}", body)
        text("Evidence completeness: ${model.completeness.percent}%", bold)
        text(ReportBuilder.methodology(), small)
        gap(8f)

        heading("Summary")
        text("Paint: ${model.paintHeadline}", body)
        text("Mileage: ${model.mileageHeadline}", body)
        text("Engine: ${model.engineHeadline}", body)
        text("OBD: ${model.obdHeadline}", body)
        text("Tyres: ${model.tyresHeadline}", body)
        text("Finding summary: " + Severity.entries.joinToString { "${it.label} ${model.severityCounts[it] ?: 0}" }, body)
        gap(6f)

        heading("What to verify before buying")
        model.whatToVerify.forEachIndexed { i, s -> text("${i + 1}. $s", body) }
        gap(6f)

        ActionBucket.entries.forEach { bucket ->
            val list = model.buckets[bucket].orEmpty()
            if (list.isEmpty()) return@forEach
            heading("${bucket.title} (${list.size})")
            list.forEach { finding(it) }
        }

        heading("Sections")
        model.sections.forEach { s ->
            text("${s.section.title}: ${s.headline}", body)
        }
        gap(6f)

        heading("Mileage timeline")
        if (model.mileage.timeline.isEmpty()) text("No mileage records.", body)
        model.mileage.timeline.forEach { r ->
            text("• ${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(r.recordedDate))} — ${"%,d".format(r.valueKm)} km (${r.source.label})", body)
        }
        text(model.mileage.explanation, small)
        gap(6f)

        heading("Important limitations")
        listOf(Limitations.GENERAL, Limitations.PAINT, Limitations.MILEAGE, Limitations.ENGINE, Limitations.ACCIDENT)
            .forEach { text("• $it", small) }
        text("This report supports your decision; it does not tell you whether to buy the vehicle. " +
            "Findings are AI observations with stated confidence and must be physically verified.", small)
        text("Generated ${date.format(Date(model.generatedAt))} by AI Car Checking.", small)

        finishPage()
        target.parentFile?.mkdirs()
        target.outputStream().use { doc.writeTo(it) }
        doc.close()
        target
    }

    private fun finding(f: Finding) {
        val where = f.panel?.label ?: f.category.label
        text("$where — ${f.statusLabel}", bold)
        text(
            "Severity: ${f.severity.label} · Confidence: ${f.confidence.label} · Certainty: ${f.certainty.label}" +
                (if (f.safetyCritical) " · SAFETY" else ""),
            small,
        )
        text("Observation: ${f.observation}", body)
        if (f.possibleCauses.isNotEmpty()) text("Possible causes: ${f.possibleCauses.joinToString()}", body)
        text("Recommendation: ${f.recommendation}", body)
        text("Verification: ${f.verificationMethod}", body)
        text(
            "Evidence: ${f.evidenceIds.joinToString()}" + (f.frameNumber?.let { " (frame $it)" } ?: "") +
                " · Source: ${f.providerName ?: f.source.name}",
            small,
        )
        gap(6f)
    }

    private fun heading(s: String) {
        gap(4f)
        text(s, h2)
        gap(2f)
    }

    private fun text(s: String, paint: TextPaint) {
        val layout = StaticLayout.Builder.obtain(s, 0, s.length, paint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(1.5f, 1f)
            .build()
        if (y + layout.height > pageHeight - margin - 14) newPage()
        val canvas = page!!.canvas
        canvas.save()
        canvas.translate(margin, y)
        layout.draw(canvas)
        canvas.restore()
        y += layout.height + 3f
    }

    private fun gap(px: Float) { y += px }

    private fun newPage() {
        finishPage()
        pageNo++
        page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNo).create())
        y = margin
    }

    private fun finishPage() {
        page?.let { p ->
            p.canvas.drawText("AI Car Checking · page $pageNo", margin, pageHeight - 20f, small)
            doc.finishPage(p)
        }
        page = null
    }
}
