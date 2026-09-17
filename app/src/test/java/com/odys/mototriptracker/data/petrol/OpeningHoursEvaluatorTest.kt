package com.odys.mototriptracker.data.petrol

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class OpeningHoursEvaluatorTest {

    private fun calendar(
        year: Int = 2026,
        month: Int = Calendar.SEPTEMBER,
        day: Int = 17, // Thursday
        hour: Int,
        minute: Int = 0,
    ): Calendar = Calendar.getInstance(TimeZone.getTimeZone("Europe/Athens")).apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month)
        set(Calendar.DAY_OF_MONTH, day)
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    @Test
    fun emptyOrNullIsUnknown() {
        val cal = calendar(hour = 12)
        assertEquals(OpeningHoursEvaluator.Status.UNKNOWN, OpeningHoursEvaluator.status(null, cal.time, cal))
        assertEquals(OpeningHoursEvaluator.Status.UNKNOWN, OpeningHoursEvaluator.status("  ", cal.time, cal))
    }

    @Test
    fun alwaysOpenKeywords() {
        val cal = calendar(hour = 3)
        assertEquals(OpeningHoursEvaluator.Status.OPEN, OpeningHoursEvaluator.status("24/7", cal.time, cal))
        assertEquals(OpeningHoursEvaluator.Status.OPEN, OpeningHoursEvaluator.status("open", cal.time, cal))
    }

    @Test
    fun closedKeywords() {
        val cal = calendar(hour = 12)
        assertEquals(OpeningHoursEvaluator.Status.CLOSED, OpeningHoursEvaluator.status("closed", cal.time, cal))
        assertEquals(OpeningHoursEvaluator.Status.CLOSED, OpeningHoursEvaluator.status("off", cal.time, cal))
    }

    @Test
    fun weekdayRangeOpenDuringHours() {
        val cal = calendar(hour = 10) // Thu 10:00
        assertEquals(
            OpeningHoursEvaluator.Status.OPEN,
            OpeningHoursEvaluator.status("Mo-Fr 08:00-18:00", cal.time, cal)
        )
    }

    @Test
    fun weekdayRangeClosedOutsideHours() {
        val cal = calendar(hour = 20)
        assertEquals(
            OpeningHoursEvaluator.Status.CLOSED,
            OpeningHoursEvaluator.status("Mo-Fr 08:00-18:00", cal.time, cal)
        )
    }

    @Test
    fun weekendRuleClosedOnWeekday() {
        val cal = calendar(hour = 12) // Thursday
        assertEquals(
            OpeningHoursEvaluator.Status.CLOSED,
            OpeningHoursEvaluator.status("Sa-Su 09:00-14:00", cal.time, cal)
        )
    }

    @Test
    fun overnightRangeSpansMidnight() {
        // Open 22:00–06:00; Thursday 23:00 should be open
        val late = calendar(hour = 23)
        assertEquals(
            OpeningHoursEvaluator.Status.OPEN,
            OpeningHoursEvaluator.status("Mo-Su 22:00-06:00", late.time, late)
        )
        val morning = calendar(hour = 5)
        assertEquals(
            OpeningHoursEvaluator.Status.OPEN,
            OpeningHoursEvaluator.status("Mo-Su 22:00-06:00", morning.time, morning)
        )
        val midday = calendar(hour = 12)
        assertEquals(
            OpeningHoursEvaluator.Status.CLOSED,
            OpeningHoursEvaluator.status("Mo-Su 22:00-06:00", midday.time, midday)
        )
    }

    @Test
    fun dayOffOverride() {
        // Thursday open normally, but Th off
        val cal = calendar(hour = 12)
        assertEquals(
            OpeningHoursEvaluator.Status.CLOSED,
            OpeningHoursEvaluator.status("Mo-Fr 08:00-18:00; Th off", cal.time, cal)
        )
    }

    @Test
    fun unparsableIsUnknown() {
        val cal = calendar(hour = 12)
        assertEquals(
            OpeningHoursEvaluator.Status.UNKNOWN,
            OpeningHoursEvaluator.status("PH open \"sunrise-sunset\"", cal.time, cal)
        )
    }

    @Test
    fun enDashNormalized() {
        val cal = calendar(hour = 10)
        assertEquals(
            OpeningHoursEvaluator.Status.OPEN,
            OpeningHoursEvaluator.status("Mo–Fr 08:00–18:00", cal.time, cal)
        )
    }
}
