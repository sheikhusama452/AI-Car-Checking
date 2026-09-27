package com.aicarchecking.data.repository

import androidx.room.withTransaction
import com.aicarchecking.data.local.AppDatabase
import com.aicarchecking.data.local.DtcCodeEntity
import com.aicarchecking.data.local.ObdScanEntity
import com.aicarchecking.data.local.toDomain
import com.aicarchecking.data.local.toEntity
import com.aicarchecking.domain.model.AiAnalysisRecord
import com.aicarchecking.domain.model.Finding
import com.aicarchecking.domain.model.FindingCategory
import com.aicarchecking.domain.model.Inspection
import com.aicarchecking.domain.model.InspectionStatus
import com.aicarchecking.domain.model.InspectionStep
import com.aicarchecking.domain.model.InspectionType
import com.aicarchecking.domain.model.MediaAsset
import com.aicarchecking.domain.model.MileageRecord
import com.aicarchecking.domain.model.ObdScan
import com.aicarchecking.domain.model.ServiceRecord
import com.aicarchecking.domain.model.StorageTier
import com.aicarchecking.domain.model.Vehicle
import com.aicarchecking.domain.repository.AiAnalysisRepository
import com.aicarchecking.domain.repository.EvidenceRepository
import com.aicarchecking.domain.repository.FindingRepository
import com.aicarchecking.domain.repository.InspectionRepository
import com.aicarchecking.domain.repository.MileageRepository
import com.aicarchecking.domain.repository.ObdRepository
import com.aicarchecking.domain.repository.ServiceRecordRepository
import com.aicarchecking.domain.repository.VehicleRepository
import com.aicarchecking.data.local.AiAnalysisEntity
import com.aicarchecking.media.storage.MediaStorageManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.UUID

private fun MediaAsset.allPaths(): List<String> = listOfNotNull(localPath, thumbnailPath) + framePaths

class VehicleRepositoryImpl(
    private val db: AppDatabase,
    private val storage: MediaStorageManager,
) : VehicleRepository {
    private val dao = db.vehicleDao()

    override fun observeAll(): Flow<List<Vehicle>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    override fun observe(id: String): Flow<Vehicle?> = dao.observe(id).map { it?.toDomain() }
    override suspend fun get(id: String): Vehicle? = dao.get(id)?.toDomain()
    override suspend fun save(vehicle: Vehicle) = dao.upsert(vehicle.copy(updatedAt = System.currentTimeMillis()).toEntity())

    override suspend fun delete(id: String) {
        val media = db.mediaAssetDao().listForVehicle(id).map { it.toDomain() }
        val photo = dao.get(id)?.photoPath
        dao.delete(id) // cascades to inspections, media rows, findings, mileage, OBD
        media.flatMap { it.allPaths() }.forEach(storage::deleteQuietly)
        storage.deleteQuietly(photo)
    }
}

class InspectionRepositoryImpl(
    private val db: AppDatabase,
    private val storage: MediaStorageManager,
) : InspectionRepository {
    private val dao = db.inspectionDao()

    override fun observeAll(): Flow<List<Inspection>> = dao.observeAll().map { l -> l.map { it.toDomain() } }
    override fun observeForVehicle(vehicleId: String): Flow<List<Inspection>> =
        dao.observeForVehicle(vehicleId).map { l -> l.map { it.toDomain() } }
    override fun observe(id: String): Flow<Inspection?> = dao.observe(id).map { it?.toDomain() }
    override suspend fun get(id: String): Inspection? = dao.get(id)?.toDomain()

    override suspend fun create(vehicleId: String, type: InspectionType, isDemo: Boolean): Inspection {
        val mileage = db.vehicleDao().get(vehicleId)?.currentMileageKm
        val inspection = Inspection(
            id = UUID.randomUUID().toString(), vehicleId = vehicleId, type = type, mileageKm = mileage, isDemo = isDemo,
        )
        dao.upsert(inspection.toEntity())
        return inspection
    }

    override suspend fun save(inspection: Inspection) = dao.upsert(inspection.toEntity())

    override suspend fun setStepSkipped(id: String, step: InspectionStep, skipped: Boolean) {
        val current = dao.get(id)?.toDomain() ?: return
        val updated = if (skipped) current.skippedSteps + step else current.skippedSteps - step
        dao.upsert(current.copy(skippedSteps = updated).toEntity())
    }

    override suspend fun markCompleted(id: String) {
        val current = dao.get(id)?.toDomain() ?: return
        dao.upsert(current.copy(status = InspectionStatus.COMPLETED, completedAt = System.currentTimeMillis()).toEntity())
    }

    override suspend fun delete(id: String) {
        val media = db.mediaAssetDao().listForInspection(id).map { it.toDomain() }
        val reports = db.reportDao().forInspection(id)
        dao.delete(id)
        media.flatMap { it.allPaths() }.forEach(storage::deleteQuietly)
        reports.forEach { storage.deleteQuietly(it.pdfPath) }
    }
}

