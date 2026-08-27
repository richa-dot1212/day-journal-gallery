package com.journalgallery.shared.work

/**
 * Schedules the incremental color-extraction sweep off the main thread.
 * Android: WorkManager one-time work. iOS: BGProcessingTaskRequest.
 */
interface BackgroundScheduler {
    /** Enqueue a color sweep. Coalesces with any already-pending sweep. */
    fun scheduleColorSweep()

    /** Enqueue a drain of the outbound sync queue (color + audio). */
    fun scheduleSyncDrain()
}
