package com.redforge.app.util

object NumericInputParser {
    fun parseWeight(input: String): Double? =
        input.trim()
            .replace(',', '.')
            .takeIf { it.isNotEmpty() }
            ?.toDoubleOrNull()
            ?.takeIf { it >= 0.0 }

    fun parseReps(input: String): Int? =
        input.trim()
            .takeIf { it.isNotEmpty() }
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
}
