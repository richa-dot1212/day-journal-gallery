package com.journalgallery.shared.media

import com.journalgallery.shared.domain.MonthBucket
import com.journalgallery.shared.domain.MonthKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The single source of truth for grouped gallery contents. Reads the platform [MediaSource]
 * once, groups in common code, and caches the result until [refresh] is called again.
 */
class MediaRepository(private val source: MediaSource) {

    private val _months = MutableStateFlow<List<MonthBucket>>(emptyList())
    val months: StateFlow<List<MonthBucket>> = _months.asStateFlow()

    private val refreshLock = Mutex()

    suspend fun hasPermission(): Boolean = source.hasPermission()

    /** Re-scan the device and regroup. Safe to call repeatedly; serialized internally. */
    suspend fun refresh() = refreshLock.withLock {
        val all = source.listAll()
        _months.value = MediaGrouping.groupByMonth(all)
    }

    fun month(key: MonthKey): MonthBucket? = _months.value.firstOrNull { it.month == key }

    /** Every live media id, for cache pruning. */
    fun liveMediaIds(): List<String> =
        _months.value.flatMap { m -> m.days.flatMap { d -> d.items.map { it.id } } }
}
