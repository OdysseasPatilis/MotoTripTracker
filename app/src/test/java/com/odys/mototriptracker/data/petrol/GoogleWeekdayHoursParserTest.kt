package com.odys.mototriptracker.data.petrol

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class GoogleWeekdayHoursParserTest {
    @Test
    fun parsesOpenRangeWithAmPm() {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Europe/Athens")).apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.AUGUST)
            set(Calendar.DAY_OF_MONTH, 29) // Saturday
            set(Calendar.HOUR_OF_DAY, 15)
            set(Calendar.MINUTE, 0)
        }
        val status = GoogleWeekdayHoursParser.statusNow(
            listOf("Saturday: 6:00 AM – 10:00 PM"),
            calendar
        )
        assertEquals(OpeningHoursEvaluator.Status.OPEN, status)
    }

    @Test
    fun parsesClosedDay() {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Europe/Athens")).apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.AUGUST)
            set(Calendar.DAY_OF_MONTH, 30) // Sunday
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
        }
        val status = GoogleWeekdayHoursParser.statusNow(
            listOf("Sunday: Closed"),
            calendar
        )
        assertEquals(OpeningHoursEvaluator.Status.CLOSED, status)
    }
}
