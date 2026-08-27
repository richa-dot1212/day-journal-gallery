package com.journalgallery.shared.sync

import com.journalgallery.shared.audio.FileBytes
import com.journalgallery.shared.data.AudioStore
import com.journalgallery.shared.data.ColorCache
import com.journalgallery.shared.data.PairingStore
import com.journalgallery.shared.domain.AudioSyncState
import com.journalgallery.shared.domain.ColorSyncState
import com.journalgallery.shared.domain.DayKey
import com.journalgallery.shared.domain.MonthKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns "eventually get this to the device" semantics. The DB rows ARE the queue:
 * a `day_color` with `color_sync != SYNCED` or an `audio_entry` not `CONFIRMED` is pending work.
 *
 * [drain] is idempotent and safe to call on every reconnect / from background work.
 * Never throws for device-unreachable — it just leaves rows pending for the next drain.
 */
class SyncQueue(
    private val client: DeviceSyncClient,
    private val colorCache: ColorCache,
    private val audioStore: AudioStore,
    private val pairing: PairingStore,
) {
    private val lock = Mutex()

    /** User/telemetry-facing outcome of one drain pass. */
    data class DrainReport(
        val colorsPushed: Int,
        val audioPushed: Int,
        val failures: Int,
        val deviceReachable: Boolean,
    )

    suspend fun drain(monthsInScope: List<MonthKey>): DrainReport = lock.withLock {
        val device = pairing.current() ?: return DrainReport(0, 0, 0, deviceReachable = false)

        val status = runCatching { client.status(device) }.getOrNull()
            ?: return DrainReport(0, 0, 0, deviceReachable = false)

        var colors = 0
        var audio = 0
        var failures = 0

        for (month in monthsInScope) {
            val dayColors = colorCache.dayColorsForMonth(month)
            if (dayColors.isEmpty()) continue

            val states = colorCache.dayColorSyncStates(month)
            val unsynced = dayColors.filter { states[it.key] != ColorSyncState.SYNCED }

            // Prefer one batch when the device has nothing for this month or many days are stale.
            if (!status.isMonthCached(month.month) || unsynced.size > 5) {
                val ack = runCatching {
                    client.sendMonthBatch(device, month.month, dayColors.toList())
                }.getOrNull()
                if (ack?.ok == true) {
                    dayColors.keys.forEach { d ->
                        colorCache.setColorSyncState(DayKey(month.year, month.month, d), ColorSyncState.SYNCED)
                    }
                    colors += dayColors.size
                } else {
                    failures++
                }
            } else {
                for ((day, dc) in unsynced) {
                    val key = DayKey(month.year, month.month, day)
                    colorCache.setColorSyncState(key, ColorSyncState.SENDING)
                    val ack = runCatching { client.sendDayColor(device, month.month, day, dc) }.getOrNull()
                    if (ack?.ok == true) {
                        colorCache.setColorSyncState(key, ColorSyncState.SYNCED); colors++
                    } else {
                        colorCache.setColorSyncState(key, ColorSyncState.FAILED); failures++
                    }
                }
            }

            // Audio for the same months.
            for (entry in audioStore.entriesForMonth(month).values) {
                if (entry.state == AudioSyncState.CONFIRMED) continue
                if (!FileBytes.exists(entry.localPath)) continue
                audioStore.setState(entry.day, AudioSyncState.SENDING)
                val bytes = FileBytes.read(entry.localPath)
                val ack = runCatching {
                    client.uploadAudio(
                        device, entry.day.month, entry.day.day,
                        "day_${pad2(entry.day.day)}.mp3", bytes, entry.crc32,
                    )
                }.getOrNull()
                when {
                    ack?.ok == true && ack.crc32 == entry.crc32 -> {
                        audioStore.setState(entry.day, AudioSyncState.CONFIRMED); audio++
                    }
                    ack?.ok == true -> {
                        audioStore.setState(entry.day, AudioSyncState.SENT); failures++ // crc mismatch -> retry
                    }
                    else -> {
                        audioStore.setState(entry.day, AudioSyncState.FAILED); failures++
                    }
                }
            }
        }

        DrainReport(colors, audio, failures, deviceReachable = true)
    }

    /** Exponential backoff helper for a caller-driven retry loop. */
    suspend fun drainWithRetry(monthsInScope: List<MonthKey>, maxAttempts: Int = 4): DrainReport {
        var attempt = 0
        var last = DrainReport(0, 0, 0, deviceReachable = false)
        while (attempt < maxAttempts) {
            last = drain(monthsInScope)
            if (last.deviceReachable && last.failures == 0) return last
            attempt++
            delay(minOf(30_000L, 1_000L shl attempt))
        }
        return last
    }

    private fun pad2(v: Int) = if (v < 10) "0$v" else "$v"
}