class EvidenceRepositoryImpl(
    private val db: AppDatabase,
    private val storage: MediaStorageManager,
) : EvidenceRepository {
    private val dao = db.mediaAssetDao()

    override fun observeForInspection(inspectionId: String): Flow<List<MediaAsset>> =
        dao.observeForInspection(inspectionId).map { l -> l.map { it.toDomain() } }

    override suspend fun listForInspection(inspectionId: String): List<MediaAsset> =
        dao.listForInspection(inspectionId).map { it.toDomain() }

    override suspend fun listForStep(inspectionId: String, step: InspectionStep): List<MediaAsset> =
        dao.listForStep(inspectionId, step.name).map { it.toDomain() }

    override suspend fun get(id: String): MediaAsset? = dao.get(id)?.toDomain()
    override suspend fun getMany(ids: List<String>): List<MediaAsset> =
        if (ids.isEmpty()) emptyList() else dao.getMany(ids).map { it.toDomain() }

    override suspend fun save(asset: MediaAsset) = dao.upsert(asset.toEntity())

    override suspend fun delete(id: String) {
        val asset = dao.get(id)?.toDomain() ?: return
        dao.delete(id)
        asset.allPaths().forEach(storage::deleteQuietly)
    }

    override suspend fun keepPermanently(id: String) {
        val asset = dao.get(id)?.toDomain() ?: return
        if (asset.storageTier == StorageTier.PERMANENT) return
        val newPath = storage.promoteToPermanent(asset.localPath)
        dao.updateStorage(id, StorageTier.PERMANENT, newPath)
    }

    override suspend fun clearTemporaryEvidence(): Int {
        val temp = dao.listAll().map { it.toDomain() }.filter { it.storageTier == StorageTier.TEMPORARY && !it.isDemo }
        temp.forEach { delete(it.id) }
        storage.clearFrameCache()
        return temp.size
    }

    override suspend fun deleteAllForInspection(inspectionId: String) {
        val media = dao.listForInspection(inspectionId).map { it.toDomain() }
        dao.deleteForInspection(inspectionId)
        media.flatMap { it.allPaths() }.forEach(storage::deleteQuietly)
    }
}

class FindingRepositoryImpl(private val db: AppDatabase) : FindingRepository {
    private val dao = db.findingDao()

    override fun observeForInspection(inspectionId: String): Flow<List<Finding>> =
        dao.observeForInspection(inspectionId).map { l -> l.map { it.toDomain() } }

    override suspend fun listForInspection(inspectionId: String): List<Finding> =
        dao.listForInspection(inspectionId).map { it.toDomain() }

    override fun observeImportantUnresolved(limit: Int): Flow<List<Finding>> =
        dao.observeImportantUnresolved(limit).map { l -> l.map { it.toDomain() } }

    override suspend fun replaceForStep(
        inspectionId: String, step: InspectionStep, category: FindingCategory, findings: List<Finding>,
    ) {
        db.withTransaction {
            dao.deleteForStepCategory(inspectionId, step.name, category.name)
            dao.insertAll(findings.map { it.toEntity() })
        }
    }

    override suspend fun add(findings: List<Finding>) = dao.insertAll(findings.map { it.toEntity() })
    override suspend fun setResolved(id: String, resolved: Boolean) = dao.setResolved(id, resolved)
}

class MileageRepositoryImpl(db: AppDatabase) : MileageRepository {
    private val dao = db.mileageDao()
    override fun observeForVehicle(vehicleId: String): Flow<List<MileageRecord>> =
        dao.observeForVehicle(vehicleId).map { l -> l.map { it.toDomain() } }
    override suspend fun listForVehicle(vehicleId: String): List<MileageRecord> =
        dao.listForVehicle(vehicleId).map { it.toDomain() }
    override suspend fun add(record: MileageRecord) = dao.insert(record.toEntity())
    override suspend fun delete(id: String) = dao.delete(id)
}

class ServiceRecordRepositoryImpl(db: AppDatabase) : ServiceRecordRepository {
    private val dao = db.serviceRecordDao()
    override fun observeForVehicle(vehicleId: String): Flow<List<ServiceRecord>> =
        dao.observeForVehicle(vehicleId).map { l -> l.map { it.toDomain() } }
    override suspend fun add(record: ServiceRecord) = dao.insert(record.toEntity())
}

@OptIn(ExperimentalCoroutinesApi::class)
class ObdRepositoryImpl(private val db: AppDatabase) : ObdRepository {
    private val dao = db.obdDao()

    private suspend fun withCodes(scans: List<ObdScanEntity>): List<ObdScan> =
        scans.map { it.toDomain(dao.codesForScan(it.id)) }

    override fun observeForVehicle(vehicleId: String): Flow<List<ObdScan>> =
        dao.observeScans(vehicleId).flatMapLatest { flowOf(withCodes(it)) }

    override fun observeAll(): Flow<List<ObdScan>> =
        dao.observeAllScans().flatMapLatest { flowOf(withCodes(it)) }

    override suspend fun forInspection(inspectionId: String): List<ObdScan> = withCodes(dao.scansForInspection(inspectionId))

    override suspend fun save(scan: ObdScan) {
        db.withTransaction {
            dao.insertScan(
                ObdScanEntity(
                    scan.id, scan.vehicleId, scan.inspectionId, scan.scannedAt, scan.source,
                    scan.batteryVoltage, scan.liveData, scan.notes,
                )
            )
            dao.insertCodes(scan.codes.map { DtcCodeEntity(UUID.randomUUID().toString(), scan.id, it.code, it.description, it.status) })
        }
    }

    override suspend fun delete(id: String) = dao.deleteScan(id)
}

class AiAnalysisRepositoryImpl(db: AppDatabase) : AiAnalysisRepository {
    private val dao = db.aiAnalysisDao()
    override suspend fun record(record: AiAnalysisRecord) = dao.insert(
        AiAnalysisEntity(
            record.id, record.inspectionId, record.step?.name, record.task, record.providerName,
            record.requestedAt, record.completedAt, record.succeeded, record.userMessage,
            record.validationWarnings, record.evidenceIds,
        )
    )
    override fun observeForInspection(inspectionId: String): Flow<List<AiAnalysisRecord>> =
        dao.observeForInspection(inspectionId).map { l -> l.map { it.toDomain() } }
    override suspend fun clearHistory() = dao.clearAll()
}
