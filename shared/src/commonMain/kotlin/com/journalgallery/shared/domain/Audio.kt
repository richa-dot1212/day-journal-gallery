package com.journalgallery.shared.domain

/** Where a day's recorded journal audio is in its journey to the ESP32 SD card. */
enum class AudioSyncState {
    /** No recording exists for this day. */
    NONE,

    /** Recorded locally, not yet queued for transfer. */
    PENDING,

    /** Upload in progress. */
    SENDING,

    /** Uploaded; awaiting the device's checksum ack. */
    SENT,

    /** Device confirmed the file is written and verified on the SD card. */
    CONFIRMED,

    /** Last transfer attempt failed; eligible for retry. */
    FAILED,
}

/**
 * A day's voice-journal entry. One per day; re-recording overwrites [localPath] and resets
 * [state] to [AudioSyncState.PENDING].
 *
 * @param localPath app-internal file path (mono, low-bitrate — see build plan Open Item 1).
 * @param durationMillis capped at [MAX_DURATION_MILLIS] at record time.
 */
data class AudioEntry(
    val day: DayKey,
    val localPath: String,
    val durationMillis: Long,
    val sizeBytes: Long,
    val crc32: Long,
    val state: AudioSyncState,
    val updatedAtEpochMillis: Long,
) {
    companion object {
        const val MAX_DURATION_MILLIS: Long = 90_000
    }
}
