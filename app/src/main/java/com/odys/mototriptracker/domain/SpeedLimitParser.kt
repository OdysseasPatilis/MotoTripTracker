package com.odys.mototriptracker.domain

import kotlin.math.roundToInt

object SpeedLimitParser {

    private val MPH_REGEX = Regex("""(\d+)\s*mph""", RegexOption.IGNORE_CASE)
    private val NUMBER_REGEX = Regex("""(\d+)""")

    fun parse(raw: String?): Int? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isBlank()) return null

        val lower = trimmed.lowercase()
        if (lower in UNSUPPORTED_VALUES) return null
        if (lower.contains(':') && !lower.contains("km/h") && !lower.contains("mph")) return null

        MPH_REGEX.find(trimmed)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { mph ->
            return (mph * 1.60934).roundToInt()
        }

        NUMBER_REGEX.find(trimmed)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { kmh ->
            return kmh.coerceIn(5, 200)
        }

        return null
    }

    private val UNSUPPORTED_VALUES = setOf(
        "signals",
        "variable",
        "none",
        "walk",
        "yes",
        "no"
    )
}
