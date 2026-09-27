package com.aicarchecking.di

import android.content.Context
import androidx.work.WorkManager
import com.aicarchecking.ai.AIProviderRegistry
import com.aicarchecking.ai.DemoAIProvider
import com.aicarchecking.ai.GeminiProvider
import com.aicarchecking.data.demo.DemoDataSeeder
import com.aicarchecking.data.local.AppDatabase
import com.aicarchecking.data.repository.AiAnalysisRepositoryImpl
import com.aicarchecking.data.repository.EvidenceRepositoryImpl
import com.aicarchecking.data.repository.FindingRepositoryImpl
import com.aicarchecking.data.repository.InspectionRepositoryImpl
import com.aicarchecking.data.repository.MileageRepositoryImpl
import com.aicarchecking.data.repository.ObdRepositoryImpl
import com.aicarchecking.data.repository.ServiceRecordRepositoryImpl
import com.aicarchecking.data.repository.VehicleRepositoryImpl
import com.aicarchecking.data.settings.SecureKeyStore
import com.aicarchecking.data.settings.SettingsRepository
import com.aicarchecking.domain.repository.AiAnalysisRepository
import com.aicarchecking.domain.repository.EvidenceRepository
import com.aicarchecking.domain.repository.FindingRepository
import com.aicarchecking.domain.repository.InspectionRepository
import com.aicarchecking.domain.repository.MileageRepository
import com.aicarchecking.domain.repository.ObdRepository
import com.aicarchecking.domain.repository.ServiceRecordRepository
import com.aicarchecking.domain.repository.VehicleRepository
import com.aicarchecking.domain.usecase.AddEvidenceUseCase
import com.aicarchecking.domain.usecase.AnalyzeStepUseCase
import com.aicarchecking.media.ocr.MlKitOcrEngine
import com.aicarchecking.media.ocr.OcrEngine
import com.aicarchecking.media.storage.MediaStorageManager
import com.aicarchecking.media.work.MediaProcessingScheduler
import com.aicarchecking.report.PdfReportGenerator

/**
 * Manual dependency injection container. Kept framework-free so it is easy to test and to replace
 * with Hilt/Koin later without touching feature code.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val db: AppDatabase by lazy { AppDatabase.build(appContext) }
    val storage: MediaStorageManager by lazy { MediaStorageManager(appContext) }
    val settings: SettingsRepository by lazy { SettingsRepository(appContext, SecureKeyStore()) }
    val workManager: WorkManager by lazy { WorkManager.getInstance(appContext) }
    val scheduler: MediaProcessingScheduler by lazy { MediaProcessingScheduler(workManager) }
    val ocr: OcrEngine by lazy { MlKitOcrEngine() }

    val vehicleRepository: VehicleRepository by lazy { VehicleRepositoryImpl(db, storage) }
    val inspectionRepository: InspectionRepository by lazy { InspectionRepositoryImpl(db, storage) }
    val evidenceRepository: EvidenceRepository by lazy { EvidenceRepositoryImpl(db, storage) }
    val findingRepository: FindingRepository by lazy { FindingRepositoryImpl(db) }
    val mileageRepository: MileageRepository by lazy { MileageRepositoryImpl(db) }
    val serviceRecordRepository: ServiceRecordRepository by lazy { ServiceRecordRepositoryImpl(db) }
    val obdRepository: ObdRepository by lazy { ObdRepositoryImpl(db) }
    val aiAnalysisRepository: AiAnalysisRepository by lazy { AiAnalysisRepositoryImpl(db) }

    val demoProvider by lazy { DemoAIProvider() }
    val geminiProvider by lazy { GeminiProvider(settings) }
    val providerRegistry by lazy { AIProviderRegistry(settings, geminiProvider, demoProvider) }

    val addEvidence by lazy { AddEvidenceUseCase(storage, evidenceRepository, scheduler) }
    val analyzeStep by lazy {
        AnalyzeStepUseCase(
            inspectionRepository, vehicleRepository, evidenceRepository, findingRepository,
            mileageRepository, aiAnalysisRepository, providerRegistry, settings,
        )
    }
    val demoSeeder by lazy {
        DemoDataSeeder(
            db, storage, vehicleRepository, inspectionRepository, evidenceRepository, mileageRepository,
            serviceRecordRepository, obdRepository, analyzeStep, settings,
        )
    }

    fun newPdfGenerator() = PdfReportGenerator()
}
