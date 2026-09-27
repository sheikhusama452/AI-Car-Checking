package com.aicarchecking.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        VehicleEntity::class,
        InspectionEntity::class,
        MediaAssetEntity::class,
        FindingEntity::class,
        AiAnalysisEntity::class,
        MileageRecordEntity::class,
        ServiceRecordEntity::class,
        ObdScanEntity::class,
        DtcCodeEntity::class,
        InspectionReportEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao
    abstract fun inspectionDao(): InspectionDao
    abstract fun mediaAssetDao(): MediaAssetDao
    abstract fun findingDao(): FindingDao
    abstract fun aiAnalysisDao(): AiAnalysisDao
    abstract fun mileageDao(): MileageDao
    abstract fun serviceRecordDao(): ServiceRecordDao
    abstract fun obdDao(): ObdDao
    abstract fun reportDao(): ReportDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "ai_car_checking.db")
                // Pre-release: no migrations yet. Replace with real Migrations before public release.
                .fallbackToDestructiveMigration()
                .build()
    }
}
