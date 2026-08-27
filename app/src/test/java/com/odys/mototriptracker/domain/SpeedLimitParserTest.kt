package com.odys.mototriptracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeedLimitParserTest {

    @Test
    fun parse_plainKmh() {
        assertEquals(50, SpeedLimitParser.parse("50"))
        assertEquals(90, SpeedLimitParser.parse("90 km/h"))
    }

    @Test
    fun parse_mph() {
        assertEquals(48, SpeedLimitParser.parse("30 mph"))
    }

    @Test
    fun parse_implicitCountryLimits() {
        assertEquals(50, SpeedLimitParser.parse("GR:urban"))
        assertEquals(90, SpeedLimitParser.parse("gr:rural"))
        assertEquals(130, SpeedLimitParser.parse("gr:motorway"))
        assertEquals(20, SpeedLimitParser.parse("living_street"))
    }

    @Test
    fun parse_unsupportedValues() {
        assertNull(SpeedLimitParser.parse("signals"))
        assertNull(SpeedLimitParser.parse("variable"))
        assertNull(SpeedLimitParser.parse("none"))
    }

    @Test
    fun parse_blank() {
        assertNull(SpeedLimitParser.parse(null))
        assertNull(SpeedLimitParser.parse(""))
    }
}
