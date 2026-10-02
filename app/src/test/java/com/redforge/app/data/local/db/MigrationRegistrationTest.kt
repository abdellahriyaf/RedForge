package com.redforge.app.data.local.db

import org.junit.Assert.assertEquals
import org.junit.Test

class MigrationRegistrationTest {

    @Test
    fun allSchemaVersionsHaveARegisteredForwardMigration() {
        assertEquals(
            listOf(2, 3, 4, 5),
            ALL_MIGRATIONS.map { it.endVersion }
        )
        assertEquals(
            listOf(1, 2, 3, 4),
            ALL_MIGRATIONS.map { it.startVersion }
        )
    }
}
