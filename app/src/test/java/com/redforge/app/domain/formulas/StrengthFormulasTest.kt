package com.redforge.app.domain.formulas

import org.junit.Test
import org.junit.Assert.assertEquals

class StrengthFormulasTest {

    @Test
    fun highRepEpleyIsCappedAtTwelveReps() {
        assertEquals(
            StrengthFormulas.epley1RM(40.0, 12),
            StrengthFormulas.epley1RM(40.0, 35),
            absoluteTolerance = 0.0001
        )
    }

    @Test
    fun highRepBrzyckiIsCappedAtTwelveReps() {
        assertEquals(
            StrengthFormulas.brzycki1RM(40.0, 12),
            StrengthFormulas.brzycki1RM(40.0, 35),
            absoluteTolerance = 0.0001
        )
    }

    @Test
    fun oneRepMaxStillUsesTheLoggedWeight() {
        assertEquals(100.0, StrengthFormulas.estimated1RM(100.0, 1), 0.0001)
    }
}
