package com.aicarchecking.data.demo

import android.graphics.Color
import com.aicarchecking.domain.model.DistanceUnit
import com.aicarchecking.domain.model.DtcCode
import com.aicarchecking.domain.model.EvidenceQuality
import com.aicarchecking.domain.model.EvidenceType
import com.aicarchecking.domain.model.InspectionStep
import com.aicarchecking.domain.model.InspectionType
import com.aicarchecking.domain.model.MediaAsset
import com.aicarchecking.domain.model.MileageRecord
import com.aicarchecking.domain.model.MileageSource
import com.aicarchecking.domain.model.ObdScan
import com.aicarchecking.domain.model.ObdSource
import com.aicarchecking.domain.model.ProcessingStatus
import com.aicarchecking.domain.model.ServiceRecord
import com.aicarchecking.domain.model.StorageTier
import com.aicarchecking.domain.model.Vehicle
import com.aicarchecking.data.local.AppDatabase
import com.aicarchecking.data.obd.DtcDictionary
import com.aicarchecking.data.settings.SettingsRepository
import com.aicarchecking.domain.repository.EvidenceRepository
import com.aicarchecking.domain.repository.InspectionRepository
import com.aicarchecking.domain.repository.MileageRepository
import com.aicarchecking.domain.repository.ObdRepository
import com.aicarchecking.domain.repository.ServiceRecordRepository
import com.aicarchecking.domain.repository.VehicleRepository
import com.aicarchecking.domain.usecase.AnalyzeStepUseCase
import com.aicarchecking.media.storage.MediaStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import java.util.UUID

/**
 * Creates the complete offline Demo Mode dataset: vehicle, painted-panel photos, dashboard,
 * engine-bay and exhaust video frames, engine sound, service documents, OBD codes, and then runs the
 * normal analysis pipeline with the scripted demo provider so a full report is available offline.
 */
