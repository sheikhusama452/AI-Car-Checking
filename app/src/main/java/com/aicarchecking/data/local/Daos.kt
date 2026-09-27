package com.aicarchecking.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.aicarchecking.domain.model.AnalysisStatus
import com.aicarchecking.domain.model.ProcessingStatus
import com.aicarchecking.domain.model.StorageTier
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {
    @Query("SELECT * FROM vehicles ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles WHERE id = :id")
    fun observe(id: String): Flow<VehicleEntity?>

    @Query("SELECT * FROM vehicles WHERE id = :id")
    suspend fun get(id: String): VehicleEntity?

    @Upsert
    suspend fun upsert(vehicle: VehicleEntity)

    @Query("DELETE FROM vehicles WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT id FROM vehicles WHERE isDemo = 1")
    suspend fun demoIds(): List<String>
}

@Dao
interface InspectionDao {
    @Query("SELECT * FROM inspections ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<InspectionEntity>>

    @Query("SELECT * FROM inspections WHERE vehicleId = :vehicleId ORDER BY startedAt DESC")
    fun observeForVehicle(vehicleId: String): Flow<List<InspectionEntity>>

    @Query("SELECT * FROM inspections WHERE id = :id")
    fun observe(id: String): Flow<InspectionEntity?>

    @Query("SELECT * FROM inspections WHERE id = :id")
    suspend fun get(id: String): InspectionEntity?

    @Upsert
    suspend fun upsert(inspection: InspectionEntity)

    @Query("DELETE FROM inspections WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface MediaAssetDao {
    @Query("SELECT * FROM media_assets WHERE inspectionId = :inspectionId ORDER BY createdAt ASC")
    fun observeForInspection(inspectionId: String): Flow<List<MediaAssetEntity>>

    @Query("SELECT * FROM media_assets WHERE inspectionId = :inspectionId ORDER BY createdAt ASC")
    suspend fun listForInspection(inspectionId: String): List<MediaAssetEntity>

    @Query("SELECT * FROM media_assets WHERE inspectionId = :inspectionId AND stepId = :stepId ORDER BY createdAt ASC")
    suspend fun listForStep(inspectionId: String, stepId: String): List<MediaAssetEntity>

    @Query("SELECT * FROM media_assets WHERE vehicleId = :vehicleId")
    suspend fun listForVehicle(vehicleId: String): List<MediaAssetEntity>

    @Query("SELECT * FROM media_assets")
    suspend fun listAll(): List<MediaAssetEntity>

    @Query("SELECT * FROM media_assets WHERE id = :id")
    suspend fun get(id: String): MediaAssetEntity?

    @Query("SELECT * FROM media_assets WHERE id IN (:ids)")
    suspend fun getMany(ids: List<String>): List<MediaAssetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(asset: MediaAssetEntity)

    @Upsert
    suspend fun upsert(asset: MediaAssetEntity)

    @Query("UPDATE media_assets SET processingStatus = :status, processingProgress = :progress WHERE id = :id")
    suspend fun updateProcessing(id: String, status: ProcessingStatus, progress: Int)

    @Query("UPDATE media_assets SET analysisStatus = :status WHERE id IN (:ids)")
    suspend fun updateAnalysisStatus(ids: List<String>, status: AnalysisStatus)

    @Query("UPDATE media_assets SET ocrText = :text WHERE id = :id")
    suspend fun updateOcr(id: String, text: String?)

    @Query("UPDATE media_assets SET storageTier = :tier, localPath = :path WHERE id = :id")
    suspend fun updateStorage(id: String, tier: StorageTier, path: String)

    @Query("DELETE FROM media_assets WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM media_assets WHERE inspectionId = :inspectionId")
    suspend fun deleteForInspection(inspectionId: String)
}

@Dao
interface FindingDao {
    @Query("SELECT * FROM findings WHERE inspectionId = :inspectionId ORDER BY timestamp ASC")
    fun observeForInspection(inspectionId: String): Flow<List<FindingEntity>>

    @Query("SELECT * FROM findings WHERE inspectionId = :inspectionId ORDER BY timestamp ASC")
    suspend fun listForInspection(inspectionId: String): List<FindingEntity>

    @Query(
        "SELECT * FROM findings WHERE resolved = 0 AND severity IN ('HIGH','CRITICAL') " +
            "ORDER BY timestamp DESC LIMIT :limit"
    )
    fun observeImportantUnresolved(limit: Int): Flow<List<FindingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(findings: List<FindingEntity>)

    @Query("DELETE FROM findings WHERE inspectionId = :inspectionId AND stepId = :stepId AND category = :category")
    suspend fun deleteForStepCategory(inspectionId: String, stepId: String, category: String)

    @Query("UPDATE findings SET resolved = :resolved WHERE id = :id")
    suspend fun setResolved(id: String, resolved: Boolean)

    @Query("DELETE FROM findings WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface AiAnalysisDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: AiAnalysisEntity)

    @Query("SELECT * FROM ai_analyses WHERE inspectionId = :inspectionId ORDER BY requestedAt DESC")
    fun observeForInspection(inspectionId: String): Flow<List<AiAnalysisEntity>>

    @Query("DELETE FROM ai_analyses")
    suspend fun clearAll()
}

@Dao
interface MileageDao {
    @Query("SELECT * FROM mileage_records WHERE vehicleId = :vehicleId ORDER BY recordedDate ASC")
    fun observeForVehicle(vehicleId: String): Flow<List<MileageRecordEntity>>

    @Query("SELECT * FROM mileage_records WHERE vehicleId = :vehicleId ORDER BY recordedDate ASC")
    suspend fun listForVehicle(vehicleId: String): List<MileageRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: MileageRecordEntity)

    @Query("DELETE FROM mileage_records WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ServiceRecordDao {
    @Query("SELECT * FROM service_records WHERE vehicleId = :vehicleId ORDER BY date ASC")
    fun observeForVehicle(vehicleId: String): Flow<List<ServiceRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: ServiceRecordEntity)
}

@Dao
interface ObdDao {
    @Query("SELECT * FROM obd_scans WHERE vehicleId = :vehicleId ORDER BY scannedAt DESC")
    fun observeScans(vehicleId: String): Flow<List<ObdScanEntity>>

    @Query("SELECT * FROM obd_scans WHERE inspectionId = :inspectionId ORDER BY scannedAt DESC")
    suspend fun scansForInspection(inspectionId: String): List<ObdScanEntity>

    @Query("SELECT * FROM obd_scans ORDER BY scannedAt DESC")
    fun observeAllScans(): Flow<List<ObdScanEntity>>

    @Query("SELECT * FROM dtc_codes WHERE scanId = :scanId")
    suspend fun codesForScan(scanId: String): List<DtcCodeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScan(scan: ObdScanEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCodes(codes: List<DtcCodeEntity>)

    @Query("DELETE FROM obd_scans WHERE id = :id")
    suspend fun deleteScan(id: String)
}

@Dao
interface ReportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(report: InspectionReportEntity)

    @Query("SELECT * FROM inspection_reports WHERE inspectionId = :inspectionId ORDER BY generatedAt DESC")
    suspend fun forInspection(inspectionId: String): List<InspectionReportEntity>

    @Query("SELECT * FROM inspection_reports")
    suspend fun all(): List<InspectionReportEntity>

    @Query("DELETE FROM inspection_reports")
    suspend fun deleteAll()
}
