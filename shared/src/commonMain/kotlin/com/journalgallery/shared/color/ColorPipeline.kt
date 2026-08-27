package com.journalgallery.shared.color

import com.journalgallery.shared.data.ColorCache
import com.journalgallery.shared.domain.DayBucket
import com.journalgallery.shared.domain.DayColors
import com.journalgallery.shared.domain.MediaItem
import com.journalgallery.shared.media.MediaRepository
import com.journalgallery.shared.media.MediaSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext

/**
 * Incremental per-item + per-day dominant-color computation with content-hash caching.
 *
 * [sweep] walks every day, extracts colors only for items whose `contentHash` changed,
 * re-aggregates a day only when its contributing set changed, and emits [DayColorReady]
 * for each day it (re)computes so the UI can fill gradients progressively.
 */
class ColorPipeline(
    private val repo: MediaRepository,
    private val source: MediaSource,
    private val cache: ColorCache,
    private val computeDispatcher: CoroutineDispatcher,
) {
    private val _updates = MutableSharedFlow<DayColorReady>(extraBufferCapacity = 64)
    val updates: SharedFlow<DayColorReady> = _updates

    data class DayColorReady(val dayIso: String, val colors: DayColors)
    data class Progress(val daysDone: Int, val daysTotal: Int)

    var onProgress: ((Progress) -> Unit)? = null

    suspend fun sweep() = withContext(computeDispatcher) {
        val months = repo.months.value
        val allDays = months.flatMap { it.days }
        cache.pruneItemsNotIn(repo.liveMediaIds())

        allDays.forEachIndexed { index, day ->
            recomputeDay(day)
            onProgress?.invoke(Progress(index + 1, allDays.size))
        }
    }

    /** Recompute a single day (used by the "new photo -> only that day" path). */
    suspend fun recomputeDay(day: DayBucket) = withContext(computeDispatcher) {
        val perItem = ArrayList<List<com.journalgallery.shared.domain.WeightedColor>>(day.items.size)

        for (item in day.items) {
            val cached = cache.itemColor(item.id)
            if (cached != null && cached.contentHash == item.contentHash) {
                perItem.add(cached.colors)
                continue
            }
            val pixels = source.loadPixels(item) ?: continue
            val colors = ColorExtractor.extract(pixels, k = 3)
            if (colors.isEmpty()) continue
            cache.putItemColor(item.id, item.contentHash, day.day.iso(), colors)
            perItem.add(colors)
        }

        val sourceHash = day.items.joinToString("|") { "${it.id}:${it.contentHash}" }.hashCode().toString()
        if (cache.dayColorSourceHash(day.day) == sourceHash && cache.dayColor(day.day) != null) return@withContext

        val aggregated = DayColorAggregator.aggregate(perItem) ?: return@withContext
        cache.putDayColor(day.day, aggregated, sourceHash)
        _updates.emit(DayColorReady(day.day.iso(), aggregated))
    }
}
