package com.journalgallery.shared.media

import com.journalgallery.shared.domain.DayBucket
import com.journalgallery.shared.domain.DayKey
import com.journalgallery.shared.domain.MediaItem
import com.journalgallery.shared.domain.MonthBucket
import com.journalgallery.shared.domain.MonthKey
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Groups a flat media list into the Month -> Day hierarchy the UI navigates. */
object MediaGrouping {

    fun dayKeyOf(item: MediaItem, zone: TimeZone = TimeZone.currentSystemDefault()): DayKey {
        val dt = Instant.fromEpochMilliseconds(item.takenAtEpochMillis).toLocalDateTime(zone)
        return DayKey(dt.year, dt.monthNumber, dt.dayOfMonth)
    }

    fun groupByMonth(
        items: List<MediaItem>,
        zone: TimeZone = TimeZone.currentSystemDefault(),
    ): List<MonthBucket> {
        val byDay: Map<DayKey, List<MediaItem>> = items.groupBy { dayKeyOf(it, zone) }

        val days: List<DayBucket> = byDay.entries
            .map { (day, dayItems) ->
                DayBucket(day, dayItems.sortedByDescending { it.takenAtEpochMillis })
            }

        return days
            .groupBy { it.day.monthKey }
            .entries
            .map { (month, monthDays) -> MonthBucket(month, monthDays.sortedBy { it.day }) }
            .sortedByDescending { it.month }
    }
}
