package com.journalgallery.shared.orb

import com.journalgallery.shared.domain.MonthBucket
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * The orb only knows (month, day) — no year. Pick the year the way a person would expect:
 * the most recent year in the library that actually has media on that date, otherwise the
 * current year.
 */
object DayResolution {
    fun resolveYear(
        months: List<MonthBucket>,
        month: Int,
        day: Int,
        now: () -> Int = { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year },
    ): Int {
        val fromLibrary = months
            .filter { it.month.month == month && it.days.any { d -> d.day.day == day } }
            .maxByOrNull { it.month.year }
            ?.month?.year
        return fromLibrary ?: now()
    }
}
