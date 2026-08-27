package com.journalgallery.shared.data

import com.journalgallery.shared.db.JournalDatabase
import com.journalgallery.shared.domain.ColorSyncState
import com.journalgallery.shared.domain.DayColors
import com.journalgallery.shared.domain.DayKey
import com.journalgallery.shared.domain.MonthKey
import com.journalgallery.shared.domain.WeightedColor
import com.journalgallery.shared.util.Json
import kotlinx.datetime.Clock

/** Read/write access to the per-item and per-day color caches. */
class ColorCache(db: JournalDatabase) {
    private val q = db.journalQueries

    fun itemColor(mediaId: String): CachedItemColor? =
        q.selectItemColor(mediaId).executeAsOneOrNull()?.let {
            CachedItemColor(it.media_id, it.content_hash, Json.decodeWeightedColors(it.colors_json))
        }

    fun putItemColor(mediaId: String, contentHash: String, dayIso: String, colors: List<WeightedColor>) {
        q.upsertItemColor(mediaId, contentHash, dayIso, Json.encodeWeightedColors(colors), Clock.System.now().toEpochMilliseconds())
    }

    fun itemColorsForDay(dayIso: String): List<List<WeightedColor>> =
        q.selectItemColorsForDay(dayIso).executeAsList().map { Json.decodeWeightedColors(it.colors_json) }

    fun dayColor(day: DayKey): DayColors? =
        q.selectDayColor(day.iso()).executeAsOneOrNull()?.let { Json.decodeDayColors(it.colors_json) }

    fun dayColorsForMonth(month: MonthKey): Map<Int, DayColors> =
        q.selectDayColorsForMonth(month.year.toLong(), month.month.toLong()).executeAsList()
            .associate { it.day.toInt() to Json.decodeDayColors(it.colors_json) }

    fun dayColorSyncStates(month: MonthKey): Map<Int, ColorSyncState> =
        q.selectDayColorsForMonth(month.year.toLong(), month.month.toLong()).executeAsList()
            .associate { it.day.toInt() to runCatching { ColorSyncState.valueOf(it.color_sync) }.getOrDefault(ColorSyncState.NOT_SENT) }

    fun putDayColor(day: DayKey, colors: DayColors, sourceHash: String) {
        q.upsertDayColor(
            day.iso(), day.year.toLong(), day.month.toLong(), day.day.toLong(),
            Json.encodeDayColors(colors), sourceHash, ColorSyncState.NOT_SENT.name,
            Clock.System.now().toEpochMilliseconds(),
        )
    }

    fun dayColorSourceHash(day: DayKey): String? =
        q.selectDayColor(day.iso()).executeAsOneOrNull()?.source_hash

    fun setColorSyncState(day: DayKey, state: ColorSyncState) {
        q.updateDayColorSync(state.name, day.iso())
    }

    fun pruneItemsNotIn(liveMediaIds: List<String>) {
        if (liveMediaIds.isEmpty()) return
        q.deleteItemColorsNotIn(liveMediaIds)
    }
}

data class CachedItemColor(val mediaId: String, val contentHash: String, val colors: List<WeightedColor>)
