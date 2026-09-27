package com.aicarchecking.domain.repository

import com.aicarchecking.domain.model.AiAnalysisRecord
import com.aicarchecking.domain.model.Finding
import com.aicarchecking.domain.model.FindingCategory
import com.aicarchecking.domain.model.Inspection
import com.aicarchecking.domain.model.InspectionStep
import com.aicarchecking.domain.model.InspectionType
import com.aicarchecking.domain.model.MediaAsset
import com.aicarchecking.domain.model.MileageRecord
import com.aicarchecking.domain.model.ObdScan
import com.aicarchecking.domain.model.ServiceRecord
import com.aicarchecking.domain.model.Vehicle
import kotlinx.coroutines.flow.Flow

interface VehicleRepository {
    fun observeAll(): Flow<List<Vehicle>>
    fun observe(id: String): Flow<Vehicle?>
    suspend fun get(id: String): Vehicle?
    suspend fun save(vehicle: Vehicle)
    /** Deletes the vehicle, its inspections, findings and all evidence files. */
    suspend fun delete(id: String)
}

interface InspectionRepository {
    fun observeAll(): Flow<List<Inspection>>
    fun observeForVehicle(vehicleId: String): Flow<List<Inspection>>
    fun observe(id: String): Flow<Inspection?>
    suspend fun get(id: String): Inspection?
    suspend fun create(vehicleId: String, type: InspectionType, isDemo: Boolean = false): Inspection
    suspend fun save(inspection: Inspection)
    suspend fun setStepSkipped(id: String, step: InspectionStep, skipped: Boolean)
    suspend fun markCompleted(id: String)
    /** Deletes the inspection, its findings and evidence files. */
    suspend fun delete(id: String)
}

interface EvidenceRepository {
    fun observeForInspection(inspectionId: String): Flow<List<MediaAsset>>
    suspend fun listForInspection(inspectionId: String): List<MediaAsset>
    suspend fun listForStep(inspectionId: String, step: InspectionStep): List<MediaAsset>
    suspend fun get(id: String): MediaAsset?
    suspend fun getMany(ids: List<String>): List<MediaAsset>
    suspend fun save(asset: MediaAsset)
    suspend fun delete(id: String)
    /** Moves evidence from temporary cache into permanent app storage. */
    suspend fun keepPermanently(id: String)
    /** Deletes all temporary evidence (files and records). Returns count removed. */
    suspend fun clearTemporaryEvidence(): Int
    suspend fun deleteAllForInspection(inspectionId: String)
}

interface FindingRepository {
    fun observeForInspection(inspectionId: String): Flow<List<Finding>>
    suspend fun listForInspection(inspectionId: String): List<Finding>
    fun observeImportantUnresolved(limit: Int = 10): Flow<List<Finding>>
    suspend fun replaceForStep(inspectionId: String, step: InspectionStep, category: FindingCategory, findings: List<Finding>)
    suspend fun add(findings: List<Finding>)
    suspend fun setResolved(id: String, resolved: Boolean)
}

interface MileageRepository {
    fun observeForVehicle(vehicleId: String): Flow<List<MileageRecord>>
    suspend fun listForVehicle(vehicleId: String): List<MileageRecord>
    suspend fun add(record: MileageRecord)
    suspend fun delete(id: String)
}

interface ServiceRecordRepository {
    fun observeForVehicle(vehicleId: String): Flow<List<ServiceRecord>>
    suspend fun add(record: ServiceRecord)
}

interface ObdRepository {
    fun observeForVehicle(vehicleId: String): Flow<List<ObdScan>>
    fun observeAll(): Flow<List<ObdScan>>
    suspend fun forInspection(inspectionId: String): List<ObdScan>
    suspend fun save(scan: ObdScan)
    suspend fun delete(id: String)
}

interface AiAnalysisRepository {
    suspend fun record(record: AiAnalysisRecord)
    fun observeForInspection(inspectionId: String): Flow<List<AiAnalysisRecord>>
    suspend fun clearHistory()
}
