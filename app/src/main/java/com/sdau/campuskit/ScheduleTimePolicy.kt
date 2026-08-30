package com.sdau.campuskit

import java.util.Calendar

internal enum class ScheduleMode {
    SPRING,
    SUMMER
}

/**
 * Selects the university timetable from the device's local calendar.
 *
 * Spring/autumn timetable: August 1 through April 30 of the following year.
 * Summer timetable: May 1 through July 31.
 */
internal object ScheduleTimePolicy {
    fun modeFor(date: Calendar): ScheduleMode {
        val month = date.get(Calendar.MONTH)
        return if (month in Calendar.MAY..Calendar.JULY) {
            ScheduleMode.SUMMER
        } else {
            ScheduleMode.SPRING
        }
    }

    fun currentMode(): ScheduleMode = modeFor(Calendar.getInstance())
}
