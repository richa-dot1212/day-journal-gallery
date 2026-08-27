package com.journalgallery.shared.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.journalgallery.shared.color.ColorPipeline
import com.journalgallery.shared.media.MediaRepository
import com.journalgallery.shared.sync.SyncQueue
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AndroidBackgroundScheduler(private val context: Context) : BackgroundScheduler {
    override fun scheduleColorSweep() {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "color-sweep", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ColorSweepWorker>().build(),
        )
    }

    override fun scheduleSyncDrain() {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "sync-drain", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<SyncDrainWorker>().build(),
        )
    }
}

class ColorSweepWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params), KoinComponent {
    private val repo: MediaRepository by inject()
    private val pipeline: ColorPipeline by inject()

    override suspend fun doWork(): Result {
        if (!repo.hasPermission()) return Result.success()
        repo.refresh()
        pipeline.sweep()
        return Result.success()
    }
}

class SyncDrainWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params), KoinComponent {
    private val repo: MediaRepository by inject()
    private val queue: SyncQueue by inject()

    override suspend fun doWork(): Result {
        val months = repo.months.value.map { it.month }
        val report = queue.drain(months)
        return if (report.deviceReachable) Result.success() else Result.retry()
    }
}
