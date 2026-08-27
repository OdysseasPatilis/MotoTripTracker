package com.odys.mototriptracker.domain

import kotlin.math.roundToInt

/**
 * Parses OpenStreetMap `maxspeed` tag values into km/h.
 * Parity with iOS `OSMMaxSpeedParser`.
 */
object SpeedLimitParser {

    private val implicitLimits = mapOf(
        "gr:urban" to 50,
        "gr:rural" to 90,
        "gr:trunk" to 110,
        "gr:motorway" to 130,
        "gr:living_street" to 20,
        "urban" to 50,
        "rural" to 90,
        "trunk" to 110,
        "motorway" to 130,
        "living_street" to 20,
        "walk" to 5,
        "none" to 0
    )

    fun parse(raw: String?): Int? {
        val value = raw?.trim()?.lowercase().orEmpty()
        if (value.isEmpty()) return null

        implicitLimits[value]?.let { implicit ->
            return if (implicit == 0) null else implicit
        }

        if (value == "signals" || value == "variable" || value.startsWith("maxspeed:variable")) {
            return null
        }

        if (value.contains("mph")) {
            val digits = value.filter { it.isDigit() || it == '.' }
            val mph = digits.toDoubleOrNull() ?: return null
            return (mph * 1.60934).roundToInt().takeIf { it > 0 }
        }

        val numeric = value
            .replace("km/h", "")
            .replace("kph", "")
            .trim()
            .filter { it.isDigit() }
        val kmh = numeric.toIntOrNull() ?: return null
        return kmh.takeIf { it > 0 }?.coerceIn(5, 200)
    }
}
