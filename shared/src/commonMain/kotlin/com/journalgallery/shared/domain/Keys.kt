package com.journalgallery.shared.domain

import kotlinx.serialization.Serializable

/**
 * A calendar month. [year] is a full year (e.g. 2026); [month] is 1..12.
 * The ESP32 board only tracks [month] (1..12); [year] disambiguates on the phone side.
 */
@Serializable
data class MonthKey(val year: Int, val month: Int) : Comparable<MonthKey> {
    init {
        require(month in 1..12) { "month must be 1..12, was $month" }
    }

    override fun compareTo(other: MonthKey): Int =
        if (year != other.year) year.compareTo(other.year) else month.compareTo(other.month)

    /** Number of days in this month (Gregorian). */
    val lengthInDays: Int
        get() = when (month) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            2 -> if (isLeapYear(year)) 29 else 28
            else -> error("unreachable")
        }

    companion object {
        fun isLeapYear(year: Int): Boolean =
            (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
    }
}

/** A specific calendar day. [day] is 1..31 and must be valid for [month]. */
@Serializable
data class DayKey(val year: Int, val month: Int, val day: Int) : Comparable<DayKey> {
    init {
        require(month in 1..12) { "month must be 1..12, was $month" }
        require(day in 1..31) { "day must be 1..31, was $day" }
    }

    val monthKey: MonthKey get() = MonthKey(year, month)

    override fun compareTo(other: DayKey): Int {
        if (year != other.year) return year.compareTo(other.year)
        if (month != other.month) return month.compareTo(other.month)
        return day.compareTo(other.day)
    }

    /** Stable string form used as a DB key and SD-card path component: `2026-08-07`. */
    fun iso(): String = "$year-${pad2(month)}-${pad2(day)}"

    companion object {
        private fun pad2(v: Int): String = if (v < 10) "0$v" else "$v"
    }
}
