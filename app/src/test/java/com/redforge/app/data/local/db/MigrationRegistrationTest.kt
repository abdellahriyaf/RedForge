package com.redforge.app.data.local.db

import org.junit.Assert.assertEquals
import org.junit.Test

class MigrationRegistrationTest {

    @Test
    fun currentSchemaHasARegisteredForwardMigrationForEveryUpgrade() {
        assertEquals(
            listOf(2, 3),
            ALL_MIGRATIONS.map { it.endVersion }
        )
        assertEquals(
            listOf(1, 2),
            ALL_MIGRATIONS.map { it.startVersion }
        )
    }
}