class DemoDataSeeder(
    private val db: AppDatabase,
    private val storage: MediaStorageManager,
    private val vehicles: VehicleRepository,
    private val inspections: InspectionRepository,
    private val evidence: EvidenceRepository,
    private val mileage: MileageRepository,
    private val services: ServiceRecordRepository,
    private val obd: ObdRepository,
    private val analyze: AnalyzeStepUseCase,
    private val settings: SettingsRepository,
) {

    /** Returns the demo inspection id. Removes any previous demo data first. */
    suspend fun seed(): String = withContext(Dispatchers.IO) {
        clear()
        val gen = DemoAssetGenerator(storage.demoDir)
        val now = System.currentTimeMillis()

        val vehicle = Vehicle(
            id = "demo_vehicle_" + UUID.randomUUID().toString().take(8),
            make = "Toyota", model = "Corolla", variant = "Altis 1.6 (Demo)", year = 2021,
            registration = "DEMO-123", vin = "JTDBR32E720123456", engineType = "Inline-4",
            engineCapacity = "1598 cc", fuelType = "Petrol", transmission = "Automatic",
            currentMileageKm = 86_200, notes = "Demo vehicle with sample data. Not a real car.", isDemo = true,
        )
        vehicles.save(vehicle)
        val inspection = inspections.create(vehicle.id, InspectionType.PRE_PURCHASE, isDemo = true)

        val silver = Color.rgb(150, 155, 162)
        val silverLighter = Color.rgb(166, 170, 176)

        fun asset(step: InspectionStep?, file: File, type: EvidenceType, mime: String, label: String, frames: List<File> = emptyList(), ocr: String? = null, durationMs: Long? = null) =
            MediaAsset(
                id = "demo_media_" + UUID.randomUUID().toString().replace("-", "").take(10),
                vehicleId = vehicle.id, inspectionId = inspection.id, step = step, evidenceType = type, mimeType = mime,
                localPath = file.absolutePath, thumbnailPath = (frames.firstOrNull() ?: file).takeIf { mime.startsWith("image") || frames.isNotEmpty() }?.absolutePath,
                sizeBytes = file.length(), durationMs = durationMs, width = 1280, height = 960,
                storageTier = StorageTier.PERMANENT, processingStatus = ProcessingStatus.COMPLETED, processingProgress = 100,
                quality = EvidenceQuality.GOOD, framePaths = frames.mapIndexed { i, f -> "${f.absolutePath}|${i * 2000L}" },
                ocrText = ocr, label = label, isDemo = true,
            )

        val items = listOf(
            asset(InspectionStep.FRONT, gen.panels("front.jpg", "Front — bumper & bonnet", listOf("Front bumper" to silver, "Bonnet" to silver)), EvidenceType.PHOTO, "image/jpeg", "Front"),
            asset(InspectionStep.FRONT_LEFT_FENDER, gen.panels("fl_fender.jpg", "Front left fender vs door", listOf("Front left fender" to silverLighter, "Front left door" to silver)), EvidenceType.PHOTO, "image/jpeg", "Front left fender"),
            asset(InspectionStep.LEFT_SIDE, gen.panels("left.jpg", "Left side", listOf("FL door" to silver, "RL door" to silver, "RL fender" to silver)), EvidenceType.PHOTO, "image/jpeg", "Left side"),
            asset(InspectionStep.RIGHT_SIDE, gen.panels("right.jpg", "Right side", listOf("FR door" to silver, "RR door" to silverLighter, "RR fender" to silver)), EvidenceType.PHOTO, "image/jpeg", "Right side"),
            asset(InspectionStep.BONNET, gen.panels("bonnet.jpg", "Bonnet", listOf("Bonnet" to silver)), EvidenceType.PHOTO, "image/jpeg", "Bonnet"),
            asset(InspectionStep.REAR, gen.panels("rear.jpg", "Rear", listOf("Rear bumper" to silver, "Trunk" to silver)), EvidenceType.PHOTO, "image/jpeg", "Rear"),
            asset(InspectionStep.DASHBOARD, gen.dashboard("dashboard.jpg", "ODO 86,200 km", true), EvidenceType.PHOTO, "image/jpeg", "Dashboard", ocr = "ODO 86,200 km\nkm/h\nx1000 rpm\nCHECK"),
            asset(InspectionStep.WARNING_LIGHTS, gen.dashboard("warning.jpg", "ODO 86,200 km", true), EvidenceType.PHOTO, "image/jpeg", "Warning lights (engine running)"),
            asset(InspectionStep.TYRES, gen.tyre("tyre_rl.jpg", "Rear left tyre — tread"), EvidenceType.PHOTO, "image/jpeg", "Rear left tyre"),
            asset(InspectionStep.TYRES, gen.tyre("tyre_rl2.jpg", "Rear left tyre — second angle"), EvidenceType.PHOTO, "image/jpeg", "Rear left tyre (angle 2)"),
            run {
                val frames = (0 until 3).map { gen.engineBayFrame("engine_f$it.jpg", "Engine video — frame ${it + 1} (sample frames)", it) }
                asset(InspectionStep.ENGINE_BAY, frames.first(), EvidenceType.VIDEO, "image/jpeg", "Engine video (demo frames)", frames = frames, durationMs = 8_000)
            },
            run {
                val frames = listOf(180, 120, 60).mapIndexed { i, a -> gen.exhaustFrame("exhaust_f$i.jpg", a, "Exhaust video — frame ${i + 1} (${i * 2}s)") }
                asset(InspectionStep.EXHAUST_SMOKE, frames.first(), EvidenceType.VIDEO, "image/jpeg", "Exhaust video (demo frames)", frames = frames, durationMs = 12_000)
            },
            asset(InspectionStep.ENGINE_SOUND, gen.engineSound("engine_idle.wav"), EvidenceType.AUDIO, "audio/wav", "Engine idle sound (synthetic demo)", durationMs = 12_000),
        )
        items.forEach { evidence.save(it) }

        // Service documents (for the mileage timeline)
        val invoiceDate = date(2023, 3, 14)
        val invoice = gen.serviceInvoice(
            "invoice_2023.jpg",
            listOf("Demo Motors Service Centre", "Invoice #D-2291", "Date: 14/03/2023", "Vehicle: Toyota Corolla DEMO-123",
                "Mileage: 119,000 km", "Engine oil & filter change", "Air filter replaced", "Total: PKR (demo)"),
        )
        val invoiceAsset = asset(null, invoice, EvidenceType.SERVICE_DOCUMENT, "image/jpeg", "Service invoice 14/03/2023",
            ocr = "Demo Motors Service Centre\nDate: 14/03/2023\nMileage: 119,000 km\nEngine oil & filter change")
        evidence.save(invoiceAsset)

        services.add(ServiceRecord(UUID.randomUUID().toString(), vehicle.id, date(2022, 1, 20), 72_500, "Periodic service", "Demo Motors", "Demo record"))
        services.add(ServiceRecord(UUID.randomUUID().toString(), vehicle.id, invoiceDate, 119_000, "Oil & filter change", "Demo Motors", "Demo record", invoiceAsset.id))

        mileage.add(MileageRecord(UUID.randomUUID().toString(), vehicle.id, null, MileageSource.SERVICE_DOCUMENT, 72_500, 72_500, DistanceUnit.KM, date(2022, 1, 20), null, "Demo service record"))
        mileage.add(MileageRecord(UUID.randomUUID().toString(), vehicle.id, null, MileageSource.SERVICE_DOCUMENT, 119_000, 119_000, DistanceUnit.KM, invoiceDate, invoiceAsset.id, "Demo invoice (OCR)"))

        val p0420 = DtcDictionary.lookup("P0420")
        obd.save(
            ObdScan(
                id = UUID.randomUUID().toString(), vehicleId = vehicle.id, inspectionId = inspection.id, scannedAt = now,
                source = ObdSource.DEMO,
                codes = listOf(DtcCode("P0420", p0420?.meaning ?: "Catalyst system efficiency below threshold (Bank 1)")),
                batteryVoltage = 14.1,
                liveData = mapOf("Engine RPM" to "760 rpm", "Coolant temperature" to "88 °C", "Short-term fuel trim (B1)" to "+2.3 %", "Long-term fuel trim (B1)" to "+4.7 %"),
                notes = "Demo OBD data — no adapter was used.",
            )
        )

        // Run the same analysis pipeline the real app uses (scripted provider, validator, safety engine).
        val stepsWithTasks = items.mapNotNull { it.step }.distinct()
        for (step in stepsWithTasks) {
            for (task in step.tasks) {
                analyze(inspection.id, step, task, userConfirmedUpload = true)
            }
        }
        settings.setDemoSeeded(true)
        inspection.id
    }

    suspend fun clear() {
        withContext(Dispatchers.IO) {
            db.vehicleDao().demoIds().forEach { vehicles.delete(it) }
            storage.demoDir.listFiles()?.forEach { it.delete() }
            settings.setDemoSeeded(false)
        }
    }

    private fun date(y: Int, m: Int, d: Int): Long = Calendar.getInstance().apply { clear(); set(y, m - 1, d, 10, 0) }.timeInMillis
}
