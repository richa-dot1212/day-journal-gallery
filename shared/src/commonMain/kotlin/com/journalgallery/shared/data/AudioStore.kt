package com.journalgallery.shared.data

import com.journalgallery.shared.db.JournalDatabase
import com.journalgallery.shared.domain.AudioEntry
import com.journalgallery.shared.domain.AudioSyncState
import com.journalgallery.shared.domain.DayKey
import com.journalgallery.shared.domain.MonthKey
import kotlinx.datetime.Clock

class AudioStore(db: JournalDatabase) {
    private val q = db.journalQueries

    fun entry(day: DayKey): AudioEntry? = q.selectAudioEntry(day.iso()).executeAsOneOrNull()?.toDomain()

    fun entriesForMonth(month: MonthKey): Map<Int, AudioEntry> =
        q.selectAudioEntriesForMonth(month.year.toLong(), month.month.toLong()).executeAsList()
            .associate { it.day.toInt() to it.toDomain() }

    fun entriesInState(state: AudioSyncState): List<AudioEntry> =
        q.selectAudioEntriesByState(state.name).executeAsList().map { it.toDomain() }

    fun put(entry: AudioEntry) {
        q.upsertAudioEntry(
            entry.day.iso(), entry.day.year.toLong(), entry.day.month.toLong(), entry.day.day.toLong(),
            entry.localPath, entry.durationMillis, entry.sizeBytes, entry.crc32,
            entry.state.name, entry.updatedAtEpochMillis,
        )
    }

    fun setState(day: DayKey, state: AudioSyncState) {
        q.updateAudioSyncState(state.name, Clock.System.now().toEpochMilliseconds(), day.iso())
    }

    fun delete(day: DayKey) = q.deleteAudioEntry(day.iso())

    private fun com.journalgallery.shared.db.Audio_entry.toDomain() = AudioEntry(
        day = DayKey(year.toInt(), month.toInt(), day.toInt()),
        localPath = local_path,
        durationMillis = duration_ms,
        sizeBytes = size_bytes,
        crc32 = crc32,
        state = runCatching { AudioSyncState.valueOf(sync_state) }.getOrDefault(AudioSyncState.PENDING),
        updatedAtEpochMillis = updated_at,
    )
}
