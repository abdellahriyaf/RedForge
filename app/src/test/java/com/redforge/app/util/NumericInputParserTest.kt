package com.redforge.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumericInputParserTest {

    @Test
    fun weightAcceptsCommaDecimal() {
        assertEquals(82.5, NumericInputParser.parseWeight("82,5")!!, 0.0001)
    }

    @Test
    fun weightAcceptsDotDecimal() {
        assertEquals(82.5, NumericInputParser.parseWeight("82.5")!!, 0.0001)
    }

    @Test
    fun weightRejectsInvalidInput() {
        assertNull(NumericInputParser.parseWeight("82..5"))
        assertNull(NumericInputParser.parseWeight(""))
        assertNull(NumericInputParser.parseWeight("-5"))
    }

    @Test
    fun repsRequiresPositiveWholeNumber() {
        assertEquals(8, NumericInputParser.parseReps("8")!!)
        assertNull(NumericInputParser.parseReps("8.5"))
        assertNull(NumericInputParser.parseReps("0"))
        assertNull(NumericInputParser.parseReps(""))
    }
}
