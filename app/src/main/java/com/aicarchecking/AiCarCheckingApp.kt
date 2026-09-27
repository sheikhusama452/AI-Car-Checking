package com.aicarchecking

import android.app.Application
import androidx.work.Configuration
import com.aicarchecking.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AiCarCheckingApp : Application(), Configuration.Provider {

    lateinit var container: AppContainer
        private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setMinimumLoggingLevel(android.util.Log.WARN).build()

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Remove evidence files that no longer belong to any record (e.g. after a crash mid-import).
        appScope.launch {
            runCatching {
                val referenced = container.db.mediaAssetDao().listAll()
                    .flatMap { listOfNotNull(it.localPath, it.thumbnailPath) + it.framePaths.map { f -> f.substringBefore('|') } }
                    .toSet()
                container.storage.cleanOrphans(referenced)
            }
        }
    }
}
