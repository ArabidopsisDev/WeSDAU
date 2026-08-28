package com.sdau.campuskit

import java.util.Calendar

/** Fixed annual holidays that hide scheduled classes without changing teaching-week numbers. */
internal object CampusHolidayCalendar {
    fun isHoliday(date: Calendar): Boolean {
        val year = date.get(Calendar.YEAR)
        val month = date.get(Calendar.MONTH) + 1
        val day = date.get(Calendar.DAY_OF_MONTH)
        if (year == 2026 && month == 9 && day in 25..27) return true
        return when (month) {
            1 -> day in 1..3
            5 -> day in 1..5
            10 -> day in 1..7
            else -> false
        }
    }
}
